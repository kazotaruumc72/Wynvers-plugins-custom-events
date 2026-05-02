package com.wynvers.customevents.nexo.breachcharge;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Nexo factory for the Breach Defuser tool. Right-clicking an active Breach
 * Charge furniture with the defuser cancels its countdown and removes the
 * charge entity. Optionally consumes the defuser item.
 */
public class DefuserMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "breach_defuser";

    private static DefuserMechanicFactory instance;
    private final JavaPlugin plugin;

    public DefuserMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static DefuserMechanicFactory instance() { return instance; }

    @Override
    public @Nullable DefuserMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof DefuserMechanic d) ? d : null;
    }

    @Override
    public @Nullable DefuserMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof DefuserMechanic d) ? d : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        DefuserMechanic mechanic = new DefuserMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    /**
     * Right-click on a Breach Charge furniture entity (or its Interaction
     * hitbox child). Uses PlayerInteractAtEntityEvent which fires for any
     * entity click — more reliable than NexoFurnitureInteractEvent which only
     * fires when Nexo's furniture has interact actions configured.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        DefuserMechanic defuser = getMechanic(hand);
        if (defuser == null) return;

        UUID baseId = resolveBaseEntityId(event.getRightClicked());
        if (baseId == null) return;

        BreachChargeMechanicFactory chargeFactory = BreachChargeMechanicFactory.instance();
        if (chargeFactory == null) return;
        if (!chargeFactory.manager().isActive(baseId)) return;

        chargeFactory.defuse(baseId, player);
        event.setCancelled(true);

        if (defuser.consumeOnUse() && player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.6f);
    }

    /**
     * Resolves the base ItemDisplay UUID for a Nexo furniture from a clicked
     * entity. Nexo wraps furniture in two entities: the visual ItemDisplay
     * and an Interaction hitbox. The player's right-click typically targets
     * the Interaction entity, so we resolve back to the base.
     */
    private UUID resolveBaseEntityId(Entity clicked) {
        if (clicked instanceof ItemDisplay id) return id.getUniqueId();
        try {
            ItemDisplay base = NexoFurniture.baseEntity(clicked.getLocation());
            if (base != null) return base.getUniqueId();
        } catch (Throwable ignored) {}
        return null;
    }
}