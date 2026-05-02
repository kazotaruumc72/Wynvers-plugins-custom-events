package com.wynvers.customevents.nexo.explosionreducer;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Real Nexo {@link Mechanic} that reduces explosion damage on a custom block.
 *
 * <p>YAML example (alongside {@code custom_block} and optionally
 * {@code wither_properties}):
 * <pre>
 * reinforced_obsidian:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 100
 *     wither_properties:
 *       block_wither_damage: true
 *       block_wither_skull_damage: true
 *     explosion_reducer:
 *       reduction_percent: 75   # each non-player break deals (100-75)=25 damage
 *                                 # block has 100 HP → breaks after 4 hits.
 * </pre>
 *
 * <p>Player-driven breaks (mining) are never reduced.
 */
public class ExplosionReducerMechanic extends Mechanic {

    private final int reductionPercent;

    public ExplosionReducerMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        int rp = section.getInt("reduction_percent", 75);
        this.reductionPercent = Math.max(0, Math.min(99, rp));
    }

    public int reductionPercent() { return reductionPercent; }

    /** Damage dealt per explosion hit. Always at least 1. */
    public int damagePerHit() {
        return Math.max(1, 100 - reductionPercent);
    }

    /** Number of explosion hits required to break the block. */
    public int hitsToBreak() {
        return (int) Math.ceil(100.0 / damagePerHit());
    }
}