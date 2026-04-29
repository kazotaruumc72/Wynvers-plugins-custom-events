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
            int explosionPct = getPercentWithAliases(
                    witherSec,
                    "wither_explosion_break_block_percent",
                    "wither-explosion-break-block-percent",
                    "witherExplosionBreakBlockPercent");
            int skullPct = getPercentWithAliases(
                    witherSec,
                    "wither_damage_throw_break_block_percent",
                    "wither-damage-throw-break-block-percent",
                    "witherDamageThrowBreakBlockPercent",
                    "wither_skull_break_block_percent");
            registry.put(itemId.toLowerCase(), new WitherProperties(
                    explosionDamage, damageThrow, explosionPct, skullPct));
            plugin.getLogger().info(
                    "[WitherProperties]  '" + itemId + "' explosion=" + explosionDamage
                    + " skull=" + damageThrow
                    + (explosionPct >= 0 ? " explosionBreak%=" + explosionPct : "")
                    + (skullPct >= 0 ? " skullBreak%=" + skullPct : ""));
        }
    }

    private int getPercentWithAliases(ConfigurationSection section, String... keys) {
        for (String k : keys) {
            if (!section.contains(k)) continue;
            Object raw = section.get(k);
            int v;
            if (raw instanceof Number n) {
                v = n.intValue();
            } else if (raw instanceof String s) {
                try { v = Integer.parseInt(s.trim().replace("%", "")); }
                catch (NumberFormatException e) { return WitherProperties.CHANCE_UNSET; }
            } else {
                return WitherProperties.CHANCE_UNSET;
            }
            if (v < 0)   v = 0;
            if (v > 100) v = 100;
            return v;
        }
        return WitherProperties.CHANCE_UNSET;
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