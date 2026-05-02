package com.wynvers.customevents.nexo.breachcharge;

import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureInteract(NexoFurnitureInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        DefuserMechanic defuser = getMechanic(hand);
        if (defuser == null) return;

        ItemDisplay base = event.getBaseEntity();
        if (base == null) return;

        BreachChargeMechanicFactory chargeFactory = BreachChargeMechanicFactory.instance();
        if (chargeFactory == null) return;
        if (!chargeFactory.manager().isActive(base.getUniqueId())) {
            player.sendMessage("§7[Désamorceur] §oCet item n'est pas une charge active.");
            return;
        }

        chargeFactory.defuse(base.getUniqueId(), player);
        event.setCancelled(true);

        if (defuser.consumeOnUse() && player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.6f);
    }
}