package com.wynvers.customevents.zmenu;

import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.button.Button;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges WynversCustomEvents with the zMenu plugin:
 * <ul>
 *   <li>Registers the two custom buttons: {@code WCE_UPGRADE_SLOT} and
 *       {@code WCE_VALIDATION}.</li>
 *   <li>Copies the bundled {@code blockbreaker.yml} into
 *       {@code plugins/zMenu/inventories/} on first start (only the default).
 *       Tier-specific files (e.g. {@code blockbreaker_tier_1.yml}) are user-
 *       managed and lazy-loaded on demand.</li>
 *   <li>Caches the slot layout per filename so each BlockBreaker variant can
 *       reference its own {@code zmenu_inventory:} without forcing a global
 *       singleton layout.</li>
 * </ul>
 */
public final class BlockBreakerZMenuBridge {

    public static final String BUTTON_UPGRADE_SLOT = "wce_upgrade_slot";
    public static final String BUTTON_VALIDATION  = "wce_validation";
    public static final String DEFAULT_INVENTORY_FILE = "blockbreaker.yml";

    private static final Map<String, Layout> cachedByFile = new ConcurrentHashMap<>();

    private BlockBreakerZMenuBridge() {}

    /** Snapshot of the slot layout the BlockBreaker GUI should mirror. */
    public static final class Layout {
        private final int[] upgradeSlots;
        private final int validateSlot;
        private final ItemStack fillerItem;
        private final ItemStack validationItem;

        public Layout(int[] upgradeSlots, int validateSlot,
                      @Nullable ItemStack fillerItem,
                      @Nullable ItemStack validationItem) {
            this.upgradeSlots = upgradeSlots;
            this.validateSlot = validateSlot;
            this.fillerItem = fillerItem;
            this.validationItem = validationItem;
        }

        public int[] upgradeSlots()    { return upgradeSlots; }
        public int validateSlot()      { return validateSlot; }
        public @Nullable ItemStack fillerItem()     { return fillerItem; }
        public @Nullable ItemStack validationItem() { return validationItem; }
    }

    /** Returns true if zMenu is loaded and the API services are reachable. */
    public static boolean isZMenuAvailable() {
        return Bukkit.getPluginManager().getPlugin("zMenu") != null;
    }

