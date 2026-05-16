package com.wynvers.customevents.papi;

import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.blockbreaker.BlockBreakerMechanicFactory;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PlaceholderAPI expansion exposing the coordinates of the player's last
 * broken block.
 *
 * <p>Identifier: {@code wce}. Provided placeholders:
 * <ul>
 *   <li>{@code %wce_block_break_x%} / {@code %wce_break_block_x%}</li>
 *   <li>{@code %wce_block_break_y%} / {@code %wce_break_block_y%}</li>
 *   <li>{@code %wce_block_break_z%} / {@code %wce_break_block_z%}</li>
 *   <li>{@code %wce_block_closest%} — comma-separated list of vanilla
 *       Material names + Nexo block ids (prefixed with {@code nexo:})
 *       within 1 block in every direction of the player (3×3×3 cube),
 *       sorted by distance, deduplicated, excluding AIR / WATER / LAVA.</li>
 *   <li>{@code %wce_blockbreaker_closest%} — same content/format as
 *       {@code %wce_block_closest%} but centred on the location of the
 *       last BlockBreaker the player interacted with. The BlockBreaker
 *       itself is excluded from the result. Returns an empty string if
 *       the player has never interacted with a BlockBreaker, or if the
 *       last one no longer exists.</li>
 *   <li>{@code %wce_blockbreakers_closest%} — aggregate of
 *       {@code blockbreaker_closest} over <em>every</em> BlockBreaker
 *       the player has placed. Same format: a deduplicated list of
 *       block ids, sorted by the smallest distance encountered across
 *       all of the player's breakers. Returns an empty string when the
 *       player owns no currently-placed BlockBreaker.</li>
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
        String key = params.toLowerCase(Locale.ROOT).trim();

        if (key.equals("block_closest")) {
            return blockClosest(player);
        }
        if (key.equals("blockbreaker_closest")) {
            return blockBreakerClosest(player);
        }
        if (key.equals("blockbreakers_closest")) {
            return blockBreakersClosest(player);
        }

        int axis = axisOf(key);
        if (axis < 0) return null;
        int[] coords = lastBreak.get(player.getUniqueId());
        if (coords == null) return "";
        return Integer.toString(coords[axis]);
    }

    private static int axisOf(String key) {
        return switch (key) {
            case "block_break_x", "break_block_x" -> 0;
            case "block_break_y", "break_block_y" -> 1;
            case "block_break_z", "break_block_z" -> 2;
            default -> -1;
        };
    }

    /**
     * Returns a comma-separated list of block identifiers within the 3×3×3
     * cube centred on the player's foot block, sorted by squared distance
     * to the player's exact location, deduplicated, with AIR / WATER / LAVA
     * filtered out.
     *
     * <p>Nexo custom blocks are reported as {@code nexo:<itemId>}; everything
     * else is reported as the vanilla {@link Material} name. When Nexo is
     * not loaded, the Nexo lookup is skipped silently and every block is
     * reported as its vanilla material.
     */
    private String blockClosest(OfflinePlayer offline) {
        Player online = offline.getPlayer();
        if (online == null) return "";
        Location origin = online.getLocation();
        Map<String, Double> aggregated = new LinkedHashMap<>();
        collectAround(origin, origin.getBlock(), false,
                Bukkit.getPluginManager().getPlugin("Nexo") != null, aggregated);
        return joinSorted(aggregated);
    }

    /**
     * BlockBreaker-scoped variant of {@link #blockClosest}: scans the 3×3×3
     * cube around the last BlockBreaker this player interacted with. The
     * breaker's own block is excluded so the placeholder reports only the
     * surrounding terrain.
     */
    private String blockBreakerClosest(OfflinePlayer offline) {
        if (BlockBreakerMechanicFactory.instance() == null) return "";
        Location breakerLoc = BlockBreakerMechanicFactory.instance()
                .lastInteractedFor(offline.getUniqueId());
        if (breakerLoc == null) return "";
        Map<String, Double> aggregated = new LinkedHashMap<>();
        Location origin = breakerLoc.clone().add(0.5, 0.5, 0.5);
        collectAround(origin, breakerLoc.getBlock(), true,
                Bukkit.getPluginManager().getPlugin("Nexo") != null, aggregated);
        return joinSorted(aggregated);
    }

    /**
     * Aggregates {@code blockBreakerClosest} over every BlockBreaker the
     * player has placed. Block ids are deduplicated across breakers; the
     * sort order uses the smallest distance encountered for that id
     * across all scans, so the most "accessible" resources surface first.
     */
    private String blockBreakersClosest(OfflinePlayer offline) {
        if (BlockBreakerMechanicFactory.instance() == null) return "";
        List<Location> locations = BlockBreakerMechanicFactory.instance()
                .locationsOwnedBy(offline.getUniqueId());
        if (locations.isEmpty()) return "";
        boolean nexoLoaded = Bukkit.getPluginManager().getPlugin("Nexo") != null;
        Map<String, Double> aggregated = new LinkedHashMap<>();
        for (Location loc : locations) {
            Location origin = loc.clone().add(0.5, 0.5, 0.5);
            collectAround(origin, loc.getBlock(), true, nexoLoaded, aggregated);
        }
        return joinSorted(aggregated);
    }

    /**
     * Shared 3×3×3 scan. Each non-filtered neighbour contributes its id and
     * squared distance to {@code out}; on collision the smaller distance
     * wins. {@code excludeCenter} skips the {@code (0,0,0)} offset (useful
     * when the centre block is the BlockBreaker itself).
     */
    private static void collectAround(Location origin, Block center, boolean excludeCenter,
                                      boolean nexoLoaded, Map<String, Double> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (excludeCenter && dx == 0 && dy == 0 && dz == 0) continue;
                    Block b = center.getRelative(dx, dy, dz);
                    Material mat = b.getType();
                    if (mat.isAir() || mat == Material.WATER || mat == Material.LAVA) continue;

                    String id = resolveId(b, mat, nexoLoaded);
                    double cx = b.getX() + 0.5;
                    double cy = b.getY() + 0.5;
                    double cz = b.getZ() + 0.5;
                    double dxx = cx - origin.getX();
                    double dyy = cy - origin.getY();
                    double dzz = cz - origin.getZ();
                    double distSq = dxx * dxx + dyy * dyy + dzz * dzz;
                    out.merge(id, distSq, Math::min);
                }
            }
        }
    }

    private static String joinSorted(Map<String, Double> aggregated) {
        return aggregated.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
    }

    private static String resolveId(Block block, Material fallback, boolean nexoLoaded) {
        if (nexoLoaded) {
            try {
                var cb = com.nexomc.nexo.api.NexoBlocks.customBlockMechanic(block);
                if (cb != null && cb.getItemID() != null && !cb.getItemID().isEmpty()) {
                    return "nexo:" + cb.getItemID();
                }
            } catch (Throwable ignored) {
                // Nexo absent or API change — fall back to vanilla material.
            }
        }
        return fallback.name();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        lastBreak.put(event.getPlayer().getUniqueId(),
                new int[] { b.getX(), b.getY(), b.getZ() });
    }
}