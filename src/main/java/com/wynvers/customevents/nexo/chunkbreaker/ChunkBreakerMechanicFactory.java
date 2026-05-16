package com.wynvers.customevents.nexo.chunkbreaker;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.events.custom_block.NexoBlockPlaceEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Nexo {@link MechanicFactory} for the {@code chunk_breaker} mechanic.
 *
 * <p>Lifecycle (one-shot, consumed at placement):
 * <ol>
 *   <li>Player places the ChunkBreaker → {@link NexoBlockPlaceEvent} fires.</li>
 *   <li>The placed block is cleared next tick so it doesn't show up as a
 *       loose ChunkBreaker the player could pick up.</li>
 *   <li>A batched task walks the chunk from world max-height down to
 *       world min-height, processing {@code batch_y_slices} layers per
 *       tick. For each non-air, non-bedrock, non-blacklisted block:
 *       vanilla blocks contribute drops via {@code getDrops(syntheticTool)}
 *       and are set to AIR; Nexo blocks are removed via
 *       {@code NexoBlocks.remove} so RoseLoot loot tables fire.</li>
 *   <li>Once the bottom is reached, every loose {@link Item} entity in
 *       the chunk (that wasn't there before the operation) is vacuumed and
 *       all collected drops are re-spawned, stacked at the chunk's centre
 *       on top of the highest bedrock block.</li>
 * </ol>
 */
public class ChunkBreakerMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "chunk_breaker";

    private static ChunkBreakerMechanicFactory instance;

    private final JavaPlugin plugin;

    public ChunkBreakerMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static ChunkBreakerMechanicFactory instance() { return instance; }

    @Override
    public @Nullable ChunkBreakerMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof ChunkBreakerMechanic c) ? c : null;
    }

    @Override
    public @Nullable ChunkBreakerMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof ChunkBreakerMechanic c) ? c : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        ChunkBreakerMechanic mechanic = new ChunkBreakerMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    // ─── Placement triggers the raze ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNexoBlockPlace(NexoBlockPlaceEvent event) {
        String nexoId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        ChunkBreakerMechanic mech = getMechanic(nexoId);
        if (mech == null) return;

        Player player = event.getPlayer();
        Block placedBlock = event.getBlock();
        Chunk chunk = placedBlock.getChunk();
        World world = chunk.getWorld();
        UUID ownerId = player.getUniqueId();

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        Location announceLoc = new Location(world,
                baseX + 8.5, placedBlock.getY() + 0.5, baseZ + 8.5);

        world.spawnParticle(mech.startParticle(), announceLoc, 1, 0, 0, 0, 0);
        world.playSound(announceLoc, mech.startSound(), 1.5f, 0.6f);
        player.sendMessage("§5[ChunkBreaker] §fMinage du chunk en cours…");

        // Snapshot existing Item UUIDs so we don't vacuum loose items that
        // were already in the chunk before the operation started.
        Set<UUID> preExistingItemIds = new HashSet<>();
        for (Entity e : chunk.getEntities()) {
            if (e instanceof Item) preExistingItemIds.add(e.getUniqueId());
        }

        // Defer one tick so the placed block is fully registered, then remove
        // the ChunkBreaker block itself (no drop) and kick off the loop.
        Bukkit.getScheduler().runTask(plugin, () -> {
            placedBlock.setType(Material.AIR, false);
            runBatchedBreak(world, chunk, mech, ownerId, preExistingItemIds);
        });
    }

    // ─── Batched chunk break ─────────────────────────────────────────────────

    private void runBatchedBreak(World world, Chunk chunk, ChunkBreakerMechanic mech,
                                 UUID ownerId, Set<UUID> preExistingItemIds) {
        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight() - 1;
        final int slicePerTick = mech.batchYSlices();
        final int baseX = chunk.getX() << 4;
        final int baseZ = chunk.getZ() << 4;

        final List<ItemStack> collected = new ArrayList<>();
        final ItemStack tool = makeTool(mech);

        // Mutable state captured by the runnable.
        final int[] currentY = { maxY };
        final int[] highestBedrockY = { Integer.MIN_VALUE };

        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (processed < slicePerTick && currentY[0] >= minY) {
                    int y = currentY[0];
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            processBlock(world, baseX + dx, y, baseZ + dz,
                                    mech, tool, ownerId, collected, highestBedrockY);
                        }
                    }
                    currentY[0]--;
                    processed++;
                }
                if (currentY[0] < minY) {
                    dropAndCleanup(world, chunk, mech, collected,
                            highestBedrockY[0], baseX, baseZ, preExistingItemIds, ownerId);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void processBlock(World world, int x, int y, int z,
                              ChunkBreakerMechanic mech, ItemStack tool, UUID ownerId,
                              List<ItemStack> collected, int[] highestBedrockY) {
        Block target = world.getBlockAt(x, y, z);
        Material type = target.getType();
        if (type.isAir()) return;

        if (type == Material.BEDROCK) {
            if (y > highestBedrockY[0]) highestBedrockY[0] = y;
            return;
        }
        if (mech.isBlocked(type)) return;

        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(target);
        if (cb != null && mech.isBlocked(cb.getItemID())) return;

        if (cb != null) {
            // Nexo custom_block — fire the canonical event so RoseLoot's loot
            // tables run. Drops will appear at the block location; they're
            // swept and re-positioned at the end.
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null) {
                try {
                    NexoBlocks.remove(target.getLocation(), owner);
                    return;
                } catch (Throwable t) {
                    // Fallthrough to setType.
                }
            }
            target.setType(Material.AIR, false);
            return;
        }

        if (type == Material.WATER || type == Material.LAVA) {
            target.setType(Material.AIR, false);
            return;
        }

        // Vanilla / modded solid block: collect drops, then clear.
        for (ItemStack drop : target.getDrops(tool)) {
            if (drop != null && !drop.getType().isAir()) collected.add(drop);
        }
        target.setType(Material.AIR, false);
    }

    private void dropAndCleanup(World world, Chunk chunk, ChunkBreakerMechanic mech,
                                List<ItemStack> collected, int bedrockTopY,
                                int baseX, int baseZ, Set<UUID> preExistingItemIds,
                                UUID ownerId) {
        int dropY = (bedrockTopY == Integer.MIN_VALUE) ? world.getMinHeight() + 1 : bedrockTopY + 1;
        Location dropLoc = new Location(world, baseX + 8.5, dropY + 0.5, baseZ + 8.5);

        // Sweep loose Item entities that weren't present before the operation
        // (drops produced by NexoBlocks.remove).
        for (Entity e : chunk.getEntities()) {
            if (!(e instanceof Item item)) continue;
            if (preExistingItemIds.contains(item.getUniqueId())) continue;
            if (!item.isValid() || item.isDead()) continue;
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;
            collected.add(stack);
            item.remove();
        }

        for (ItemStack stack : collected) {
            if (stack == null || stack.getType().isAir()) continue;
            world.dropItem(dropLoc, stack);
        }

        world.spawnParticle(mech.startParticle(), dropLoc.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
        world.playSound(dropLoc, mech.finishSound(), 1.5f, 1.0f);

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§5[ChunkBreaker] §fChunk miné. §7Drops déposés sur la bedrock §8("
                    + collected.size() + " stacks§8)");
        }
    }

    private ItemStack makeTool(ChunkBreakerMechanic mech) {
        ItemStack tool = new ItemStack(Material.NETHERITE_PICKAXE);
        if (mech.silkTouch()) {
            tool.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        } else if (mech.fortuneLevel() > 0) {
            tool.addUnsafeEnchantment(Enchantment.FORTUNE, mech.fortuneLevel());
        }
        return tool;
    }
}