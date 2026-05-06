package com.wynvers.customevents.nexo.breachcharge;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds runtime state for active Breach Charges and per-player cooldowns.
 *
 * <p>Charges are keyed by block coordinates ({@code worldUid:x:y:z}) since the
 * charge is a Nexo {@code custom_block} (NOTEBLOCK variant), not a furniture
 * entity, and therefore has no UUID.
 */
public class BreachChargeManager {

    public static final class ActiveCharge {
        public final UUID placerId;
        public final Location chargeBlock;       // the placed custom_block location
        public final Location wallBlock;         // wall the charge is attached to
        public final BlockFace tunnelDirection;  // direction the tunnel digs into
        public final BreachChargeMechanic mechanic;
        public final BukkitTask countdownTask;

        public ActiveCharge(UUID placerId,
                            Location chargeBlock,
                            Location wallBlock,
                            BlockFace tunnelDirection,
                            BreachChargeMechanic mechanic,
                            BukkitTask countdownTask) {
            this.placerId = placerId;
            this.chargeBlock = chargeBlock;
            this.wallBlock = wallBlock;
            this.tunnelDirection = tunnelDirection;
            this.mechanic = mechanic;
            this.countdownTask = countdownTask;
        }
    }

    private final Map<String, ActiveCharge> activeCharges = new HashMap<>();
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

    public void register(String chargeKey, ActiveCharge charge) {
        activeCharges.put(chargeKey, charge);
    }

    public ActiveCharge get(String chargeKey) {
        return activeCharges.get(chargeKey);
    }

    public boolean isActive(String chargeKey) {
        return activeCharges.containsKey(chargeKey);
    }

    public ActiveCharge remove(String chargeKey) {
        return activeCharges.remove(chargeKey);
    }

    public void shutdown() {
        for (ActiveCharge c : activeCharges.values()) {
            try { c.countdownTask.cancel(); } catch (Throwable ignored) {}
        }
        activeCharges.clear();
        cooldowns.clear();
    }
}