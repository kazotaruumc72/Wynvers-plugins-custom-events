package com.wynvers.customevents.nexo.slipthrough;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Polls item entities and teleports them through any block whose Nexo ID is
 * registered as slip-through. Runs every 2 ticks (10 Hz) — short-circuits when
 * the registry is empty so the cost is negligible until at least one item uses
 * the flag.
 *
 * <p>An item resting on top of a block sits at {@code y ≈ block.y + 1.0} —
 * checking the block at {@code item.y - 0.1} catches the "on top" case, and
 * the block at {@code item.y} catches the "inside the hitbox" case (e.g.
 * spawned mid-block).
 */
public class SlipThroughListener implements Listener {

    private final JavaPlugin plugin;
    private final SlipThroughLoader loader;
    private BukkitTask task;

    public SlipThroughListener(JavaPlugin plugin, SlipThroughLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    private void tick() {
        if (loader.isEmpty()) return;
        for (World w : Bukkit.getWorlds()) {
            for (Item item : w.getEntitiesByClass(Item.class)) {
                if (!item.isValid() || item.isDead()) continue;
                processItem(item);
            }
        }
    }

    private void processItem(Item item) {
        Location loc = item.getLocation();
        Block at = loc.getBlock();
        Block below = loc.clone().subtract(0, 0.1, 0).getBlock();

        Block slip = isSlip(at) ? at : (isSlip(below) ? below : null);
        if (slip == null) return;

        // Drop the item just under the slip block so its Y is below the hitbox;
        // gravity then continues naturally through any further leaves underneath.
        Location target = new Location(
                slip.getWorld(),
                slip.getX() + 0.5,
                slip.getY() - 0.05,
                slip.getZ() + 0.5);

        Vector vel = item.getVelocity();
        item.teleport(target);
        // Preserve horizontal velocity, force a downward fall so it doesn't sit
        // on whatever is immediately below.
        item.setVelocity(new Vector(vel.getX(), Math.min(vel.getY(), -0.1), vel.getZ()));
    }

    private boolean isSlip(Block b) {
        if (b == null) return false;
        CustomBlockMechanic mech = NexoBlocks.customBlockMechanic(b);
        return mech != null && loader.isSlipThrough(mech.getItemID());
    }

    public void shutdown() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
    }
}