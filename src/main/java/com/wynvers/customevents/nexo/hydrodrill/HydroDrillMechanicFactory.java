package com.wynvers.customevents.nexo.hydrodrill;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.wynvers.customevents.integration.SaberFactionsHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Nexo {@link MechanicFactory} for the {@code hydro_drill} mechanic.
 *
 * <p>Self-contained: parses items, listens to placement / break / explosion
 * events, runs the countdown task, applies the cube effect, restores obsidian.
 */
public class HydroDrillMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "hydro_drill";

    private static HydroDrillMechanicFactory instance;

    private final JavaPlugin plugin;
    private final HydroDrillManager manager;

    public HydroDrillMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        this.manager = new HydroDrillManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static HydroDrillMechanicFactory instance() {
        return instance;
    }

    public HydroDrillManager manager() {
        return manager;
    }

    @Override
    public @Nullable HydroDrillMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof HydroDrillMechanic d) ? d : null;
    }

    @Override
    public @Nullable HydroDrillMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof HydroDrillMechanic d) ? d : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        HydroDrillMechanic mechanic = new HydroDrillMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    // ─── Placement ──────────────────────────────────────────────────────────

    /**
     * Runs at HIGHEST and does <strong>not</strong> ignore cancellation: this
     * lets us override SaberFactions' default block-placement protection that
     * cancels the event at NORMAL/HIGH priority — placing a drill in an enemy
     * claim is the whole point of the mechanic.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFurniturePlace(NexoFurniturePlaceEvent event) {
        FurnitureMechanic furn = event.getMechanic();
        if (furn == null) return;
        HydroDrillMechanic drill = getMechanic(furn.getItemID());
        if (drill == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        if (manager.isOnCooldown(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c[Foreuse] Cooldown : "
                    + manager.cooldownRemainingSeconds(player.getUniqueId()) + "s restantes.");
            return;
        }

        Block block = event.getBlock();
        if (block == null) return;

        if (SaberFactionsHook.isAvailable()
                && !SaberFactionsHook.isInEnemyClaim(player, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§c[Foreuse] Doit être placée dans un claim ennemi.");
            return;
        }

        // Override SaberFactions protection: enemy claim is where this belongs.
        if (event.isCancelled()) {
            event.setCancelled(false);
        }

        ItemDisplay base = event.getBaseEntity();
        if (base == null) return;

        manager.setCooldown(player.getUniqueId(), drill.cooldownSeconds());
        startCountdown(drill, base, player, block.getLocation());
    }

    private void startCountdown(HydroDrillMechanic drill,
                                ItemDisplay drillEntity,
                                Player placer,
                                Location center) {
        UUID drillId = drillEntity.getUniqueId();
        int totalSeconds = drill.countdownSeconds();

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!drillEntity.isValid()) {
                    cancel();
                    manager.removeDrill(drillId);
                    return;
                }

                int remaining = totalSeconds - elapsed;
                playCountdownEffects(drill, center, remaining);
                if (remaining > 0 && (remaining % 10 == 0 || remaining <= 5)) {
                    placer.sendMessage("§b[Foreuse] §fActive — " + remaining + "s");
                }

                if (elapsed >= totalSeconds) {
                    triggerExplosion(drill, center, drillId);
                    NexoFurniture.remove(drillEntity, null);
                    manager.removeDrill(drillId);
                    cancel();
                    return;
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        manager.registerDrill(drillId, new HydroDrillManager.ActiveDrill(placer.getUniqueId(), task));
    }

    private void playCountdownEffects(HydroDrillMechanic drill, Location center, int remaining) {
        // Particle density grows as countdown shrinks.
        int particleCount = Math.max(8, 64 - remaining);
        center.getWorld().spawnParticle(drill.particle(),
                center.clone().add(0.5, 0.5, 0.5),
                particleCount,
                drill.radius() * 0.5, drill.radius() * 0.5, drill.radius() * 0.5,
                0.05);

        float pitch = 0.5f + (1.5f * (1f - remaining / (float) drill.countdownSeconds()));
        center.getWorld().playSound(center, drill.sound(), 1.0f, Math.min(2.0f, pitch));
    }

    // ─── Explosion (T = 0) ─────────────────────────────────────────────────

    private void triggerExplosion(HydroDrillMechanic drill, Location center, UUID drillId) {
        int r = drill.radius();
        long restoreAtMs = System.currentTimeMillis() + drill.obsidianSwapSeconds() * 1000L;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block b = center.clone().add(dx, dy, dz).getBlock();
                    Material type = b.getType();
                    if (type.isAir()) continue;
                    if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
                        b.setType(Material.AIR, false);
                        continue;
                    }
                    // Skip blocks already swapped (overlapping drills).
                    if (manager.isSwappedBlock(b.getLocation())) continue;

                    org.bukkit.block.data.BlockData original = b.getBlockData().clone();
                    b.setType(Material.COBBLESTONE, false);
                    manager.registerBlockSwap(b.getLocation(),
                            new HydroDrillManager.BlockSwap(restoreAtMs, drillId, original));
                }
            }
        }

        center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        center.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION,
                center.clone().add(0.5, 0.5, 0.5), 5);

        // Schedule the restoration sweep when the swap window expires.
        Bukkit.getScheduler().runTaskLater(plugin, manager::runRestorationSweep,
                drill.obsidianSwapSeconds() * 20L + 1L);
    }

    // ─── Break protection during countdown ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureBreak(NexoFurnitureBreakEvent event) {
        ItemDisplay base = event.getBaseEntity();
        if (base == null) return;
        if (!manager.isActiveDrill(base.getUniqueId())) return;
        event.setCancelled(true);
        Player breaker = event.getPlayer();
        if (breaker != null) {
            breaker.sendMessage("§c[Foreuse] Incassable pendant le compte à rebours.");
        }
    }

    // ─── Track TNT-broken cobblestones in the swap window ──────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            if (b.getType() == Material.COBBLESTONE
                    && manager.isSwappedBlock(b.getLocation())) {
                manager.forgetBlockSwap(b.getLocation());
            }
        }
    }
}