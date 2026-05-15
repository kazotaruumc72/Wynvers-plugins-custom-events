package com.wynvers.customevents.nexo.blockbreaker;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds runtime state for every placed BlockBreaker:
 * - which faces are currently active
 * - the upgrade items committed via the GUI
 * - the repeating break task
 *
 * <p>State is in-memory only — a full server restart resets every breaker
 * (consistent with the rest of this plugin's mechanics).
 */
public class BlockBreakerManager {

    public static final class State {
        /**
         * Current Nexo variant id of the breaker block. Mutable so face-toggle
         * swaps to a different texture variant can be tracked (see
         * {@link com.wynvers.customevents.nexo.blockbreaker.BlockBreakerMechanic#computeVariantId}).
         */
        public String nexoId;
        public final UUID ownerId;
        public final EnumSet<BlockFace> activeFaces = EnumSet.noneOf(BlockFace.class);
        public final EnumMap<BlockBreakerUpgrade.Type, Integer> upgrades = new EnumMap<>(BlockBreakerUpgrade.Type.class);
        /** Raw item snapshots (kept so the GUI can show what's installed). */
        public final ItemStack[] upgradeItems = new ItemStack[5];
        public BukkitTask task;

        public State(String nexoId, UUID ownerId) {
            this.nexoId = nexoId;
            this.ownerId = ownerId;
        }
    }

    private final Map<String, State> states = new HashMap<>();

    public static String keyOf(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public State get(String key) {
        return states.get(key);
    }

    public State getOrCreate(String key, String nexoId, UUID owner) {
        return states.computeIfAbsent(key, k -> new State(nexoId, owner));
    }

    public State remove(String key) {
        State s = states.remove(key);
        if (s != null && s.task != null) {
            try { s.task.cancel(); } catch (Throwable ignored) {}
        }
        return s;
    }

    /** Returns the live map of states — used by {@link BlockBreakerStore} for serialisation. */
    public Map<String, State> states() {
        return states;
    }

    /** Inserts a state under {@code key}, overwriting any existing entry. */
    public void put(String key, State state) {
        states.put(key, state);
    }

    public void shutdown() {
        for (State s : states.values()) {
            if (s.task != null) {
                try { s.task.cancel(); } catch (Throwable ignored) {}
            }
        }
        states.clear();
    }
}