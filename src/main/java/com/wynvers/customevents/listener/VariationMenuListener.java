package com.wynvers.customevents.listener;

import com.wynvers.customevents.nexo.VariationMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VariationMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof VariationMenu menu)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == VariationMenu.SLOT_PREV) {
            if (menu.getPage() > 0) {
                menu.prevPage();
                player.openInventory(menu.getInventory());
            }
        } else if (slot == VariationMenu.SLOT_NEXT) {
            if (menu.getPage() < VariationMenu.TOTAL_PAGES - 1) {
                menu.nextPage();
                player.openInventory(menu.getInventory());
            }
        } else if (slot == VariationMenu.SLOT_CLOSE) {
            player.closeInventory();
        }
    }
}