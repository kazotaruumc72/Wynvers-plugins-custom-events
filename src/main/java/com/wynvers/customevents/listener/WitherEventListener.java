package com.wynvers.customevents.listener;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.NexoWitherPropertiesLoader;
import com.wynvers.customevents.nexo.WitherProperties;
import com.wynvers.customevents.nexo.WitherPropertiesMechanic;
import com.wynvers.customevents.nexo.WitherPropertiesMechanicFactory;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
/**
 * Protects Nexo custom blocks from Wither / Wither-skull damage.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Hook into {@link EntityExplodeEvent} at {@code LOWEST} to remember
 *       which entity is currently exploding (Wither body or skull).</li>
 *   <li>Hook into Nexo's {@link NexoBlockBreakEvent} (fired right before Nexo
 *       breaks one of its custom blocks): if the block has a
 *       {@code wither_properties} mechanic that forbids that source, cancel
 *       the event – Nexo then leaves the block intact.</li>
 *   <li>Also remove the block from the vanilla {@code blockList()} so vanilla
 *       drops don't kick in either.</li>
 *   <li>Cancel {@link EntityChangeBlockEvent} for the Wither body's contact
 *       damage (when the boss walks through blocks).</li>
 * </ul>
 *
 * <p>This bypasses the issue where Nexo's own listener removes its custom
 * blocks from {@code EntityExplodeEvent.blockList()} before our HIGHEST
 * listener runs and breaks them itself via internal API calls.
 */
public class WitherEventListener implements Listener {
    /** Tracks the type of explosion entity the current Nexo block break belongs to. */
    private final WeakHashMap<UUID, EntityType> explosionEntityType = new WeakHashMap<>();
    /** Tracks block locations protected during the current explosion (per entity). */
    private final WeakHashMap<UUID, Set<String>> protectedThisTick = new WeakHashMap<>();
    private final WynversCustomEvents plugin;
    private final NexoWitherPropertiesLoader fallbackLoader;
    public WitherEventListener(WynversCustomEvents plugin, NexoWitherPropertiesLoader fallbackLoader) {
        this.plugin = plugin;
        this.fallbackLoader = fallbackLoader;
    }
    // -------------------------------------------------------------------------
    // 1. Mark the explosion source as early as possible
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityExplodeEarly(EntityExplodeEvent event) {
        EntityType type = event.getEntity().getType();
        if (type != EntityType.WITHER && type != EntityType.WITHER_SKULL) return;
        UUID id = event.getEntity().getUniqueId();
        explosionEntityType.put(id, type);
        protectedThisTick.put(id, new HashSet<>());
        boolean debug = plugin.getConfig().getBoolean("wither-debug", true);
        if (debug) plugin.getLogger().info("[WitherDebug] explosion start: " + type
                + " size=" + event.blockList().size());
        // ALSO try to remove protected Nexo blocks from blockList for vanilla path.
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Resolution r = resolveByBlock(b);
            if (r == null) continue;
            if ((type == EntityType.WITHER && !r.allowsExplosion()) ||
                (type == EntityType.WITHER_SKULL && !r.allowsSkull())) {
                it.remove();
                if (debug) plugin.getLogger().info(
                        "[WitherDebug]   pre-removed " + b.getType() + " @"
                        + b.getX() + "," + b.getY() + "," + b.getZ());
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplodeCleanup(EntityExplodeEvent event) {
        UUID id = event.getEntity().getUniqueId();
        explosionEntityType.remove(id);
        protectedThisTick.remove(id);
    }
    // -------------------------------------------------------------------------
    // 2. Hook into Nexo's own block-break event
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNexoBlockBreak(NexoBlockBreakEvent event) {
        // Player breaks (mining) are not our business.
        if (event.getPlayer() != null) return;
        Block block = event.getBlock();
        String itemId;
        try {
            itemId = event.getMechanic().getItemID();
        } catch (Throwable t) {
            return;
        }
        Resolution r = resolveByItemId(itemId);
        if (r == null) return;
        // Find which active wither/skull explosion this break belongs to.
        EntityType current = currentExplosionAround(block);
        boolean debug = plugin.getConfig().getBoolean("wither-debug", true);
        if (debug) plugin.getLogger().info(
                "[WitherDebug] NexoBlockBreakEvent itemId=" + itemId
                + " currentExplosion=" + current);
        if (current == EntityType.WITHER && !r.allowsExplosion()) {
            event.setCancelled(true);
        } else if (current == EntityType.WITHER_SKULL && !r.allowsSkull()) {
            event.setCancelled(true);
        }
    }
    // -------------------------------------------------------------------------
    // 3. Wither body contact damage (walking through blocks)
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWitherChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.WITHER) return;
        Resolution r = resolveByBlock(event.getBlock());
        if (r == null) return;
        if (!r.allowsExplosion()) {
            event.setCancelled(true);
        }
    }
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    /** Returns the type of any wither/skull explosion currently active. */
    private EntityType currentExplosionAround(Block block) {
        // We don't track per-position which entity exploded where, so any
        // active explosion type wins. Multiple simultaneous wither explosions
        // overlapping is extremely rare; the first found is good enough.
        for (EntityType t : explosionEntityType.values()) {
            return t;
        }
        return null;
    }
    private Resolution resolveByBlock(Block block) {
        String itemId = nexoItemId(block);
        if (itemId == null) return null;
        return resolveByItemId(itemId);
    }
    private Resolution resolveByItemId(String itemId) {
        if (itemId == null) return null;
        WitherPropertiesMechanicFactory factory = WitherPropertiesMechanicFactory.instance();
        if (factory != null) {
            WitherPropertiesMechanic m = factory.getMechanic(itemId);
            if (m != null) return new Resolution(m.allowsWitherExplosionDamage(), m.allowsWitherDamageThrow());
        }
        if (fallbackLoader != null) {
            WitherProperties p = fallbackLoader.getProperties(itemId);
            if (p != null) return new Resolution(p.allowsWitherExplosionDamage(), p.allowsWitherDamageThrow());
        }
        return null;
    }
    private String nexoItemId(Block block) {
        try {
            var m = com.nexomc.nexo.api.NexoBlocks.customBlockMechanic(block);
            if (m != null) return m.getItemID();
        } catch (Throwable ignored) {}
        try {
            var m = com.nexomc.nexo.api.NexoBlocks.customBlockMechanic(block.getBlockData());
            if (m != null) return m.getItemID();
        } catch (Throwable ignored) {}
        try {
            var m = com.nexomc.nexo.api.NexoBlocks.customBlockMechanic(block.getLocation());
            if (m != null) return m.getItemID();
        } catch (Throwable ignored) {}
        return null;
    }
    private record Resolution(boolean allowsExplosion, boolean allowsSkull) {}
}