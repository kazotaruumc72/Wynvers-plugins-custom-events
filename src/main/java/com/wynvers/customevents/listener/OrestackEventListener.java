package com.wynvers.customevents.listener;

import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.action.ActionExecutor;
import com.wynvers.customevents.orestack.OrestackGeneratorsLoader;
import com.wynvers.customevents.orestack.OrestackGeneratorsLoader.StageDef;
import io.github.pigaut.orestack.api.event.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Listens to OreStack generator events and dispatches custom actions
 * declared <strong>directly inside the OreStack generator configuration
 * files</strong> (e.g. {@code plugins/Orestack/generators/<name>.yml}).
 *
 * <p>Each generator file is a multi-document YAML (stages separated by
 * {@code ---}). Stages are matched by their index, which corresponds to the
 * value returned by {@link GeneratorEvent#getGeneratorStage()}.
 *
 * <p>Supported event keys (per stage):
 * <ul>
 *   <li>{@code on-break}     → {@link GeneratorMineEvent}     (alias: {@code on-mine})</li>
 *   <li>{@code on-harvest}   → {@link GeneratorHarvestEvent}</li>
 *   <li>{@code on-destroy}   → {@link GeneratorDestroyEvent}</li>
 *   <li>{@code on-place}     → {@link GeneratorPlaceEvent}</li>
 *   <li>{@code on-hit}       → {@link GeneratorHitEvent}</li>
 *   <li>{@code on-interact}  → {@link GeneratorInteractEvent}</li>
 *   <li>{@code on-growth}    → {@link GeneratorGrowthEvent} (no player – console only)</li>
 * </ul>
 */
public class OrestackEventListener implements Listener {

    private final WynversCustomEvents plugin;
    private final ActionExecutor executor;
    private final OrestackGeneratorsLoader generatorsLoader;

    public OrestackEventListener(@NotNull WynversCustomEvents plugin) {
        this.plugin = plugin;
        this.executor = new ActionExecutor(plugin);
        this.generatorsLoader = new OrestackGeneratorsLoader(plugin);
        loadActionsConfig();
    }

    /** Reloads the OreStack generators directory. */
    public void loadActionsConfig() {
        File generatorsDir = resolveGeneratorsDir();
        generatorsLoader.reload(generatorsDir);
    }

    /**
     * Resolves the OreStack generators directory.
     *
     * <p>The path is read from this plugin's {@code config.yml} key
     * {@code orestack-generators-path} when present, otherwise it falls back
     * to {@link OrestackGeneratorsLoader#DEFAULT_GENERATORS_PATH}. Relative
     * paths are resolved against the server's working directory.
     */
    private File resolveGeneratorsDir() {
        String configured = plugin.getConfig().getString(
                "orestack-generators-path",
                OrestackGeneratorsLoader.DEFAULT_GENERATORS_PATH);
        File f = new File(configured);
        if (!f.isAbsolute()) {
            f = new File(plugin.getDataFolder().getParentFile().getParentFile(), configured);
        }
        return f;
    }

    // -------------------------------------------------------------------------
    // OreStack event handlers
    // -------------------------------------------------------------------------

    @EventHandler
    public void onGeneratorMine(GeneratorMineEvent event) {
        // "on-break" is the canonical key (matches the user-facing config),
        // "on-mine" is kept as an alias for backwards compatibility.
        dispatch(event, "on-break", "on-mine", event.getPlayer());
    }

    @EventHandler
    public void onGeneratorHarvest(GeneratorHarvestEvent event) {
        dispatch(event, "on-harvest", null, event.getPlayer());
    }

    @EventHandler
    public void onGeneratorDestroy(GeneratorDestroyEvent event) {
        dispatch(event, "on-destroy", null, event.getPlayer());
    }

    @EventHandler
    public void onGeneratorPlace(GeneratorPlaceEvent event) {
        dispatch(event, "on-place", null, event.getPlayer());
    }

    @EventHandler
    public void onGeneratorHit(GeneratorHitEvent event) {
        dispatch(event, "on-hit", null, event.getPlayer());
    }

    @EventHandler
    public void onGeneratorInteract(GeneratorInteractEvent event) {
        dispatch(event, "on-interact", null, event.getPlayer());
    }

    @EventHandler
    public void onGeneratorGrowth(GeneratorGrowthEvent event) {
        // No player is associated with a growth event. Only console actions
        // without %player% will be executed; others are skipped with a warning.
        StageDef stage = generatorsLoader.getStage(event.getGenerator(), event.getGeneratorStage());
        if (stage == null) return;
        for (String action : stage.getActions("on-growth")) {
            executor.executeWithoutPlayer(action);
        }
    }

    // -------------------------------------------------------------------------
    // Action dispatching
    // -------------------------------------------------------------------------

    /**
     * Resolves the action list for a generator+stage+event combination from
     * the OreStack generator file and executes each action.
     *
     * @param event       the OreStack generator event
     * @param eventKey    the canonical YAML key (e.g. "on-break")
     * @param aliasKey    optional alias key checked when {@code eventKey} has
     *                    no matching actions, or {@code null}
     * @param player      the player that triggered the event (must be non-null)
     */
    private void dispatch(@NotNull GeneratorEvent event,
                          @NotNull String eventKey,
                          @Nullable String aliasKey,
                          @Nullable Player player) {
        if (player == null) return;

        StageDef stage = generatorsLoader.getStage(event.getGenerator(), event.getGeneratorStage());
        if (stage == null) return;

        List<String> actions = stage.getActions(eventKey);
        if (actions.isEmpty() && aliasKey != null) {
            actions = stage.getActions(aliasKey);
        }
        for (String action : actions) {
            executor.execute(action, player);
        }
    }
}