    /**
     * Registers the two custom buttons with zMenu, copies the bundled default
     * {@code blockbreaker.yml} into zMenu's {@code inventories/} folder (if it
     * isn't already there), and eagerly caches its layout. Tier-specific
     * inventories declared via {@code zmenu_inventory:} are loaded lazily on
     * first use through {@link #layoutFor(JavaPlugin, String)}.
     */
    public static void register(JavaPlugin plugin) {
        if (!isZMenuAvailable()) {
            plugin.getLogger().info("zMenu not detected — BlockBreaker GUI will use the built-in fallback layout.");
            return;
        }
        try {
            MenuPlugin menuPlugin = getMenuPlugin();
            if (menuPlugin == null) {
                plugin.getLogger().warning("zMenu detected but MenuPlugin service is unavailable.");
                return;
            }

            ButtonManager buttons = menuPlugin.getButtonManager();
            buttons.register(new WceUpgradeSlotLoader(plugin));
            buttons.register(new WceValidationLoader(plugin));
            plugin.getLogger().info("Registered zMenu buttons: " + BUTTON_UPGRADE_SLOT + ", " + BUTTON_VALIDATION + ".");

            // Copy the bundled default into zMenu's inventories folder once.
            File invDir = zMenuInventoriesDir();
            if (invDir != null && !invDir.exists()) invDir.mkdirs();
            File defaultTarget = invDir == null ? null : new File(invDir, DEFAULT_INVENTORY_FILE);
            if (defaultTarget != null && !defaultTarget.exists()) {
                try (InputStream in = plugin.getResource("zmenu/" + DEFAULT_INVENTORY_FILE)) {
                    if (in == null) {
                        plugin.getLogger().warning("Bundled zmenu/" + DEFAULT_INVENTORY_FILE + " is missing from the jar.");
                    } else {
                        Files.copy(in, Path.of(defaultTarget.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("Installed default " + DEFAULT_INVENTORY_FILE + " into zMenu/inventories/.");
                    }
                } catch (IOException io) {
                    plugin.getLogger().warning("Failed to copy " + DEFAULT_INVENTORY_FILE + ": " + io.getMessage());
                }
            }

            // Pre-load the default file on next tick so zMenu has finished its own bootstrap.
            Bukkit.getScheduler().runTask(plugin, () -> layoutFor(plugin, DEFAULT_INVENTORY_FILE));
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to wire up zMenu BlockBreaker bridge: " + t.getMessage());
        }
    }

    /**
     * Returns the layout for the named zMenu inventory file (e.g.
     * {@code blockbreaker_tier_1.yml}), loading it from
     * {@code plugins/zMenu/inventories/} on first use. Null when zMenu is
     * absent or the file is missing — callers should fall back to the
     * hard-coded default layout.
     */
    public static @Nullable Layout layoutFor(JavaPlugin plugin, String fileName) {
        if (fileName == null || fileName.isEmpty()) fileName = DEFAULT_INVENTORY_FILE;
        Layout hit = cachedByFile.get(fileName);
        if (hit != null) return hit;
        if (!isZMenuAvailable()) return null;

        try {
            MenuPlugin menuPlugin = getMenuPlugin();
            if (menuPlugin == null) return null;
            InventoryManager invManager = menuPlugin.getInventoryManager();

            File invDir = zMenuInventoriesDir();
            if (invDir == null) return null;
            File target = new File(invDir, fileName);
            if (!target.exists()) {
                plugin.getLogger().warning("[BlockBreaker] zMenu inventory file not found: "
                        + target.getPath() + " — falling back to built-in layout.");
                return null;
            }

            Inventory loaded = invManager.loadInventory(plugin, target);
            if (loaded == null) return null;

            int[] upgradeSlots = scanSlots(loaded, BUTTON_UPGRADE_SLOT);
            int validateSlot = firstSlot(loaded, BUTTON_VALIDATION);
            Layout layout = new Layout(upgradeSlots, validateSlot, null, null);
            cachedByFile.put(fileName, layout);
            plugin.getLogger().info("Loaded zMenu inventory '" + fileName
                    + "' — upgrade slots: " + Arrays.toString(upgradeSlots)
                    + ", validate slot: " + validateSlot + ".");
            return layout;
        } catch (Throwable t) {
            plugin.getLogger().warning("[BlockBreaker] Failed to load zMenu inventory '"
                    + fileName + "': " + t.getMessage());
            return null;
        }
    }

    /** Clears the layout cache — call after a /nexo reload or zMenu reload. */
    public static void invalidate() {
        cachedByFile.clear();
    }

    private static @Nullable File zMenuInventoriesDir() {
        org.bukkit.plugin.Plugin zMenu = Bukkit.getPluginManager().getPlugin("zMenu");
        if (zMenu == null) return null;
        return new File(zMenu.getDataFolder(), "inventories");
    }

    private static int[] scanSlots(Inventory inv, String buttonName) {
        return inv.getButtons().stream()
                .filter(b -> b != null && buttonName.equalsIgnoreCase(b.getName()))
                .flatMap(b -> b.getSlots() == null ? java.util.stream.Stream.<Integer>empty() : b.getSlots().stream())
                .mapToInt(Integer::intValue)
                .sorted()
                .toArray();
    }

    private static int firstSlot(Inventory inv, String buttonName) {
        for (Button b : inv.getButtons()) {
            if (b == null || !buttonName.equalsIgnoreCase(b.getName())) continue;
            if (b.getSlots() != null && !b.getSlots().isEmpty()) {
                return b.getSlots().iterator().next();
            }
        }
        return -1;
    }

    private static @Nullable MenuPlugin getMenuPlugin() {
        try {
            return Bukkit.getServicesManager().load(MenuPlugin.class);
        } catch (Throwable t) {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("zMenu");
            return (p instanceof MenuPlugin mp) ? mp : null;
        }
    }
}