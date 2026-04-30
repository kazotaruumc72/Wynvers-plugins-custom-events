package com.wynvers.customevents;

import com.wynvers.customevents.listener.OrestackEventListener;
import com.wynvers.customevents.listener.WitherEventListener;
import com.wynvers.customevents.listener.FarmerEventListener;
import com.wynvers.customevents.listener.HarvesterEventListener;
import com.wynvers.customevents.listener.SeedCooldownListener;
import com.wynvers.customevents.nexo.NexoWitherPropertiesLoader;
import com.wynvers.customevents.nexo.WitherPropertiesMechanicFactory;
import com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory;
import com.wynvers.customevents.nexo.harvester.HarvesterMechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
    private NexoWitherPropertiesLoader nexoWitherLoader;
    private WitherEventListener witherListener;
    private FarmerEventListener farmerListener;
    private HarvesterEventListener harvesterListener;

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

            // Register our custom Nexo mechanic via NexoMechanicsRegisteredEvent.
            // This is the correct timing: Nexo fires this event right before
            // it parses items, which is the only window where a third-party
            // factory can be registered AND applied to all items on first load.
            // (Pattern taken from the official NexoExampleMechanic project.)
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onNexoMechanicsRegistered(
                        com.nexomc.nexo.api.events.NexoMechanicsRegisteredEvent e) {
                    try {
                        if (WitherPropertiesMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new WitherPropertiesMechanicFactory(), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + WitherPropertiesMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (FarmerMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new FarmerMechanicFactory(), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + FarmerMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (HarvesterMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new HarvesterMechanicFactory(), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + HarvesterMechanicFactory.MECHANIC_ID + "'.");
                        }
                    } catch (Throwable t) {
                        getLogger().warning(
                                "Failed to register custom Nexo mechanic: " + t.getMessage());
                    }
                }
            }, this);

            // Wither properties mechanic
            nexoWitherLoader = new NexoWitherPropertiesLoader(this);
            nexoWitherLoader.reload(resolveNexoItemsDir());
            witherListener = new WitherEventListener(this, nexoWitherLoader);
            Bukkit.getPluginManager().registerEvents(witherListener, this);
            getLogger().info("Nexo wither_properties mechanic enabled.");

            // Farmer mechanic (real Nexo Mechanic – parsed by Nexo via FarmerMechanicFactory)
            farmerListener = new FarmerEventListener(this);
            Bukkit.getPluginManager().registerEvents(farmerListener, this);
            getLogger().info("Nexo farmer mechanic enabled.");

            // Harvester mechanic: right-click on furniture to harvest items and damage tool
            harvesterListener = new HarvesterEventListener(this);
            Bukkit.getPluginManager().registerEvents(harvesterListener, this);
            getLogger().info("Harvester mechanic enabled.");

            // Seed cooldown: forces use_cooldown=0 on farmer seed items so
            // they place their furniture instantly on right-click.
            Bukkit.getPluginManager().registerEvents(new SeedCooldownListener(this), this);
            getLogger().info("Seed use_cooldown=0 enforcement enabled.");
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
     * generator files and Nexo item files.
     */
    public void reloadActionsConfig() {
        reloadConfig();
        if (orestackListener != null) {
            orestackListener.loadActionsConfig();
            getLogger().info("OreStack generator actions reloaded.");
        }
        if (nexoWitherLoader != null) {
            nexoWitherLoader.reload(resolveNexoItemsDir());
            getLogger().info("Nexo wither_properties reloaded.");
        }
    }


    /**
     * Resolves the Nexo items directory.
     * The path is read from {@code config.yml} key {@code nexo-items-path},
     * defaulting to {@link NexoWitherPropertiesLoader#DEFAULT_NEXO_ITEMS_PATH}.
     */
    private File resolveNexoItemsDir() {
        String configured = getConfig().getString(
                "nexo-items-path",
                NexoWitherPropertiesLoader.DEFAULT_NEXO_ITEMS_PATH);
        File f = new File(configured);
        if (!f.isAbsolute()) {
            f = new File(getDataFolder().getParentFile().getParentFile(), configured);
        }
        return f;
    }
}
