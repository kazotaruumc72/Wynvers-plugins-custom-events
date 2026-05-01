package com.wynvers.customevents.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
}