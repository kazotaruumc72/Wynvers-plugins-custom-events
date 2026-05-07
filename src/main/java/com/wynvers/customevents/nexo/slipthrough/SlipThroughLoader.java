package com.wynvers.customevents.nexo.slipthrough;

import com.wynvers.customevents.WynversCustomEvents;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans Nexo item files for the {@code Mechanics.custom_block.to_slip_through.enable}
 * flag and exposes the set of Nexo IDs that should let items slip through.
 *
 * <p>Nexo's mechanic-factory parser only sees top-level entries in {@code Mechanics:}.
 * Since {@code to_slip_through} is nested inside {@code custom_block}, we read it
 * directly from the YAML — same pattern as {@link com.wynvers.customevents.nexo.NexoWitherPropertiesLoader}.
 */
public class SlipThroughLoader {

    private final WynversCustomEvents plugin;
    private final Set<String> slipThroughIds = ConcurrentHashMap.newKeySet();

    public SlipThroughLoader(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    public boolean isEmpty() {
        return slipThroughIds.isEmpty();
    }

    public boolean isSlipThrough(String nexoId) {
        return nexoId != null && slipThroughIds.contains(nexoId.toLowerCase());
    }

    public void reload(File nexoItemsDir) {
        slipThroughIds.clear();
        if (nexoItemsDir == null || !nexoItemsDir.isDirectory()) {
            plugin.getLogger().warning(
                    "[SlipThrough] Nexo items dir not found: "
                    + (nexoItemsDir != null ? nexoItemsDir.getAbsolutePath() : "null"));
            return;
        }
        scanDirectory(nexoItemsDir);
        plugin.getLogger().info(
                "[SlipThrough] Loaded " + slipThroughIds.size() + " slip-through block(s).");
    }

    private void scanDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                scanDirectory(child);
            } else {
                String n = child.getName().toLowerCase();
                if (n.endsWith(".yml") || n.endsWith(".yaml")) {
                    parseItemFile(child);
                }
            }
        }
    }

    private void parseItemFile(File file) {
        YamlConfiguration config;
        try {
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[SlipThrough] Failed to load " + file.getName() + ": " + e.getMessage());
            return;
        }
        for (String itemId : config.getKeys(false)) {
            ConfigurationSection itemSec = config.getConfigurationSection(itemId);
            if (itemSec == null) continue;
            ConfigurationSection mechanics = itemSec.getConfigurationSection("Mechanics");
            if (mechanics == null) mechanics = itemSec.getConfigurationSection("mechanics");
            if (mechanics == null) continue;
            ConfigurationSection cb = mechanics.getConfigurationSection("custom_block");
            if (cb == null) continue;
            ConfigurationSection slip = cb.getConfigurationSection("to_slip_through");
            if (slip == null) continue;
            if (!slip.getBoolean("enable", false)) continue;

            slipThroughIds.add(itemId.toLowerCase());
            plugin.getLogger().info("[SlipThrough]  '" + itemId + "' enabled.");
        }
    }
}