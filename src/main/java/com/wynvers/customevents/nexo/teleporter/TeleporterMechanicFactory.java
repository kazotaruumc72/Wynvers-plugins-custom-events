package com.wynvers.customevents.nexo.teleporter;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nexo {@link MechanicFactory} for the {@code teleporter} mechanic.
 * Handles both item parsing (via Nexo) and the step-on teleportation
 * trigger (via its own PlayerMoveEvent listener), keeping the mechanic
 * fully self-contained within Nexo's mechanic system.
 */
public class TeleporterMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "teleporter";
    private static final long TELEPORT_COOLDOWN_MS = 500;

    private static TeleporterMechanicFactory instance;
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    public TeleporterMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static TeleporterMechanicFactory instance() {
        return instance;
    }

    @Override
    public @Nullable TeleporterMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof TeleporterMechanic t) ? t : null;
    }

    @Override
    public @Nullable TeleporterMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof TeleporterMechanic t) ? t : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        TeleporterMechanic mechanic = new TeleporterMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLoc = player.getLocation();

        if (playerLoc.getBlockX() == event.getFrom().getBlockX() &&
            playerLoc.getBlockY() == event.getFrom().getBlockY() &&
            playerLoc.getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }

        ItemDisplay teleporter = findTeleporterAt(playerLoc);
        if (teleporter == null) return;

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTele = lastTeleport.get(playerId);
        if (lastTele != null && (now - lastTele) < TELEPORT_COOLDOWN_MS) return;
        lastTeleport.put(playerId, now);

        var furnMech = NexoFurniture.furnitureMechanic(teleporter);
        if (furnMech == null) return;

        String furnitureId = furnMech.getItemID();
        if (furnitureId == null) return;

        TeleporterMechanic mechanic = getMechanic(furnitureId);
        if (mechanic == null) return;

        mechanic.teleport(player);
    }

    private ItemDisplay findTeleporterAt(Location loc) {
        if (loc.getWorld() == null) return null;

        for (ItemDisplay display : loc.getWorld().getEntitiesByClass(ItemDisplay.class)) {
            if (!display.getLocation().getBlock().equals(loc.getBlock())) continue;
            try {
                var furnMech = NexoFurniture.furnitureMechanic(display);
                if (furnMech == null) continue;

                String itemId = furnMech.getItemID();
                if (itemId == null) continue;

                if (getMechanic(itemId) != null) return display;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}

