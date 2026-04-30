package com.wynvers.customevents.listener;

import com.nexomc.nexo.api.events.custom_block.NexoBlockPlaceEvent;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.teleporter.TeleporterConfigGenerator;
import com.wynvers.customevents.nexo.teleporter.TeleporterSetupManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;

/**
 * Handles the teleporter setup flow: custom block placement triggers the wizard,
 * and chat input drives it step by step.
 */
public class TeleporterInputListener implements Listener {

    private static final String TELEPORTER_BASE_ID = "teleporter_base";

    private final WynversCustomEvents plugin;
    private final TeleporterSetupManager setupManager;
    private final TeleporterConfigGenerator configGenerator;

    public TeleporterInputListener(WynversCustomEvents plugin, TeleporterSetupManager setupManager) {
        this.plugin = plugin;
        this.setupManager = setupManager;
        File nexoItemsDir = new File(plugin.getDataFolder().getParentFile(), "Nexo/items");
        this.configGenerator = new TeleporterConfigGenerator(nexoItemsDir);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNexoBlockPlace(NexoBlockPlaceEvent event) {
        if (!TELEPORTER_BASE_ID.equals(event.getMechanic().getItemID())) return;

        Player player = event.getPlayer();
        if (player == null) return;

        setupManager.startSetup(player);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!setupManager.isInSetup(player)) return;

        event.setCancelled(true);
        String input = event.getMessage();

        Bukkit.getScheduler().runTask(plugin, () -> {
            setupManager.advanceSetup(player, input);

            if (setupManager.isSetupCompleted(player)) {
                String teleporterId = setupManager.getTeleporterName(player);
                String world = setupManager.getDestinationWorld(player);
                double x = setupManager.getDestinationX(player);
                double y = setupManager.getDestinationY(player);
                double z = setupManager.getDestinationZ(player);
                float yaw = setupManager.getDestinationYaw(player);
                float pitch = setupManager.getDestinationPitch(player);

                boolean success = configGenerator.generateTeleporterConfig(
                        teleporterId, world, x, y, z, yaw, pitch);

                if (success) {
                    player.sendMessage("§a[Teleporter] Configuration sauvegardée!");
                    player.sendMessage("§e[Teleporter] Exécutez §f/nexo reload");
                } else {
                    player.sendMessage("§c[Teleporter] Erreur lors de la sauvegarde!");
                }

                setupManager.finishSetup(player);
            }
        });
    }

}
