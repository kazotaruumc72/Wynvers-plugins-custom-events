package com.wynvers.customevents.zmenu;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Marker button used inside zMenu's {@code blockbreaker.yml} to identify the
 * slot that finalises the upgrades currently sitting in the
 * {@link WceUpgradeSlotButton} slots.
 *
 * <p>Click handling for the actual BlockBreaker GUI is performed by
 * {@link com.wynvers.customevents.nexo.blockbreaker.BlockBreakerUpgradeMenuListener}
 * — see {@link BlockBreakerZMenuBridge} for the rationale.
 */
public class WceValidationButton extends Button {

    @Override
    public void onClick(@NotNull Player player,
                        @NotNull InventoryClickEvent event,
                        @NotNull InventoryEngine inventory,
                        int slot,
                        @NotNull Placeholders placeholders) {
        event.setCancelled(true);
    }
}