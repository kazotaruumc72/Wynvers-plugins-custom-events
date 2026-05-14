package com.wynvers.customevents.nexo;

import com.wynvers.customevents.WynversCustomEvents;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class CustomVariationAnalyzer {
    private final WynversCustomEvents plugin;

    public CustomVariationAnalyzer(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    public VariationAnalysisResult analyzeVariations(File nexoItemsDir) {
        VariationAnalysisResult result = new VariationAnalysisResult();
        Map<Integer, List<String>> usedVariations = new HashMap<>();

        // Prefer Nexo's runtime cache when available: only items Nexo actually
        // loaded count, so removed/excluded entries vanish immediately after
        // /nexo reload. Fall back to a recursive YAML walk if the cache is
        // unavailable (Nexo missing, or items not yet loaded).
        boolean usedRuntime = scanFromNexoRuntime(usedVariations);
        if (!usedRuntime) {
            if (nexoItemsDir == null || !nexoItemsDir.isDirectory()) {
                result.error = "Nexo items directory not found: " +
                        (nexoItemsDir != null ? nexoItemsDir.getAbsolutePath() : "null");
                return result;
            }
            scanDirectory(nexoItemsDir, usedVariations);
        }

        for (Map.Entry<Integer, List<String>> entry : usedVariations.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.duplicates.put(entry.getKey(), entry.getValue());
            }
        }

        // NOTEBLOCK custom_block type allows up to 1149 variations (one per
        // blockstate), so the free pool spans [0, 1149).
        int maxVariation = 1149;
        for (int i = 0; i < maxVariation; i++) {
            if (!usedVariations.containsKey(i)) {
                result.freeVariations.add(i);
            }
        }

        result.usedVariations = usedVariations;
        result.runtimeBacked = usedRuntime;

        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean scanFromNexoRuntime(Map<Integer, List<String>> usedVariations) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Nexo") == null) return false;
            Map<String, kotlin.Pair<File, ConfigurationSection>> cache =
                    com.nexomc.nexo.api.NexoItems.INSTANCE.getItemConfigCache();
            if (cache == null || cache.isEmpty()) return false;

            for (Map.Entry<String, kotlin.Pair<File, ConfigurationSection>> e : cache.entrySet()) {
                String itemId = e.getKey();
                ConfigurationSection sec = e.getValue() != null ? e.getValue().getSecond() : null;
                if (sec == null) continue;
                collectVariationsRecursive(itemId, sec, usedVariations);
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().fine("[CustomVariationAnalyzer] runtime scan unavailable: " + t.getMessage());
            return false;
        }
    }

    private void scanDirectory(File dir, Map<Integer, List<String>> usedVariations) {
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                scanDirectory(child, usedVariations);
            } else {
                String name = child.getName().toLowerCase();
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    parseItemFile(child, usedVariations);
                }
            }
        }
    }

    private void parseItemFile(File file, Map<Integer, List<String>> usedVariations) {
        YamlConfiguration config;
        try {
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[CustomVariationAnalyzer] Failed to load " + file.getName() + ": " + e.getMessage());
            return;
        }

        for (String itemId : config.getKeys(false)) {
            ConfigurationSection itemSec = config.getConfigurationSection(itemId);
            if (itemSec == null) continue;
            collectVariationsRecursive(itemId, itemSec, usedVariations);
        }
    }

    private void collectVariationsRecursive(String itemId,
                                            ConfigurationSection sec,
                                            Map<Integer, List<String>> usedVariations) {
        for (String key : sec.getKeys(false)) {
            Object val = sec.get(key);
            if ("custom_variation".equals(key) && val instanceof Number) {
                int variation = ((Number) val).intValue();
                usedVariations.computeIfAbsent(variation, k -> new ArrayList<>()).add(itemId);
            } else if (val instanceof ConfigurationSection) {
                collectVariationsRecursive(itemId, (ConfigurationSection) val, usedVariations);
            }
        }
    }

    public static class VariationAnalysisResult {
        public Map<Integer, List<String>> usedVariations = new HashMap<>();
        public Map<Integer, List<String>> duplicates = new HashMap<>();
        public List<Integer> freeVariations = new ArrayList<>();
        public boolean runtimeBacked = false;
        public String error = null;

        public boolean hasError() {
            return error != null;
        }
    }
}
