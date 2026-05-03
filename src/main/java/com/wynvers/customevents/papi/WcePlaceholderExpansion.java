package com.wynvers.customevents.papi;

import com.wynvers.customevents.WynversCustomEvents;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion exposing the coordinates of the player's last
 * broken block.
 *
 * <p>Identifier: {@code wce}. Provided placeholders:
 * <ul>
 *   <li>{@code %wce_block_break_x%} / {@code %wce_break_block_x%}</li>
 *   <li>{@code %wce_block_break_y%} / {@code %wce_break_block_y%}</li>
 *   <li>{@code %wce_block_break_z%} / {@code %wce_break_block_z%}</li>
 * </ul>
 *
 * <p>Returns an empty string if the player has not broken a block since the
 * server started. State is in-memory and not persisted across restarts.
 */
public class WcePlaceholderExpansion extends PlaceholderExpansion implements Listener {

    private final WynversCustomEvents plugin;
    private final ConcurrentHashMap<UUID, int[]> lastBreak = new ConcurrentHashMap<>();

    public WcePlaceholderExpansion(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "wce"; }
    @Override public @NotNull String getAuthor() { return "Wynvers"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        int axis = axisOf(params);
        if (axis < 0) return null;
        int[] coords = lastBreak.get(player.getUniqueId());
        if (coords == null) return "";
        return Integer.toString(coords[axis]);
    }

    private static int axisOf(String params) {
        String key = params.toLowerCase(Locale.ROOT).trim();
        return switch (key) {
            case "block_break_x", "break_block_x" -> 0;
            case "block_break_y", "break_block_y" -> 1;
            case "block_break_z", "break_block_z" -> 2;
            default -> -1;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        lastBreak.put(event.getPlayer().getUniqueId(),
                new int[] { b.getX(), b.getY(), b.getZ() });
    }
}