package com.wynvers.customevents.nexo.breachcharge;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Real Nexo {@link Mechanic} for the Breach Defuser tool.
 *
 * <p>YAML example:
 * <pre>
 * breach_defuser:
 *   itemname: "<aqua>Désamorceur de Charge</aqua>"
 *   material: PAPER
 *   Mechanics:
 *     breach_defuser:
 *       consume_on_use: true
 * </pre>
 */
public class DefuserMechanic extends Mechanic {

    private final boolean consumeOnUse;

    public DefuserMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.consumeOnUse = section.getBoolean("consume_on_use", true);
    }

    public boolean consumeOnUse() { return consumeOnUse; }
}