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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
     * Per-player location of the last BlockBreaker the player interacted with
     * (right-click, left-click, shift+right-click). Used by the
     * {@code %wce_blockbreaker_closest%} placeholder to scope its scan to the
     * "current" breaker. Cleared lazily when the entry no longer resolves to
     * a registered BlockBreaker.
     */
    private final Map<UUID, Location> lastInteracted = new ConcurrentHashMap<>();

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
     * Returns the location of the last BlockBreaker the given player
     * interacted with, or {@code null} if the player never interacted with
     * one or the cached location no longer resolves to a BlockBreaker
     * (block was broken, world unloaded, etc.). Self-healing: stale entries
     * are evicted on read.
     */
    public @Nullable Location lastInteractedFor(UUID playerId) {
        Location loc = lastInteracted.get(playerId);
        if (loc == null) return null;
        if (loc.getWorld() == null) {
            lastInteracted.remove(playerId);
            return null;
        }
        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(loc.getBlock());
        if (cb == null || getMechanic(cb.getItemID()) == null) {
            lastInteracted.remove(playerId);
            return null;
        }
        return loc;
    }

    /**
     * Returns every currently registered BlockBreaker location owned by
     * {@code ownerId}. Entries whose chunk is unloaded, whose world is
     * gone, or whose block no longer resolves to a BlockBreaker are
     * skipped (not evicted — the manager owns lifecycle there).
     *
     * <p>Used by the {@code %wce_blockbreakers_closest%} placeholder to
     * scan around every placed breaker of the requesting player.
     */
    public @NotNull List<Location> locationsOwnedBy(UUID ownerId) {
        List<Location> result = new ArrayList<>();
        for (var entry : manager.states().entrySet()) {
            BlockBreakerManager.State state = entry.getValue();
            if (state == null || state.ownerId == null) continue;
            if (!state.ownerId.equals(ownerId)) continue;
            String[] parts = entry.getKey().split(":");
            if (parts.length != 4) continue;
            try {
                var world = Bukkit.getWorld(UUID.fromString(parts[0]));
                if (world == null) continue;
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block b = world.getBlockAt(x, y, z);
                CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(b);
                if (cb == null || getMechanic(cb.getItemID()) == null) continue;
                result.add(b.getLocation());
            } catch (Exception ignored) {}
        }
        return result;
    }

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
            if (mech == null) continue;
            Block breakerBlock = resolveBlock(e.getKey());
            if (breakerBlock == null) continue;
            // Re-apply the texture variant in case the saved nexoId got out of
            // sync with the live block (e.g. world data restored from backup).
            applyVariant(breakerBlock, e.getValue(), mech);
            if (e.getValue().activeFaces.isEmpty()) continue;
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
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(clicked);
        if (cb == null) return;
        BlockBreakerMechanic mech = getMechanic(cb.getItemID());
        if (mech == null) return;

        Player player = event.getPlayer();
        String blockKey = BlockBreakerManager.keyOf(clicked.getLocation());
        // Record this interaction so %wce_blockbreaker_closest% knows which
        // breaker to scan for this player.
        lastInteracted.put(player.getUniqueId(), clicked.getLocation().clone());

        // Left-click with the activator → show status, no face toggle, no break.
        if (action == Action.LEFT_CLICK_BLOCK) {
            ItemStack inHand = event.getItem();
            if (!isActivator(inHand, mech)) return;
            event.setCancelled(true);
            sendStatus(player, manager.get(blockKey));
            return;
        }

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
        applyVariant(clicked, state, mech);
        saveAsync();
    }

    /**
     * If the breaker mechanic declares a {@code variant_base}, swap the
     * underlying Nexo block to the variant matching the current set of active
     * faces so the texture reflects each face's state.
     *
     * <p>The variant id is computed from {@link BlockBreakerMechanic#computeVariantId}.
     * If Nexo doesn't know that id (variant not defined by the admin), the
     * block is left as-is and a warning is logged.
     */
    private void applyVariant(Block block, BlockBreakerManager.State state, BlockBreakerMechanic mech) {
        if (!mech.hasVariants()) {
            plugin.getLogger().fine("[BlockBreaker] applyVariant: mechanic has no variants — skip.");
            return;
        }
        String desired = mech.computeVariantId(state.activeFaces);
        if (desired == null || desired.isEmpty()) {
            plugin.getLogger().warning("[BlockBreaker] applyVariant: computeVariantId returned null/empty "
                    + "(variant_base='" + mech.variantBase() + "', faces=" + state.activeFaces + ").");
            return;
        }

        CustomBlockMechanic current = NexoBlocks.customBlockMechanic(block);
        if (current != null && desired.equals(current.getItemID())) {
            plugin.getLogger().fine("[BlockBreaker] applyVariant: already on '" + desired + "', no swap.");
            return;
        }

        if (!NexoBlocks.isCustomBlock(desired)) {
            plugin.getLogger().warning("[BlockBreaker] Variant '" + desired
                    + "' is not registered as a Nexo custom_block. "
                    + "Make sure plugins/Nexo/items/<file>_breaker_variants.yml was generated and /nexo reload was run.");
            return;
        }

        // Resolve the BlockData of the target variant and apply it directly to
        // the block — non-destructive, no place-event, no item drops.
        org.bukkit.block.data.BlockData newData = NexoBlocks.blockData(desired);
        if (newData == null) {
            plugin.getLogger().warning("[BlockBreaker] NexoBlocks.blockData('" + desired
                    + "') returned null — cannot swap texture.");
            return;
        }
        block.setBlockData(newData, false);

        CustomBlockMechanic after = NexoBlocks.customBlockMechanic(block);
        if (after != null && desired.equals(after.getItemID())) {
            state.nexoId = desired;
            plugin.getLogger().info("[BlockBreaker] Swapped to variant '" + desired + "'.");
        } else {
            plugin.getLogger().warning("[BlockBreaker] Swap to '" + desired
                    + "' didn't take — block now resolves to '"
                    + (after == null ? "null" : after.getItemID()) + "'.");
        }
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

    /** French name of an upgrade type for player-facing messages. */
    private static String upgradeFr(BlockBreakerUpgrade.Type type) {
        return switch (type) {
            case FORTUNE     -> "Fortune";
            case SPEED       -> "Vitesse";
            case RANGE       -> "Portée";
            case SILK_TOUCH  -> "Toucher de Soie";
            case AUTO_SMELT  -> "Auto-Fusion";
            default          -> type.name();
        };
    }

    /**
     * Sends the breaker's current status (active faces + applied upgrades) to
     * the player as a chat message. Tolerates a {@code null} state (untouched
     * breaker — nothing toggled yet) by reporting empty lists.
     */
    private void sendStatus(Player player, BlockBreakerManager.State state) {
        player.sendMessage("§6═════ §eBlock Breaker §6═════");

        if (state == null || state.activeFaces.isEmpty()) {
            player.sendMessage("§7Faces actives : §caucune");
        } else {
            StringBuilder faces = new StringBuilder("§7Faces actives §8(§f")
                    .append(state.activeFaces.size()).append("§8/§f6§8)§7 : ");
            boolean first = true;
            for (BlockFace f : state.activeFaces) {
                if (!first) faces.append("§7, ");
                faces.append("§a").append(faceFr(f));
                first = false;
            }
            player.sendMessage(faces.toString());
        }

        if (state == null || state.upgrades.isEmpty()) {
            player.sendMessage("§7Upgrades : §caucune");
        } else {
            player.sendMessage("§7Upgrades :");
            for (var e : state.upgrades.entrySet()) {
                player.sendMessage("  §8• §a" + upgradeFr(e.getKey()) + " §7→ §f" + e.getValue());
            }
        }
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

                // The breaker itself was removed? Tolerate variant swaps —
                // accept any block whose id resolves to *some* BlockBreaker
                // mechanic (so a face-toggle swap to wynvers_block_breaker_n
                // doesn't kill the loop attached to wynvers_block_breaker).
                CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(breaker);
                if (cb == null || getMechanic(cb.getItemID()) == null) {
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
            } finally {
                inv.setItemInMainHand(originalMainHand);
            }
            if (!removed) return false;

            // Schedule the extra Fortune rolls one tick apart so each drop
            // arrives as its own Item entity (rather than stacking into one).
            //   Tick 0  → original drop
            //   Tick +1 → first extra roll
            //   Tick +2 → second extra roll … etc.
            // Player must still be online when each tick fires; otherwise the
            // remaining rolls are silently skipped.
            if (extraRolls > 0 && brokenNexoId != null) {
                final UUID ownerId = owner.getUniqueId();
                final BlockBreakerManager.State stateRef = s;
                for (int i = 1; i <= extraRolls; i++) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Player p = Bukkit.getPlayer(ownerId);
                        if (p == null) return;
                        var inv2 = p.getInventory();
                        ItemStack orig = inv2.getItemInMainHand();
                        inv2.setItemInMainHand(syntheticTool(stateRef));
                        try {
                            NexoBlocks.place(brokenNexoId, targetLoc);
                            NexoBlocks.remove(targetLoc, p);
                        } finally {
                            inv2.setItemInMainHand(orig);
                        }
                    }, i);
                }
            }
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


    public void shutdown() {
        // Synchronous final save so we don't lose the latest state.
        store.saveAll(manager.states());
        manager.shutdown();
    }
}