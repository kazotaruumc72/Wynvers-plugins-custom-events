package com.wynvers.customevents.listener;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.action.ActionExecutor;
import com.wynvers.customevents.nexo.farmer.FarmerMechanic;
import com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listens to Nexo furniture place events and drives the {@link FarmerMechanic}
 * lifecycle (placement restrictions, timed growth, terminal stage actions,
 * boss roll). All configuration is read from the Nexo-registered
 * {@link FarmerMechanicFactory} – we never re-parse YAML ourselves.
 */
public class FarmerEventListener implements Listener {

    private final WynversCustomEvents plugin;
    private final ActionExecutor actions;

    public FarmerEventListener(WynversCustomEvents plugin) {
        this.plugin = plugin;
        this.actions = new ActionExecutor(plugin);
    }

    private static FarmerMechanic mechanicFor(String itemId) {
        FarmerMechanicFactory f = FarmerMechanicFactory.instance();
        return f == null ? null : f.getMechanic(itemId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurniturePlace(NexoFurniturePlaceEvent event) {
        String itemId = resolveItemId(event);
        if (itemId == null) return;

        FarmerMechanic data = mechanicFor(itemId);
        if (data == null) return;

        Block placedAt = event.getBlock();

        if (data.requiresFarmland()) {
            Block ground = placedAt.getRelative(BlockFace.DOWN);
            if (ground.getType() != Material.FARMLAND) {
                event.setCancelled(true);
                Player p = event.getPlayer();
                if (p != null) p.sendMessage("§cCe semis ne pousse que sur de la terre labourée.");
                return;
            }
        }
        if (!isYInRange(placedAt.getY(), data)) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            if (p != null) p.sendMessage("§cCe semis ne peut pas pousser à cette altitude.");
            return;
        }
        if (!isBiomeAllowed(placedAt, data)) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            if (p != null) p.sendMessage("§cCe semis ne pousse pas dans ce biome.");
            return;
        }

        if (debug()) plugin.getLogger().info("[Farmer] placed '" + itemId
                + "' at " + locStr(placedAt.getLocation())
                + ", scheduling growth in " + data.delayTicks() + "t");

        scheduleGrowth(itemId, placedAt.getLocation(), data);
    }

    // -------------------------------------------------------------------------

    private void scheduleGrowth(String currentItemId, Location loc, FarmerMechanic data) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> tick(currentItemId, loc, data),
                Math.max(1L, data.delayTicks()));
    }

    private void tick(String currentItemId, Location loc, FarmerMechanic data) {
        ItemDisplay current = NexoFurniture.baseEntity(loc);
        if (current == null) {
            if (debug()) plugin.getLogger().info(
                    "[Farmer] " + currentItemId + " at " + locStr(loc)
                    + " no longer exists – stopping growth.");
            return;
        }

        if (data.requiresFarmland()) {
            Block ground = loc.getBlock().getRelative(BlockFace.DOWN);
            if (ground.getType() != Material.FARMLAND) {
                if (debug()) plugin.getLogger().info(
                        "[Farmer] " + currentItemId + " no longer on farmland – stop.");
                return;
            }
        }
        if (!isYInRange(loc.getBlockY(), data) || !isBiomeAllowed(loc.getBlock(), data)) {
            if (debug()) plugin.getLogger().info(
                    "[Farmer] " + currentItemId + " out of biome/Y range – stop.");
            return;
        }

        if (!data.hasNextStage()) {
            if (debug()) plugin.getLogger().info(
                    "[Farmer] " + currentItemId + " is final stage – running on_final.");
            runActions(data.onFinalActions(), loc, currentItemId, null);
            rollBoss(data, loc, currentItemId);
            return;
        }

        int chance = data.probabilityPercent();
        int light = loc.getBlock().getLightLevel();
        if (data.lightBoost() && light >= FarmerMechanic.LIGHT_THRESHOLD) {
            chance = Math.min(100, chance + FarmerMechanic.LIGHT_BOOST_BONUS_PERCENT);
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        boolean grow = roll < chance;
        if (debug()) plugin.getLogger().info(
                "[Farmer] " + currentItemId + " roll=" + roll + "/" + chance
                + " light=" + light + (grow ? " -> GROW" : " -> retry"));

        if (!grow) {
            scheduleGrowth(currentItemId, loc, data);
            return;
        }

        String nextId = data.nextStageItemId();
        try {
            float yaw = current.getLocation().getYaw();
            Location placeLoc = loc.getBlock().getLocation();
            NexoFurniture.remove(current, null, null);
            ItemDisplay placed = NexoFurniture.place(nextId, placeLoc, yaw, BlockFace.UP);
            if (placed == null) {
                plugin.getLogger().warning(
                        "[Farmer] Failed to place next_stage '" + nextId + "' at " + locStr(loc));
                return;
            }
            if (debug()) plugin.getLogger().info(
                    "[Farmer] grown " + currentItemId + " -> " + nextId + " @ " + locStr(loc));
        } catch (Throwable t) {
            plugin.getLogger().warning("[Farmer] Error replacing furniture: " + t.getMessage());
            return;
        }

        runActions(data.onGrowActions(), loc, currentItemId, nextId);
        rollBoss(data, loc, currentItemId);

        FarmerMechanic nextData = mechanicFor(nextId);
        if (nextData != null) {
            scheduleGrowth(nextId, loc, nextData);
        }
    }

    // -------------------------------------------------------------------------

    private static boolean isYInRange(double y, FarmerMechanic d) {
        return y >= d.minY() && y <= d.maxY();
    }

    private static boolean isBiomeAllowed(Block b, FarmerMechanic d) {
        Set<String> wl = d.biomeWhitelist();
        Set<String> bl = d.biomeBlacklist();
        if (wl.isEmpty() && bl.isEmpty()) return true;
        String biome = b.getBiome().getKey().getKey().toLowerCase();
        if (!wl.isEmpty() && !wl.contains(biome)) return false;
        if (bl.contains(biome)) return false;
        return true;
    }

    private void runActions(List<String> raw, Location loc, String currentId, String nextId) {
        if (raw == null || raw.isEmpty()) return;
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            actions.executeWithoutPlayer(applyPlaceholders(s, loc, currentId, nextId));
        }
    }

    private void rollBoss(FarmerMechanic data, Location loc, String currentId) {
        if (data.bossChancePercent() <= 0) return;
        if (data.bossActions() == null || data.bossActions().isEmpty()) return;
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll >= data.bossChancePercent()) return;
        if (debug()) plugin.getLogger().info(
                "[Farmer] boss triggered for " + currentId + " @ " + locStr(loc));
        runActions(data.bossActions(), loc, currentId, null);
    }

    private static String applyPlaceholders(String s, Location l, String item, String next) {
        return s
                .replace("{x}", Integer.toString(l.getBlockX()))
                .replace("{y}", Integer.toString(l.getBlockY()))
                .replace("{z}", Integer.toString(l.getBlockZ()))
                .replace("{world}", l.getWorld() != null ? l.getWorld().getName() : "")
                .replace("{item}", item != null ? item : "")
                .replace("{next}", next != null ? next : "");
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("farmer-debug", false);
    }

    private String resolveItemId(NexoFurniturePlaceEvent event) {
        try {
            FurnitureMechanic m = event.getMechanic();
            if (m != null) return m.getItemID();
        } catch (Throwable ignored) {}
        try {
            ItemDisplay base = event.getBaseEntity();
            if (base != null) {
                FurnitureMechanic m = NexoFurniture.furnitureMechanic(base);
                if (m != null) return m.getItemID();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String locStr(Location l) {
        return "(" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
    }
}

