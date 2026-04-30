package com.wynvers.customevents.nexo.harvester;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for the {@link HarvesterMechanic} Nexo mechanic.
 * Registers the harvester mechanic with Nexo and provides access to its configuration.
 *
 * <p>The harvester is only enabled for items that are on the final stage of a crop
 * (i.e., items with no next_stage defined in their farmer mechanic).
 */
public class HarvesterMechanicFactory extends MechanicFactory {

    public static final String MECHANIC_ID = "harvester";
    private static HarvesterMechanicFactory instance;

    public HarvesterMechanicFactory() {
        super(MECHANIC_ID);
        instance = this;
    }

    public static HarvesterMechanicFactory instance() {
        return instance;
    }

    /**
     * Checks if an item is on the final stage of a crop (no next_stage).
     * @param itemId the Nexo item ID
     * @return true if the item has no next_stage (is final), false otherwise
     */
    private boolean isFinalStage(String itemId) {
        try {
            FarmerMechanicFactory farmerFactory = FarmerMechanicFactory.instance();
            if (farmerFactory == null) {
                // Farmer mechanic not loaded, assume final stage for safety
                return true;
            }
            var farmerMechanic = farmerFactory.getMechanic(itemId);
            if (farmerMechanic == null) {
                // No farmer config, assume final stage (standalone harvester item)
                return true;
            }
            // Check if this farmer has a next stage
            return !farmerMechanic.hasNextStage();
        } catch (Throwable ignored) {
            return true; // Assume final on error
        }
    }

    @Override
    public @Nullable HarvesterMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        if (m instanceof HarvesterMechanic h && h.isFinalStage()) {
            return h;
        }
        return null;
    }

    @Override
    public @Nullable HarvesterMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        if (m instanceof HarvesterMechanic h && h.isFinalStage()) {
            return h;
        }
        return null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        // Determine if this is the final stage by checking the item ID
        // The item ID is stored in the parent section's key
        String itemId = section.getName();
        if (itemId == null) {
            itemId = "";
        }

        boolean isFinal = isFinalStage(itemId);
        HarvesterMechanic mechanic = new HarvesterMechanic(this, section, isFinal);
        addToImplemented(mechanic);

        // Register in cache if this is a final-stage harvester
        if (isFinal && !itemId.isEmpty()) {
            HarvesterCache.registerFinalStageItem(itemId);
        }

        return mechanic;
    }
}


