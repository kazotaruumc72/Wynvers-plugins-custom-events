package com.wynvers.customevents.nexo.harvesting;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for the {@link HarvestingMechanic} Nexo mechanic – attached to
 * tools (e.g. a custom hoe) that can mass-harvest farmer crops in range.
 */
public class HarvestingMechanicFactory extends MechanicFactory {

    public static final String MECHANIC_ID = "harvesting";

    private static HarvestingMechanicFactory instance;

    public HarvestingMechanicFactory() {
        super(MECHANIC_ID);
        instance = this;
    }

    public static HarvestingMechanicFactory instance() {
        return instance;
    }

    @Override
    public @Nullable HarvestingMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof HarvestingMechanic h) ? h : null;
    }

    @Override
    public @Nullable HarvestingMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof HarvestingMechanic h) ? h : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        HarvestingMechanic mechanic = new HarvestingMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }
}

