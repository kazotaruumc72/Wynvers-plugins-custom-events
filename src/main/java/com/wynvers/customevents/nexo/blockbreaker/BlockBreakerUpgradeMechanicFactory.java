package com.wynvers.customevents.nexo.blockbreaker;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Nexo {@link MechanicFactory} for the {@code block_breaker_upgrade} mechanic.
 *
 * <p>Every upgrade item defined in Nexo's items folder simply declares
 * {@code Mechanics.block_breaker_upgrade.type/value}; this factory parses
 * those sections and stores the resulting mechanics so
 * {@link BlockBreakerUpgrade#fromItem} can recognise them by Nexo id.
 */
public class BlockBreakerUpgradeMechanicFactory extends MechanicFactory {

    public static final String MECHANIC_ID = "block_breaker_upgrade";

    private static BlockBreakerUpgradeMechanicFactory instance;

    private final JavaPlugin plugin;

    public BlockBreakerUpgradeMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
    }

    public static BlockBreakerUpgradeMechanicFactory instance() { return instance; }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        BlockBreakerUpgradeMechanic mechanic = new BlockBreakerUpgradeMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    /** Returns the registered upgrade mechanic for a Nexo item id, or {@code null}. */
    public @Nullable BlockBreakerUpgradeMechanic get(String nexoId) {
        if (nexoId == null) return null;
        Mechanic m = super.getMechanic(nexoId.toLowerCase(Locale.ROOT));
        return (m instanceof BlockBreakerUpgradeMechanic u) ? u : null;
    }
}