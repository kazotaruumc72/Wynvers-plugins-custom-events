package com.wynvers.customevents.nexo.harvestermachine;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Companion mechanic for the Harvester Machine's storage block.
 *
 * <p>Acts as a 54-slot virtual chest. Items deposited by nearby Harvester
 * Machines accumulate here; players can right-click the block to open the
 * GUI; a hopper placed directly below pulls items at the usual hopper rate.
 *
 * <p>YAML example:
 * <pre>
 * wynvers_harvester_storage:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 351
 *     harvester_storage:
 *       gui_title: "&8Coffre du Récolteur"
 *       hopper_pull_period_ticks: 8   # how often a hopper below pulls one item
 * </pre>
 */
public class HarvesterStorageMechanic extends Mechanic {

    private final String guiTitle;
    private final int hopperPullPeriodTicks;

    public HarvesterStorageMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.guiTitle = section.getString("gui_title", "&8Coffre du Récolteur");
        this.hopperPullPeriodTicks = Math.max(1, section.getInt("hopper_pull_period_ticks", 8));
    }

    public String guiTitle()              { return guiTitle; }
    public int hopperPullPeriodTicks()    { return hopperPullPeriodTicks; }
}