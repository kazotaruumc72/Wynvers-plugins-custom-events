package com.wynvers.customevents.nexo.breachcharge;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.wynvers.customevents.integration.SaberFactionsHook;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Nexo {@link MechanicFactory} for the {@code breach_charge} mechanic.
 *
 * <p>Self-contained: parses items, handles wall placement (with SaberFactions
 * bypass via manual NexoFurniture.place), runs the countdown, plays bips and
 * particles (orange → red in the critical phase), defuses on furniture break,
 * and dynamites a directional tunnel at T=0.
 */
public class BreachChargeMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "breach_charge";

    private static BreachChargeMechanicFactory instance;

    private final JavaPlugin plugin;
    private final BreachChargeManager manager;

    public BreachChargeMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        this.manager = new BreachChargeManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static BreachChargeMechanicFactory instance() { return instance; }
    public BreachChargeManager manager() { return manager; }

    @Override
    public @Nullable BreachChargeMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof BreachChargeMechanic c) ? c : null;
    }

    @Override
    public @Nullable BreachChargeMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof BreachChargeMechanic c) ? c : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        BreachChargeMechanic mechanic = new BreachChargeMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
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

        BreachChargeMechanic charge = getMechanic(item);
        if (charge == null) return;

        Player player = event.getPlayer();
        BlockFace face = event.getBlockFace();

        // Wall-only constraint.
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            event.setCancelled(true);
            player.sendMessage("§c[Charge] Doit être posée sur un mur (côté), pas au sol ni au plafond.");
            return;
        }

        Location targetLoc = clicked.getRelative(face).getLocation();

        if (manager.isOnCooldown(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c[Charge] Cooldown : "
                    + manager.cooldownRemainingSeconds(player.getUniqueId()) + "s restantes.");
            return;
        }

        if (SaberFactionsHook.isAvailable() && SaberFactionsHook.isProtectedZone(targetLoc)) {
            event.setCancelled(true);
            player.sendMessage("§c[Charge] Interdite en SafeZone / WarZone.");
            return;
        }

        // Take control: cancel so SaberFactions / Nexo's regular flow don't process it.
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);

        String itemId = NexoItems.idFromItem(item);
        if (itemId == null) return;

        float yaw = player.getLocation().getYaw();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemDisplay placed = NexoFurniture.place(itemId, targetLoc, yaw, face);
            if (placed == null) {
                player.sendMessage("§c[Charge] Échec de placement.");
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                hand.setAmount(hand.getAmount() - 1);
            }
            manager.setCooldown(player.getUniqueId(), charge.cooldownSeconds());

            // The wall block this charge is attached to:
            Block wallBlock = clicked;
            BlockFace tunnelDirection = face.getOppositeFace(); // into the wall

            startCountdown(charge, placed, player, wallBlock, tunnelDirection);
            alertFaction(charge, player, wallBlock.getLocation());
        });
    }

    private void alertFaction(BreachChargeMechanic charge, Player attacker, Location at) {
        if (!charge.alertFaction()) return;
        if (!SaberFactionsHook.isAvailable()) return;
        List<Player> defenders = SaberFactionsHook.getOnlineFactionMembers(at);
        if (defenders.isEmpty()) return;
        String coords = at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
        for (Player def : defenders) {
            def.sendMessage("§4§l[ALERTE] §cUne charge de brèche a été posée à §f" + coords
                    + "§c par §f" + attacker.getName() + "§c. §o10 secondes pour la désamorcer.");
            def.playSound(def.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.6f);
        }
    }

    // ─── Countdown ──────────────────────────────────────────────────────────

    private void startCountdown(BreachChargeMechanic charge,
                                ItemDisplay drillEntity,
                                Player placer,
                                Block wallBlock,
                                BlockFace tunnelDirection) {
        UUID drillId = drillEntity.getUniqueId();
        int totalSeconds = charge.countdownSeconds();
        int criticalThreshold = charge.criticalThresholdSeconds();
        Location chargeLoc = drillEntity.getLocation();

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!drillEntity.isValid()) {
                    cancel();
                    manager.remove(drillId);
                    return;
                }
                int remaining = totalSeconds - elapsed;

                if (remaining <= criticalThreshold) {
                    // Critical phase: red dust, fast bips.
                    chargeLoc.getWorld().spawnParticle(Particle.DUST, chargeLoc, 20,
                            0.4, 0.4, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.6f));
                    chargeLoc.getWorld().playSound(chargeLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f, 2.0f);
                    chargeLoc.getWorld().playSound(chargeLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f, 2.0f);
                } else {
                    // Normal phase: orange flames, slow bip.
                    chargeLoc.getWorld().spawnParticle(Particle.FLAME, chargeLoc, 12,
                            0.3, 0.3, 0.3, 0.01);
                    chargeLoc.getWorld().playSound(chargeLoc, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.4f);
                }

                if (elapsed >= totalSeconds) {
                    detonate(charge, drillEntity, wallBlock, tunnelDirection, drillId);
                    cancel();
                    return;
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        manager.register(drillId, new BreachChargeManager.ActiveCharge(
                placer.getUniqueId(), wallBlock.getLocation(), tunnelDirection, charge, task));
    }

    // ─── Detonation ─────────────────────────────────────────────────────────

    private void detonate(BreachChargeMechanic charge,
                          ItemDisplay drillEntity,
                          Block wallBlock,
                          BlockFace tunnelDirection,
                          UUID drillId) {
        Location at = drillEntity.getLocation();
        try { NexoFurniture.remove(drillEntity, null); } catch (Throwable ignored) {}
        manager.remove(drillId);

        if (at.getWorld() == null) return;

        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 3);
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 30,
                1.0, 1.0, 1.0, 0.05);

        // Determine width axis perpendicular to tunnel direction (always horizontal).
        BlockFace widthAxis = (tunnelDirection == BlockFace.NORTH || tunnelDirection == BlockFace.SOUTH)
                ? BlockFace.EAST : BlockFace.NORTH;

        int hRange = charge.tunnelHeight() / 2;
        int wRange = charge.tunnelWidth() / 2;

        for (int d = 0; d < charge.tunnelDepth(); d++) {
            Block layer = wallBlock.getRelative(tunnelDirection, d);
            for (int h = -hRange; h <= hRange; h++) {
                for (int w = -wRange; w <= wRange; w++) {
                    Block target = layer.getRelative(0, h, 0).getRelative(widthAxis, w);
                    Material type = target.getType();
                    if (type.isAir()) continue;
                    if (type == Material.OBSIDIAN && !charge.destroyObsidian()) continue;
                    if (type == Material.BEDROCK) continue;
                    target.breakNaturally();
                }
            }
        }
    }

    // ─── Defuse on furniture break (defender or water) ─────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureBreak(NexoFurnitureBreakEvent event) {
        ItemDisplay base = event.getBaseEntity();
        if (base == null) return;
        if (!manager.isActive(base.getUniqueId())) return;
        defuse(base.getUniqueId(), event.getPlayer());
        // Let Nexo handle the entity removal (don't cancel).
    }

    /**
     * Cancels the countdown for the given charge, removes the visual furniture,
     * and notifies the defuser (if any).
     */
    public void defuse(UUID entityId, Player defuser) {
        BreachChargeManager.ActiveCharge active = manager.remove(entityId);
        if (active == null) return;
        try { active.countdownTask.cancel(); } catch (Throwable ignored) {}

        // Remove the visual furniture so the wall is clear.
        org.bukkit.entity.Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof ItemDisplay base) {
            try { NexoFurniture.remove(base, null); } catch (Throwable ignored) {}
        }

        Location at = active.wallBlock;
        if (at != null && at.getWorld() != null) {
            at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 1.0f);
            at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0.5, 0.5, 0.5),
                    20, 0.4, 0.4, 0.4, 0.02);
        }
        if (defuser != null) {
            defuser.sendMessage("§a[Charge] Désamorcée.");
        }
    }
}