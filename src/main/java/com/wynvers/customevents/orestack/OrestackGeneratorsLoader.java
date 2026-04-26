package com.wynvers.customevents.orestack;

import com.wynvers.customevents.WynversCustomEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Scans the OreStack generators directory and parses each multi-document
 * YAML file (stages separated by {@code ---}) so that custom actions
 * defined under {@code on-break}, {@code on-harvest}, etc. can be executed
 * directly from the native OreStack generator file – no separate
 * {@code actions.yml} entry required.
 *
 * <p>Each generator file is expected to contain one YAML document per
 * stage, in the same order OreStack itself uses (stage 0 = first
 * document). Example:
 * <pre>
 * type: depleted
 * nx-block: moonstone_stone_ore
 * growth: 10s
 * ---
 * type: ripe
 * block: bedrock
 * default-drops: true
 * growth: 20s
 * on-break:
 *   - do: giveItem NexoItems:enchanted_cobblestone
 * ---
 * type: regrown
 * nx-block: moonstone_stone_ore
 * default-drops: true
 * </pre>
 */
public class OrestackGeneratorsLoader {

    /** Default location of the OreStack generators folder. */
    public static final String DEFAULT_GENERATORS_PATH = "plugins/Orestack/generators";

    private final WynversCustomEvents plugin;

    /** generatorName (lower-case) → list of stage definitions in file order. */
    private final Map<String, List<StageDef>> generatorStages = new HashMap<>();

    public OrestackGeneratorsLoader(@NotNull WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    /**
     * Reloads every generator file under the given directory.
     * Existing data is cleared first.
     *
     * @param generatorsDir the directory to scan; if it does not exist a
     *                      warning is logged and the loader is left empty
     */
    public void reload(@NotNull File generatorsDir) {
        generatorStages.clear();

        if (!generatorsDir.exists() || !generatorsDir.isDirectory()) {
            plugin.getLogger().warning(
                    "OreStack generators directory not found at '"
                    + generatorsDir.getAbsolutePath()
                    + "' – no custom actions will be loaded.");
            return;
        }

        int fileCount = 0;
        int generatorCount = 0;
        List<File> files = new ArrayList<>();
        collectYamlFiles(generatorsDir, files);

        for (File file : files) {
            fileCount++;
            try {
                List<StageDef> stages = parseFile(file);
                if (stages.isEmpty()) continue;

                String name = stripExtension(file.getName()).toLowerCase(Locale.ROOT);
                generatorStages.put(name, stages);
                generatorCount++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to parse OreStack generator file '" + file.getName() + "'", e);
            }
        }

        plugin.getLogger().info("Loaded " + generatorCount + " OreStack generator(s) from "
                + fileCount + " file(s) in " + generatorsDir.getAbsolutePath());
    }

    /**
     * Returns the stage definition for a given generator name and stage index,
     * or {@code null} if the generator is unknown or the index is out of range.
     *
     * @param generatorName generator name (case-insensitive)
     * @param stageIndex    zero-based stage index from
     *                      {@code GeneratorEvent#getGeneratorStage()}
     */
    @Nullable
    public StageDef getStage(@NotNull String generatorName, int stageIndex) {
        List<StageDef> stages = generatorStages.get(generatorName.toLowerCase(Locale.ROOT));
        if (stages == null || stageIndex < 0 || stageIndex >= stages.size()) {
            return null;
        }
        return stages.get(stageIndex);
    }

    /** Returns {@code true} if the given generator name is known to this loader. */
    public boolean hasGenerator(@NotNull String generatorName) {
        return generatorStages.containsKey(generatorName.toLowerCase(Locale.ROOT));
    }

    // -------------------------------------------------------------------------
    // Internal parsing
    // -------------------------------------------------------------------------

    private void collectYamlFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectYamlFiles(child, out);
            } else {
                String n = child.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".yml") || n.endsWith(".yaml")) {
                    out.add(child);
                }
            }
        }
    }

    private List<StageDef> parseFile(File file) throws IOException {
        Yaml yaml = new Yaml();
        List<StageDef> stages = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            for (Object doc : yaml.loadAll(reader)) {
                if (doc instanceof Map<?, ?> map) {
                    stages.add(StageDef.fromMap(map));
                }
            }
        }
        return stages;
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    // -------------------------------------------------------------------------
    // Stage definition
    // -------------------------------------------------------------------------

    /**
     * A single stage parsed from a generator YAML document. Holds the action
     * lists keyed by event key (e.g. {@code on-break}). Native OreStack keys
     * such as {@code type}, {@code block}, {@code nx-block}, {@code growth},
     * {@code default-drops} are intentionally ignored.
     */
    public static final class StageDef {

        private final String type;
        private final Map<String, List<String>> actionsByEvent;

        private StageDef(@Nullable String type,
                        @NotNull Map<String, List<String>> actionsByEvent) {
            this.type = type;
            this.actionsByEvent = actionsByEvent;
        }

        @Nullable
        public String getType() {
            return type;
        }

        /**
         * Returns the action strings configured for {@code eventKey} on this
         * stage; never {@code null}, possibly empty.
         */
        @NotNull
        public List<String> getActions(@NotNull String eventKey) {
            List<String> list = actionsByEvent.get(eventKey);
            return list != null ? list : Collections.emptyList();
        }

        /** Recognized event keys. {@code on-break} is the canonical mine key. */
        private static final List<String> EVENT_KEYS = List.of(
                "on-break", "on-mine", "on-harvest", "on-place",
                "on-destroy", "on-hit", "on-interact", "on-growth");

        static StageDef fromMap(Map<?, ?> raw) {
            String type = raw.get("type") instanceof String s ? s : null;
            Map<String, List<String>> actions = new HashMap<>();

            for (String key : EVENT_KEYS) {
                Object value = raw.get(key);
                if (value instanceof List<?> list) {
                    List<String> resolved = resolveActionList(list);
                    if (!resolved.isEmpty()) {
                        actions.put(key, resolved);
                    }
                }
            }
            return new StageDef(type, actions);
        }

        /**
         * Accepts either a list of strings (e.g. {@code - giveItem ...}) or a
         * list of single-entry maps with a {@code do:} key
         * (e.g. {@code - do: giveItem ...}).
         */
        private static List<String> resolveActionList(List<?> raw) {
            List<String> out = new ArrayList<>(raw.size());
            for (Object item : raw) {
                if (item instanceof String s) {
                    if (!s.isBlank()) out.add(s);
                } else if (item instanceof Map<?, ?> map) {
                    Object doVal = map.get("do");
                    if (doVal instanceof String s && !s.isBlank()) {
                        out.add(s);
                    }
                }
            }
            return out;
        }
    }
}
