package com.wynvers.customevents;

import com.wynvers.customevents.listener.OrestackEventListener;
import com.wynvers.customevents.listener.WitherEventListener;
import com.wynvers.customevents.listener.FarmerEventListener;
import com.wynvers.customevents.listener.SeedPlantListener;
import com.wynvers.customevents.listener.HarvesterEventListener;
import com.wynvers.customevents.listener.HarvestingToolListener;
import com.wynvers.customevents.listener.TeleporterInputListener;
import com.wynvers.customevents.nexo.NexoWitherPropertiesLoader;
import com.wynvers.customevents.nexo.WitherPropertiesMechanicFactory;
import com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory;
import com.wynvers.customevents.nexo.harvester.HarvesterMechanicFactory;
import com.wynvers.customevents.nexo.breachcharge.BreachChargeMechanicFactory;
import com.wynvers.customevents.nexo.breachcharge.DefuserMechanicFactory;
import com.wynvers.customevents.nexo.enderjammer.EnderJammerMechanicFactory;
import com.wynvers.customevents.nexo.explosionreducer.ExplosionReducerMechanicFactory;
import com.wynvers.customevents.nexo.harvesting.HarvestingMechanicFactory;
import com.wynvers.customevents.nexo.hydrodrill.HydroDrillMechanicFactory;
import com.wynvers.customevents.nexo.runeshop.ShopRuneMechanicFactory;
import com.wynvers.customevents.nexo.teleporter.TeleporterMechanicFactory;
import com.wynvers.customevents.nexo.teleporter.TeleporterSetupManager;
import com.wynvers.customevents.papi.WcePlaceholderExpansion;
import com.wynvers.customevents.roseloot.NexoBlockCondition;
import com.wynvers.customevents.roseloot.SetNexoBlockLootItem;
import com.wynvers.customevents.worldguard.BlockPriorityFlag;
import com.wynvers.customevents.worldguard.BlockPriorityListener;
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
    private TeleporterSetupManager teleporterSetupManager;

    @Override
    public void onLoad() {
        // WorldGuard custom flags MUST be registered during onLoad(), before
        // WG starts loading region data. Skip silently when WG is absent.
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                BlockPriorityFlag.register();
                getLogger().info("Registered WorldGuard flag '" + BlockPriorityFlag.FLAG_NAME + "'.");
            } catch (Throwable t) {
                getLogger().warning("Failed to register WorldGuard flag '"
                        + BlockPriorityFlag.FLAG_NAME + "': " + t.getMessage());
            }
        }
    }

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

            // Register our custom Nexo mechanics via NexoMechanicsRegisteredEvent.
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
                        if (HarvestingMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new HarvestingMechanicFactory(), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + HarvestingMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (TeleporterMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new TeleporterMechanicFactory(WynversCustomEvents.this), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + TeleporterMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (HydroDrillMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new HydroDrillMechanicFactory(WynversCustomEvents.this), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + HydroDrillMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (EnderJammerMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new EnderJammerMechanicFactory(WynversCustomEvents.this), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + EnderJammerMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (BreachChargeMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new BreachChargeMechanicFactory(WynversCustomEvents.this), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + BreachChargeMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (DefuserMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new DefuserMechanicFactory(WynversCustomEvents.this), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + DefuserMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (ExplosionReducerMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new ExplosionReducerMechanicFactory(WynversCustomEvents.this), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + ExplosionReducerMechanicFactory.MECHANIC_ID + "'.");
                        }
                        if (ShopRuneMechanicFactory.instance() == null) {
                            com.nexomc.nexo.mechanics.MechanicsManager.INSTANCE
                                    .registerMechanicFactory(new ShopRuneMechanicFactory(WynversCustomEvents.this), true);
                            getLogger().info("Registered Nexo mechanic '"
                                    + ShopRuneMechanicFactory.MECHANIC_ID + "'.");
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

            // Auto-plant: when a player right-clicks farmland with a farmer
            // seed item, place the furniture immediately and force the
            // correct visual properties (model + transform).
            Bukkit.getPluginManager().registerEvents(
                    new SeedPlantListener(this, farmerListener), this);
            getLogger().info("Farmer seed auto-plant enabled.");

            // Harvester mechanic: right-click on furniture to harvest items and damage tool
            harvesterListener = new HarvesterEventListener(this);
            Bukkit.getPluginManager().registerEvents(harvesterListener, this);
            getLogger().info("Harvester mechanic enabled.");

            // Harvesting mechanic: right-click with a tool to mass-harvest
            // every farmer-final-stage furniture in range.
            Bukkit.getPluginManager().registerEvents(new HarvestingToolListener(this), this);
            getLogger().info("Harvesting tool mechanic enabled.");

            // Teleporter setup wizard (block place + chat input)
            teleporterSetupManager = new TeleporterSetupManager();
            Bukkit.getPluginManager().registerEvents(
                    new TeleporterInputListener(this, teleporterSetupManager), this);
            getLogger().info("Teleporter mechanic enabled.");
        } else {
            getLogger().warning("Nexo not found - 'giveItem NexoItems:' actions will be skipped.");
        }

        // WorldGuard 'block-priority' flag listener
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null
                && BlockPriorityFlag.get() != null) {
            Bukkit.getPluginManager().registerEvents(new BlockPriorityListener(), this);
            getLogger().info("WorldGuard '" + BlockPriorityFlag.FLAG_NAME + "' flag enabled.");
        }

        // RoseLoot integration: nexo-block condition + set_nexo_block loot item
        if (Bukkit.getPluginManager().getPlugin("RoseLoot") != null
                && Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            try {
                var api = dev.rosewood.roseloot.api.RoseLootAPI.getInstance();
                if (api.registerCustomLootCondition("nexo-block", NexoBlockCondition::parse)) {
                    getLogger().info("Registered RoseLoot condition 'nexo-block'.");
                }
                if (api.registerCustomLootItem("set_nexo_block", SetNexoBlockLootItem::fromSection)) {
                    getLogger().info("Registered RoseLoot loot item 'set_nexo_block'.");
                }
            } catch (Throwable t) {
                getLogger().warning("Failed to register RoseLoot integration: " + t.getMessage());
            }
        }

        // PlaceholderAPI expansion (last-broken-block coordinates)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                WcePlaceholderExpansion expansion = new WcePlaceholderExpansion(this);
                if (expansion.register()) {
                    Bukkit.getPluginManager().registerEvents(expansion, this);
                    getLogger().info("PlaceholderAPI expansion 'wce' registered.");
                } else {
                    getLogger().warning("PlaceholderAPI expansion 'wce' refused to register.");
                }
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        getLogger().info("WynversCustomEvents v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (HydroDrillMechanicFactory.instance() != null) {
            HydroDrillMechanicFactory.instance().manager().shutdown();
        }
        if (BreachChargeMechanicFactory.instance() != null) {
            BreachChargeMechanicFactory.instance().manager().shutdown();
        }
        if (ExplosionReducerMechanicFactory.instance() != null) {
            ExplosionReducerMechanicFactory.instance().shutdown();
        }
        if (ShopRuneMechanicFactory.instance() != null) {
            ShopRuneMechanicFactory.instance().shutdown();
        }
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
