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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Plants a Nexo furniture instantly when a player right-clicks on
 * farmland with a Nexo seed item (an item with a {@code farmer}
 * mechanic).
 *
 * <p>Why this listener exists:
 * <ul>
 *   <li>Nexo's automatic right-click placement is silently dropped on
 *       farmland (some interaction in the vanilla / Paper / Nexo chain
 *       eats the event), so without this listener nothing happens when
 *       the player tries to plant a seed.</li>
 *   <li>{@link NexoFurniture#place} alone leaves the resulting
 *       {@link ItemDisplay} with the default {@code FIXED} transform,
 *       which renders the item flat like an invisible item-frame instead
 *       of as a 3D model.</li>
 *   <li>Farmland is only 15/16 of a block tall, so a furniture placed in
 *       the air block above floats 1/16th of a block above the surface.</li>
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Catch {@code RIGHT_CLICK_BLOCK} on a farmland TOP face with a
 *       Nexo seed item; cancel the original event.</li>
 *   <li>Next tick, call {@link NexoFurniture#place} on the air block above.</li>
 *   <li>Force {@code display_transform = NONE} so the 3D model renders.</li>
 *   <li>Teleport the entity 0.0625 blocks down so it sits flush on the
 *       farmland surface.</li>
 *   <li>Consume one seed (unless creative).</li>
 *   <li>Schedule growth via {@link FarmerEventListener#onManualPlacement}.</li>
 * </ol>
 */
public class SeedPlantListener implements Listener {

    /** Farmland is 15/16 of a block tall – this is the visual gap to compensate. */
    private static final double FARMLAND_Y_OFFSET = 0.0625;

    private final WynversCustomEvents plugin;
    private final FarmerEventListener farmerListener;

    public SeedPlantListener(WynversCustomEvents plugin,
                             FarmerEventListener farmerListener) {
        this.plugin = plugin;
        this.farmerListener = farmerListener;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onFarmlandClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (clicked.getType() != Material.FARMLAND) return;
        if (event.getBlockFace() != BlockFace.UP) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        // Resolve Nexo item id (filters out non-Nexo items)
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

        Block airBlock = clicked.getRelative(BlockFace.UP);
        if (!airBlock.getType().isAir()) return;

        // A furniture can sit in air, so we also have to check whether a
        // Nexo furniture entity already occupies that location.
        if (NexoFurniture.baseEntity(airBlock.getLocation()) != null) return;

        // Block vanilla / other plugins from handling the click
        event.setCancelled(true);

        Player player = event.getPlayer();
        // Center the furniture on the block (X+0.5, Z+0.5) so it sits in
        // the middle of the farmland, like vanilla wheat does, and force
        // a fixed yaw=0 so every crop is axis-aligned (no random tilt
        // depending on the way the player was facing).
        Location placeLoc = airBlock.getLocation().add(0.5, 0, 0.5);
        float yaw = 0f;
        ItemStack seedItem = item;

        if (debug()) plugin.getLogger().info("[SeedPlant] " + player.getName()
                + " planting '" + itemId + "' at " + placeLoc.getBlockX() + ","
                + placeLoc.getBlockY() + "," + placeLoc.getBlockZ());

        // Plant next tick so we don't recurse inside the cancelled interact event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!airBlock.getType().isAir()) return;

            ItemDisplay placed;
            try {
                placed = NexoFurniture.place(itemId, placeLoc, yaw, BlockFace.UP);
            } catch (Throwable t) {
                plugin.getLogger().warning("[SeedPlant] NexoFurniture.place threw for '"
                        + itemId + "': " + t.getMessage());
                return;
            }
            if (placed == null) {
                plugin.getLogger().warning("[SeedPlant] NexoFurniture.place returned null for '"
                        + itemId + "' at " + placeLoc.getBlockX() + ","
                        + placeLoc.getBlockY() + "," + placeLoc.getBlockZ());
                return;
            }

            // Force display_transform=NONE so the 3D model renders correctly
            // (default would be FIXED → flat item-frame look).
            try {
                placed.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            } catch (Throwable ignored) {}

            // Sit flush on the farmland surface (15/16 block tall).
            try {
                placed.teleport(placed.getLocation().add(0, -FARMLAND_Y_OFFSET, 0));
            } catch (Throwable ignored) {}

            if (player.getGameMode() != GameMode.CREATIVE) {
                seedItem.setAmount(seedItem.getAmount() - 1);
            }

            farmerListener.onManualPlacement(itemId, placeLoc, farmer);

            if (debug()) plugin.getLogger().info("[SeedPlant] '" + itemId
                    + "' planted, growth scheduled.");
        });
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("farmer-debug", false);
    }
}



