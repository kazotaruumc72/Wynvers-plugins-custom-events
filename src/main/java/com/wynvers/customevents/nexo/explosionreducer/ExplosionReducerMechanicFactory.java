package com.wynvers.customevents.nexo.explosionreducer;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Nexo {@link MechanicFactory} for the {@code explosion_reducer} mechanic.
 *
 * <p>Tracks how many non-player hits each custom block has accumulated, and
 * cancels {@link NexoBlockBreakEvent} until the configured threshold is
 * reached. Player-driven breaks (mining) bypass the reducer entirely and
 * clear any pending damage.
 *
 * <p>Damage state is in-memory: cleared on plugin disable / server restart.
 */
public class ExplosionReducerMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "explosion_reducer";

    private static ExplosionReducerMechanicFactory instance;
    private final JavaPlugin plugin;

    /** Per-block accumulated damage points (0..99). 100+ → break allowed. */
    private final Map<Location, Integer> damageMap = new HashMap<>();

    public ExplosionReducerMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static ExplosionReducerMechanicFactory instance() { return instance; }

    @Override
    public @Nullable ExplosionReducerMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof ExplosionReducerMechanic r) ? r : null;
    }

    @Override
    public @Nullable ExplosionReducerMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof ExplosionReducerMechanic r) ? r : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        ExplosionReducerMechanic mechanic = new ExplosionReducerMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNexoBlockBreak(NexoBlockBreakEvent event) {
        Block block = event.getBlock();
        CustomBlockMechanic blockMech = NexoBlocks.customBlockMechanic(block);
        if (blockMech == null) return;

        ExplosionReducerMechanic reducer = getMechanic(blockMech.getItemID());
        if (reducer == null) return;

        Location key = block.getLocation();

        // Player mining: bypass the reducer entirely and clear any history.
        if (event.getPlayer() != null) {
            damageMap.remove(key);
            return;
        }

        int hp = damageMap.getOrDefault(key, 0) + reducer.damagePerHit();
        if (hp >= 100) {
            damageMap.remove(key);
            return; // allow break
        }
        damageMap.put(key, hp);
        event.setCancelled(true);

        // Visual feedback: cracks-like effect at the surviving block.
        Location at = key.clone().add(0.5, 0.5, 0.5);
        if (at.getWorld() != null) {
            at.getWorld().spawnParticle(Particle.DUST, at, 12,
                    0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(60, 60, 60), 1.4f));
            at.getWorld().playSound(at, Sound.BLOCK_DEEPSLATE_HIT, 1.0f, 0.6f);
        }
    }

    /** Clears in-memory damage state. Called on plugin disable. */
    public void shutdown() {
        damageMap.clear();
    }
}