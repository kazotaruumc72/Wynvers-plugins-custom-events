package com.wynvers.customevents.listener;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.harvester.HarvesterCache;
import com.wynvers.customevents.nexo.harvester.HarvesterMechanic;
import com.wynvers.customevents.nexo.harvester.HarvesterMechanicFactory;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Harvester mechanic: when a player right-clicks a Nexo furniture with a tool,
 * scans the surrounding area for other furnitures and damages the tool accordingly.
 *
 * <p>Configuration is read from the Nexo item's {@code harvester} mechanic section.
 */
public class HarvesterEventListener implements Listener {

    private final WynversCustomEvents plugin;

    public HarvesterEventListener(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    private static HarvesterMechanic mechanicFor(ItemDisplay baseEntity) {
        try {
            FurnitureMechanic furniture = NexoFurniture.furnitureMechanic(baseEntity);
            if (furniture == null) {
                return null;
            }
            String itemId = furniture.getItemID();

            // Quick check: is this item in the final-stage harvesters cache?
            if (!HarvesterCache.isFinalStageHarvester(itemId)) {
                return null;
            }

            HarvesterMechanicFactory factory = HarvesterMechanicFactory.instance();
            if (factory == null) {
                return null;
            }
            return factory.getMechanic(itemId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only care about right-click on blocks
        if (event.getAction().name().startsWith("LEFT")) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Location clickedLoc = event.getClickedBlock().getLocation();

        // Check if there's a Nexo furniture at the clicked location
        ItemDisplay baseEntity = NexoFurniture.baseEntity(clickedLoc);
        if (baseEntity == null) {
            return;
        }

        // Get harvester config from the item's mechanic
        // (mechanicFor() already checks if it's a final-stage harvester via cache)
        HarvesterMechanic harvesterConfig = mechanicFor(baseEntity);
        if (harvesterConfig == null) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType().isAir()) {
            itemInHand = player.getInventory().getItemInOffHand();
        }
        if (itemInHand.getType().isAir()) {
            return;
        }

        int scanRadius = harvesterConfig.scanRadius();
        int scanHeight = harvesterConfig.scanHeight();

        // Debug log
        if (debug()) {
            plugin.getLogger().info("[Harvester] " + player.getName() + " trigger ("
                    + itemInHand.getType().name() + ") action=" + event.getAction().name()
                    + " around (" + clickedLoc.getBlockX() + "," + clickedLoc.getBlockY()
                    + "," + clickedLoc.getBlockZ() + ") r=" + scanRadius + " h=" + scanHeight);
        }

        // Scan for nearby ItemDisplay entities
        ScanResult scanResult = scanForItemDisplays(clickedLoc, scanRadius, scanHeight);

        if (debug()) {
            plugin.getLogger().info("[Harvester]   scanned " + scanResult.totalScanned
                    + " ItemDisplay(s), " + scanResult.matches + " match(es)");
        }

        // Damage the tool for each furniture found
        int damagePerFurniture = harvesterConfig.damagePerItem();
        int totalDamage = scanResult.matches * damagePerFurniture;

        if (totalDamage > 0) {
            // Apply damage even in creative mode by modifying durability
            damageTool(itemInHand, totalDamage);

            if (debug()) {
                plugin.getLogger().info("[Harvester] " + player.getName()
                        + " harvested=" + scanResult.matches);
            }
        }
    }

    /**
     * Scans the area around a location for ItemDisplay entities and furnitures.
     */
    private ScanResult scanForItemDisplays(Location center, int radius, int height) {
        ScanResult result = new ScanResult();

        if (center.getWorld() == null) {
            return result;
        }

        Location scanMin = center.clone().add(-radius, -height, -radius);
        Location scanMax = center.clone().add(radius, height, radius);

        // Find all ItemDisplay entities in the world
        var allDisplays = center.getWorld().getEntitiesByClass(ItemDisplay.class);

        for (ItemDisplay display : allDisplays) {
            Location displayLoc = display.getLocation();

            // Check if within bounds
            if (displayLoc.getBlockX() >= scanMin.getBlockX()
                    && displayLoc.getBlockX() <= scanMax.getBlockX()
                    && displayLoc.getBlockY() >= scanMin.getBlockY()
                    && displayLoc.getBlockY() <= scanMax.getBlockY()
                    && displayLoc.getBlockZ() >= scanMin.getBlockZ()
                    && displayLoc.getBlockZ() <= scanMax.getBlockZ()) {

                result.totalScanned++;

                // Check if it's actually a Nexo furniture
                if (NexoFurniture.furnitureMechanic(display) != null) {
                    result.matches++;
                }
            }
        }

        return result;
    }

    /**
     * Helper class to store scan results (total items vs matching furnitures).
     */
    private static class ScanResult {
        int totalScanned = 0;
        int matches = 0;
    }

    /**
     * Damages a tool item by reducing its durability.
     * Works even in creative mode.
     */
    private void damageTool(ItemStack item, int damage) {
        if (item.getType().isAir() || damage <= 0) {
            return;
        }

        // Get or create Damageable tag
        if (item.getItemMeta() instanceof Damageable) {
            Damageable meta = (Damageable) item.getItemMeta();
            if (meta != null) {
                int currentDamage = meta.getDamage();
                int maxDurability = item.getType().getMaxDurability();

                // Apply damage
                int newDamage = Math.min(currentDamage + damage, maxDurability);
                meta.setDamage(newDamage);

                item.setItemMeta(meta);

                // If tool is broken, remove it
                if (newDamage >= maxDurability) {
                    item.setAmount(0);
                }
            }
        }
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("harvester-debug", false);
    }
}





