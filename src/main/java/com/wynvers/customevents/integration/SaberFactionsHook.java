package com.wynvers.customevents.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reflection-based bridge to SaberFactions / FactionsUUID.
 *
 * <p>Avoids a hard compile-time dependency: if the Factions plugin is missing
 * or its API doesn't match expectations, {@link #isInEnemyClaim(Player, Location)}
 * returns {@code false} and a warning is logged once.
 *
 * <p>Tries several ways to build an {@code FLocation} from a Bukkit
 * {@link Location} (constructor first, then static factory methods) so the hook
 * stays compatible with both SaberFactions and the various FactionsUUID forks.
 */
public final class SaberFactionsHook {

    private static final Logger LOG = Logger.getLogger("WynversCustomEvents/Factions");

    private static boolean initialized = false;
    private static boolean available = false;
    private static boolean warnedNotDetected = false;

    // Reflection handles
    private static Method boardGetInstance;
    private static Method boardGetFactionAt;
    private static Method boardSetFactionAt;
    private static Method boardRemoveAt;
    private static Method fPlayersGetInstance;
    private static Method fPlayersGetByPlayer;
    private static Method fPlayerGetFaction;
    private static Method factionIsWilderness;
    private static Method factionIsSafeZone;
    private static Method factionIsWarZone;
    private static Method factionGetFPlayers;
    private static Method factionGetAllClaims;
    private static Method factionGetTag;
    private static Method fPlayerIsOnline;
    private static Method fPlayerGetPlayer;
    private static Method factionGetRelationTo;
    private static Method flocGetX;
    private static Method flocGetZ;
    private static Object enemyRelation;

    // FLocation builder strategy chosen at init time.
    private static FLocationBuilder fLocationBuilder;

    private SaberFactionsHook() {}

    private interface FLocationBuilder {
        Object build(Location loc) throws ReflectiveOperationException;
    }

    private static synchronized void init() {
        if (initialized) return;

        if (Bukkit.getPluginManager().getPlugin("Factions") == null
                && Bukkit.getPluginManager().getPlugin("SaberFactions") == null) {
            if (!warnedNotDetected) {
                LOG.warning("Factions plugin not detected — claim checks will allow all placements.");
                warnedNotDetected = true;
            }
            // Leave initialized=false so a later call retries once the plugin loads.
            return;
        }

        initialized = true;
        try {
            Class<?> boardCls = Class.forName("com.massivecraft.factions.Board");
            Class<?> fLocCls = Class.forName("com.massivecraft.factions.FLocation");
            Class<?> fPlayersCls = Class.forName("com.massivecraft.factions.FPlayers");
            Class<?> fPlayerCls = Class.forName("com.massivecraft.factions.FPlayer");
            Class<?> factionCls = Class.forName("com.massivecraft.factions.Faction");
            Class<?> relationCls = Class.forName("com.massivecraft.factions.struct.Relation");

            boardGetInstance = boardCls.getMethod("getInstance");
            boardGetFactionAt = boardCls.getMethod("getFactionAt", fLocCls);
            fPlayersGetInstance = fPlayersCls.getMethod("getInstance");
            fPlayersGetByPlayer = fPlayersCls.getMethod("getByPlayer", Player.class);
            fPlayerGetFaction = fPlayerCls.getMethod("getFaction");
            factionIsWilderness = factionCls.getMethod("isWilderness");
            factionGetRelationTo = factionCls.getMethod("getRelationTo", factionCls);

            // Optional methods (best-effort).
            factionIsSafeZone = tryMethod(factionCls, "isSafeZone");
            factionIsWarZone = tryMethod(factionCls, "isWarZone");
            factionGetFPlayers = tryMethod(factionCls, "getFPlayers");
            factionGetAllClaims = tryMethod(factionCls, "getAllClaims");
            factionGetTag = tryMethod(factionCls, "getTag");
            fPlayerIsOnline = tryMethod(fPlayerCls, "isOnline");
            fPlayerGetPlayer = tryMethod(fPlayerCls, "getPlayer");

            // Claim setter — used by Base Claim Protector. Best-effort: if missing,
            // base-claim-protector will just refuse to claim.
            boardSetFactionAt = tryMethod(boardCls, "setFactionAt", factionCls, fLocCls);
            boardRemoveAt = tryMethod(boardCls, "removeAt", fLocCls);
            flocGetX = tryMethod(fLocCls, "getX");
            flocGetZ = tryMethod(fLocCls, "getZ");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enemy = Enum.valueOf((Class<Enum>) relationCls, "ENEMY");
            enemyRelation = enemy;

            fLocationBuilder = resolveFLocationBuilder(fLocCls);
            if (fLocationBuilder == null) {
                LOG.warning("FLocation has no usable constructor or factory — claim checks disabled.");
                return;
            }

            available = true;
            LOG.info("SaberFactions / FactionsUUID API hooked successfully.");
        } catch (Throwable t) {
            LOG.warning("Failed to hook Factions API: " + t.getClass().getSimpleName()
                    + " " + t.getMessage() + " — claim checks disabled.");
        }
    }

