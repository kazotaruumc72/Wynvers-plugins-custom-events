package com.wynvers.customevents.nexo.blockbreaker;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all click / drag / close interactions on a BlockBreaker upgrade menu.
 *
 * <p>Slot policy:
 * <ul>
 *   <li>{@code WCE_UPGRADE_SLOT} (the 5 center slots) — interactive. Players
 *       can place an upgrade item or take it back. Clicks pass through.</li>
 *   <li>{@code WCE_VALIDATION} — clicking commits the upgrades to the block
 *       and closes the menu.</li>
 *   <li>Any decorative slot — clicks are cancelled.</li>
 * </ul>
 */
public class BlockBreakerUpgradeMenuListener implements Listener {

    private final BlockBreakerMechanicFactory factory;

    public BlockBreakerUpgradeMenuListener(BlockBreakerMechanicFactory factory) {
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BlockBreakerUpgradeMenu menu)) return;

        // Clicks inside player inventory: only allow if shift-clicking an upgrade item
        // into a free upgrade slot, otherwise treat normally — but block shift-click
        // that would spam stacks of unknown items into the menu.
        if (event.getClickedInventory() != null && event.getClickedInventory() != top) {
            // Player's own inventory.
            if (event.getClick().isShiftClick()) {
                ItemStack cursor = event.getCurrentItem();
                if (cursor == null) return;
                BlockBreakerUpgrade up = BlockBreakerUpgrade.fromItem(factory.plugin(), cursor);
                if (up == null) {
                    event.setCancelled(true); // only upgrades may shift-click into the menu
                }
                // else: let vanilla place it; the empty target slot must be an upgrade slot
                // — if all upgrade slots are full it will stay in the player inventory.
            }
            return;
        }

        // Top inventory: validate, upgrade-slot, or filler.
        int slot = event.getRawSlot();
        if (menu.isValidateSlot(slot)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                factory.applyValidation(menu, p);
            }
            return;
        }

        if (menu.isUpgradeSlot(slot)) {
            // Only allow valid BlockBreaker upgrades (or empty stacks for take-back).
            ItemStack moving = event.getCursor();
            if (moving != null && moving.getType() != org.bukkit.Material.AIR) {
                BlockBreakerUpgrade up = BlockBreakerUpgrade.fromItem(factory.plugin(), moving);
                if (up == null) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                    }
                    return;
                }
            }
            // Cap stacks at 1 to keep the per-slot semantics clean.
            if (event.getAction() == InventoryAction.PLACE_ALL
                    || event.getAction() == InventoryAction.PLACE_SOME) {
                if (moving != null && moving.getAmount() > 1) {
                    event.setCancelled(true);
                    ItemStack single = moving.clone();
                    single.setAmount(1);
                    if (top.getItem(slot) == null) {
                        top.setItem(slot, single);
                        moving.setAmount(moving.getAmount() - 1);
                        event.getView().setCursor(moving);
                    }
                }
            }
            return;
        }

        // Filler slot — block.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BlockBreakerUpgradeMenu menu)) return;
        for (int raw : event.getRawSlots()) {
            if (raw >= top.getSize()) continue;
            if (!menu.isUpgradeSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        // Only upgrade items may be dragged.
        ItemStack moving = event.getOldCursor();
        if (BlockBreakerUpgrade.fromItem(factory.plugin(), moving) == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BlockBreakerUpgradeMenu menu)) return;
        if (!(event.getPlayer() instanceof Player p)) return;
        // Return any unvalidated items still sitting in the upgrade slots so
        // they don't get destroyed by a stray ESC.
        factory.returnLeftoverItems(menu, p);
    }
}