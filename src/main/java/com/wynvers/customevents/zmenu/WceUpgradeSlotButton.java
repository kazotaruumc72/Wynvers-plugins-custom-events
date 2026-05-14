package com.wynvers.customevents.zmenu;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Marker button used inside zMenu's {@code blockbreaker.yml} to identify a
 * slot that should accept an upgrade item.
 *
 * <p>Clicks are intentionally ignored — the WynversCustomEvents BlockBreaker
 * opens a vanilla 3-row inventory mirroring this layout and handles slot
 * interactions there. zMenu only renders the visual on the slot.
 */
public class WceUpgradeSlotButton extends Button {

    @Override
    public boolean isClickable() {
        return false;
    }

    @Override
    public void onClick(@NotNull Player player,
                        @NotNull InventoryClickEvent event,
                        @NotNull InventoryEngine inventory,
                        int slot,
                        @NotNull Placeholders placeholders) {
        event.setCancelled(true);
    }
}