    private static FLocationBuilder resolveFLocationBuilder(Class<?> fLocCls) {
        // 1. Constructor(Location)
        try {
            Constructor<?> ctor = fLocCls.getConstructor(Location.class);
            return loc -> ctor.newInstance(loc);
        } catch (NoSuchMethodException ignored) {}

        // 2. Constructor(Player) — fall back to launcher.getLocation() at the
        //    Player level (less precise but acceptable).
        try {
            Constructor<?> ctor = fLocCls.getConstructor(Player.class);
            // Cannot satisfy this from a raw Location alone — skip.
        } catch (NoSuchMethodException ignored) {}

        // 3. Constructor(String world, int x, int z) — chunk-based.
        try {
            Constructor<?> ctor = fLocCls.getConstructor(String.class, int.class, int.class);
            return loc -> ctor.newInstance(
                    loc.getWorld() != null ? loc.getWorld().getName() : "world",
                    loc.getBlockX() >> 4,
                    loc.getBlockZ() >> 4);
        } catch (NoSuchMethodException ignored) {}

        // 4. static FLocation.valueOf(Location)
        try {
            Method m = fLocCls.getMethod("valueOf", Location.class);
            return loc -> m.invoke(null, loc);
        } catch (NoSuchMethodException ignored) {}

        // 5. static FLocation.fromLocation(Location)
        try {
            Method m = fLocCls.getMethod("fromLocation", Location.class);
            return loc -> m.invoke(null, loc);
        } catch (NoSuchMethodException ignored) {}

        return null;
    }

    /**
     * Returns {@code true} only if {@code location} is inside a claim that the
     * given player's faction considers an ENEMY. Returns {@code false} when
     * Factions is missing, when the chunk is wilderness, or when it belongs to
     * the player's own / allied faction.
     */
    public static boolean isInEnemyClaim(Player player, Location location) {
        init();
        if (!available) return false;
        try {
            Object board = boardGetInstance.invoke(null);
            Object floc = fLocationBuilder.build(location);
            Object factionAt = boardGetFactionAt.invoke(board, floc);
            if (factionAt == null) return false;
            if ((boolean) factionIsWilderness.invoke(factionAt)) return false;

            Object fPlayers = fPlayersGetInstance.invoke(null);
            Object fPlayer = fPlayersGetByPlayer.invoke(fPlayers, player);
            if (fPlayer == null) return false;
            Object playerFaction = fPlayerGetFaction.invoke(fPlayer);
            if (playerFaction == null) return false;

            Object relation = factionGetRelationTo.invoke(playerFaction, factionAt);
            return relation == enemyRelation;
        } catch (Throwable t) {
            LOG.warning("Claim check failed: " + t.getMessage());
            return false;
        }
    }

    /** True when the Factions API was successfully resolved. */
    public static boolean isAvailable() {
        init();
        return available;
    }

