package com.wynvers.customevents.nexo.harvestermachine;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.nexomc.nexo.api.events.custom_block.NexoBlockPlaceEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nexo {@link MechanicFactory} for the {@code harvester_machine} mechanic.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>Placement is rejected if {@code require_water_below} is true and the
 *       block directly under the placement isn't a water source (incl.
 *       waterlogged stairs/slabs/leaves).</li>
 *   <li>While idle, a fuel item dropped on top of the machine activates it
 *       for {@code fuel_duration}; the texture swaps to the active variant
 *       and a hologram displays the remaining time.</li>
 *   <li>While active, every {@code harvest_interval_seconds} the machine
 *       scans its {@code radius} cube for fully-grown crops (vanilla Ageable
 *       or whitelisted Nexo custom_blocks) and harvests + replants them.
 *       Drops are pushed into the nearest tracked {@link HarvesterStorageManager}
 *       block within {@code storage_search_radius}, or fall on the ground.</li>
 *   <li>When the fuel runs out, the texture reverts and the hologram disappears.</li>
 * </ul>
 */
public class HarvesterMachineMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "harvester_machine";

    private static HarvesterMachineMechanicFactory instance;

    private final JavaPlugin plugin;
    private final Map<String, ActiveMachine> active = new ConcurrentHashMap<>();
    /** block-keys we're swapping ourselves (variant change) — suppress break handling. */
    private final Set<String> pendingSwap = ConcurrentHashMap.newKeySet();
    private BukkitTask tickTask;

    public HarvesterMachineMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Tick every 0.5s — drives the harvest loop, fuel scan and hologram.
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public static HarvesterMachineMechanicFactory instance() { return instance; }

    /**
     * Returns the set of Nexo item ids configured as fuel by every currently
     * registered Harvester Machine. The Storage block uses this to skip
     * absorbing fuel items so the user can re-fuel a depleted machine without
     * the nearby storage stealing the fuel first.
     */
    public java.util.Set<String> activeFuelIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ActiveMachine m : active.values()) {
            String fuel = m.mechanic.fuelItem();
            if (fuel != null && !fuel.isEmpty()) ids.add(fuel.toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    @Override
    public @Nullable HarvesterMachineMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof HarvesterMachineMechanic h) ? h : null;
    }

    @Override
    public @Nullable HarvesterMachineMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof HarvesterMachineMechanic h) ? h : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        HarvesterMachineMechanic mechanic = new HarvesterMachineMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    public void shutdown() {
        if (tickTask != null) {
            try { tickTask.cancel(); } catch (Throwable ignored) {}
            tickTask = null;
        }
        for (ActiveMachine m : active.values()) removeHologram(m);
        active.clear();
        pendingSwap.clear();
    }

    // ─── Placement: validate water-source below ──────────────────────────────
    //
    // Nexo NOTEBLOCK-style custom_blocks are placed via PlayerInteractEvent and
    // do NOT trigger Bukkit's BlockPlaceEvent — we listen to Nexo's own
    // NexoBlockPlaceEvent instead.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNexoBlockPlace(NexoBlockPlaceEvent event) {
        String nexoId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        HarvesterMachineMechanic mech = getMechanic(nexoId);
        if (mech == null) return;

        if (mech.requireWaterBelow()) {
            Block below = event.getBlock().getRelative(0, -1, 0);
            if (!containsWaterSource(below)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(
                        "§c[Récolteur] Le bloc en dessous doit contenir une source d'eau "
                      + "(bloc d'eau, escalier/dalle/feuilles waterlogged, etc.).");
                return;
            }
        }

        // Register the machine in our active map so the tick loop sees it.
        Block placed = event.getBlock();
        String key = keyOf(placed.getLocation());
        ActiveMachine m = new ActiveMachine(key, event.getPlayer().getUniqueId(),
                placed.getLocation().clone(), mech);
        m.inactiveNexoId = nexoId;
        active.put(key, m);
        event.getPlayer().sendMessage("§a[Récolteur] Placé. Jette §e"
                + mech.fuelItem() + "§a sur le bloc pour l'alimenter.");
    }

    private static boolean containsWaterSource(Block block) {
        if (block.getType() == Material.WATER) {
            BlockData data = block.getBlockData();
            if (data instanceof org.bukkit.block.data.Levelled lvl) {
                return lvl.getLevel() == 0; // 0 = source
            }
            return true;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged w) {
            return w.isWaterlogged();
        }
        return false;
    }

    // ─── Register / unregister ───────────────────────────────────────────────

    public static String keyOf(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(NexoBlockBreakEvent event) {
        Block b = event.getBlock();
        if (b == null) return;
        String key = keyOf(b.getLocation());
        if (pendingSwap.remove(key)) return;
        ActiveMachine m = active.remove(key);
        if (m != null) removeHologram(m);
    }

    // ─── Main tick ───────────────────────────────────────────────────────────

    private int slowTickCounter = 0;

    private void tick() {
        if (active.isEmpty()) return;
        slowTickCounter++;
        long nowMs = System.currentTimeMillis();
        for (ActiveMachine m : active.values()) {
            tickMachine(m, nowMs);
        }
    }

    private void tickMachine(ActiveMachine m, long nowMs) {
        World world = m.location.getWorld();
        if (world == null) return;
        if (!world.isChunkLoaded(m.location.getBlockX() >> 4, m.location.getBlockZ() >> 4)) return;

        Block block = m.location.getBlock();
        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(block);
        if (cb == null) {
            // External removal — drop state.
            removeHologram(m);
            active.remove(m.blockKey);
            return;
        }

        // Always scan for fuel-item drops on top — feeds add to active duration.
        scanForFuel(m, world, nowMs);

        // Active duration management.
        if (m.activeUntilMs > 0) {
            updateHologram(m, nowMs);
            if (nowMs >= m.activeUntilMs) {
                expire(m);
                return;
            }
            // Harvest every harvest_interval_seconds. We tick every 0.5s so the
            // counter divisor is interval * 2.
            int periodTicks = Math.max(1, m.mechanic.harvestIntervalSeconds() * 2);
            if (slowTickCounter % periodTicks == 0) {
                runHarvest(m, world, block);
            }
        }
    }

    // ─── Fuel feed ───────────────────────────────────────────────────────────

    private void scanForFuel(ActiveMachine m, World world, long nowMs) {
        String fuelId = m.mechanic.fuelItem();
        if (fuelId == null || fuelId.isEmpty()) return;

        double r = m.mechanic.feedRadius();
        Location feedPoint = m.location.clone().add(0.5, 1.0, 0.5);

        for (Entity e : world.getNearbyEntities(feedPoint, r, r + 0.5, r)) {
            if (!(e instanceof Item itemEntity)) continue;
            if (!itemEntity.isValid() || itemEntity.isDead()) continue;
            ItemStack stack = itemEntity.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;
            String nexoId = NexoItems.idFromItem(stack);
            if (nexoId == null || !nexoId.equalsIgnoreCase(fuelId)) continue;

            int amount = stack.getAmount();
            long addedMs = amount * m.mechanic.fuelDurationTicks() * 50L;
            boolean wasActive = m.activeUntilMs > nowMs;
            m.activeUntilMs = Math.max(m.activeUntilMs, nowMs) + addedMs;

            itemEntity.remove();
            world.spawnParticle(m.mechanic.particle(), feedPoint, 16, 0.4, 0.4, 0.4, 0.02);
            world.playSound(feedPoint, m.mechanic.fuelSound(), 1.0f, 1.2f);

            Player owner = Bukkit.getPlayer(m.ownerId);
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§a[Récolteur] §f+" + formatDuration(addedMs / 50L)
                        + " §7(restant: §f" + formatDuration((m.activeUntilMs - nowMs) / 50L) + "§7)");
            }

            if (!wasActive) {
                swapToActive(m);
                spawnHologram(m, world);
            }
            updateHologram(m, nowMs);
        }
    }

    // ─── Expire / variant swap ───────────────────────────────────────────────

    private void expire(ActiveMachine m) {
        m.activeUntilMs = 0;
        removeHologram(m);
        swapToInactive(m);
        Player owner = Bukkit.getPlayer(m.ownerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§e[Récolteur] §fLe carburant est épuisé.");
        }
    }

    private void swapToActive(ActiveMachine m) {
        String active = m.mechanic.activeVariant();
        if (active == null || active.isEmpty()) return;
        if (!NexoBlocks.isCustomBlock(active)) return;
        BlockData data = NexoBlocks.blockData(active);
        if (data == null) return;
        pendingSwap.add(m.blockKey);
        m.location.getBlock().setBlockData(data, false);
        Bukkit.getScheduler().runTask(plugin, () -> pendingSwap.remove(m.blockKey));
    }

    private void swapToInactive(ActiveMachine m) {
        if (m.inactiveNexoId == null || m.inactiveNexoId.isEmpty()) return;
        if (!NexoBlocks.isCustomBlock(m.inactiveNexoId)) return;
        BlockData data = NexoBlocks.blockData(m.inactiveNexoId);
        if (data == null) return;
        pendingSwap.add(m.blockKey);
        m.location.getBlock().setBlockData(data, false);
        Bukkit.getScheduler().runTask(plugin, () -> pendingSwap.remove(m.blockKey));
    }

    // ─── Harvest loop ────────────────────────────────────────────────────────

    private void runHarvest(ActiveMachine m, World world, Block centre) {
        int r = m.mechanic.radius();
        int harvested = 0;

        // Pass A: vanilla Ageable + Nexo custom_block crops (block-aligned).
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block target = centre.getRelative(dx, dy, dz);
                    if (tryHarvestBlock(m, world, target)) harvested++;
                }
            }
        }

        // Pass B: Nexo furniture crops in the radius. Furnitures are entities,
        // not blocks — iterate ItemDisplay entities in the bounding box so we
        // catch crops whose render position isn't pixel-aligned with a block.
        Location c = centre.getLocation().add(0.5, 0.5, 0.5);
        double rr = r + 0.5;
        for (Entity e : world.getNearbyEntities(c, rr, rr, rr)) {
            if (!(e instanceof ItemDisplay base)) continue;
            if (tryHarvestFurniture(m, world, base)) harvested++;
        }

        if (harvested > 0) {
            world.spawnParticle(m.mechanic.particle(), centre.getLocation().add(0.5, 1.2, 0.5),
                    harvested * 3, 0.5, 0.5, 0.5, 0.02);
            world.playSound(centre.getLocation(), m.mechanic.harvestSound(), 0.5f, 1.1f);
        }
    }

    /** Handles vanilla Ageable crops and Nexo custom_block crops. */
    private boolean tryHarvestBlock(ActiveMachine m, World world, Block target) {
        // 1. Vanilla ageable crops at max age?
        BlockData data = target.getBlockData();
        if (data instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
            Material crop = target.getType();
            ItemStack tool = new ItemStack(Material.DIAMOND_HOE);
            var drops = target.getDrops(tool);
            target.setType(Material.AIR, false);
            target.setType(crop, false);
            BlockData newData = target.getBlockData();
            if (newData instanceof Ageable a) {
                a.setAge(0);
                target.setBlockData(a, false);
            }
            depositOrDrop(m, world, target.getLocation(), drops);
            return true;
        }

        // 2. Nexo custom_block crop in whitelist?
        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(target);
        if (cb != null && cb.getItemID() != null) {
            String idLower = cb.getItemID().toLowerCase(Locale.ROOT);
            String replantId = m.mechanic.whitelistNexoCrops().get(idLower);
            if (replantId != null) {
                Player owner = Bukkit.getPlayer(m.ownerId);
                if (owner == null) return false;
                Location loc = target.getLocation();
                if (!NexoBlocks.remove(loc, owner)) return false;
                if (NexoBlocks.isCustomBlock(replantId)) {
                    NexoBlocks.place(replantId, loc);
                }
                sweepNearbyItems(m, world, loc);
                return true;
            }
        }
        return false;
    }

    /**
     * Handles Nexo furniture crops (the standard multi-stage crop shape).
     *
     * <p>Why this isn't just {@code NexoFurniture.remove(base, owner)}: Nexo's
     * internal break path skips drops when the player is in CREATIVE mode
     * (admin testing) and depends on the player's main-hand item satisfying
     * {@code Drop.isToolEnough}. For an automated harvester we want
     * deterministic drops regardless of who the owner is or what they're
     * holding, so we replicate the player-break flow manually:
     * <ol>
     *   <li>Fire {@link com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent}
     *       so RoseLoot / other plugins see the break.</li>
     *   <li>Call {@code drop.furnitureSpawns(base, syntheticTool)} ourselves —
     *       bypasses the creative-mode skip and uses a deterministic tool.</li>
     *   <li>Remove the base entity via {@code NexoFurniture.remove(base, null, null)}
     *       (null player → Nexo skips its own drop logic and just removes).</li>
     * </ol>
     */
    private boolean tryHarvestFurniture(ActiveMachine m, World world, ItemDisplay base) {
        var fm = NexoFurniture.furnitureMechanic(base);
        if (fm == null || fm.getItemID() == null) return false;
        String idLower = fm.getItemID().toLowerCase(Locale.ROOT);
        String replantId = m.mechanic.whitelistNexoCrops().get(idLower);
        if (replantId == null) return false;

        Player owner = Bukkit.getPlayer(m.ownerId);
        if (owner == null) return false;

        Location baseLoc = base.getLocation().getBlock().getLocation();
        float yaw = base.getLocation().getYaw();

        // 1. Fetch the configured drop (built by Nexo from Mechanics.furniture.drop.*).
        var breakable = fm.getBreakable();
        var drop = (breakable != null) ? breakable.getDrop() : null;

        // 2. Fire the canonical NexoFurnitureBreakEvent so RoseLoot's
        //    NexoBlockBreakListener.onNexoFurnitureBlockBreak picks it up and
        //    runs any loot table the admin attached to this furniture id.
        try {
            var event = new com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent(fm, base, owner);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            // RoseLoot or another listener may have rewritten the Drop.
            if (event.getDrop() != null) drop = event.getDrop();
        } catch (Throwable t) {
            plugin.getLogger().warning("[Harvester] Failed firing NexoFurnitureBreakEvent: " + t.getMessage());
        }

        // 3. Spawn the drops manually with a synthetic tool — bypasses Nexo's
        //    creative-mode + main-hand checks.
        if (drop != null && !drop.isEmpty()) {
            try {
                drop.furnitureSpawns(base, new ItemStack(Material.DIAMOND_HOE));
            } catch (Throwable t) {
                plugin.getLogger().warning("[Harvester] Drop.furnitureSpawns failed: " + t.getMessage());
            }
        }

        // 4. Remove the entity. Passing null player makes Nexo skip its own
        //    drop step (we already did it in step 3) and just despawn the
        //    ItemDisplay.
        try {
            NexoFurniture.remove(base, null, null);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Harvester] NexoFurniture.remove failed: " + t.getMessage());
            return false;
        }

        // 5. Replant the first-stage furniture and start the growth schedule.
        if (NexoFurniture.isFurniture(replantId)) {
            NexoFurniture.place(replantId, baseLoc, yaw, BlockFace.UP);
            triggerFarmerGrowth(replantId, baseLoc);
        }

        // Drops are picked up by the HarvesterStorage's passive vacuum.
        return true;
    }

    /** Kicks off the FarmerEventListener growth cycle for a furniture we just placed. */
    private void triggerFarmerGrowth(String replantId, Location loc) {
        try {
            var farmerFactory = com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory.instance();
            var listener = com.wynvers.customevents.listener.FarmerEventListener.instance();
            if (farmerFactory == null || listener == null) return;
            var farmerMech = farmerFactory.getMechanic(replantId);
            if (farmerMech == null) return;
            listener.onManualPlacement(replantId, loc, farmerMech);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Harvester] Could not trigger farmer growth for '"
                    + replantId + "': " + t.getMessage());
        }
    }

    private void depositOrDrop(ActiveMachine m, World world, Location centre, Iterable<ItemStack> stacks) {
        HarvesterStorageManager storage = HarvesterStorageMechanicFactory.instance() != null
                ? HarvesterStorageMechanicFactory.instance().manager() : null;
        HarvesterStorageManager.StorageHolder holder = storage == null ? null
                : storage.findNearby(m.location, m.mechanic.storageSearchRadius());

        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir()) continue;
            ItemStack leftover = holder == null ? stack : storage.deposit(holder, stack);
            if (leftover != null) {
                world.dropItemNaturally(centre.clone().add(0.5, 0.5, 0.5), leftover);
            }
        }
    }

    private void sweepNearbyItems(ActiveMachine m, World world, Location centre) {
        HarvesterStorageManager storage = HarvesterStorageMechanicFactory.instance() != null
                ? HarvesterStorageMechanicFactory.instance().manager() : null;
        HarvesterStorageManager.StorageHolder holder = storage == null ? null
                : storage.findNearby(m.location, m.mechanic.storageSearchRadius());
        if (holder == null) return; // leave on the ground

        for (Entity e : world.getNearbyEntities(centre, 1.2, 1.2, 1.2)) {
            if (!(e instanceof Item itemEntity)) continue;
            if (!itemEntity.isValid() || itemEntity.isDead()) continue;
            ItemStack stack = itemEntity.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;
            ItemStack leftover = storage.deposit(holder, stack);
            if (leftover == null) {
                itemEntity.remove();
            } else {
                itemEntity.setItemStack(leftover);
            }
        }
    }

    // ─── Hologram ────────────────────────────────────────────────────────────

    private void spawnHologram(ActiveMachine m, World world) {
        removeHologram(m);
        Location holoLoc = m.location.clone().add(0.5, 1.6, 0.5);
        TextDisplay td = world.spawn(holoLoc, TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setSeeThrough(false);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            t.setPersistent(false);
        });
        m.hologramId = td.getUniqueId();
    }

    private void updateHologram(ActiveMachine m, long nowMs) {
        if (m.hologramId == null) return;
        Entity e = Bukkit.getEntity(m.hologramId);
        if (!(e instanceof TextDisplay td)) { m.hologramId = null; return; }
        long remaining = Math.max(0, m.activeUntilMs - nowMs);
        td.setText("§a§lRécolteur §7| §f" + formatDuration(remaining / 50L));
    }

    private void removeHologram(ActiveMachine m) {
        if (m.hologramId == null) return;
        Entity e = Bukkit.getEntity(m.hologramId);
        if (e != null) try { e.remove(); } catch (Throwable ignored) {}
        m.hologramId = null;
    }

    private static String formatDuration(long ticks) {
        long totalSeconds = Math.max(0, ticks / 20L);
        long h = totalSeconds / 3600;
        long mm = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, mm, s);
        return String.format("%02d:%02d", mm, s);
    }

    // ─── State holder ────────────────────────────────────────────────────────

    private static final class ActiveMachine {
        final String blockKey;
        final UUID ownerId;
        final Location location;
        final HarvesterMachineMechanic mechanic;
        /** Nexo id of the inactive (= original) variant; resolved at placement time. */
        String inactiveNexoId;
        volatile long activeUntilMs = 0;
        volatile UUID hologramId;

        ActiveMachine(String blockKey, UUID ownerId, Location location, HarvesterMachineMechanic mechanic) {
            this.blockKey = blockKey;
            this.ownerId = ownerId;
            this.location = location;
            this.mechanic = mechanic;
        }
    }
}