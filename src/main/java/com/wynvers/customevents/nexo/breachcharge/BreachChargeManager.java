package com.wynvers.customevents.nexo.breachcharge;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds runtime state for active Breach Charges and per-player cooldowns.
 */
public class BreachChargeManager {

    public static final class ActiveCharge {
        public final UUID placerId;
        public final Location wallBlock;       // the wall block the charge is attached to
        public final BlockFace tunnelDirection; // direction the tunnel digs into
        public final BreachChargeMechanic mechanic;
        public final BukkitTask countdownTask;

        public ActiveCharge(UUID placerId,
                            Location wallBlock,
                            BlockFace tunnelDirection,
                            BreachChargeMechanic mechanic,
                            BukkitTask countdownTask) {
            this.placerId = placerId;
            this.wallBlock = wallBlock;
            this.tunnelDirection = tunnelDirection;
            this.mechanic = mechanic;
            this.countdownTask = countdownTask;
        }
    }

    private final Map<UUID, ActiveCharge> activeCharges = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

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
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(UUID playerId, int seconds) {
        cooldowns.put(playerId, System.currentTimeMillis() + seconds * 1000L);
    }

    public void register(UUID drillId, ActiveCharge charge) {
        activeCharges.put(drillId, charge);
    }

    public ActiveCharge get(UUID entityId) {
        return activeCharges.get(entityId);
    }

    public boolean isActive(UUID entityId) {
        return activeCharges.containsKey(entityId);
    }

    public ActiveCharge remove(UUID entityId) {
        return activeCharges.remove(entityId);
    }

    public void shutdown() {
        for (ActiveCharge c : activeCharges.values()) {
            try { c.countdownTask.cancel(); } catch (Throwable ignored) {}
        }
        activeCharges.clear();
        cooldowns.clear();
    }
}