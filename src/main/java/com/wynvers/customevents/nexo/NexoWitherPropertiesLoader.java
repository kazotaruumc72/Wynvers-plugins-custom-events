package com.wynvers.customevents.nexo;
import com.wynvers.customevents.WynversCustomEvents;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
public class NexoWitherPropertiesLoader {
    public static final String DEFAULT_NEXO_ITEMS_PATH = "plugins/Nexo/items";
    private final WynversCustomEvents plugin;
    private final Map<String, WitherProperties> registry = new HashMap<>();
    public NexoWitherPropertiesLoader(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }
    public void reload(File nexoItemsDir) {
        registry.clear();
        if (nexoItemsDir == null || !nexoItemsDir.isDirectory()) {
            plugin.getLogger().warning(
                    "[WitherProperties] Nexo items dir not found: "
                    + (nexoItemsDir != null ? nexoItemsDir.getAbsolutePath() : "null"));
            return;
        }
        scanDirectory(nexoItemsDir);
        plugin.getLogger().info(
                "[WitherProperties] Loaded wither_properties for " + registry.size() + " item(s).");
    }
    public WitherProperties getProperties(String itemId) {
        if (itemId == null) return null;
        return registry.get(itemId.toLowerCase());
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
            plugin.getLogger().warning("[WitherProperties] Failed to load " + file.getName() + ": " + e.getMessage());
            return;
        }
        for (String itemId : config.getKeys(false)) {
            ConfigurationSection itemSec = config.getConfigurationSection(itemId);
            if (itemSec == null) continue;
            ConfigurationSection mechanics = itemSec.getConfigurationSection("Mechanics");
            if (mechanics == null) mechanics = itemSec.getConfigurationSection("mechanics");
            if (mechanics == null) continue;
            ConfigurationSection witherSec = mechanics.getConfigurationSection("wither_properties");
            if (witherSec == null) witherSec = mechanics.getConfigurationSection("wither-properties");
            if (witherSec == null) continue;

            // Accept both standard keys and common aliases/typos used in configs.
            boolean explosionDamage = getBooleanWithAliases(
                    witherSec,
                    true,
                    "wither_explosion_damage",
                    "wither-explosion-damage",
                    "witherExplosionDamage",
                    "whther_explosion_damage",
                    "whther_explossion_damage");
            boolean damageThrow = getBooleanWithAliases(
                    witherSec,
                    true,
                    "wither_damage_throw",
                    "wither-damage-throw",
                    "witherDamageThrow");
            registry.put(itemId.toLowerCase(), new WitherProperties(explosionDamage, damageThrow));
            plugin.getLogger().info(
                    "[WitherProperties]  '" + itemId + "' explosion=" + explosionDamage + " skull=" + damageThrow);
        }
    }

    private boolean getBooleanWithAliases(ConfigurationSection section, boolean defaultValue, String... keys) {
        for (String key : keys) {
            if (!section.contains(key)) continue;

            Object raw = section.get(key);
            if (raw instanceof Boolean b) return b;
            if (raw instanceof String s) {
                String normalized = s.trim().toLowerCase();
                if ("true".equals(normalized)) return true;
                if ("false".equals(normalized)) return false;
            }
            return section.getBoolean(key, defaultValue);
        }
        return defaultValue;
    }
}