package com.wynvers.customevents.nexo.enderjammer;

import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.wynvers.customevents.integration.SaberFactionsHook;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Nexo {@link MechanicFactory} for the {@code ender_jammer} mechanic.
 *
 * <p>Self-contained: parses items, registers active jammers on placement,
 * intercepts {@link ProjectileLaunchEvent} for ender pearls and tracks
 * in-flight pearls until they cross a jammer cube.
 */
public class EnderJammerMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "ender_jammer";

    private static EnderJammerMechanicFactory instance;

    private final JavaPlugin plugin;
    private final EnderJammerManager manager;

    public EnderJammerMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        this.manager = new EnderJammerManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static EnderJammerMechanicFactory instance() {
        return instance;
    }

    public EnderJammerManager manager() {
        return manager;
    }

    @Override
    public @Nullable EnderJammerMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof EnderJammerMechanic j) ? j : null;
    }

    @Override
    public @Nullable EnderJammerMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof EnderJammerMechanic j) ? j : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        EnderJammerMechanic mechanic = new EnderJammerMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    // ─── Placement / removal ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurniturePlace(NexoFurniturePlaceEvent event) {
        FurnitureMechanic furn = event.getMechanic();
        if (furn == null) return;
        EnderJammerMechanic jammer = getMechanic(furn.getItemID());
        if (jammer == null) return;

        ItemDisplay base = event.getBaseEntity();
        Block block = event.getBlock();
        if (base == null || block == null) return;

        manager.register(new EnderJammerManager.ActiveJammer(
                base.getUniqueId(), block.getLocation(), jammer));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnitureBreak(NexoFurnitureBreakEvent event) {
        ItemDisplay base = event.getBaseEntity();
        if (base == null) return;
        manager.unregister(base.getUniqueId());
    }

    // ─── Pearl launch interception ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        ProjectileSource source = pearl.getShooter();
        if (!(source instanceof Player launcher)) return;

        if (manager.all().isEmpty()) return;

        // Pre-filter: jammers in the launcher's world that the launcher's
        // faction sees as ENEMY. Without Factions, isInEnemyClaim returns
        // false → list stays empty and the pearl is not blocked.
        List<EnderJammerManager.ActiveJammer> applicable = new ArrayList<>();
        for (EnderJammerManager.ActiveJammer j : manager.all()) {
            if (j.center.getWorld() == null) continue;
            if (!j.center.getWorld().getUID().equals(launcher.getWorld().getUID())) continue;
            if (!SaberFactionsHook.isInEnemyClaim(launcher, j.center)) continue;
            applicable.add(j);
        }
        if (applicable.isEmpty()) return;

        // Launcher already inside a jammer cube → cancel at launch.
        for (EnderJammerManager.ActiveJammer j : applicable) {
            if (!j.mechanic.blockInside()) continue;
            if (j.contains(launcher.getLocation())) {
                event.setCancelled(true);
                launcher.sendMessage("§5[Brouilleur] §cVotre Ender Pearl est neutralisée.");
                launcher.getWorld().playSound(launcher.getLocation(),
                        Sound.BLOCK_GLASS_BREAK, 1.0f, 1.4f);
                return;
            }
        }

        // Otherwise track the pearl mid-flight.
        trackPearl(pearl, applicable);
    }

    private void trackPearl(EnderPearl pearl,
                            List<EnderJammerManager.ActiveJammer> applicable) {
        new BukkitRunnable() {
            int ticks = 0;
            final int interval = applicable.get(0).mechanic.trackTickInterval();
            final int maxTicks = applicable.get(0).mechanic.maxTrackTicks();

            @Override
            public void run() {
                if (!pearl.isValid() || pearl.isDead()) {
                    cancel();
                    return;
                }
                ticks += interval;
                if (ticks > maxTicks) {
                    cancel();
                    return;
                }
                for (EnderJammerManager.ActiveJammer j : applicable) {
                    if (j.contains(pearl.getLocation())) {
                        org.bukkit.Location at = pearl.getLocation();
                        pearl.remove();
                        if (at.getWorld() != null) {
                            at.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.4f);
                            at.getWorld().spawnParticle(Particle.SMOKE, at, 20,
                                    0.3, 0.3, 0.3, 0.02);
                        }
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin,
                applicable.get(0).mechanic.trackTickInterval(),
                applicable.get(0).mechanic.trackTickInterval());
    }
}