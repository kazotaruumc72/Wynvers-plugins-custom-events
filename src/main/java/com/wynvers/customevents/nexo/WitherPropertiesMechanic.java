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
 *       wither_explosion_damage: false
 *       wither_damage_throw: false
 * </pre>
 */
public class WitherPropertiesMechanic extends Mechanic {
    private final boolean witherExplosionDamage;
    private final boolean witherDamageThrow;
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
    }
    public boolean allowsWitherExplosionDamage() {
        return witherExplosionDamage;
    }
    public boolean allowsWitherDamageThrow() {
        return witherDamageThrow;
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
}