package com.wynvers.customevents.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reflection-based bridge to SaberFactions / FactionsUUID.
 *
 * <p>Avoids a hard compile-time dependency: if the Factions plugin is missing,
 * {@link #isInEnemyClaim(Player, Location)} returns {@code false} and a warning
 * is logged once.
 */
public final class SaberFactionsHook {

    private static final Logger LOG = Logger.getLogger("WynversCustomEvents/Factions");

    private static boolean initialized = false;
    private static boolean available = false;

    private static Method boardGetInstance;
    private static Method boardGetFactionAt;
    private static Method fLocationConstructorViaLocation;
    private static Method fPlayersGetInstance;
    private static Method fPlayersGetByPlayer;
    private static Method fPlayerGetFaction;
    private static Method factionIsWilderness;
    private static Method factionGetRelationTo;
    private static Object enemyRelation;

    private SaberFactionsHook() {}

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
            fLocationConstructorViaLocation = fLocCls.getMethod("valueOf", Location.class);
            fPlayersGetInstance = fPlayersCls.getMethod("getInstance");
            fPlayersGetByPlayer = fPlayersCls.getMethod("getByPlayer", Player.class);
            fPlayerGetFaction = fPlayerCls.getMethod("getFaction");
            factionIsWilderness = factionCls.getMethod("isWilderness");
            factionGetRelationTo = factionCls.getMethod("getRelationTo", factionCls);
            enemyRelation = Enum.valueOf((Class<Enum>) relationCls, "ENEMY");

            available = true;
            LOG.info("SaberFactions / FactionsUUID API hooked successfully.");
        } catch (NoSuchMethodException nsme) {
            // FLocation might use a constructor instead of a static valueOf.
            try {
                Class<?> fLocCls = Class.forName("com.massivecraft.factions.FLocation");
                fLocationConstructorViaLocation = null;
                Method byChunk = fLocCls.getMethod("valueOf", Object.class);
                fLocationConstructorViaLocation = byChunk;
                available = boardGetInstance != null;
                if (available) LOG.info("SaberFactions API hooked (fallback FLocation lookup).");
            } catch (Throwable t) {
                LOG.warning("Factions API mismatch: " + t.getMessage()
                        + " — claim checks disabled.");
            }
        } catch (Throwable t) {
            LOG.warning("Failed to hook Factions API: " + t.getMessage()
                    + " — claim checks disabled.");
        }
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
            Object floc = fLocationConstructorViaLocation.invoke(null, location);
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