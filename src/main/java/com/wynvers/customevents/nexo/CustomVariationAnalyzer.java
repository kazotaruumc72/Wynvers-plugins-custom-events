package com.wynvers.customevents.nexo;

import com.wynvers.customevents.WynversCustomEvents;
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

        if (nexoItemsDir == null || !nexoItemsDir.isDirectory()) {
            result.error = "Nexo items directory not found: " +
                    (nexoItemsDir != null ? nexoItemsDir.getAbsolutePath() : "null");
            return result;
        }

        Map<Integer, List<String>> usedVariations = new HashMap<>();

        scanDirectory(nexoItemsDir, usedVariations);

        // Find duplicates
        for (Map.Entry<Integer, List<String>> entry : usedVariations.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.duplicates.put(entry.getKey(), entry.getValue());
            }
        }

        // Find free variations (0-255 typically, but let's check up to 256)
        int maxVariation = 256;
        for (int i = 0; i < maxVariation; i++) {
            if (!usedVariations.containsKey(i)) {
                result.freeVariations.add(i);
            }
        }

        result.usedVariations = usedVariations;

        return result;
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

            extractVariations(itemId, itemSec, usedVariations);
        }
    }

    private void extractVariations(String itemId, ConfigurationSection section, Map<Integer, List<String>> usedVariations) {
        ConfigurationSection mechanics = section.getConfigurationSection("Mechanics");
        if (mechanics == null) {
            mechanics = section.getConfigurationSection("mechanics");
        }

        if (mechanics != null) {
            // Check custom_block mechanic
            ConfigurationSection customBlock = mechanics.getConfigurationSection("custom_block");
            if (customBlock != null && customBlock.contains("custom_variation")) {
                int variation = customBlock.getInt("custom_variation");
                addVariationEntry(usedVariations, variation, itemId);
            }

            // Check other mechanics recursively for custom_variation
            for (String key : mechanics.getKeys(false)) {
                if (key.equals("custom_block")) continue;
                ConfigurationSection mechSection = mechanics.getConfigurationSection(key);
                if (mechSection != null && mechSection.contains("custom_variation")) {
                    int variation = mechSection.getInt("custom_variation");
                    addVariationEntry(usedVariations, variation, itemId);
                }
            }
        }
    }

    private void addVariationEntry(Map<Integer, List<String>> map, int variation, String itemId) {
        map.computeIfAbsent(variation, k -> new ArrayList<>()).add(itemId);
    }

    public static class VariationAnalysisResult {
        public Map<Integer, List<String>> usedVariations = new HashMap<>();
        public Map<Integer, List<String>> duplicates = new HashMap<>();
        public List<Integer> freeVariations = new ArrayList<>();
        public String error = null;

        public boolean hasError() {
            return error != null;
        }
    }
}