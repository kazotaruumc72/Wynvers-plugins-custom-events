package com.wynvers.customevents.nexo.harvestermachine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the virtual inventories of all placed {@link HarvesterStorageMechanic}
 * blocks and orchestrates hopper export.
 *
 * <p>Each storage block has its own 54-slot Bukkit inventory, kept alive in
 * memory and re-rendered to players on open. A scheduled task periodically
 * checks every block for a {@link Hopper} directly below and transfers one
 * item per pull period — matching vanilla hopper-from-chest behaviour.
 *
 * <p>Persistence is intentionally in-memory only (no save) to keep parity
 * with the rest of this plugin's mechanics. Server restarts wipe storages.
 */
public class HarvesterStorageManager implements Listener {

    /** Marker {@link InventoryHolder} so click listeners can recognise our inventories. */
    public static final class StorageHolder implements InventoryHolder {
        public final String blockKey;
        public final Location location;
        public final HarvesterStorageMechanic mechanic;
        public Inventory inventory;

        StorageHolder(String blockKey, Location location, HarvesterStorageMechanic mechanic) {
            this.blockKey = blockKey;
            this.location = location;
            this.mechanic = mechanic;
        }

        @NotNull
        @Override
        public Inventory getInventory() { return inventory; }
    }

    private final JavaPlugin plugin;
    private final Map<String, StorageHolder> storages = new HashMap<>();
    private BukkitTask hopperTask;

    public HarvesterStorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Single global tick that walks every storage every 4 ticks. We use
        // the per-storage `hopperPullPeriodTicks` config to throttle pulls.
        this.hopperTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    public void shutdown() {
        if (hopperTask != null) {
            try { hopperTask.cancel(); } catch (Throwable ignored) {}
            hopperTask = null;
        }
        storages.clear();
    }

    /** Returns (creating if needed) the storage holder for a given block. */
    public StorageHolder getOrCreate(Block block, HarvesterStorageMechanic mech) {
        String key = keyOf(block.getLocation());
        StorageHolder holder = storages.get(key);
        if (holder != null) return holder;
        holder = new StorageHolder(key, block.getLocation().clone(), mech);
        String title = ChatColor.translateAlternateColorCodes('&', mech.guiTitle());
        holder.inventory = Bukkit.createInventory(holder, 54, title);
        storages.put(key, holder);
        return holder;
    }

    /** Returns the holder for a block, or null if none is tracked there. */
    public StorageHolder get(Block block) {
        return storages.get(keyOf(block.getLocation()));
    }

    public StorageHolder removeAt(Location loc) {
        return storages.remove(keyOf(loc));
    }

    /** Adds {@code stack} to the nearest storage's inventory; returns the leftover (null = all fit). */
    public ItemStack deposit(StorageHolder holder, ItemStack stack) {
        if (holder == null || holder.inventory == null) return stack;
        var leftover = holder.inventory.addItem(stack);
        if (leftover.isEmpty()) return null;
        return leftover.values().iterator().next();
    }

    /**
     * Finds the closest tracked storage block within {@code radius} blocks of
     * the given centre. Returns null if none exists in range.
     */
    public StorageHolder findNearby(Location centre, int radius) {
        StorageHolder best = null;
        double bestSq = Double.MAX_VALUE;
        for (StorageHolder h : storages.values()) {
            if (h.location.getWorld() != centre.getWorld()) continue;
            double d = h.location.distanceSquared(centre);
            if (d > radius * radius * 1.0) continue;
            if (d < bestSq) { bestSq = d; best = h; }
        }
        return best;
    }

    public void openFor(Player player, Block block, HarvesterStorageMechanic mech) {
        StorageHolder holder = getOrCreate(block, mech);
        player.openInventory(holder.inventory);
    }

    public static String keyOf(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    // ─── Hopper export tick ──────────────────────────────────────────────────

    private int tickCounter = 0;

    private void tick() {
        tickCounter++;
        if (storages.isEmpty()) return;
        for (StorageHolder h : storages.values()) {
            if (h.location.getWorld() == null) continue;

            // 1. Vacuum nearby Item entities into the storage (every tick).
            vacuumNearbyItems(h);

            // 2. Export one item to a hopper below every other manager tick
            //    (manager runs every 4 ticks → hopper pull ~every 8 ticks).
            if (tickCounter % 2 != 0) continue;
            Block storageBlock = h.location.getBlock();
            Block below = storageBlock.getRelative(0, -1, 0);
            if (below.getType() != Material.HOPPER) continue;
            if (!(below.getState() instanceof Hopper hopperState)) continue;
            transferOneItem(h.inventory, hopperState.getInventory());
        }
    }

    /**
     * Pulls every loose {@link Item} entity within {@code pickup_radius} blocks
     * of the storage into its inventory. This is what gives the storage its
     * "vacuum" feel: any drop landing nearby (from a Harvester Machine, a
     * player breaking a crop, etc.) is absorbed within a tick.
     */
    private void vacuumNearbyItems(StorageHolder holder) {
        if (holder.inventory == null || holder.mechanic == null) return;
        double r = holder.mechanic.pickupRadius();
        Location centre = holder.location.clone().add(0.5, 0.5, 0.5);

        // Items configured as fuel by any registered Harvester Machine are
        // skipped so the user can re-fuel a depleted machine without the
        // storage stealing the fuel item before the machine grabs it.
        var harvesterFactory = HarvesterMachineMechanicFactory.instance();
        var fuelIds = harvesterFactory == null
                ? java.util.Collections.<String>emptySet()
                : harvesterFactory.activeFuelIds();

        for (Entity e : holder.location.getWorld().getNearbyEntities(centre, r, r, r)) {
            if (!(e instanceof Item itemEntity)) continue;
            if (!itemEntity.isValid() || itemEntity.isDead()) continue;
            ItemStack stack = itemEntity.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;

            // Skip harvester fuel items.
            if (!fuelIds.isEmpty()) {
                try {
                    String nexoId = com.nexomc.nexo.api.NexoItems.idFromItem(stack);
                    if (nexoId != null && fuelIds.contains(nexoId.toLowerCase(java.util.Locale.ROOT))) continue;
                } catch (Throwable ignored) {}
            }

            // Whitelist filter — leave non-allowed items in the world.
            if (!holder.mechanic.isAllowed(stack)) continue;

            ItemStack leftover = deposit(holder, stack);
            if (leftover == null) {
                itemEntity.remove();
            } else {
                itemEntity.setItemStack(leftover);
            }
        }
    }

    private void transferOneItem(Inventory source, Inventory destination) {
        for (int i = 0; i < source.getSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            ItemStack toMove = stack.clone();
            toMove.setAmount(1);
            var leftover = destination.addItem(toMove);
            if (!leftover.isEmpty()) continue;       // destination full — try next slot
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) source.setItem(i, null);
            return;
        }
    }

    // ─── Bukkit listeners ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // Standard chest-like behaviour — allow all clicks. No protection.
        if (!(event.getView().getTopInventory().getHolder() instanceof StorageHolder)) return;
        // Nothing to do; vanilla handles add/remove.
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StorageHolder holder)) return;
        if (event.getPlayer() instanceof Player p) {
            // No-op — kept for future hooks (e.g. permission checks).
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // No-op — contents stay in memory for the next viewer.
    }
}