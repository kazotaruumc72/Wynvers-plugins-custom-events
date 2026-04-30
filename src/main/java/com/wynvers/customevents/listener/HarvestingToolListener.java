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
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
 * <p>A Nexo furniture can be clicked through two different Bukkit events
 * depending on its config:
 * <ul>
 *   <li>{@link PlayerInteractEvent} when the furniture has a barrier
 *       block (right-click goes through {@code clickedBlock})</li>
 *   <li>{@link PlayerInteractEntityEvent} when the furniture is purely
 *       entity-based (right-click goes through the Interaction entity)</li>
 * </ul>
 * We listen to both.
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
        plugin.getLogger().info("[Harvesting] Listener instantiated. "
                + "HarvestingMechanicFactory.instance="
                + (HarvestingMechanicFactory.instance() != null ? "OK" : "NULL"));
    }

    // -------------------------------------------------------------------------
    // PlayerInteractEvent: right-click on a barrier block (legacy furnitures)
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        // VERY VERBOSE – log EVERY right-click while we debug
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR) {
            ItemStack hand = event.getItem();
            plugin.getLogger().info("[Harvesting][TRACE] PlayerInteractEvent action="
                    + event.getAction() + " hand=" + event.getHand()
                    + " item=" + (hand == null ? "null" : hand.getType())
                    + " nexoId=" + (hand == null ? "-" : safeIdFromItem(hand))
                    + " block=" + (event.getClickedBlock() != null
                            ? event.getClickedBlock().getType() : "AIR"));
        }

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack tool = event.getItem();
        if (tool == null || tool.getType().isAir()) return;

        String toolId = safeIdFromItem(tool);
        if (toolId == null) return;

        HarvestingMechanic mechanic = mechanicFor(tool);
        if (mechanic == null) {
            plugin.getLogger().warning("[Harvesting] '" + toolId
                    + "' has NO 'harvesting' mechanic! Did you /nexo reload all? "
                    + "(factory=" + (HarvestingMechanicFactory.instance() != null ? "OK" : "NULL") + ")");
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        ItemDisplay clickedFurniture = NexoFurniture.baseEntity(clicked.getLocation());
        if (clickedFurniture == null) {
            plugin.getLogger().info("[Harvesting]   block " + clicked.getType()
                    + " at " + clicked.getX() + "," + clicked.getY() + "," + clicked.getZ()
                    + " is NOT a Nexo furniture base. (Maybe entity-only furniture?)");
            return;
        }

        plugin.getLogger().info("[Harvesting]   found furniture base entity, harvesting!");
        event.setCancelled(true);
        triggerHarvest(event.getPlayer(), tool, mechanic, clickedFurniture);
    }

    // -------------------------------------------------------------------------
    // PlayerInteractEntityEvent: right-click on an Interaction entity
    // (entity-only furnitures use this)
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        // VERY VERBOSE
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        plugin.getLogger().info("[Harvesting][TRACE] PlayerInteractEntityEvent hand="
                + event.getHand() + " entityType=" + event.getRightClicked().getType()
                + " mainItem=" + hand.getType()
                + " nexoId=" + safeIdFromItem(hand));

        if (event.getHand() != EquipmentSlot.HAND) return;
        handleEntityClick(event.getPlayer(), event.getRightClicked(), event, "InteractEntity");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        // VERY VERBOSE
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        plugin.getLogger().info("[Harvesting][TRACE] PlayerInteractAtEntityEvent hand="
                + event.getHand() + " entityType=" + event.getRightClicked().getType()
                + " mainItem=" + hand.getType()
                + " nexoId=" + safeIdFromItem(hand));

        if (event.getHand() != EquipmentSlot.HAND) return;
        handleEntityClick(event.getPlayer(), event.getRightClicked(), event, "InteractAtEntity");
    }

    private void handleEntityClick(Player player, Entity entity,
                                   org.bukkit.event.Cancellable cancellable,
                                   String evtName) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) return;

        String toolId = safeIdFromItem(tool);
        if (toolId == null) return;

        plugin.getLogger().info("[Harvesting] >>> " + evtName + " with Nexo item '" + toolId
                + "' on entity type=" + entity.getType());

        HarvestingMechanic mechanic = mechanicFor(tool);
        if (mechanic == null) {
            plugin.getLogger().warning("[Harvesting] '" + toolId
                    + "' has NO 'harvesting' mechanic! Did you /nexo reload all?");
            return;
        }

        // Find the underlying Nexo furniture base entity
        ItemDisplay base;
        if (entity instanceof ItemDisplay display) {
            base = display;
        } else {
            base = NexoFurniture.baseEntity(entity.getLocation());
        }
        if (base == null) {
            plugin.getLogger().info("[Harvesting]   no Nexo base entity found at "
                    + entity.getLocation().getBlockX() + ","
                    + entity.getLocation().getBlockY() + ","
                    + entity.getLocation().getBlockZ());
            return;
        }
        if (NexoFurniture.furnitureMechanic(base) == null) {
            plugin.getLogger().info("[Harvesting]   entity exists but has no furniture mechanic.");
            return;
        }

        plugin.getLogger().info("[Harvesting]   found furniture base entity via " + evtName + ", harvesting!");
        cancellable.setCancelled(true);
        triggerHarvest(player, tool, mechanic, base);
    }

    // -------------------------------------------------------------------------
    // Shared harvesting logic
    // -------------------------------------------------------------------------
    private void triggerHarvest(Player player, ItemStack tool,
                                HarvestingMechanic mechanic,
                                ItemDisplay clickedFurniture) {
        // Cooldown
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < mechanic.cooldownMs()) {
            plugin.getLogger().info("[Harvesting] " + player.getName()
                    + " on cooldown (" + (mechanic.cooldownMs() - (now - last))
                    + "ms left).");
            return;
        }
        lastUse.put(player.getUniqueId(), now);

        Location center = clickedFurniture.getLocation();
        int radius = mechanic.radius();
        int height = mechanic.height();

        plugin.getLogger().info("[Harvesting] " + player.getName()
                + " trigger around (" + center.getBlockX() + ","
                + center.getBlockY() + "," + center.getBlockZ()
                + ") r=" + radius + " h=" + height);

        if (center.getWorld() == null) return;

        FarmerMechanicFactory farmerFactory = FarmerMechanicFactory.instance();
        if (farmerFactory == null) {
            plugin.getLogger().warning("[Harvesting] FarmerMechanicFactory not registered!");
            return;
        }

        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minY = center.getBlockY() - height;
        int maxY = center.getBlockY() + height;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;

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

            // Fire BlockBreakEvent first so other plugins (Jobs, etc.) count it
            Block furnBlock = display.getLocation().getBlock();
            try {
                BlockBreakEvent breakEvent = new BlockBreakEvent(furnBlock, player);
                breakEvent.setDropItems(false);
                Bukkit.getPluginManager().callEvent(breakEvent);
                if (breakEvent.isCancelled()) continue;
            } catch (Throwable ignored) {}

            try {
                NexoFurniture.remove(display.getLocation(), player);
                harvested++;
            } catch (Throwable t) {
                plugin.getLogger().warning("[Harvesting] remove failed on '"
                        + furnitureId + "': " + t.getMessage());
            }
        }

        plugin.getLogger().info("[Harvesting]   scanned " + scanned
                + " furniture(s), harvested " + harvested);

        if (harvested > 0) {
            damageTool(tool, harvested);
        }
    }

    /** Resolves the {@link HarvestingMechanic} for a given tool, or null. */
    private HarvestingMechanic mechanicFor(ItemStack tool) {
        String toolId = safeIdFromItem(tool);
        if (toolId == null) return null;

        HarvestingMechanicFactory factory = HarvestingMechanicFactory.instance();
        if (factory == null) return null;
        return factory.getMechanic(toolId);
    }

    private static String safeIdFromItem(ItemStack tool) {
        try {
            return NexoItems.idFromItem(tool);
        } catch (Throwable t) {
            return null;
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
}


