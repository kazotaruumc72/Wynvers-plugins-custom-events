package com.wynvers.customevents.nexo.breachcharge;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Real Nexo {@link Mechanic} for the directional Breach Charge.
 *
 * <p>YAML example:
 * <pre>
 * breach_charge:
 *   Mechanics:
 *     furniture:
 *       type: ITEM_FRAME
 *       hitbox: { height: 1.0, width: 1.0 }
 *     breach_charge:
 *       countdown_seconds: 10
 *       critical_threshold_seconds: 3   # last 3 seconds switch to red particles + fast bips
 *       cooldown_seconds: 300
 *       tunnel_width: 3
 *       tunnel_height: 3
 *       tunnel_depth: 4
 *       destroy_obsidian: false
 *       alert_faction: true
 * </pre>
 */
public class BreachChargeMechanic extends Mechanic {

    private final int countdownSeconds;
    private final int criticalThresholdSeconds;
    private final int cooldownSeconds;
    private final int tunnelWidth;
    private final int tunnelHeight;
    private final int tunnelDepth;
    private final boolean destroyObsidian;
    private final boolean alertFaction;

    public BreachChargeMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.countdownSeconds = Math.max(1, section.getInt("countdown_seconds", 10));
        this.criticalThresholdSeconds = Math.max(0, section.getInt("critical_threshold_seconds", 3));
        this.cooldownSeconds = Math.max(0, section.getInt("cooldown_seconds", 300));
        this.tunnelWidth = Math.max(1, section.getInt("tunnel_width", 3));
        this.tunnelHeight = Math.max(1, section.getInt("tunnel_height", 3));
        this.tunnelDepth = Math.max(1, section.getInt("tunnel_depth", 4));
        this.destroyObsidian = section.getBoolean("destroy_obsidian", false);
        this.alertFaction = section.getBoolean("alert_faction", true);
    }

    public int countdownSeconds() { return countdownSeconds; }
    public int criticalThresholdSeconds() { return criticalThresholdSeconds; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public int tunnelWidth() { return tunnelWidth; }
    public int tunnelHeight() { return tunnelHeight; }
    public int tunnelDepth() { return tunnelDepth; }
    public boolean destroyObsidian() { return destroyObsidian; }
    public boolean alertFaction() { return alertFaction; }
}