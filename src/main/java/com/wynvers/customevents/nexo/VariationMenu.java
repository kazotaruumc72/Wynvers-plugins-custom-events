package com.wynvers.customevents.nexo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paginated GUI listing every custom_variation in [0, 1149).
 * Top 5 rows (slots 0..44) display variations as dyes:
 *   - {@link Material#RED_DYE}        used by a single item
 *   - {@link Material#ORANGE_DYE}     used by multiple items (duplicate)
 *   - {@link Material#GRAY_DYE}       unused (free)
 * Bottom row (slots 45..53) is the navigation bar.
 */
public class VariationMenu implements InventoryHolder {

    public static final int MAX_VARIATION = 1149;
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int VARIATIONS_PER_PAGE = 45;
    public static final int TOTAL_PAGES =
            (MAX_VARIATION + VARIATIONS_PER_PAGE - 1) / VARIATIONS_PER_PAGE;

    public static final int SLOT_PREV = 45;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_NEXT = 53;

    private final CustomVariationAnalyzer.VariationAnalysisResult data;
    private int page;
    private Inventory inventory;

    public VariationMenu(CustomVariationAnalyzer.VariationAnalysisResult data, int page) {
        this.data = data;
        this.page = clampPage(page);
        rebuild();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public int getPage() {
        return page;
    }

    public void nextPage() {
        setPage(page + 1);
    }

    public void prevPage() {
        setPage(page - 1);
    }

    private void setPage(int newPage) {
        this.page = clampPage(newPage);
        rebuild();
    }

    private static int clampPage(int page) {
        if (page < 0) return 0;
        if (page >= TOTAL_PAGES) return TOTAL_PAGES - 1;
        return page;
    }

    private void rebuild() {
        String title = ChatColor.DARK_GRAY + "Variations "
                + ChatColor.GRAY + "(" + ChatColor.YELLOW + (page + 1)
                + ChatColor.GRAY + "/" + ChatColor.YELLOW + TOTAL_PAGES
                + ChatColor.GRAY + ")";
        inventory = Bukkit.createInventory(this, SIZE, title);

        int start = page * VARIATIONS_PER_PAGE;
        int end = Math.min(start + VARIATIONS_PER_PAGE, MAX_VARIATION);
        for (int variation = start; variation < end; variation++) {
            inventory.setItem(variation - start, buildVariationItem(variation));
        }

        ItemStack filler = simpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int s = 45; s <= 53; s++) {
            inventory.setItem(s, filler);
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, simpleItem(Material.ARROW,
                    ChatColor.YELLOW + "« Previous page",
                    Collections.singletonList(ChatColor.GRAY + "Go to page "
                            + ChatColor.WHITE + page + ChatColor.GRAY + "/" + TOTAL_PAGES)));
        }

        inventory.setItem(SLOT_CLOSE, simpleItem(Material.BARRIER,
                ChatColor.RED + "Close", null));

        if (page < TOTAL_PAGES - 1) {
            inventory.setItem(SLOT_NEXT, simpleItem(Material.ARROW,
                    ChatColor.YELLOW + "Next page »",
                    Collections.singletonList(ChatColor.GRAY + "Go to page "
                            + ChatColor.WHITE + (page + 2) + ChatColor.GRAY + "/" + TOTAL_PAGES)));
        }
    }

    private ItemStack buildVariationItem(int variation) {
        boolean isDuplicate = data.duplicates.containsKey(variation);
        List<String> users = data.usedVariations.get(variation);
        boolean isUsed = users != null && !users.isEmpty();

        Material mat;
        String status;
        if (isDuplicate) {
            mat = Material.ORANGE_DYE;
            status = ChatColor.GOLD + "Duplicate " + ChatColor.GRAY + "("
                    + ChatColor.WHITE + users.size() + ChatColor.GRAY + " items)";
        } else if (isUsed) {
            mat = Material.RED_DYE;
            status = ChatColor.RED + "Used";
        } else {
            mat = Material.GRAY_DYE;
            status = ChatColor.GREEN + "Free";
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Status: " + status);
        if (isUsed) {
            lore.add("");
            lore.add(ChatColor.GRAY + "Items:");
            for (String item : users) {
                lore.add(ChatColor.DARK_GRAY + "• " + ChatColor.WHITE + item);
            }
        }

        return simpleItem(mat,
                ChatColor.YELLOW + "Variation " + ChatColor.GOLD + "#" + variation,
                lore);
    }

    private static ItemStack simpleItem(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}