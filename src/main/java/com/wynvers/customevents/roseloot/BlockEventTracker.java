package com.wynvers.customevents.roseloot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the {@link BlockBreakEvent} currently being processed on the main
 * thread so {@link CancelEventLootItem} can reach it from inside RoseLoot's
 * {@code triggerExtras()} call.
 *
 * <p>Bukkit dispatches events synchronously on the main thread, so a
 * ThreadLocal per event chain is safe.
 */
public final class BlockEventTracker implements Listener {

    private static final ThreadLocal<BlockBreakEvent> CURRENT = new ThreadLocal<>();

    public static @Nullable BlockBreakEvent current() {
        return CURRENT.get();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreakLowest(BlockBreakEvent event) {
        CURRENT.set(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        CURRENT.remove();
    }
}