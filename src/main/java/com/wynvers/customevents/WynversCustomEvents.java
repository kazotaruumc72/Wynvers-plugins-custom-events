package com.wynvers.customevents;

import com.wynvers.customevents.listener.OrestackEventListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * WynversCustomEvents - OreStack addon that enables custom Nexo item actions
 * in OreStack generator events via the actions.yml configuration.
 *
 * <p>Usage in orestack/events/actions.yml:
 * <pre>
 * generators:
 *   my_generator:
 *     on-mine:
 *       - do: giveItem NexoItems:enchanted_cobblestone
 * </pre>
 */
public class WynversCustomEvents extends JavaPlugin {

    private static WynversCustomEvents instance;
    private OrestackEventListener orestackListener;

    @Override
    public void onEnable() {
        instance = this;

        // Save default actions config if it does not exist
        File actionsFile = new File(getDataFolder(), "orestack/events/actions.yml");
        if (!actionsFile.exists()) {
            saveResource("orestack/events/actions.yml", false);
        }

        // Hook into OreStack if present
        if (Bukkit.getPluginManager().getPlugin("Orestack") != null) {
            orestackListener = new OrestackEventListener(this);
            Bukkit.getPluginManager().registerEvents(orestackListener, this);
            getLogger().info("OreStack integration enabled.");
        } else {
            getLogger().warning("OreStack not found - OreStack integration disabled.");
        }

        // Inform about Nexo availability
        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            getLogger().info("Nexo integration enabled.");
        } else {
            getLogger().warning("Nexo not found - 'giveItem NexoItems:' actions will be skipped.");
        }

        getLogger().info("WynversCustomEvents v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("WynversCustomEvents disabled.");
    }

    /**
     * Returns the singleton instance of this plugin.
     */
    public static WynversCustomEvents getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("wcereload")) {
            return false;
        }
        if (!sender.hasPermission("wynverscustomevents.reload")) {
            sender.sendMessage("§cYou do not have permission to run this command.");
            return true;
        }
        reloadActionsConfig();
        sender.sendMessage("§aWynversCustomEvents configuration reloaded.");
        return true;
    }

    /**
     * Reloads the OreStack actions configuration from disk.
     */
    public void reloadActionsConfig() {
        if (orestackListener != null) {
            orestackListener.loadActionsConfig();
            getLogger().info("OreStack actions configuration reloaded.");
        }
    }
}