    /**
     * Returns {@code true} if {@code location} is inside a SafeZone or
     * WarZone claim. Returns {@code false} when Factions is missing or the
     * relevant API methods are unavailable.
     */
    public static boolean isProtectedZone(Location location) {
        init();
        if (!available) return false;
        try {
            Object faction = factionAt(location);
            if (faction == null) return false;
            if (factionIsSafeZone != null && (boolean) factionIsSafeZone.invoke(faction)) return true;
            if (factionIsWarZone != null && (boolean) factionIsWarZone.invoke(faction)) return true;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns the online players belonging to the faction owning the given
     * location. Empty list when the location is wilderness, safezone, warzone,
     * or when the Factions API is unavailable.
     */
    public static List<Player> getOnlineFactionMembers(Location location) {
        init();
        if (!available) return Collections.emptyList();
        if (factionGetFPlayers == null || fPlayerGetPlayer == null) return Collections.emptyList();
        try {
            Object faction = factionAt(location);
            if (faction == null) return Collections.emptyList();
            if ((boolean) factionIsWilderness.invoke(faction)) return Collections.emptyList();
            if (factionIsSafeZone != null && (boolean) factionIsSafeZone.invoke(faction)) return Collections.emptyList();
            if (factionIsWarZone != null && (boolean) factionIsWarZone.invoke(faction)) return Collections.emptyList();

            Object members = factionGetFPlayers.invoke(faction);
            if (!(members instanceof Collection<?> collection)) return Collections.emptyList();

            List<Player> result = new ArrayList<>();
            for (Object fp : collection) {
                if (fp == null) continue;
                if (fPlayerIsOnline != null && !(boolean) fPlayerIsOnline.invoke(fp)) continue;
                Object p = fPlayerGetPlayer.invoke(fp);
                if (p instanceof Player player && player.isOnline()) result.add(player);
            }
            return result;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static Object factionAt(Location location) throws ReflectiveOperationException {
        Object board = boardGetInstance.invoke(null);
        Object floc = fLocationBuilder.build(location);
        return boardGetFactionAt.invoke(board, floc);
    }

    /**
     * Returns the player's faction (FactionsUUID/SaberFactions {@code Faction}
     * object) or {@code null} when the player is factionless or the API is
     * unavailable. The caller treats it as an opaque token.
     */
    public static Object getPlayerFaction(Player player) {
        init();
        if (!available) return null;
        try {
            Object fPlayers = fPlayersGetInstance.invoke(null);
            Object fPlayer = fPlayersGetByPlayer.invoke(fPlayers, player);
            if (fPlayer == null) return null;
            Object faction = fPlayerGetFaction.invoke(fPlayer);
            if (faction == null) return null;
            if ((boolean) factionIsWilderness.invoke(faction)) return null;
            return faction;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns the chunk coordinates ({@code [chunkX, chunkZ]}) of the centroid
     * of the player's faction's claimed land, or {@code null} when:
     * <ul>
     *   <li>The Factions API is unavailable,</li>
     *   <li>The player has no faction,</li>
     *   <li>The faction has no claims,</li>
     *   <li>The {@code getAllClaims} reflection target is missing on this fork.</li>
     * </ul>
     *
     * <p>The centroid is the average chunk-coordinate across all claimed
     * chunks; we then snap to the actually-claimed chunk closest to that
     * average so the result is a real chunk owned by the faction.
     */
    public static int[] getFactionCenterChunk(Player player) {
        init();
        if (!available) return null;
        if (factionGetAllClaims == null || flocGetX == null || flocGetZ == null) return null;
        try {
            Object faction = getPlayerFaction(player);
            if (faction == null) return null;

            Object claims = factionGetAllClaims.invoke(faction);
            if (!(claims instanceof Collection<?> coll) || coll.isEmpty()) return null;

            double sumX = 0, sumZ = 0;
            int count = 0;
            for (Object floc : coll) {
                if (floc == null) continue;
                sumX += ((Number) flocGetX.invoke(floc)).doubleValue();
                sumZ += ((Number) flocGetZ.invoke(floc)).doubleValue();
                count++;
            }
            if (count == 0) return null;
            double avgX = sumX / count;
            double avgZ = sumZ / count;

            int bestX = 0, bestZ = 0;
            double bestDist = Double.MAX_VALUE;
            boolean found = false;
            for (Object floc : coll) {
                if (floc == null) continue;
                int cx = ((Number) flocGetX.invoke(floc)).intValue();
                int cz = ((Number) flocGetZ.invoke(floc)).intValue();
                double dx = cx - avgX;
                double dz = cz - avgZ;
                double d = dx * dx + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    bestX = cx;
                    bestZ = cz;
                    found = true;
                }
            }
            return found ? new int[]{bestX, bestZ} : null;
        } catch (Throwable t) {
            LOG.warning("getFactionCenterChunk failed: " + t.getMessage());
            return null;
        }
    }

    /**
     * Returns {@code true} if the chunk containing {@code location} is the
     * centroid chunk of the player's faction's claims.
     */
    public static boolean isAtFactionBaseCenter(Player player, Location location) {
        int[] center = getFactionCenterChunk(player);
        if (center == null) return false;
        int placedX = location.getBlockX() >> 4;
        int placedZ = location.getBlockZ() >> 4;
        return center[0] == placedX && center[1] == placedZ;
    }

    /**
     * Claims a single chunk for the player's faction by directly writing to
     * the Board, bypassing the regular {@code /f claim} flow (and therefore
     * its power check). Returns {@code true} when the claim was applied,
     * {@code false} when the chunk was already claimed by some non-wilderness
     * faction, or when the API is unavailable.
     *
     * <p>Use carefully: this overwrites whatever was previously at the chunk
     * if the caller chose to call it on non-wilderness, so the caller is
     * expected to filter wilderness via {@link #isChunkWilderness} first.
     */
    public static boolean setFactionAtChunk(Player player, World world, int chunkX, int chunkZ) {
        init();
        if (!available) return false;
        if (boardSetFactionAt == null) return false;
        if (world == null) return false;
        try {
            Object faction = getPlayerFaction(player);
            if (faction == null) return false;

            Location proxy = new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
            Object floc = fLocationBuilder.build(proxy);
            Object board = boardGetInstance.invoke(null);
            boardSetFactionAt.invoke(board, faction, floc);
            return true;
        } catch (Throwable t) {
            LOG.warning("setFactionAtChunk failed: " + t.getMessage());
            return false;
        }
    }

    /**
     * Returns {@code true} if the chunk at the given coordinates is currently
     * unclaimed (wilderness). Returns {@code false} when the chunk belongs to
     * any faction (including SafeZone / WarZone) or the API is unavailable.
     */
    public static boolean isChunkWilderness(World world, int chunkX, int chunkZ) {
        init();
        if (!available) return false;
        if (world == null) return false;
        try {
            Location proxy = new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
            Object faction = factionAt(proxy);
            if (faction == null) return true;
            return (boolean) factionIsWilderness.invoke(faction);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Releases the claim on the given chunk, returning it to wilderness. No-op
     * if the chunk is already wilderness, the API is missing, or the
     * {@code Board.removeAt} method isn't exposed by this fork.
     */
    public static boolean unclaimChunk(World world, int chunkX, int chunkZ) {
        init();
        if (!available) return false;
        if (boardRemoveAt == null || world == null) return false;
        try {
            Location proxy = new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
            Object floc = fLocationBuilder.build(proxy);
            Object board = boardGetInstance.invoke(null);
            boardRemoveAt.invoke(board, floc);
            return true;
        } catch (Throwable t) {
            LOG.warning("unclaimChunk failed: " + t.getMessage());
            return false;
        }
    }

    /**
     * Returns the faction tag (display name) of the faction owning the chunk
     * at {@code location}, or {@code null} when wilderness, missing API, or
     * the {@code Faction.getTag} accessor isn't exposed. Used for messaging.
     */
    public static String getFactionTagAt(Location location) {
        init();
        if (!available) return null;
        if (factionGetTag == null) return null;
        try {
            Object faction = factionAt(location);
            if (faction == null) return null;
            if ((boolean) factionIsWilderness.invoke(faction)) return null;
            Object tag = factionGetTag.invoke(faction);
            return tag != null ? tag.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns the faction tag (display name) for the player's faction, or
     * {@code null} when factionless / unavailable. Used for messaging only.
     */
    public static String getPlayerFactionTag(Player player) {
        init();
        if (!available) return null;
        if (factionGetTag == null) return null;
        try {
            Object faction = getPlayerFaction(player);
            if (faction == null) return null;
            Object tag = factionGetTag.invoke(faction);
            return tag != null ? tag.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method tryMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}