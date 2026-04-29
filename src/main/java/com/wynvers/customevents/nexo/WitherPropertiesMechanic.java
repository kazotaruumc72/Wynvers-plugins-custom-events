package com.wynvers.customevents.nexo;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;
/**
 * Real Nexo Mechanic representing the {@code wither_properties} block in an
 * item's {@code Mechanics:} section.
 *
 * <p>Yaml example (inside a Nexo item file):
 * <pre>
 * obsidienne_verte:
 *   Mechanics:
 *     wither_properties:
 *       wither_explosion_damage: false               # all-or-nothing protection (body)
 *       wither_damage_throw:    false                # all-or-nothing protection (skull)
 *       wither_explosion_break_block_percent: 30     # 30% chance to break on body explosion
 *       wither_damage_throw_break_block_percent: 50  # 50% chance to break on skull hit
 * </pre>
 *
 * <p>If a {@code *_break_block_percent} key is present, it overrides the
 * boolean above: instead of "always break / never break", the block has the
 * given percentage of chance to actually break for each explosion event.
 */
public class WitherPropertiesMechanic extends Mechanic {
    /** Sentinel meaning "no chance defined; fall back to the boolean flag". */
    public static final int CHANCE_UNSET = -1;

    private final boolean witherExplosionDamage;
    private final boolean witherDamageThrow;
    private final int explosionBreakChancePercent;
    private final int skullBreakChancePercent;

    public WitherPropertiesMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        // Accept the standard keys + a few common typos found in user configs
        this.witherExplosionDamage = readBool(section, true,
                "wither_explosion_damage", "wither-explosion-damage",
                "witherExplosionDamage",
                "whther_explosion_damage", "whther_explossion_damage");
        this.witherDamageThrow = readBool(section, true,
                "wither_damage_throw", "wither-damage-throw",
                "witherDamageThrow");
        this.explosionBreakChancePercent = readPercent(section,
                "wither_explosion_break_block_percent",
                "wither-explosion-break-block-percent",
                "witherExplosionBreakBlockPercent");
        this.skullBreakChancePercent = readPercent(section,
                "wither_damage_throw_break_block_percent",
                "wither-damage-throw-break-block-percent",
                "witherDamageThrowBreakBlockPercent",
                "wither_skull_break_block_percent");
    }
    public boolean allowsWitherExplosionDamage() {
        return witherExplosionDamage;
    }
    public boolean allowsWitherDamageThrow() {
        return witherDamageThrow;
    }
    /** Returns 0..100 if a chance is configured, otherwise {@link #CHANCE_UNSET}. */
    public int explosionBreakChancePercent() {
        return explosionBreakChancePercent;
    }
    /** Returns 0..100 if a chance is configured, otherwise {@link #CHANCE_UNSET}. */
    public int skullBreakChancePercent() {
        return skullBreakChancePercent;
    }
    private static boolean readBool(ConfigurationSection section, boolean def, String... keys) {
        for (String k : keys) {
            if (!section.contains(k)) continue;
            Object raw = section.get(k);
            if (raw instanceof Boolean b) return b;
            if (raw instanceof String s) {
                String n = s.trim().toLowerCase();
                if ("true".equals(n))  return true;
                if ("false".equals(n)) return false;
            }
            return section.getBoolean(k, def);
        }
        return def;
    }
    private static int readPercent(ConfigurationSection section, String... keys) {
        for (String k : keys) {
            if (!section.contains(k)) continue;
            Object raw = section.get(k);
            int v;
            if (raw instanceof Number n) {
                v = n.intValue();
            } else if (raw instanceof String s) {
                try { v = Integer.parseInt(s.trim().replace("%", "")); }
                catch (NumberFormatException e) { return CHANCE_UNSET; }
            } else {
                return CHANCE_UNSET;
            }
            if (v < 0)   v = 0;
            if (v > 100) v = 100;
            return v;
        }
        return CHANCE_UNSET;
    }
}