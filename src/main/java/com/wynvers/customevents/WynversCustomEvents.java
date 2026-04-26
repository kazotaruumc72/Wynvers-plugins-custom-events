package com.wynvers.customevents;

import com.wynvers.customevents.listener.OrestackEventListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * WynversCustomEvents – OreStack addon that lets server admins declare
 * custom actions (give Nexo items, give vanilla items, run commands)
 * <strong>directly inside the OreStack generator configuration files</strong>
 * (e.g. {@code plugins/Orestack/generators/<name>.yml}).
 *
 * <p>Example stage in an OreStack generator file:
 * <pre>
 * type: ripe
 * block: bedrock
 * default-drops: true
 * growth: 20s
 * on-break:
 *   - do: giveItem NexoItems:enchanted_cobblestone
 * </pre>
 */
public class WynversCustomEvents extends JavaPlugin {

    private static WynversCustomEvents instance;
    private OrestackEventListener orestackListener;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config.yml (used to override the OreStack generators path)
        saveDefaultConfig();

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
     * Reloads the WynversCustomEvents config and re-scans the OreStack
     * generator files.
     */
    public void reloadActionsConfig() {
        reloadConfig();
        if (orestackListener != null) {
            orestackListener.loadActionsConfig();
            getLogger().info("OreStack generator actions reloaded.");
        }
    }
}
