package com.wynvers.customevents.nexo;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * Nexo {@link MechanicFactory} for the {@code wither_properties} mechanic.
 * Registered into Nexo's MechanicsManager via {@code NexoMechanicsRegisteredEvent}.
 */
public class WitherPropertiesMechanicFactory extends MechanicFactory {
    public static final String MECHANIC_ID = "wither_properties";
    private static WitherPropertiesMechanicFactory instance;
    public WitherPropertiesMechanicFactory() {
        super(MECHANIC_ID);
        instance = this;
    }
    public static WitherPropertiesMechanicFactory instance() {
        return instance;
    }
    @Override @Nullable
    public WitherPropertiesMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof WitherPropertiesMechanic w) ? w : null;
    }
    @Override @Nullable
    public WitherPropertiesMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof WitherPropertiesMechanic w) ? w : null;
    }
    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        WitherPropertiesMechanic mechanic = new WitherPropertiesMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }
}