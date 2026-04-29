package com.wynvers.customevents.nexo.farmer;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Nexo {@link MechanicFactory} for the {@code farmer} mechanic.
 *
 * <p>Registered into Nexo's MechanicsManager via
 * {@code NexoMechanicsRegisteredEvent}, exactly like
 * {@code wither_properties}. Once registered, every Nexo item file with a
 * top-level {@code Mechanics.farmer:} section is parsed by Nexo and a
 * {@link FarmerMechanic} instance is bound to that item ID.
 */
public class FarmerMechanicFactory extends MechanicFactory {

    public static final String MECHANIC_ID = "farmer";

    private static FarmerMechanicFactory instance;

    public FarmerMechanicFactory() {
        super(MECHANIC_ID);
        instance = this;
    }

    public static FarmerMechanicFactory instance() {
        return instance;
    }

    @Override
    public @Nullable FarmerMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof FarmerMechanic f) ? f : null;
    }

    @Override
    public @Nullable FarmerMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof FarmerMechanic f) ? f : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        FarmerMechanic mechanic = new FarmerMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }
}

