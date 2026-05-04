package com.wynvers.customevents.nexo.runeshop;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Nexo {@link MechanicFactory} for the {@code rune_boost_zshop_sell_buy_percent}
 * mechanic.
 *
 * <p>Three responsibilities:
 * <ol>
 *   <li>Apply the rune to a piece of armor when the player drag-drops a rune
 *       item onto an armor item inside their inventory ({@link InventoryClickEvent}).
 *       The rolled percent, expiry timestamp and rune color are stored in the
 *       armor's {@link PersistentDataContainer}, the rune is consumed.</li>
 *   <li>Periodically refresh the rune lore (countdown) on the wearer's armor
 *       and clean up expired runes.</li>
 *   <li>If zShop is loaded, register the {@link ShopRuneZShopListener} so that
 *       sell/buy prices are adjusted based on the wearer's active runes.</li>
 * </ol>
 */
public class ShopRuneMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "rune_boost_zshop_sell_buy_percent";

    private static ShopRuneMechanicFactory instance;

    private final JavaPlugin plugin;
    final NamespacedKey keyPercent;
    final NamespacedKey keyUntil;
    final NamespacedKey keyColor;
    private final NamespacedKey keyOrigLore;

    private BukkitTask refreshTask;

    public ShopRuneMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        this.keyPercent  = new NamespacedKey(plugin, "runeshop_pct");
        this.keyUntil    = new NamespacedKey(plugin, "runeshop_until");
        this.keyColor    = new NamespacedKey(plugin, "runeshop_color");
        this.keyOrigLore = new NamespacedKey(plugin, "runeshop_origlore");

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Refresh once per second: countdown text on equipped armor + cleanup
        // of expired runes. 20 ticks = 1 second.
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::refreshAllOnlinePlayers, 20L, 20L);

        // Conditional zShop hook (the listener class only references zShop
        // event types — keep it isolated so the rest of the mechanic still
        // loads when zShop is absent).
        if (Bukkit.getPluginManager().getPlugin("zShop") != null) {
            try {
                Bukkit.getPluginManager().registerEvents(
                        new ShopRuneZShopListener(this), plugin);
                plugin.getLogger().info("Hooked into zShop for rune boost.");
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to register zShop listener: " + t.getMessage());
            }
        }
    }

    public static ShopRuneMechanicFactory instance() { return instance; }

    @Override
    public @Nullable ShopRuneMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof ShopRuneMechanic r) ? r : null;
    }

    @Override
    public @Nullable ShopRuneMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof ShopRuneMechanic r) ? r : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        ShopRuneMechanic mechanic = new ShopRuneMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    public void shutdown() {
        if (refreshTask != null) {
            try { refreshTask.cancel(); } catch (Throwable ignored) {}
            refreshTask = null;
        }
    }

    // ─── Apply on drag-drop ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // The "drag-drop" gesture is: rune on cursor, target armor in slot.
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor == null || cursor.getType().isAir()) return;
        if (target == null || target.getType().isAir()) return;

        ShopRuneMechanic rune = getMechanic(cursor);
        if (rune == null) return;
        if (!isArmor(target.getType())) return;

        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;

        event.setCancelled(true);

        int pct = rune.rollPercent();
        long until = System.currentTimeMillis() + rune.durationSeconds() * 1000L;
        applyRune(target, pct, until, rune.colorCode());

        // Consume one rune from the cursor.
        if (cursor.getAmount() <= 1) {
            event.setCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            event.setCursor(cursor);
        }

        player.updateInventory();
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                rune.colorCode() + "Rune appliquée : Boost shop +" + pct + "% pendant "
                        + formatDuration(rune.durationSeconds()) + "."));
    }

    // ─── Lore refresh on inventory open ────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        refreshPlayer(player);
    }

    // ─── PDC application and refresh ───────────────────────────────────────

    private void applyRune(ItemStack armor, int pct, long until, String colorCode) {
        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Capture original lore on first application only (or rerun: keep the
        // stored origlore so a second application doesn't lose track of it).
        if (!pdc.has(keyOrigLore, PersistentDataType.STRING)) {
            List<String> existing = meta.getLore();
            String joined = (existing == null) ? "" : String.join("\n", existing);
            pdc.set(keyOrigLore, PersistentDataType.STRING, joined);
        }

        pdc.set(keyPercent, PersistentDataType.INTEGER, pct);
        pdc.set(keyUntil, PersistentDataType.LONG, until);
        pdc.set(keyColor, PersistentDataType.STRING, colorCode);

        meta.setLore(buildLore(pdc, pct, until, colorCode));
        armor.setItemMeta(meta);
    }

    /**
     * Refreshes the lore (chrono) of the given armor in place if it carries a
     * shop rune. Removes the rune (and restores original lore) if it expired.
     *
     * @return true if the item was modified.
     */
    boolean refreshArmor(ItemStack armor) {
        if (armor == null || armor.getType().isAir()) return false;
        if (!isArmor(armor.getType())) return false;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Long until = pdc.get(keyUntil, PersistentDataType.LONG);
        if (until == null) return false;

        Integer pct = pdc.get(keyPercent, PersistentDataType.INTEGER);
        String color = pdc.get(keyColor, PersistentDataType.STRING);
        if (pct == null || color == null) {
            // Inconsistent state — treat as no rune.
            stripRune(meta, pdc);
            armor.setItemMeta(meta);
            return true;
        }

        if (System.currentTimeMillis() >= until) {
            stripRune(meta, pdc);
            armor.setItemMeta(meta);
            return true;
        }

        meta.setLore(buildLore(pdc, pct, until, color));
        armor.setItemMeta(meta);
        return true;
    }

    private void stripRune(ItemMeta meta, PersistentDataContainer pdc) {
        String origJoined = pdc.get(keyOrigLore, PersistentDataType.STRING);
        if (origJoined == null || origJoined.isEmpty()) {
            meta.setLore(null);
        } else {
            meta.setLore(new ArrayList<>(List.of(origJoined.split("\n", -1))));
        }
        pdc.remove(keyPercent);
        pdc.remove(keyUntil);
        pdc.remove(keyColor);
        pdc.remove(keyOrigLore);
    }

    private List<String> buildLore(PersistentDataContainer pdc, int pct, long until, String colorCode) {
        String origJoined = pdc.get(keyOrigLore, PersistentDataType.STRING);
        List<String> lines = new ArrayList<>();
        if (origJoined != null && !origJoined.isEmpty()) {
            for (String s : origJoined.split("\n", -1)) lines.add(s);
        }
        long remaining = Math.max(0L, (until - System.currentTimeMillis()) / 1000L);
        String color = ChatColor.translateAlternateColorCodes('&', colorCode);
        lines.add(ChatColor.translateAlternateColorCodes('&', "&6Runes:"));
        lines.add(ChatColor.translateAlternateColorCodes('&', "&7- ") + color + "Boost shop +" + pct + "%");
        lines.add("");
        lines.add(ChatColor.translateAlternateColorCodes('&', "&7Durée:"));
        lines.add(ChatColor.translateAlternateColorCodes('&', "&7- ") + color + formatDuration(remaining));
        return lines;
    }

    private void refreshAllOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) refreshPlayer(p);
    }

    private void refreshPlayer(Player player) {
        PlayerInventory inv = player.getInventory();
        boolean changed = false;
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (refreshArmor(armor[i])) changed = true;
        }
        if (changed) inv.setArmorContents(armor);

        // Inventory contents (held + storage) — only refresh the chrono on
        // items that already carry a rune (no new application here).
        ItemStack[] contents = inv.getStorageContents();
        boolean storageChanged = false;
        for (int i = 0; i < contents.length; i++) {
            if (refreshArmor(contents[i])) storageChanged = true;
        }
        if (storageChanged) inv.setStorageContents(contents);
    }

    // ─── Public access for the zShop listener ──────────────────────────────

    /**
     * Sums the active rune percentages across all four equipped armor slots.
     * Expired runes contribute zero (they get cleaned on the next refresh
     * tick).
     */
    public int sumActivePercent(Player player) {
        int sum = 0;
        long now = System.currentTimeMillis();
        for (ItemStack a : player.getInventory().getArmorContents()) {
            if (a == null || a.getType().isAir()) continue;
            ItemMeta m = a.getItemMeta();
            if (m == null) continue;
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            Long until = pdc.get(keyUntil, PersistentDataType.LONG);
            Integer pct = pdc.get(keyPercent, PersistentDataType.INTEGER);
            if (until == null || pct == null) continue;
            if (now >= until) continue;
            sum += pct;
        }
        return sum;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private static boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS")
                || n.equals("ELYTRA")
                || n.equals("TURTLE_HELMET");
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}