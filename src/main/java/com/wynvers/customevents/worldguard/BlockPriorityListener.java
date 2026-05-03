package com.wynvers.customevents.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Honors the {@link BlockPriorityFlag} on {@link BlockBreakEvent}.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} <em>after</em> WorldGuard's
 * region-protection listener has had a chance to cancel the event. If the
 * broken block matches an entry in the region's {@code block-priority} set,
 * the event is uncancelled — emulating a block-sized region with higher
 * priority than the surrounding region.
 */
public class BlockPriorityListener implements Listener {

    private final boolean nexoAvailable;

    public BlockPriorityListener() {
        this.nexoAvailable = Bukkit.getPluginManager().getPlugin("Nexo") != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) return;
        if (BlockPriorityFlag.get() == null) return;

        Block block = event.getBlock();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));

        Set<String> allowed = regions.queryValue(null, BlockPriorityFlag.get());
        if (allowed == null || allowed.isEmpty()) return;

        if (matches(block, allowed)) {
            event.setCancelled(false);
        }
    }

    private boolean matches(Block block, Set<String> allowed) {
        if (nexoAvailable) {
            try {
                var mech = com.nexomc.nexo.api.NexoBlocks.customBlockMechanic(block.getLocation());
                if (mech != null) {
                    String id = mech.getItemID();
                    if (id != null) {
                        String lower = id.toLowerCase(Locale.ROOT);
                        for (String entry : allowed) {
                            String n = normalize(entry);
                            if (n.equals(lower) || n.equals("nexo:" + lower)) return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Nexo missing or block isn't a custom block — fall through to vanilla check.
            }
        }

        String name = block.getType().name().toLowerCase(Locale.ROOT);
        for (String entry : allowed) {
            String n = normalize(entry);
            if (n.equals(name) || n.equals("minecraft:" + name)) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}