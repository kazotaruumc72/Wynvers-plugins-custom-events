package com.wynvers.customevents.nexo.blockbreaker;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Auto-generates the 63 sibling Nexo variants required for per-face texture
 * swapping on a BlockBreaker.
 *
 * <p>The admin writes a single base item (with {@code Mechanics.block_breaker.active_textures}
 * mapping faces to their "on" texture paths). On every plugin {@code onLoad},
 * before Nexo starts loading items, this generator scans every {@code .yml}
 * in {@code plugins/Nexo/items/}, finds breaker items that declare
 * {@code active_textures}, deletes the previously-generated companion file
 * for each, and writes a fresh one containing every face-combination variant.
 *
 * <p>Naming convention: each variant id is
 * {@code <base_id>_<sorted-initials>} where the initials are {@code d, e, n,
 * s, u, w} of the active faces (sorted alphabetically). For Fortune 6
 * (= all six faces) the id is {@code <base>_densuw}.
 *
 * <p>Variants share the base's {@code Mechanics} except for their unique
 * {@code custom_variation} (assigned from the first free slots in the items
 * directory) and {@code Pack.textures} where each active face's slot is
 * replaced by the corresponding entry in {@code active_textures}. They also
 * carry {@code variant_base: <base_id>} so the runtime {@link BlockBreakerMechanic}
 * can compute swap targets.
 */
public class BlockBreakerVariantGenerator {

    private static final String VARIANT_SUFFIX = "_breaker_variants.yml";

    /** Face initial → face name pairs, in deterministic order. */
    private static final char[] FACE_CHARS = {'d', 'e', 'n', 's', 'u', 'w'};
    private static final String[] FACE_NAMES = {"down", "east", "north", "south", "up", "west"};

    private final JavaPlugin plugin;

    public BlockBreakerVariantGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Entry point — call once during {@code onLoad} before Nexo loads. */
    public void generateAll(File nexoItemsDir) {
        if (nexoItemsDir == null || !nexoItemsDir.isDirectory()) {
            plugin.getLogger().warning("[BlockBreaker] Variant generator: items dir not found ("
                    + nexoItemsDir + ").");
            return;
        }

        // 1. Wipe previously-generated files so their custom_variations are
        //    freed up before we scan and reassign.
        File[] all = nexoItemsDir.listFiles((d, n) -> n.endsWith(VARIANT_SUFFIX));
        if (all != null) for (File f : all) f.delete();

        // 2. Index every used custom_variation across the remaining files.
        Set<Integer> usedVariations = scanUsedVariations(nexoItemsDir);

        // 3. For each base file, look for items with active_textures and
        //    emit a sibling variant file.
        File[] files = nexoItemsDir.listFiles((d, n) -> n.endsWith(".yml") && !n.endsWith(VARIANT_SUFFIX));
        if (files == null) {
            plugin.getLogger().warning("[BlockBreaker] Variant generator: no .yml files in " + nexoItemsDir);
            return;
        }
        int totalFiles = 0, totalItems = 0;
        for (File baseFile : files) {
            int n = processFile(baseFile, usedVariations);
            if (n > 0) { totalFiles++; totalItems += n; }
        }
        plugin.getLogger().info("[BlockBreaker] Variant generator: scanned "
                + files.length + " file(s), generated variants for "
                + totalItems + " breaker item(s) across " + totalFiles + " file(s).");
    }

    private int processFile(File baseFile, Set<Integer> usedVariations) {
        YamlConfiguration cfg;
        try { cfg = YamlConfiguration.loadConfiguration(baseFile); }
        catch (Exception e) { return 0; }

        // Find every base item declaring active_textures.
        Map<String, ConfigurationSection> baseItems = new LinkedHashMap<>();
        for (String itemId : cfg.getKeys(false)) {
            ConfigurationSection itemSec = cfg.getConfigurationSection(itemId);
            if (itemSec == null) continue;
            ConfigurationSection breakerSec = itemSec.getConfigurationSection("Mechanics.block_breaker");
            if (breakerSec == null) continue;
            if (breakerSec.getConfigurationSection("active_textures") == null) continue;
            // Skip if this item is itself a generated variant.
            if (breakerSec.getString("variant_base", "").length() > 0) continue;
            baseItems.put(itemId, itemSec);
        }
        if (baseItems.isEmpty()) return 0;

        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, ConfigurationSection> e : baseItems.entrySet()) {
            emitVariantsFor(out, e.getKey(), e.getValue(), usedVariations);
        }

