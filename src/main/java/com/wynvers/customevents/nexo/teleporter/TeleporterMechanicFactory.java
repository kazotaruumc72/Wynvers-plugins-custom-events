package com.wynvers.customevents.nexo.teleporter;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

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
        if (event.getTo() == null) return;
        if (event.getTo().getBlockX() == event.getFrom().getBlockX() &&
            event.getTo().getBlockY() == event.getFrom().getBlockY() &&
            event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Block blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN);

        CustomBlockMechanic blockMechanic = NexoBlocks.customBlockMechanic(blockBelow);
        if (blockMechanic == null) return;

        TeleporterMechanic mechanic = getMechanic(blockMechanic.getItemID());
        if (mechanic == null) return;

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTele = lastTeleport.get(playerId);
        if (lastTele != null && (now - lastTele) < TELEPORT_COOLDOWN_MS) return;
        lastTeleport.put(playerId, now);

        mechanic.teleport(player);
    }
}

