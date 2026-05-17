package com.wynvers.customevents.worldedit;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import io.github.pigaut.orestack.api.Orestack;
import io.github.pigaut.orestack.api.OrestackAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * WorldEdit / FAWE {@link Pattern} that turns every applied block into an
 * OreStack global generator of the configured template.
 *
 * <p>Usage in-game:
 * <pre>//replace teak_wood orestackgenerator:teak</pre>
 *
 * <p>{@link #applyBlock(BlockVector3)} is called by WorldEdit (potentially
 * from a FAWE worker thread) once per position. We:
 * <ol>
 *   <li>Return {@link BlockTypes#AIR} so WorldEdit/FAWE clears the target
 *       block first — the generator's first-phase block is then placed by
 *       OreStack itself.</li>
 *   <li>Queue the Bukkit {@link Location} and schedule a Bukkit drain task
 *       on the main thread that creates the actual generator. Generator
 *       creation must run on the main thread because OreStack mutates Bukkit
 *       state.</li>
 * </ol>
 *
 * <p>OreStack's API does not expose generator creation, so we reflectively
 * call the internal {@code GlobalGenerator.create(template, location)}
 * factory. Reflection handles are cached as static fields on first use.
 */
public final class OrestackGeneratorPattern implements Pattern {

    private static final String GLOBAL_GENERATOR_CLASS =
            "io.github.pigaut.orestack.generator.global.GlobalGenerator";
    private static final String GENERATOR_TEMPLATE_CLASS =
            "io.github.pigaut.orestack.generator.template.GeneratorTemplate";
    private static final String ORESTACK_PLUGIN_CLASS =
            "io.github.pigaut.orestack.OrestackPlugin";

    /** Tick delay before the first drain pass. Gives FAWE time to flush. */
    private static final long INITIAL_DRAIN_DELAY_TICKS = 2L;
    /** Maximum generators to place per tick (cap to avoid lag spikes). */
    private static final int CREATES_PER_TICK = 200;

    private static volatile Method getInstanceMethod;
    private static volatile Method getGeneratorTemplateMethod;
    private static volatile Method createMethod;
    private static volatile Class<?> templateClass;

    private final Plugin plugin;
    private final World weWorld;
    private final String generatorName;
    private final BaseBlock placeholder;

    private final ConcurrentLinkedQueue<Location> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public OrestackGeneratorPattern(@NotNull Plugin plugin,
                                    @NotNull World weWorld,
                                    @NotNull String generatorName) {
        this.plugin = plugin;
        this.weWorld = weWorld;
        this.generatorName = generatorName;
        this.placeholder = BlockTypes.AIR.getDefaultState().toBaseBlock();
    }

    @Override
    public BaseBlock applyBlock(BlockVector3 position) {
        org.bukkit.World bukkitWorld = BukkitAdapter.adapt(weWorld);
        if (bukkitWorld != null) {
            pending.add(new Location(bukkitWorld,
                    position.getX() + 0.5, position.getY(), position.getZ() + 0.5));
            scheduleDrain();
        }
        return placeholder;
    }

    // -------------------------------------------------------------------------
    // Drain loop
    // -------------------------------------------------------------------------

    private void scheduleDrain() {
        if (!draining.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, this::drain, INITIAL_DRAIN_DELAY_TICKS);
    }

    private void drain() {
        OrestackAPI api;
        try {
            api = Orestack.getAPI();
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("OreStack API not initialized — orestackgenerator pattern skipped.");
            pending.clear();
            draining.set(false);
            return;
        }

        int budget = CREATES_PER_TICK;
        Location loc;
        while (budget-- > 0 && (loc = pending.poll()) != null) {
            // Block-aligned location: OreStack stores generators keyed by
            // exact block-corner Location, so trim the offset we used for
            // pretty printing.
            Location blockLoc = new Location(loc.getWorld(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            createGenerator(api, blockLoc);
        }

        if (pending.isEmpty()) {
            draining.set(false);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, this::drain, 1L);
        }
    }

    private void createGenerator(@NotNull OrestackAPI api, @NotNull Location location) {
        try {
            if (api.isGenerator(location)) {
                // Already a generator – skip silently to avoid overlap errors
                // when the same area is repeatedly //replace'd.
                return;
            }

            Method getInstance = getOrLoadGetInstance();
            Object orestackPlugin = getInstance.invoke(null);
            Object template = getOrLoadGetTemplate().invoke(orestackPlugin, generatorName);
            if (template == null) {
                plugin.getLogger().warning(
                        "OreStack generator template not found: " + generatorName);
                return;
            }
            getOrLoadCreate().invoke(null, template, location);
        } catch (Throwable t) {
            // GeneratorOverlapException / VirtualGeneratorUnsupportedException
            // bubble through here. We log once at WARNING per failure.
            plugin.getLogger().log(Level.WARNING,
                    "Failed to place OreStack generator '" + generatorName
                            + "' at " + location.getWorld().getName()
                            + " " + location.getBlockX() + "/"
                            + location.getBlockY() + "/"
                            + location.getBlockZ()
                            + ": " + rootMessage(t));
        }
    }

    // -------------------------------------------------------------------------
    // Lazy reflection lookups
    // -------------------------------------------------------------------------

    private static Method getOrLoadGetInstance() throws ReflectiveOperationException {
        Method m = getInstanceMethod;
        if (m == null) {
            Class<?> cls = Class.forName(ORESTACK_PLUGIN_CLASS);
            m = cls.getMethod("getInstance");
            getInstanceMethod = m;
        }
        return m;
    }

    private static Method getOrLoadGetTemplate() throws ReflectiveOperationException {
        Method m = getGeneratorTemplateMethod;
        if (m == null) {
            Class<?> cls = Class.forName(ORESTACK_PLUGIN_CLASS);
            m = cls.getMethod("getGeneratorTemplate", String.class);
            getGeneratorTemplateMethod = m;
        }
        return m;
    }

    private static Method getOrLoadCreate() throws ReflectiveOperationException {
        Method m = createMethod;
        if (m == null) {
            Class<?> tpl = templateClass;
            if (tpl == null) {
                tpl = Class.forName(GENERATOR_TEMPLATE_CLASS);
                templateClass = tpl;
            }
            Class<?> globalGen = Class.forName(GLOBAL_GENERATOR_CLASS);
            m = globalGen.getMethod("create", tpl, Location.class);
            createMethod = m;
        }
        return m;
    }

    /** Returns whether the given generator name resolves to a real template. */
    public static boolean isKnownGenerator(@NotNull String name) {
        try {
            Method getInstance = getOrLoadGetInstance();
            Object orestackPlugin = getInstance.invoke(null);
            Object template = getOrLoadGetTemplate().invoke(orestackPlugin, name);
            return template != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null ? cur.getClass().getSimpleName() + ": " + msg
                : cur.getClass().getSimpleName();
    }
}