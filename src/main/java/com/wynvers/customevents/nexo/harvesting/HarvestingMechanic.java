package com.wynvers.customevents.nexo.harvesting;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Real Nexo {@link Mechanic} implementing the {@code harvesting}
 * mechanic – a tool that, when right-clicked, scans nearby Nexo
 * furnitures and harvests every farmer-final-stage one in range.
 *
 * <p>YAML example (inside a Nexo item's {@code Mechanics:}):
 * <pre>
 * heartflame_hoe:
 *   itemname: Heartflame Hoe
 *   material: NETHERITE_HOE
 *   Components:
 *     max_damage: 410
 *   Mechanics:
 *     harvesting:
 *       cooldown: 1000   # ms between two harvests
 *       radius: 5        # horizontal radius (blocks)
 *       height: 3        # vertical radius (blocks above and below)
 *   Pack:
 *     model: heartflame/heartflame_hoe
 * </pre>
 */
public class HarvestingMechanic extends Mechanic {

    public static final long DEFAULT_COOLDOWN_MS = 1000L;
    public static final int DEFAULT_RADIUS = 3;
    public static final int DEFAULT_HEIGHT = 2;

    private final long cooldownMs;
    private final int radius;
    private final int height;

    public HarvestingMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.cooldownMs = Math.max(0L, section.getLong("cooldown", DEFAULT_COOLDOWN_MS));
        this.radius = Math.max(0, section.getInt("radius", DEFAULT_RADIUS));
        this.height = Math.max(0, section.getInt("height", DEFAULT_HEIGHT));
    }

    public long cooldownMs() { return cooldownMs; }
    public int radius()      { return radius; }
    public int height()      { return height; }
}

