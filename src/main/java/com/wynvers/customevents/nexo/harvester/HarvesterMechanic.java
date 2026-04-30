package com.wynvers.customevents.nexo.harvester;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Real Nexo {@link Mechanic} implementing the {@code harvester} mechanic –
 * when a player right-clicks a furniture with a tool, the tool takes damage
 * for each nearby furniture found.
 *
 * <p>The harvester only works on the final stage of a crop (when there is no
 * next_stage defined in the farmer mechanic).
 *
 * <p>YAML example (inside a Nexo item's {@code Mechanics:}):
 * <pre>
 * barley_final:
 *   itemname: §6Barley (Final)
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *     farmer:
 *       delay: 10s
 *       # No next_stage = this is the final stage
 *       on_final:
 *         - "console say Final stage reached!"
 *     harvester:                    # Only works because this is final stage
 *       scan_radius: 3
 *       scan_height: 2
 *       damage_per_item: 1
 * </pre>
 *
 * <p>All parameters are optional and use reasonable defaults.
 */
public class HarvesterMechanic extends Mechanic {

    public static final int DEFAULT_SCAN_RADIUS = 3;
    public static final int DEFAULT_SCAN_HEIGHT = 2;
    public static final int DEFAULT_DAMAGE_PER_ITEM = 1;

    private final int scanRadius;
    private final int scanHeight;
    private final int damagePerItem;
    private final boolean isFinalStage;

    public HarvesterMechanic(MechanicFactory factory, ConfigurationSection section, boolean isFinalStage) {
        super(factory, section);
        this.scanRadius = section.getInt("scan_radius", DEFAULT_SCAN_RADIUS);
        this.scanHeight = section.getInt("scan_height", DEFAULT_SCAN_HEIGHT);
        this.damagePerItem = section.getInt("damage_per_item", DEFAULT_DAMAGE_PER_ITEM);
        this.isFinalStage = isFinalStage;
    }

    public int scanRadius()      { return scanRadius; }
    public int scanHeight()      { return scanHeight; }
    public int damagePerItem()   { return damagePerItem; }
    public boolean isFinalStage() { return isFinalStage; }
}



