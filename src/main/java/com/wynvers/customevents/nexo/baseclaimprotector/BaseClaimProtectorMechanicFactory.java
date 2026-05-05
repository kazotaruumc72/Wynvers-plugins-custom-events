package com.wynvers.customevents.nexo.baseclaimprotector;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import com.wynvers.customevents.integration.SaberFactionsHook;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nexo {@link MechanicFactory} for the {@code base_claim_protector} mechanic.
 *
 * <p>This implementation targets Nexo's <b>{@code custom_block}</b> mechanic
 * (NOTEBLOCK variant). The protector is a real world block, not a furniture
 * entity — so it's tracked by its block coordinates rather than an entity UUID.
 *
 * <ul>
 *   <li>On placement: validates the player has a faction and the block sits
 *       in the centroid chunk of that faction's claims (reflection on
 *       SaberFactions / FactionsUUID).</li>
 *   <li>While placed: polls for {@link Item} entities of the configured Nexo
 *       food on top of the block and consumes them. When a recipe's amount is
 *       fully reached, every wilderness chunk in {@code radius} is claimed
 *       for the placer's faction <b>without checking power</b>, and the bonus
 *       stays active for the recipe's {@code time}.</li>
 *   <li>A {@link TextDisplay} hologram floats above the block during the
 *       active phase showing the remaining time (mm:ss / h:mm:ss).</li>
 *   <li>On expiry: releases the previously bonus-claimed chunks (only those),
 *       then either deletes the block (when {@code consume_on_expire}) or
 *       swaps it to the configured depleted Nexo custom_block to mark "no
 *       more food".</li>
 * </ul>
 */
