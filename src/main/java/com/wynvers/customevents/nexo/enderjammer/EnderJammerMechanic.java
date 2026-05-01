package com.wynvers.customevents.nexo.enderjammer;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Real Nexo {@link Mechanic} for the Ender Pearl Jammer block.
 *
 * <p>YAML example (top-level inside {@code Mechanics:}):
 * <pre>
 * pearl_jammer:
 *   Mechanics:
 *     furniture:
 *       type: ITEM_DISPLAY
 *       hitbox: { height: 1.0, width: 1.0 }
 *     ender_jammer:
 *       radius: 7                # cube half-edge → 15x15x15 with radius 7
 *       block_inside: true       # also cancel pearls thrown from inside the cube
 *       track_tick_interval: 2   # how often (ticks) to sample pearl position
 *       max_track_ticks: 200     # safety cap, ~10s
 * </pre>
 */
public class EnderJammerMechanic extends Mechanic {

    private final int radius;
    private final boolean blockInside;
    private final int trackTickInterval;
    private final int maxTrackTicks;

    public EnderJammerMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.radius = Math.max(1, section.getInt("radius", 7));
        this.blockInside = section.getBoolean("block_inside", true);
        this.trackTickInterval = Math.max(1, section.getInt("track_tick_interval", 2));
        this.maxTrackTicks = Math.max(20, section.getInt("max_track_ticks", 200));
    }

    public int radius() { return radius; }
    public boolean blockInside() { return blockInside; }
    public int trackTickInterval() { return trackTickInterval; }
    public int maxTrackTicks() { return maxTrackTicks; }
}