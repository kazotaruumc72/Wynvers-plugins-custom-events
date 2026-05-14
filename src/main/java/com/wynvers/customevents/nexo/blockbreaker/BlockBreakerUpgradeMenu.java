package com.wynvers.customevents.nexo.blockbreaker;

import com.wynvers.customevents.zmenu.BlockBreakerZMenuBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A 3-row Bukkit inventory wrapping a single BlockBreaker's upgrade slots.
 *
 * <p>The layout matches the spec exactly:
 * <pre>
 *  XXXXXXXXX
 *  XX00000XX
 *  XXXXVXXXX
 * </pre>
 * — X (filler/decoration), 0 (one of the 5 WCE_UPGRADE_SLOT slots), V (the
 * single WCE_VALIDATION slot). The exact slot positions and decorative items
 * are pulled from zMenu's {@code blockbreaker.yml} when present; otherwise we
 * fall back to the hard-coded layout.
 *
 * <p>The instance also serves as {@link InventoryHolder} so the click listener
 * can recognise our inventories without doing string-matching on titles.
 */
public class BlockBreakerUpgradeMenu implements InventoryHolder {

    /** Default WCE_UPGRADE_SLOT slots (row 2, columns 2-6) for the standard 27-slot layout. */
    public static final int[] DEFAULT_UPGRADE_SLOTS = {11, 12, 13, 14, 15};
    /** Default WCE_VALIDATION slot (row 3, column 5). */
    public static final int DEFAULT_VALIDATE_SLOT  = 22;

    private final String blockKey;
    private final BlockBreakerMechanic mechanic;
    private final int[] upgradeSlots;
    private final int validateSlot;
    private final BlockBreakerZMenuBridge.Layout layout;
    private Inventory inventory;

    public BlockBreakerUpgradeMenu(String blockKey, BlockBreakerMechanic mechanic, JavaPlugin plugin) {
        this.blockKey = blockKey;
        this.mechanic = mechanic;

        BlockBreakerZMenuBridge.Layout layout = BlockBreakerZMenuBridge.layoutFor(plugin, mechanic.zmenuInventory());
        this.layout = layout;
        if (layout != null && layout.upgradeSlots().length > 0) {
            int n = Math.min(layout.upgradeSlots().length, mechanic.maxUpgrades());
            this.upgradeSlots = Arrays.copyOf(layout.upgradeSlots(), n);
            this.validateSlot = layout.validateSlot() >= 0 ? layout.validateSlot() : DEFAULT_VALIDATE_SLOT;
        } else {
            int n = Math.min(DEFAULT_UPGRADE_SLOTS.length, mechanic.maxUpgrades());
            this.upgradeSlots = Arrays.copyOf(DEFAULT_UPGRADE_SLOTS, n);
            this.validateSlot = DEFAULT_VALIDATE_SLOT;
        }
    }

    public String blockKey() { return blockKey; }
    public int[] upgradeSlots() { return upgradeSlots; }
    public int validateSlot() { return validateSlot; }

    public boolean isUpgradeSlot(int slot) {
        for (int s : upgradeSlots) if (s == slot) return true;
        return false;
    }

    public boolean isValidateSlot(int slot) {
        return slot == validateSlot;
    }

    /** Builds and shows the inventory to the player, preloaded with the breaker's existing upgrade items. */
    public void open(Player viewer, ItemStack[] preloadedUpgrades) {
        String title = ChatColor.translateAlternateColorCodes('&', mechanic.guiTitle());
        this.inventory = Bukkit.createInventory(this, 27, title);

        // Fill 'X' slots with the filler item.
        ItemStack filler = filler();
        for (int i = 0; i < 27; i++) {
            if (isUpgradeSlot(i) || isValidateSlot(i)) continue;
            inventory.setItem(i, filler);
        }

        // Validation button.
        inventory.setItem(validateSlot, validationItem());

        // Preload existing upgrades.
        if (preloadedUpgrades != null) {
            for (int i = 0; i < upgradeSlots.length && i < preloadedUpgrades.length; i++) {
                if (preloadedUpgrades[i] != null) {
                    inventory.setItem(upgradeSlots[i], preloadedUpgrades[i].clone());
                }
            }
        }

        viewer.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Returns the upgrade items currently sitting in the upgrade slots, slot-aligned. */
    public ItemStack[] readCurrentUpgrades() {
        ItemStack[] out = new ItemStack[upgradeSlots.length];
        for (int i = 0; i < upgradeSlots.length; i++) {
            ItemStack s = inventory.getItem(upgradeSlots[i]);
            out[i] = (s == null || s.getType() == Material.AIR) ? null : s.clone();
        }
        return out;
    }

    /** Empties the upgrade slots without touching anything else; used after validation. */
    public void clearUpgradeSlots() {
        for (int s : upgradeSlots) inventory.setItem(s, null);
    }

    // ─── Visual items ─────────────────────────────────────────────────────

    @Nullable
    private ItemStack filler() {
        if (layout != null && layout.fillerItem() != null) return layout.fillerItem().clone();
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        if (m != null) {
            m.setDisplayName(" ");
            pane.setItemMeta(m);
        }
        return pane;
    }

    private ItemStack validationItem() {
        if (layout != null && layout.validationItem() != null) return layout.validationItem().clone();
        ItemStack stack = new ItemStack(Material.LIME_DYE);
        ItemMeta m = stack.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.GREEN + "Valider les upgrades");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Clique pour appliquer les upgrades");
            lore.add(ChatColor.GRAY + "déposées dans les emplacements.");
            m.setLore(lore);
            stack.setItemMeta(m);
        }
        return stack;
    }
}