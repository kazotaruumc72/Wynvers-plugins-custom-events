package com.wynvers.customevents.nexo.blockbreaker;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.UUID;

/**
 * Nexo {@link MechanicFactory} for the {@code block_breaker} mechanic.
 *
 * <p>Drives the full lifecycle of a placed BlockBreaker:
 * <ul>
 *   <li>Right-click with the activator item on a face → toggles that face active.</li>
 *   <li>Shift+right-click (any item / empty hand) → opens the upgrade GUI.</li>
 *   <li>Once at least one face is active, a repeating task breaks the block in
 *       front of every active face every {@code break_interval_seconds}.</li>
 *   <li>Bedrock and config blacklist entries are never broken — important for
 *       modded blocks fed in via RoseLoot.</li>
 *   <li>When the breaker itself is broken, the state and task are cleaned up.</li>
 * </ul>
 */
public class BlockBreakerMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "block_breaker";

    private static BlockBreakerMechanicFactory instance;

    private final JavaPlugin plugin;
    private final BlockBreakerManager manager;
    private final BlockBreakerStore store;
    /**
     * Block-key → Fortune tier of the currently-active break call. Populated
     * just before {@link NexoBlocks#remove(Location, Player)} so that the
     * {@code ItemSpawnEvent} listener can recognise items dropped by
     * RoseLoot during this very break and inflate their stack-size.
     */
    private final java.util.Map<String, Integer> pendingFortune =
            new java.util.concurrent.ConcurrentHashMap<>();

    public BlockBreakerMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        this.manager = new BlockBreakerManager();
        this.store = new BlockBreakerStore(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static BlockBreakerMechanicFactory instance() { return instance; }
    public BlockBreakerManager manager()                  { return manager; }
    public JavaPlugin plugin()                            { return plugin; }
    public BlockBreakerStore store()                      { return store; }

    /**
     * Loads persisted breaker states from disk and resumes any active break
     * loops. Called once after Nexo finishes loading items so that
     * {@link #getMechanic(String)} can resolve nexo ids and
     * {@link NexoBlocks#customBlockMechanic} can find the block on the map.
     */
    public void restoreFromDisk() {
        var loaded = store.loadAll();
        if (loaded.isEmpty()) return;
        int resumed = 0;
        for (var e : loaded.entrySet()) {
            manager.put(e.getKey(), e.getValue());
            BlockBreakerMechanic mech = getMechanic(e.getValue().nexoId);
            if (mech == null || e.getValue().activeFaces.isEmpty()) continue;
            Block breakerBlock = resolveBlock(e.getKey());
            if (breakerBlock == null) continue;
            startBreakLoop(breakerBlock, e.getKey(), mech);
            resumed++;
        }
        plugin.getLogger().info("Restored " + loaded.size() + " BlockBreaker state(s), resumed "
                + resumed + " break loop(s).");
    }

    /** Re-parses a "worldUid:x:y:z" key into the live {@link Block} when the world is loaded. */
    private @Nullable Block resolveBlock(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return null;
        try {
            var world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) return null;
            return world.getBlockAt(Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (Exception e) {
            return null;
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> store.saveAll(manager.states()));
    }

    @Override
    public @Nullable BlockBreakerMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof BlockBreakerMechanic b) ? b : null;
    }

    @Override
    public @Nullable BlockBreakerMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof BlockBreakerMechanic b) ? b : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        BlockBreakerMechanic mechanic = new BlockBreakerMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    // ─── Interactions ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(clicked);
        if (cb == null) return;
        BlockBreakerMechanic mech = getMechanic(cb.getItemID());
        if (mech == null) return;

        Player player = event.getPlayer();
        String blockKey = BlockBreakerManager.keyOf(clicked.getLocation());

        // Shift+right-click → upgrade GUI (no activator required, no face toggle).
        if (player.isSneaking()) {
            event.setCancelled(true);
            openUpgradeMenu(player, blockKey, mech);
            return;
        }

        // Otherwise — must hold the configured activator item.
        ItemStack inHand = event.getItem();
        if (!isActivator(inHand, mech)) return;

        event.setCancelled(true);

        BlockFace face = event.getBlockFace();
        if (face == null || face == BlockFace.SELF) return;

        BlockBreakerManager.State state = manager.getOrCreate(blockKey,
                cb.getItemID(), player.getUniqueId());

        boolean wasActive = state.activeFaces.contains(face);
        if (wasActive) {
            state.activeFaces.remove(face);
            player.sendMessage("§e[Breaker] §7Face §f" + faceFr(face)
                    + "§7 désactivée. (" + state.activeFaces.size() + "/6)");
        } else {
            state.activeFaces.add(face);
            player.sendMessage("§a[Breaker] §7Face §f" + faceFr(face)
                    + "§7 activée. (" + state.activeFaces.size() + "/6)");
        }

        // Effect feedback.
        Location at = clicked.getLocation().add(0.5, 0.5, 0.5).add(
                face.getModX() * 0.5, face.getModY() * 0.5, face.getModZ() * 0.5);
        at.getWorld().spawnParticle(mech.particle(), at, 12, 0.15, 0.15, 0.15, 0.02);
        at.getWorld().playSound(at, mech.sound(), 0.8f, wasActive ? 0.8f : 1.2f);

        if (state.activeFaces.isEmpty()) {
            stopTaskIfRunning(state);
        } else if (state.task == null) {
            startBreakLoop(clicked, blockKey, mech);
        }
        saveAsync();
    }

    /** French name of a {@link BlockFace} for player-facing messages. */
    private static String faceFr(BlockFace face) {
        return switch (face) {
            case NORTH -> "Nord";
            case SOUTH -> "Sud";
            case EAST  -> "Est";
            case WEST  -> "Ouest";
            case UP    -> "Haut";
            case DOWN  -> "Bas";
            default    -> face.name();
        };
    }

    private boolean isActivator(ItemStack stack, BlockBreakerMechanic mech) {
        if (stack == null || stack.getType().isAir()) return false;
        String configured = mech.activatorItem();
        if (configured == null || configured.isEmpty()) return false;

        // Activator must be a Nexo item — vanilla Materials are intentionally rejected.
        String nexoId = NexoItems.idFromItem(stack);
        return nexoId != null && nexoId.equalsIgnoreCase(configured);
    }

    // ─── Break loop ───────────────────────────────────────────────────────

    private void startBreakLoop(Block breaker, String blockKey, BlockBreakerMechanic mech) {
        BlockBreakerManager.State state = manager.get(blockKey);
        if (state == null) return;

        int periodTicks = mech.breakIntervalSeconds() * 20;
        state.task = new BukkitRunnable() {
            @Override
            public void run() {
                BlockBreakerManager.State s = manager.get(blockKey);
                if (s == null) { cancel(); return; }

                // The breaker itself was removed?
                CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(breaker);
                if (cb == null || !state.nexoId.equals(cb.getItemID())) {
                    manager.remove(blockKey);
                    cancel();
                    return;
                }
                if (s.activeFaces.isEmpty()) {
                    s.task = null;
                    cancel();
                    return;
                }

                BlockBreakerUpgrade.Type effSpeed = null; // reserved for future use
                int range = Math.max(1, mech.breakDistance() + s.upgrades.getOrDefault(
                        BlockBreakerUpgrade.Type.RANGE, 0));

                for (BlockFace face : s.activeFaces) {
                    for (int d = 1; d <= range; d++) {
                        Block target = breaker.getRelative(face, d);
                        if (tryBreak(target, mech, s)) break;
                    }
                }
            }
        }.runTaskTimer(plugin, periodTicks, periodTicks);
    }

    private boolean tryBreak(Block target, BlockBreakerMechanic mech, BlockBreakerManager.State s) {
        Material type = target.getType();
        if (type.isAir()) return false;
        if (mech.isBlocked(type)) return false;
        if (!type.isSolid() && type != Material.WATER && type != Material.LAVA) return false;

        // Nexo custom_block protection.
        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(target);
        if (cb != null && mech.isBlocked(cb.getItemID())) return false;

        // Resolve a Player to attribute the break to so listeners (RoseLoot,
        // Nexo) see a normal player-driven break.
        Player owner = (s.ownerId != null) ? Bukkit.getPlayer(s.ownerId) : null;

        if (cb != null) {
            // Nexo block: route through NexoBlocks.remove(loc, player) so the
            // canonical NexoBlockBreakEvent fires. RoseLoot's listener then
            // runs the loot table (drops, set_block bedrock, restore).
            //
            // RoseLoot skips creative-mode players AND most loot tables for
            // Nexo ores condition on the player holding a real pickaxe via
            //   placeholder:%checkitem_mat:netherite_pickaxe,inhand:main%=yes
            // The breaker's owner is normally holding the activator wand (or
            // is in creative for testing). To make the loot table fire we
            // temporarily swap the owner's main hand to the breaker's
            // synthetic pickaxe (carrying the Fortune/Silk upgrades) and
            // restore the original item immediately after. Game-mode is left
            // untouched on purpose — survival is still required.
            if (owner == null) return false;

            // Fortune: each extra roll re-places the Nexo block and re-fires the
            // break so RoseLoot runs the loot table again from scratch. This is
            // what lets placeholders like %wp_random_generate% in the dropped
            // item's display-name be evaluated independently each time —
            // multiplying a stack would have given N copies sharing one value.
            //
            // Formula: extraRolls = random(0, fortune+1) inclusive.
            //   Fortune 1 → 0..2 extras (total 1..3 drops)
            //   Fortune 2 → 0..3 extras (total 1..4)
            //   Fortune 3 → 0..4 extras (total 1..5)
            // Silk Touch suppresses Fortune (mutually exclusive on the tool).
            int fortune = s.upgrades.getOrDefault(BlockBreakerUpgrade.Type.FORTUNE, 0);
            boolean silk = s.upgrades.getOrDefault(BlockBreakerUpgrade.Type.SILK_TOUCH, 0) > 0;
            int extraRolls = (silk || fortune <= 0) ? 0
                    : java.util.concurrent.ThreadLocalRandom.current().nextInt(fortune + 2);

            String brokenNexoId = cb.getItemID();
            Location targetLoc = target.getLocation();

            var inv = owner.getInventory();
            ItemStack originalMainHand = inv.getItemInMainHand();
            inv.setItemInMainHand(syntheticTool(s));
            boolean removed;
            try {
                removed = NexoBlocks.remove(targetLoc, owner);
                if (removed && extraRolls > 0 && brokenNexoId != null) {
                    for (int i = 0; i < extraRolls; i++) {
                        // Re-instate the Nexo block over whatever RoseLoot
                        // replaced it with (typically bedrock), then break it
                        // again. Each iteration triggers a fresh loot pass.
                        NexoBlocks.place(brokenNexoId, targetLoc);
                        NexoBlocks.remove(targetLoc, owner);
                    }
                }
            } finally {
                inv.setItemInMainHand(originalMainHand);
            }
            if (!removed) return false;
        } else {
            // Vanilla / modded block: fire BlockBreakEvent first so RoseLoot's
            // generic BlockListener (and any other plugin) sees the break, then
            // either let breakNaturally drop the items or just blank the block.
            if (owner != null) {
                BlockBreakEvent event = new BlockBreakEvent(target, owner);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return false;
            }
            if (mech.dropAsItems()) {
                target.breakNaturally(syntheticTool(s));
            } else {
                target.setType(Material.AIR, false);
            }
        }

        target.getWorld().spawnParticle(mech.particle(),
                target.getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.02);
        return true;
    }

    private ItemStack syntheticTool(BlockBreakerManager.State s) {
        ItemStack tool = new ItemStack(Material.NETHERITE_PICKAXE);
        int fortune = s.upgrades.getOrDefault(BlockBreakerUpgrade.Type.FORTUNE, 0);
        boolean silk = s.upgrades.getOrDefault(BlockBreakerUpgrade.Type.SILK_TOUCH, 0) > 0;
        if (silk) {
            tool.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1);
        } else if (fortune > 0) {
            tool.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE, fortune);
        }
        return tool;
    }

    private void stopTaskIfRunning(BlockBreakerManager.State state) {
        if (state.task != null) {
            try { state.task.cancel(); } catch (Throwable ignored) {}
            state.task = null;
        }
    }

    // ─── Upgrade menu ─────────────────────────────────────────────────────

    private void openUpgradeMenu(Player player, String blockKey, BlockBreakerMechanic mech) {
        BlockBreakerManager.State state = manager.getOrCreate(blockKey,
                mechIdForKey(blockKey, mech), player.getUniqueId());
        BlockBreakerUpgradeMenu menu = new BlockBreakerUpgradeMenu(blockKey, mech, plugin);
        menu.open(player, state.upgradeItems);
    }

    private String mechIdForKey(String blockKey, BlockBreakerMechanic mech) {
        // We don't carry the nexoId in the BlockBreakerMechanic itself; if the
        // state is created here, we'll resync the id the next time the loop
        // looks at the world — this is a best-effort placeholder.
        return mech.getItemID() != null ? mech.getItemID() : "block_breaker";
    }

    /** Called by the menu listener when the player clicks WCE_VALIDATION. */
    public void applyValidation(BlockBreakerUpgradeMenu menu, Player player) {
        BlockBreakerManager.State state = manager.get(menu.blockKey());
        if (state == null) {
            player.closeInventory();
            return;
        }

        ItemStack[] current = menu.readCurrentUpgrades();
        EnumMap<BlockBreakerUpgrade.Type, Integer> applied = new EnumMap<>(BlockBreakerUpgrade.Type.class);

        for (ItemStack stack : current) {
            BlockBreakerUpgrade up = BlockBreakerUpgrade.fromItem(plugin, stack);
            if (up == null || up.isUnknown()) continue;
            applied.merge(up.type(), up.value(), Integer::sum);
        }

        state.upgrades.clear();
        state.upgrades.putAll(applied);
        // Snapshot the items so re-opening the menu shows them in place.
        for (int i = 0; i < state.upgradeItems.length; i++) {
            state.upgradeItems[i] = (i < current.length) ? current[i] : null;
        }
        // The items are now "consumed" by the breaker — clear the menu slots so
        // closing doesn't return them.
        menu.clearUpgradeSlots();

        StringBuilder summary = new StringBuilder();
        applied.forEach((t, v) -> summary.append(' ').append('§').append('a')
                .append(t.name().toLowerCase(Locale.ROOT)).append('§').append('7')
                .append('=').append(v));
        player.sendMessage("§a[Breaker] §7Upgrades appliquées:" + (summary.length() == 0 ? " (aucune)" : summary));
        player.closeInventory();
        saveAsync();
    }

    /** Returns items left in the menu when the player closes it without validating. */
    public void returnLeftoverItems(BlockBreakerUpgradeMenu menu, Player player) {
        Inventory inv = menu.getInventory();
        if (inv == null) return;
        for (int slot : menu.upgradeSlots()) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(slot, null);
            var leftovers = player.getInventory().addItem(it);
            for (ItemStack drop : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    // ─── Cleanup on break ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(NexoBlockBreakEvent event) {
        Block b = event.getBlock();
        if (b == null) return;
        if (manager.remove(BlockBreakerManager.keyOf(b.getLocation())) != null) {
            saveAsync();
        }
    }

    /**
     * Fortune multiplier hook: RoseLoot drops its loot table items via the
     * world drop API which fires {@link org.bukkit.event.entity.ItemSpawnEvent}.
     * If the spawning location matches a breaker block currently in
     * {@link #pendingFortune}, we inflate the dropped stack by a random number
     * of extra units. Formula: extra = random(0, fortune+1) inclusive —
     * Fortune 1 → 0..2 extra, Fortune 2 → 0..3 extra, Fortune 3 → 0..4 extra.
     *
     * <p>Mutating the existing ItemStack's amount (rather than spawning a
     * second Item entity) avoids recursive ItemSpawnEvent calls and keeps
     * the drop visually consistent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        if (pendingFortune.isEmpty()) return;
        org.bukkit.entity.Item item = event.getEntity();
        Location loc = item.getLocation();
        String key = BlockBreakerManager.keyOf(loc.getBlock().getLocation());
        Integer fortune = pendingFortune.get(key);
        if (fortune == null || fortune <= 0) return;

        int extra = java.util.concurrent.ThreadLocalRandom.current().nextInt(fortune + 2);
        if (extra <= 0) return;
        ItemStack stack = item.getItemStack();
        stack.setAmount(stack.getAmount() + extra);
        item.setItemStack(stack);
    }

    public void shutdown() {
        // Synchronous final save so we don't lose the latest state.
        store.saveAll(manager.states());
        manager.shutdown();
    }
}