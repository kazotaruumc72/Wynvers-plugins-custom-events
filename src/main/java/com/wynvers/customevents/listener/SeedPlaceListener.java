package com.wynvers.customevents.listener;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.farmer.FarmerMechanic;
import com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Manually places a Nexo furniture when a player right-clicks on farmland
 * with a Nexo seed item (item with a {@code farmer} mechanic).
 *
 * <p>This is needed because Nexo's automatic furniture placement is not
 * triggered when right-clicking on certain blocks (like farmland) – the
 * placement just silently fails and nothing happens for the player.
 *
 * <p>The flow:
 * <ol>
 *   <li>Player right-clicks on farmland with a {@code farmer} seed item.</li>
 *   <li>This listener detects it, cancels the vanilla event.</li>
 *   <li>{@link NexoFurniture#place} is called immediately on the block above.</li>
 *   <li>The seed is consumed (unless creative).</li>
 *   <li>{@link FarmerEventListener#onManualPlacement} schedules growth.</li>
 * </ol>
 */
public class SeedPlaceListener implements Listener {

    private final WynversCustomEvents plugin;
    private final FarmerEventListener farmerListener;

    public SeedPlaceListener(WynversCustomEvents plugin, FarmerEventListener farmerListener) {
        this.plugin = plugin;
        this.farmerListener = farmerListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSeedPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.useItemInHand() == Event.Result.DENY) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Only handle clicks on the top face of farmland
        if (clicked.getType() != Material.FARMLAND) return;
        if (event.getBlockFace() != BlockFace.UP) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        // Resolve Nexo item ID
        String itemId;
        try {
            itemId = NexoItems.idFromItem(item);
        } catch (Throwable t) {
            return;
        }
        if (itemId == null) return;

        // Must have a farmer mechanic
        FarmerMechanicFactory factory = FarmerMechanicFactory.instance();
        if (factory == null) return;
        FarmerMechanic farmer = factory.getMechanic(itemId);
        if (farmer == null) return;

        // The block above farmland must be empty
        Block placeBlock = clicked.getRelative(BlockFace.UP);
        if (!placeBlock.getType().isAir()) return;

        // Cancel the vanilla event so nothing else processes it
        event.setCancelled(true);

        Player player = event.getPlayer();
        Location placeLoc = placeBlock.getLocation();
        float yaw = player.getLocation().getYaw();
        ItemStack seedItem = item;

        // Place the furniture (next tick to avoid event re-entrancy)
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Re-check the block is still air (player could've placed something)
            if (!placeBlock.getType().isAir()) return;

            ItemDisplay placed;
            try {
                placed = NexoFurniture.place(itemId, placeLoc, yaw, BlockFace.UP);
            } catch (Throwable t) {
                plugin.getLogger().warning("[SeedPlace] Failed to place '" + itemId
                        + "': " + t.getMessage());
                return;
            }
            if (placed == null) {
                if (debug()) plugin.getLogger().info(
                        "[SeedPlace] NexoFurniture.place returned null for '" + itemId + "'.");
                return;
            }

            // Consume the seed if not creative
            if (player.getGameMode() != GameMode.CREATIVE) {
                seedItem.setAmount(seedItem.getAmount() - 1);
            }

            // Schedule growth via the farmer listener (same logic as auto-place)
            farmerListener.onManualPlacement(itemId, placeLoc, farmer);

            if (debug()) plugin.getLogger().info("[SeedPlace] " + player.getName()
                    + " planted '" + itemId + "' at "
                    + placeLoc.getBlockX() + "," + placeLoc.getBlockY() + "," + placeLoc.getBlockZ());
        });
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("farmer-debug", false);
    }
}

