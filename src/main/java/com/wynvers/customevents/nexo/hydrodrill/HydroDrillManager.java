package com.wynvers.customevents.nexo.hydrodrill;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Holds runtime state for active Hydro-Siege Drills:
 * <ul>
 *   <li>Active drill entities → countdown task + placer.</li>
 *   <li>Per-player cooldowns.</li>
 *   <li>Blocks swapped to cobblestone, scheduled for restoration to their
 *       original {@link BlockData} (preserves orientation, waterlogged, etc.).</li>
 * </ul>
 *
 * <p>Pure state. No event listening.
 */
public class HydroDrillManager {

    public static final class ActiveDrill {
        final UUID placerId;
        final BukkitTask countdownTask;
        public ActiveDrill(UUID placerId, BukkitTask countdownTask) {
            this.placerId = placerId;
            this.countdownTask = countdownTask;
        }
    }

    public static final class BlockSwap {
        final long restoreAtMs;
        final UUID drillId;
        final BlockData originalData;
        public BlockSwap(long restoreAtMs, UUID drillId, BlockData originalData) {
            this.restoreAtMs = restoreAtMs;
            this.drillId = drillId;
            this.originalData = originalData;
        }
    }

    private final Map<UUID, ActiveDrill> activeDrills = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<Location, BlockSwap> blockSwaps = new HashMap<>();

    public boolean isOnCooldown(UUID playerId) {
        Long expiresAt = cooldowns.get(playerId);
        if (expiresAt == null) return false;
        if (System.currentTimeMillis() >= expiresAt) {
            cooldowns.remove(playerId);
            return false;
        }
        return true;
    }

    public long cooldownRemainingSeconds(UUID playerId) {
        Long expiresAt = cooldowns.get(playerId);
        if (expiresAt == null) return 0;
        long remaining = expiresAt - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public void setCooldown(UUID playerId, int seconds) {
        cooldowns.put(playerId, System.currentTimeMillis() + seconds * 1000L);
    }

    public void registerDrill(UUID drillId, ActiveDrill drill) {
        activeDrills.put(drillId, drill);
    }

    public boolean isActiveDrill(UUID entityId) {
        return activeDrills.containsKey(entityId);
    }

    public ActiveDrill removeDrill(UUID drillId) {
        return activeDrills.remove(drillId);
    }

    public void registerBlockSwap(Location loc, BlockSwap swap) {
        blockSwaps.put(loc, swap);
    }

    /** Removes a swap from tracking — used when TNT breaks the swapped cobblestone. */
    public BlockSwap forgetBlockSwap(Location loc) {
        return blockSwaps.remove(loc);
    }

    public boolean isSwappedBlock(Location loc) {
        return blockSwaps.containsKey(loc);
    }

    /**
     * Restores any tracked block swap whose restore deadline has passed.
     * Cobblestone broken by TNT meanwhile is left alone (it was forgotten).
     */
    public void runRestorationSweep() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Location, BlockSwap>> it = blockSwaps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, BlockSwap> e = it.next();
            if (now < e.getValue().restoreAtMs) continue;

            Block b = e.getKey().getBlock();
            if (b.getType() == Material.COBBLESTONE) {
                b.setBlockData(e.getValue().originalData, false);
            }
            it.remove();
        }
    }

    /** Called on plugin disable: cancel all countdown tasks and restore swaps. */
    public void shutdown() {
        for (ActiveDrill d : activeDrills.values()) {
            try { d.countdownTask.cancel(); } catch (Throwable ignored) {}
        }
        activeDrills.clear();
        cooldowns.clear();

        for (Map.Entry<Location, BlockSwap> e : blockSwaps.entrySet()) {
            Block b = e.getKey().getBlock();
            if (b.getType() == Material.COBBLESTONE) {
                b.setBlockData(e.getValue().originalData, false);
            }
        }
        blockSwaps.clear();
    }
}