public class BaseClaimProtectorMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "base_claim_protector";

    private static BaseClaimProtectorMechanicFactory instance;

    private final JavaPlugin plugin;
    /** Block-key (worldUid:x:y:z) → state. */
    private final Map<String, ActiveProtector> active = new ConcurrentHashMap<>();
    /** Chunk-key (worldUid:cx:cz) → block-key of the protector that owns the bonus claim. */
    private final Map<String, String> bonusChunkIndex = new ConcurrentHashMap<>();
    /** Last chunk-key each player crossed into — debounces the title display. */
    private final Map<UUID, String> lastChunkKeyByPlayer = new ConcurrentHashMap<>();
    /** Block-keys we are about to swap/remove — suppress our own break-event handling. */
    private final Set<String> pendingSwap = ConcurrentHashMap.newKeySet();
    private BukkitTask feedTask;

    public BaseClaimProtectorMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Tick every 0.5s — drives food scan and hologram countdown text.
        this.feedTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public static BaseClaimProtectorMechanicFactory instance() { return instance; }

    @Override
    public @Nullable BaseClaimProtectorMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof BaseClaimProtectorMechanic p) ? p : null;
    }

    @Override
    public @Nullable BaseClaimProtectorMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof BaseClaimProtectorMechanic p) ? p : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        BaseClaimProtectorMechanic mechanic = new BaseClaimProtectorMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    public void shutdown() {
        if (feedTask != null) {
            try { feedTask.cancel(); } catch (Throwable ignored) {}
            feedTask = null;
        }
        for (ActiveProtector ap : active.values()) {
            removeHologram(ap);
        }
        active.clear();
        bonusChunkIndex.clear();
        lastChunkKeyByPlayer.clear();
        pendingSwap.clear();
    }

    private static String chunkKey(World world, int cx, int cz) {
        return world.getUID() + ":" + cx + ":" + cz;
    }

    private static String blockKey(Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    // ─── Placement (LOWEST: bypass SaberFactions territorial protection) ───

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        BaseClaimProtectorMechanic protector = getMechanic(item);
        if (protector == null) return;

        Player player = event.getPlayer();
        BlockFace face = event.getBlockFace();
        Block targetBlock = clicked.getRelative(face);
        Location targetLoc = targetBlock.getLocation();

        if (!SaberFactionsHook.isAvailable()) {
            event.setCancelled(true);
            player.sendMessage("§c[Protecteur] L'API Factions est indisponible — placement refusé.");
            return;
        }

        if (SaberFactionsHook.getPlayerFaction(player) == null) {
            event.setCancelled(true);
            player.sendMessage("§c[Protecteur] Tu dois appartenir à une faction.");
            return;
        }

        if (!SaberFactionsHook.isAtFactionBaseCenter(player, targetLoc)) {
            event.setCancelled(true);
            int[] center = SaberFactionsHook.getFactionCenterChunk(player);
            if (center == null) {
                player.sendMessage("§c[Protecteur] Ta faction n'a aucun claim — pose un claim d'abord.");
            } else {
                player.sendMessage("§c[Protecteur] Doit être posé au §ecentre§c de la base "
                        + "(chunk §f" + center[0] + ", " + center[1] + "§c).");
            }
            return;
        }

        if (!targetBlock.getType().isAir()) {
            event.setCancelled(true);
            player.sendMessage("§c[Protecteur] Le bloc ciblé n'est pas vide.");
            return;
        }

        // Take control: cancel so SaberFactions / Nexo's regular flow don't process it.
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);

        String itemId = NexoItems.idFromItem(item);
        if (itemId == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            NexoBlocks.place(itemId, targetLoc);

            // Verify the block was actually placed as the expected Nexo custom_block.
            CustomBlockMechanic placed = NexoBlocks.customBlockMechanic(targetBlock);
            if (placed == null || !itemId.equals(placed.getItemID())) {
                player.sendMessage("§c[Protecteur] Échec de placement.");
                return;
            }

            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                hand.setAmount(hand.getAmount() - 1);
            }
            register(protector, targetBlock, player, itemId);
            player.sendMessage("§a[Protecteur] Placé. Jette la nourriture Nexo sur le bloc pour activer le claim.");
            announceRecipes(protector, player);
        });
    }

    private void register(BaseClaimProtectorMechanic protector,
                          Block block,
                          Player owner,
                          String activeNexoId) {
        Map<String, Integer> fed = new LinkedHashMap<>();
        for (String id : protector.recipes().keySet()) fed.put(id, 0);
        ActiveProtector ap = new ActiveProtector(
                blockKey(block.getLocation()),
                owner.getUniqueId(),
                block.getLocation().clone(),
                protector,
                activeNexoId,
                fed);
        active.put(ap.blockKey, ap);
    }

    private void announceRecipes(BaseClaimProtectorMechanic protector, Player to) {
        if (protector.recipes().isEmpty()) {
            to.sendMessage("§7[Protecteur] (aucune recette définie)");
            return;
        }
        to.sendMessage("§7[Protecteur] Recettes acceptées :");
        for (BaseClaimProtectorMechanic.FoodRecipe r : protector.recipes().values()) {
            to.sendMessage("  §8• §f" + r.amount + "× §enexo:" + r.nexoId
                    + " §7→ §a" + formatDuration(r.durationTicks));
        }
    }

    // ─── Cleanup on block break ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(NexoBlockBreakEvent event) {
        Block block = event.getBlock();
        if (block == null) return;
        String key = blockKey(block.getLocation());
        // Skip our own swap-induced break, if any.
        if (pendingSwap.remove(key)) return;
        ActiveProtector ap = active.remove(key);
        if (ap == null) return;
        // If broken while a bonus is active, release the bonus chunks to avoid
        // permanent free claims via "place + feed + break".
        if (ap.activeUntilMs > 0) {
            releaseBonusChunks(ap);
        }
        removeHologram(ap);
    }

    // ─── Enemy-entry title (fade) ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (bonusChunkIndex.isEmpty()) return;
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        Location from = event.getFrom();
        // Fast-path: only react on chunk transitions.
        if (from.getWorld() == to.getWorld()
                && (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }

        Player player = event.getPlayer();
        int cx = to.getBlockX() >> 4;
        int cz = to.getBlockZ() >> 4;
        String key = chunkKey(to.getWorld(), cx, cz);

        // De-bounce: only fire once per cross into the same chunk.
        String previous = lastChunkKeyByPlayer.put(player.getUniqueId(), key);
        if (key.equals(previous)) return;

        if (!bonusChunkIndex.containsKey(key)) return;

        // Only show the title to ENEMIES of the chunk's owning faction.
        if (!SaberFactionsHook.isInEnemyClaim(player, to)) return;

        String tag = SaberFactionsHook.getFactionTagAt(to);
        if (tag == null || tag.isBlank()) return;

        // Title with fade-in / stay / fade-out (10t / 40t / 10t).
        player.sendTitle("§6Base claim", "§fde la §e" + tag, 10, 40, 10);
    }

    // ─── Periodic loop ─────────────────────────────────────────────────────

    private void tick() {
        if (active.isEmpty()) return;
        long nowMs = System.currentTimeMillis();
        for (ActiveProtector ap : active.values()) {
            tickProtector(ap, nowMs);
        }
    }

    private void tickProtector(ActiveProtector ap, long nowMs) {
        World world = ap.location.getWorld();
        if (world == null) return;
        if (!world.isChunkLoaded(ap.location.getBlockX() >> 4, ap.location.getBlockZ() >> 4)) {
            // Don't force-load — wait until someone is nearby.
            return;
        }

        Block block = ap.location.getBlock();
        CustomBlockMechanic mech = NexoBlocks.customBlockMechanic(block);
        if (mech == null || !ap.activeNexoId.equals(mech.getItemID())) {
            // The protector block was removed externally (explosion, replaced,
            // /setblock, etc.) without firing our break listener — drop state
            // and bonus.
            if (ap.activeUntilMs > 0) releaseBonusChunks(ap);
            removeHologram(ap);
            active.remove(ap.blockKey);
            return;
        }

        // Active phase — only update hologram + check expiry.
        if (ap.activeUntilMs > 0) {
            updateHologram(ap, nowMs);
            if (nowMs >= ap.activeUntilMs) {
                expire(ap);
            }
            return;
        }

        // Charging phase — scan for thrown food on top.
        scanForFood(ap, world);
    }

    private void scanForFood(ActiveProtector ap, World world) {
        if (ap.mechanic.recipes().isEmpty()) return;

        double r = ap.mechanic.feedRadius();
        Location feedPoint = ap.location.clone().add(0.5, 1.0, 0.5);

        for (Entity e : world.getNearbyEntities(feedPoint, r, r + 0.5, r)) {
            if (!(e instanceof Item itemEntity)) continue;
            if (!itemEntity.isValid() || itemEntity.isDead()) continue;
            ItemStack stack = itemEntity.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;

            String nexoId = NexoItems.idFromItem(stack);
            if (nexoId == null) continue;             // vanilla item — ignored
            BaseClaimProtectorMechanic.FoodRecipe recipe = ap.mechanic.recipes().get(nexoId);
            if (recipe == null) continue;             // Nexo item but not in our recipes

            int alreadyFed = ap.fedAmount.getOrDefault(nexoId, 0);
            int needed = Math.max(0, recipe.amount - alreadyFed);
            if (needed <= 0) continue;

            int available = stack.getAmount();
            int consumed = Math.min(available, needed);
            ap.fedAmount.put(nexoId, alreadyFed + consumed);

            if (consumed >= available) {
                itemEntity.remove();
            } else {
                stack.setAmount(available - consumed);
                itemEntity.setItemStack(stack);
            }

            world.spawnParticle(ap.mechanic.particle(), feedPoint, 12, 0.3, 0.3, 0.3, 0.02);
            world.playSound(feedPoint, ap.mechanic.feedSound(), 1.0f, 1.4f);

            Player owner = Bukkit.getPlayer(ap.ownerId);
            if (owner != null && owner.isOnline()) {
                int total = alreadyFed + consumed;
                owner.sendMessage("§a[Protecteur] §fnexo:" + nexoId
                        + " §7" + total + "/" + recipe.amount);
            }

            if (alreadyFed + consumed >= recipe.amount) {
                triggerClaim(ap, recipe);
                return; // active phase started; stop scanning this tick
            }
        }
    }

    // ─── Auto-claim (no power check) ───────────────────────────────────────

    private void triggerClaim(ActiveProtector ap, BaseClaimProtectorMechanic.FoodRecipe recipe) {
        Player owner = Bukkit.getPlayer(ap.ownerId);
        World world = ap.location.getWorld();
        if (world == null || owner == null || !owner.isOnline()) return;

        int centerChunkX = ap.location.getBlockX() >> 4;
        int centerChunkZ = ap.location.getBlockZ() >> 4;
        int r = ap.mechanic.radius();

        ap.bonusChunks.clear();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                if (!SaberFactionsHook.isChunkWilderness(world, cx, cz)) continue;
                if (SaberFactionsHook.setFactionAtChunk(owner, world, cx, cz)) {
                    ap.bonusChunks.add(new long[]{cx, cz});
                    bonusChunkIndex.put(chunkKey(world, cx, cz), ap.blockKey);
                }
            }
        }

        long nowMs = System.currentTimeMillis();
        ap.activeUntilMs = nowMs + recipe.durationTicks * 50L;
        // Reset the fed counters so a second activation requires re-feeding.
        for (String id : ap.fedAmount.keySet()) ap.fedAmount.put(id, 0);

        Location feedPoint = ap.location.clone().add(0.5, 1.0, 0.5);
        world.playSound(feedPoint, ap.mechanic.claimSound(), 1.5f, 1.0f);
        world.spawnParticle(ap.mechanic.particle(), feedPoint, 60, 1.5, 1.5, 1.5, 0.05);

        owner.sendMessage("§a§l[Protecteur] §a" + ap.bonusChunks.size()
                + " chunk(s) revendiqués §7(" + formatDuration(recipe.durationTicks) + ")");

        spawnHologram(ap, world);
        updateHologram(ap, nowMs);
    }

    // ─── Expiry ────────────────────────────────────────────────────────────

    private void expire(ActiveProtector ap) {
        World world = ap.location.getWorld();
        if (world == null) {
            active.remove(ap.blockKey);
            return;
        }

        releaseBonusChunks(ap);
        ap.activeUntilMs = 0;
        removeHologram(ap);

        Location feedPoint = ap.location.clone().add(0.5, 1.0, 0.5);
        world.playSound(feedPoint, ap.mechanic.expireSound(), 1.0f, 0.8f);

        Player owner = Bukkit.getPlayer(ap.ownerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§e[Protecteur] §fLe bonus a expiré, les chunks ont été libérés.");
        }

        Block block = ap.location.getBlock();

        if (ap.mechanic.consumeOnExpire()) {
            // Mark the upcoming break so we don't re-process our own removal.
            pendingSwap.add(ap.blockKey);
            block.setType(Material.AIR);
            active.remove(ap.blockKey);
            // Defensive cleanup — pendingSwap entry stays at most one tick.
            Bukkit.getScheduler().runTask(plugin, () -> pendingSwap.remove(ap.blockKey));
            return;
        }

        // Texture swap: replace the active block with the depleted custom_block
        // so it visually marks "no more food".
        String depleted = ap.mechanic.depletedNexoId();
        if (depleted == null || depleted.isBlank()) {
            // Leave the active block in place; just drop our state.
            active.remove(ap.blockKey);
            return;
        }

        pendingSwap.add(ap.blockKey);
        block.setType(Material.AIR);
        NexoBlocks.place(depleted, ap.location);

        CustomBlockMechanic swapped = NexoBlocks.customBlockMechanic(ap.location.getBlock());
        boolean ok = swapped != null && depleted.equals(swapped.getItemID());
        active.remove(ap.blockKey);
        Bukkit.getScheduler().runTask(plugin, () -> pendingSwap.remove(ap.blockKey));

        if (!ok && owner != null && owner.isOnline()) {
            owner.sendMessage("§7[Protecteur] (échec du swap de texture — variante '" + depleted + "' manquante)");
        }
        // We deliberately don't re-register: the depleted block has no mechanic.
    }

    private void releaseBonusChunks(ActiveProtector ap) {
        World world = ap.location.getWorld();
        if (world == null || ap.bonusChunks.isEmpty()) {
            ap.bonusChunks.clear();
            return;
        }
        for (long[] coords : ap.bonusChunks) {
            int cx = (int) coords[0];
            int cz = (int) coords[1];
            SaberFactionsHook.unclaimChunk(world, cx, cz);
            bonusChunkIndex.remove(chunkKey(world, cx, cz));
        }
        ap.bonusChunks.clear();
    }

    // ─── Hologram ──────────────────────────────────────────────────────────

    private void spawnHologram(ActiveProtector ap, World world) {
        removeHologram(ap);
        Location holoLoc = ap.location.clone().add(0.5, 1.6, 0.5);
        TextDisplay td = world.spawn(holoLoc, TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setSeeThrough(false);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            t.setPersistent(false);
        });
        ap.hologramId = td.getUniqueId();
    }

    private void updateHologram(ActiveProtector ap, long nowMs) {
        if (ap.hologramId == null) return;
        Entity e = Bukkit.getEntity(ap.hologramId);
        if (!(e instanceof TextDisplay td)) {
            ap.hologramId = null;
            return;
        }
        long remainingMs = Math.max(0, ap.activeUntilMs - nowMs);
        td.setText("§a§lProtecteur §7| §f" + formatDuration(remainingMs / 50L));
    }

    private void removeHologram(ActiveProtector ap) {
        if (ap.hologramId == null) return;
        Entity e = Bukkit.getEntity(ap.hologramId);
        if (e != null) {
            try { e.remove(); } catch (Throwable ignored) {}
        }
        ap.hologramId = null;
    }

    private static String formatDuration(long ticks) {
        long totalSeconds = Math.max(0, ticks / 20L);
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    // ─── State holder ──────────────────────────────────────────────────────

    private static final class ActiveProtector {
        /** Block-key (worldUid:x:y:z) — stable identifier of the protector block. */
        final String blockKey;
        final UUID ownerId;
        /** Block location (block-aligned, snapped to integer coords). */
        final Location location;
        final BaseClaimProtectorMechanic mechanic;
        final String activeNexoId;
        /** Per-recipe accumulated count (key = Nexo id). */
        final Map<String, Integer> fedAmount;
        /** Chunks claimed by the active bonus, to be released on expiry. */
        final List<long[]> bonusChunks = new ArrayList<>();
        /** Wall-clock ms at which the active bonus expires (0 = not active). */
        volatile long activeUntilMs = 0;
        /** TextDisplay hologram entity id — null while idle. */
        volatile UUID hologramId;

        ActiveProtector(String blockKey,
                        UUID ownerId,
                        Location location,
                        BaseClaimProtectorMechanic mechanic,
                        String activeNexoId,
                        Map<String, Integer> fedAmount) {
            this.blockKey = blockKey;
            this.ownerId = ownerId;
            this.location = location;
            this.mechanic = mechanic;
            this.activeNexoId = activeNexoId;
            this.fedAmount = new HashMap<>(fedAmount);
        }
    }
}