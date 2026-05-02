package com.wynvers.customevents.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    // Reflection handles
    private static Method boardGetInstance;
    private static Method boardGetFactionAt;
    private static Method fPlayersGetInstance;
    private static Method fPlayersGetByPlayer;
    private static Method fPlayerGetFaction;
    private static Method factionIsWilderness;
    private static Method factionIsSafeZone;
    private static Method factionIsWarZone;
    private static Method factionGetFPlayers;
    private static Method fPlayerIsOnline;
    private static Method fPlayerGetPlayer;
    private static Method factionGetRelationTo;
    private static Object enemyRelation;

    // FLocation builder strategy chosen at init time.
    private static FLocationBuilder fLocationBuilder;

    private SaberFactionsHook() {}

    private interface FLocationBuilder {
        Object build(Location loc) throws ReflectiveOperationException;
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        if (Bukkit.getPluginManager().getPlugin("Factions") == null
                && Bukkit.getPluginManager().getPlugin("SaberFactions") == null) {
            LOG.warning("Factions plugin not detected — claim checks will allow all placements.");
            return;
        }

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
            fPlayerIsOnline = tryMethod(fPlayerCls, "isOnline");
            fPlayerGetPlayer = tryMethod(fPlayerCls, "getPlayer");
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

    private static Method tryMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}