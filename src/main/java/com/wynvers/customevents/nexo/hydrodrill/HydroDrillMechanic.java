package com.wynvers.customevents.nexo.hydrodrill;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Real Nexo {@link Mechanic} for the Hydro-Siege Drill custom_block.
 *
 * <p>YAML example (top-level inside {@code Mechanics:}):
 * <pre>
 * hydro_drill_commun:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 2
 *     hydro_drill:
 *       radius: 1              # cube half-edge → 3x3x3 with radius 1
 *       countdown_seconds: 60
 *       obsidian_swap_seconds: 30
 *       cooldown_seconds: 60
 *       particle: SQUID_INK
 *       sound: BLOCK_BEACON_AMBIENT
 * </pre>
 */
public class HydroDrillMechanic extends Mechanic {

    private final int radius;
    private final int countdownSeconds;
    private final int obsidianSwapSeconds;
    private final int cooldownSeconds;
    private final Particle particle;
    private final Sound sound;

    public HydroDrillMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.radius = Math.max(1, section.getInt("radius", 3));
        this.countdownSeconds = Math.max(1, section.getInt("countdown_seconds", 60));
        this.obsidianSwapSeconds = Math.max(1, section.getInt("obsidian_swap_seconds", 30));
        this.cooldownSeconds = Math.max(0, section.getInt("cooldown_seconds", 60));
        this.particle = parseParticle(section.getString("particle", "SQUID_INK"));
        this.sound = parseSound(section.getString("sound", "BLOCK_BEACON_AMBIENT"));
    }

    public int radius() { return radius; }
    public int countdownSeconds() { return countdownSeconds; }
    public int obsidianSwapSeconds() { return obsidianSwapSeconds; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public Particle particle() { return particle; }
    public Sound sound() { return sound; }

    private static Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Particle.SQUID_INK;
        }
    }

    private static Sound parseSound(String name) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Sound.BLOCK_BEACON_AMBIENT;
        }
    }
}