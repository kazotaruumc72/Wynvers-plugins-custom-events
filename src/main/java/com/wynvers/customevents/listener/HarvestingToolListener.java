package com.wynvers.customevents.listener;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.farmer.FarmerMechanic;
import com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory;
import com.wynvers.customevents.nexo.harvesting.HarvestingMechanic;
import com.wynvers.customevents.nexo.harvesting.HarvestingMechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for right-clicks on a Nexo furniture with a Nexo tool that has
 * the {@code harvesting} mechanic. Mass-harvests every Nexo furniture in
 * range that is the FINAL stage of a {@code farmer} crop.
 *
 * <p>For each harvested furniture, a real {@link BlockBreakEvent} is
 * fired so plugins like Jobs Reborn / CoreProtect / statistics …
 * naturally count the harvest as a regular player block break.
 */
public class HarvestingToolListener implements Listener {

    private final WynversCustomEvents plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public HarvestingToolListener(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        ItemStack tool = event.getItem();
        if (tool == null || tool.getType().isAir()) return;

        // Resolve the tool's Nexo id and harvesting mechanic
        String toolId;
        try {
            toolId = NexoItems.idFromItem(tool);
        } catch (Throwable t) {
            return;
        }
        if (toolId == null) return;

        HarvestingMechanicFactory factory = HarvestingMechanicFactory.instance();
        if (factory == null) return;
        HarvestingMechanic mechanic = factory.getMechanic(toolId);
        if (mechanic == null) return;

        // The clicked block MUST be a Nexo furniture (not farmland, dirt …)
        ItemDisplay clickedFurniture = NexoFurniture.baseEntity(clicked.getLocation());
        if (clickedFurniture == null) return;

        Player player = event.getPlayer();

        // Prevent vanilla / Nexo from also processing this click
        event.setCancelled(true);

        // Cooldown
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < mechanic.cooldownMs()) return;
        lastUse.put(player.getUniqueId(), now);

        Location center = clickedFurniture.getLocation();
        int radius = mechanic.radius();
        int height = mechanic.height();

        if (debug()) plugin.getLogger().info("[Harvesting] " + player.getName()
                + " trigger (" + toolId + ":" + tool.getType() + ") around ("
                + center.getBlockX() + "," + center.getBlockY() + ","
                + center.getBlockZ() + ") r=" + radius + " h=" + height);

        if (center.getWorld() == null) return;

        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minY = center.getBlockY() - height;
        int maxY = center.getBlockY() + height;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;

        FarmerMechanicFactory farmerFactory = FarmerMechanicFactory.instance();
        if (farmerFactory == null) return;

        int scanned = 0;
        int harvested = 0;

        for (ItemDisplay display : center.getWorld().getEntitiesByClass(ItemDisplay.class)) {
            Location loc = display.getLocation();
            if (loc.getBlockX() < minX || loc.getBlockX() > maxX) continue;
            if (loc.getBlockY() < minY || loc.getBlockY() > maxY) continue;
            if (loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) continue;

            var furnMech = NexoFurniture.furnitureMechanic(display);
            if (furnMech == null) continue;
            scanned++;

            String furnitureId = furnMech.getItemID();
            if (furnitureId == null) continue;

            FarmerMechanic farmer = farmerFactory.getMechanic(furnitureId);
            if (farmer == null) continue;
            if (farmer.hasNextStage()) continue;

            // Fire a BlockBreakEvent so other plugins (Jobs, CoreProtect …)
            // count the harvest as a regular block break.
            Block furnBlock = display.getLocation().getBlock();
            try {
                BlockBreakEvent breakEvent = new BlockBreakEvent(furnBlock, player);
                breakEvent.setDropItems(false);
                Bukkit.getPluginManager().callEvent(breakEvent);
                if (breakEvent.isCancelled()) {
                    if (debug()) plugin.getLogger().info("[Harvesting]   "
                            + furnitureId + " -> BlockBreakEvent cancelled, skipping.");
                    continue;
                }
            } catch (Throwable t) {
                if (debug()) plugin.getLogger().warning(
                        "[Harvesting] BlockBreakEvent failed for '" + furnitureId
                        + "': " + t.getMessage());
            }

            try {
                NexoFurniture.remove(display.getLocation(), player);
                harvested++;
            } catch (Throwable t) {
                if (debug()) plugin.getLogger().warning(
                        "[Harvesting] NexoFurniture.remove failed on '" + furnitureId
                        + "': " + t.getMessage());
            }
        }

        if (debug()) plugin.getLogger().info("[Harvesting]   scanned " + scanned
                + " ItemDisplay(s), " + harvested + " harvested.");

        if (harvested > 0) {
            damageTool(tool, harvested);
            if (debug()) plugin.getLogger().info("[Harvesting] " + player.getName()
                    + " harvested=" + harvested);
        }
    }

    private void damageTool(ItemStack tool, int amount) {
        if (amount <= 0) return;
        if (!(tool.getItemMeta() instanceof Damageable meta)) return;

        int maxDurability = tool.getType().getMaxDurability();
        try {
            if (meta.hasMaxDamage()) maxDurability = meta.getMaxDamage();
        } catch (Throwable ignored) {}
        if (maxDurability <= 0) return;

        int newDamage = Math.min(meta.getDamage() + amount, maxDurability);
        meta.setDamage(newDamage);
        tool.setItemMeta(meta);

        if (newDamage >= maxDurability) tool.setAmount(0);
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("harvester-debug", false);
    }
}

