package com.wynvers.customevents.listener;

import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.action.ActionExecutor;
import io.github.pigaut.orestack.api.event.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Listens to all OreStack generator events and dispatches custom actions
 * defined in {@code plugins/WynversCustomEvents/orestack/events/actions.yml}.
 *
 * <p>Supported events and their config keys:
 * <ul>
 *   <li>{@link GeneratorMineEvent}     → {@code on-mine}</li>
 *   <li>{@link GeneratorHarvestEvent}  → {@code on-harvest}</li>
 *   <li>{@link GeneratorDestroyEvent}  → {@code on-destroy}</li>
 *   <li>{@link GeneratorPlaceEvent}    → {@code on-place}</li>
 *   <li>{@link GeneratorHitEvent}      → {@code on-hit}</li>
 *   <li>{@link GeneratorInteractEvent} → {@code on-interact}</li>
 * </ul>
 *
 * <p>Example {@code actions.yml}:
 * <pre>
 * generators:
 *   my_generator:
 *     on-mine:
 *       - do: giveItem NexoItems:enchanted_cobblestone
 *       - do: giveItem NexoItems:enchanted_cobblestone 2
 *     on-harvest:
 *       - do: giveItem DIAMOND 3
 *     on-destroy:
 *       - do: runCommand say %player% destroyed the generator!
 *     on-place:
 *       - do: console eco give %player% 100
 * </pre>
 */
public class OrestackEventListener implements Listener {

    private final WynversCustomEvents plugin;
    private final ActionExecutor executor;
    private FileConfiguration actionsConfig;

    public OrestackEventListener(@NotNull WynversCustomEvents plugin) {
        this.plugin = plugin;
        this.executor = new ActionExecutor(plugin);
        loadActionsConfig();
    }

    /** Reloads the actions configuration from disk. */
    public void loadActionsConfig() {
        File actionsFile = new File(plugin.getDataFolder(), "orestack/events/actions.yml");
        if (actionsFile.exists()) {
            actionsConfig = YamlConfiguration.loadConfiguration(actionsFile);
        } else {
            actionsConfig = new YamlConfiguration();
            plugin.getLogger().warning("orestack/events/actions.yml not found – no custom actions loaded.");
        }
    }

    // -------------------------------------------------------------------------
    // OreStack event handlers
    // -------------------------------------------------------------------------

    @EventHandler
    public void onGeneratorMine(GeneratorMineEvent event) {
        dispatch(event.getGenerator(), "on-mine", event.getPlayer());
    }

    @EventHandler
    public void onGeneratorHarvest(GeneratorHarvestEvent event) {
        dispatch(event.getGenerator(), "on-harvest", event.getPlayer());
    }

    @EventHandler
    public void onGeneratorDestroy(GeneratorDestroyEvent event) {
        dispatch(event.getGenerator(), "on-destroy", event.getPlayer());
    }

    @EventHandler
    public void onGeneratorPlace(GeneratorPlaceEvent event) {
        dispatch(event.getGenerator(), "on-place", event.getPlayer());
    }

    @EventHandler
    public void onGeneratorHit(GeneratorHitEvent event) {
        dispatch(event.getGenerator(), "on-hit", event.getPlayer());
    }

    @EventHandler
    public void onGeneratorInteract(GeneratorInteractEvent event) {
        dispatch(event.getGenerator(), "on-interact", event.getPlayer());
    }

    // -------------------------------------------------------------------------
    // Action dispatching
    // -------------------------------------------------------------------------

    /**
     * Resolves the action list for a generator+event combination and executes each action.
     *
     * @param generatorName the name of the generator as defined in OreStack config
     * @param eventKey      the YAML key (e.g. "on-mine")
     * @param player        the player that triggered the event (may be null for serverside events)
     */
    private void dispatch(@NotNull String generatorName,
                          @NotNull String eventKey,
                          @Nullable Player player) {
        if (actionsConfig == null || player == null) return;

        ConfigurationSection genSection = actionsConfig
                .getConfigurationSection("generators." + generatorName);
        if (genSection == null) return;

        List<String> actions = resolveActions(genSection, eventKey);
        for (String action : actions) {
            executor.execute(action, player);
        }
    }

    /**
     * Resolves the action list for an event key from the config section.
     *
     * <p>The config supports two equivalent formats:
     * <pre>
     * # Map format (with "do:" key)
     * on-mine:
     *   - do: giveItem NexoItems:enchanted_cobblestone
     *
     * # Plain string list
     * on-mine:
     *   - giveItem NexoItems:enchanted_cobblestone
     * </pre>
     *
     * @param genSection the generator config section
     * @param eventKey   the event YAML key
     * @return a list of action strings; never null, may be empty
     */
    private List<String> resolveActions(@NotNull ConfigurationSection genSection,
                                        @NotNull String eventKey) {
        // Try plain string list first
        List<String> stringList = genSection.getStringList(eventKey);
        if (!stringList.isEmpty()) {
            return stringList;
        }

        // Try map-entry list: each entry may be { do: "action string" }
        List<?> rawList = genSection.getList(eventKey);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        return rawList.stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<?, ?>) item)
                .map(map -> map.get("do"))
                .filter(do_ -> do_ instanceof String)
                .map(do_ -> (String) do_)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
