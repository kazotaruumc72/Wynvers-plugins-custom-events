package com.wynvers.customevents.action;

import com.wynvers.customevents.WynversCustomEvents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Parses and executes action strings for OreStack generator events.
 *
 * <p>Supported action syntax:
 * <ul>
 *   <li>{@code giveItem NexoItems:<id>} – give one Nexo custom item</li>
 *   <li>{@code giveItem NexoItems:<id> <amount>} – give {@code amount} Nexo custom items</li>
 *   <li>{@code giveItem <MATERIAL>} – give one vanilla item</li>
 *   <li>{@code giveItem <MATERIAL> <amount>} – give {@code amount} vanilla items</li>
 *   <li>{@code runCommand <command>} – dispatch a command as the player (%player% placeholder supported)</li>
 *   <li>{@code console <command>} – dispatch a command as the console (%player% placeholder supported)</li>
 * </ul>
 */
public class ActionExecutor {

    /** Prefix used in the config to denote a Nexo item (e.g. {@code NexoItems:enchanted_cobblestone}). */
    private static final String NEXO_PREFIX = "NexoItems:";

    private final WynversCustomEvents plugin;

    public ActionExecutor(@NotNull WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes a single action string for the given player.
     *
     * @param action the raw action string (e.g. "giveItem NexoItems:enchanted_cobblestone 2")
     * @param player the player that triggered the event
     */
    public void execute(@NotNull String action, @NotNull Player player) {
        if (action.isBlank()) return;

        // Split into at most 3 parts: actionType, arg1, arg2
        String[] parts = action.strip().split("\\s+", 3);
        String actionType = parts[0].toLowerCase();

        switch (actionType) {
            case "giveitem" -> handleGiveItem(parts, player);
            case "runcommand" -> handleRunCommand(action, "runCommand", player, false);
            case "console"    -> handleRunCommand(action, "console", player, true);
            default -> plugin.getLogger().warning(
                    "Unknown action type '" + parts[0] + "' in actions.yml – skipping.");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void handleGiveItem(String[] parts, Player player) {
        if (parts.length < 2) {
            plugin.getLogger().warning("'giveItem' requires an item argument – skipping.");
            return;
        }

        String itemArg = parts[1];
        int amount = parts.length >= 3 ? parseAmount(parts[2]) : 1;

        if (itemArg.startsWith(NEXO_PREFIX)) {
            String nexoId = itemArg.substring(NEXO_PREFIX.length());
            giveNexoItem(player, nexoId, amount);
        } else {
            giveVanillaItem(player, itemArg, amount);
        }
    }

    private void giveNexoItem(Player player, String itemId, int amount) {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            plugin.getLogger().warning(
                    "Nexo is not installed – cannot give NexoItems:" + itemId);
            return;
        }

        try {
            var itemBuilder = com.nexomc.nexo.api.NexoItems.itemFromId(itemId);
            if (itemBuilder == null) {
                plugin.getLogger().warning("Nexo item '" + itemId + "' not found – skipping.");
                return;
            }
            ItemStack stack = itemBuilder.build();
            stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
            player.getInventory().addItem(stack);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Error while giving Nexo item '" + itemId + "' to " + player.getName(), e);
        }
    }

    private void giveVanillaItem(Player player, String materialName, int amount) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || material == Material.AIR) {
            plugin.getLogger().warning("Unknown material '" + materialName + "' – skipping.");
            return;
        }
        int clamped = Math.max(1, Math.min(amount, material.getMaxStackSize()));
        player.getInventory().addItem(new ItemStack(material, clamped));
    }

    /**
     * Dispatches a command, stripping the leading keyword (e.g. "runCommand" or "console").
     *
     * @param rawAction  the full raw action string
     * @param keyword    the action keyword to strip from the front
     * @param player     the player context (used for placeholder replacement)
     * @param asConsole  if {@code true} the command is run as the console sender
     */
    private void handleRunCommand(String rawAction, String keyword, Player player, boolean asConsole) {
        String command = rawAction.strip();
        // Strip the leading keyword (case-insensitive prefix removal)
        if (command.toLowerCase().startsWith(keyword.toLowerCase())) {
            command = command.substring(keyword.length()).strip();
        }
        command = command.replace("%player%", player.getName());

        if (command.isEmpty()) {
            plugin.getLogger().warning("'" + keyword + "' action has no command – skipping.");
            return;
        }

        if (asConsole) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            Bukkit.dispatchCommand(player, command);
        }
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw.strip()));
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid item amount '" + raw + "' – defaulting to 1.");
            return 1;
        }
    }
}