        File outFile = new File(baseFile.getParentFile(),
                stripYml(baseFile.getName()) + VARIANT_SUFFIX);
        try {
            out.options().setHeader(List.of(
                    "AUTO-GENERATED by WynversCustomEvents — DO NOT EDIT MANUALLY.",
                    "Source: " + baseFile.getName(),
                    "Regenerated on every plugin onLoad from the source file's",
                    "Mechanics.block_breaker.active_textures map."));
            out.save(outFile);
        } catch (Exception ex) {
            plugin.getLogger().warning("[BlockBreaker] Failed to write " + outFile.getName()
                    + ": " + ex.getMessage());
            return 0;
        }
        return baseItems.size();
    }

    private void emitVariantsFor(YamlConfiguration out, String baseId,
                                 ConfigurationSection baseItemSec,
                                 Set<Integer> usedVariations) {
        ConfigurationSection breakerSec = baseItemSec.getConfigurationSection("Mechanics.block_breaker");
        ConfigurationSection activeTexSec = breakerSec.getConfigurationSection("active_textures");

        // Normalised "face name → on-texture" map.
        Map<String, String> activeTextures = new LinkedHashMap<>();
        for (String k : activeTexSec.getKeys(false)) {
            String tex = activeTexSec.getString(k);
            if (tex != null) activeTextures.put(k.toLowerCase(Locale.ROOT), tex);
        }

        // Base Pack.textures (off-state textures).
        Map<String, String> baseTextures = new LinkedHashMap<>();
        ConfigurationSection baseTexSec = baseItemSec.getConfigurationSection("Pack.textures");
        if (baseTexSec != null) {
            for (String k : baseTexSec.getKeys(false)) {
                baseTextures.put(k, baseTexSec.getString(k));
            }
        }

        // Generate one entry per non-empty face combination (mask 1..63).
        for (int mask = 1; mask <= 63; mask++) {
            StringBuilder suffix = new StringBuilder(6);
            Set<String> activeNames = new HashSet<>(6);
            for (int b = 0; b < 6; b++) {
                if ((mask & (1 << b)) != 0) {
                    suffix.append(FACE_CHARS[b]);
                    activeNames.add(FACE_NAMES[b]);
                }
            }
            String variantId = baseId + "_" + suffix;
            ConfigurationSection varSec = out.createSection(variantId);

            // Leaf-only deep copy of the base item: walk every key and copy
            // only the primitive/list leaves. Skipping nested ConfigurationSection
            // values avoids wonky cross-section linkage when setHeader serialises.
            copyLeaves(baseItemSec, "", varSec);

            // Unique custom_variation override.
            int newVar = nextFreeVariation(usedVariations);
            usedVariations.add(newVar);
            varSec.set("Mechanics.custom_block.custom_variation", newVar);

            // Strip the active_textures map from the variant (no recursion) and
            // anchor the variant to the base id.
            ConfigurationSection variantBreaker = varSec.getConfigurationSection("Mechanics.block_breaker");
            if (variantBreaker != null) {
                variantBreaker.set("active_textures", null);
            }
            varSec.set("Mechanics.block_breaker.variant_base", baseId);

            // Rebuild Pack.textures: base map + overrides on active face slots.
            ConfigurationSection variantPackTex = varSec.getConfigurationSection("Pack.textures");
            if (variantPackTex != null) {
                for (String k : new java.util.ArrayList<>(variantPackTex.getKeys(false))) {
                    variantPackTex.set(k, null);
                }
            }
            Map<String, String> textures = new LinkedHashMap<>(baseTextures);
            for (String activeName : activeNames) {
                String onTex = activeTextures.get(activeName);
                if (onTex != null) textures.put(activeName, onTex);
            }
            for (Map.Entry<String, String> e : textures.entrySet()) {
                varSec.set("Pack.textures." + e.getKey(), e.getValue());
            }
        }
    }

    private static void copyLeaves(ConfigurationSection src, String prefix, ConfigurationSection dst) {
        for (String k : src.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? k : prefix + "." + k;
            Object v = src.get(k);
            if (v instanceof ConfigurationSection child) {
                copyLeaves(child, fullKey, dst);
            } else if (v != null) {
                dst.set(fullKey, v);
            }
        }
    }

    private Set<Integer> scanUsedVariations(File dir) {
        Set<Integer> used = new HashSet<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return used;
        for (File f : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                for (String itemId : cfg.getKeys(false)) {
                    ConfigurationSection sec = cfg.getConfigurationSection(itemId);
                    if (sec == null) continue;
                    int v = sec.getInt("Mechanics.custom_block.custom_variation", -1);
                    if (v > 0) used.add(v);
                }
            } catch (Exception ignored) {}
        }
        return used;
    }

    private int nextFreeVariation(Set<Integer> used) {
        for (int v = 1; v < 16384; v++) {
            if (!used.contains(v)) return v;
        }
        throw new IllegalStateException("[BlockBreaker] No free custom_variation slots available.");
    }

    private static String stripYml(String name) {
        return name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
    }
}