package com.wynvers.customevents.nexo.blockbreaker;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Parser for upgrade items recognised by the BlockBreaker.
 *
 * <p><strong>Nexo-only.</strong> An ItemStack is considered an upgrade exactly
 * when its Nexo item id resolves to a {@link BlockBreakerUpgradeMechanic}
 * registered via the {@code block_breaker_upgrade} Nexo mechanic. Plain
 * Bukkit items — even with the right NBT — are rejected on purpose so that
 * every upgrade lives inside Nexo's items folder.
 */
public final class BlockBreakerUpgrade {

    public enum Type { FORTUNE, SPEED, RANGE, SILK_TOUCH, AUTO_SMELT, UNKNOWN }

    private final Type type;
    private final int value;

    public BlockBreakerUpgrade(Type type, int value) {
        this.type = type;
        this.value = value;
    }

    public Type type()   { return type; }
    public int value()   { return value; }
    public boolean isUnknown() { return type == Type.UNKNOWN; }

    public static BlockBreakerUpgrade fromItem(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        String nexoId;
        try {
            nexoId = NexoItems.idFromItem(stack);
        } catch (Throwable t) {
            return null; // Nexo absent
        }
        if (nexoId == null) return null;

        BlockBreakerUpgradeMechanicFactory factory = BlockBreakerUpgradeMechanicFactory.instance();
        if (factory == null) return null;

        BlockBreakerUpgradeMechanic mech = factory.get(nexoId);
        if (mech == null || mech.type() == Type.UNKNOWN) return null;
        return new BlockBreakerUpgrade(mech.type(), mech.value());
    }
}