package com.wynvers.customevents.nexo.blockbreaker;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Real Nexo {@link Mechanic} for the {@code block_breaker_upgrade} type.
 *
 * <p>Holds the upgrade type/value parsed from the Nexo item YAML. The runtime
 * lookup is done by Nexo item id — see {@link BlockBreakerUpgrade#fromItem}
 * — so any item must come from Nexo's items folder to be recognised.
 *
 * <p>YAML example:
 * <pre>
 * wynvers_fortune_1:
 *   Mechanics:
 *     block_breaker_upgrade:
 *       type: fortune
 *       value: 1
 * </pre>
 */
public class BlockBreakerUpgradeMechanic extends Mechanic {

    private final BlockBreakerUpgrade.Type type;
    private final int value;

    public BlockBreakerUpgradeMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        String raw = section.getString("type", "fortune");
        BlockBreakerUpgrade.Type parsed;
        try {
            parsed = BlockBreakerUpgrade.Type.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = BlockBreakerUpgrade.Type.UNKNOWN;
        }
        this.type = parsed;
        this.value = Math.max(1, section.getInt("value", 1));
    }

    public BlockBreakerUpgrade.Type type() { return type; }
    public int value() { return value; }
}