package com.wynvers.customevents.nexo.breachcharge;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Nexo factory for the Breach Defuser tool. Right-clicking an active Breach
 * Charge custom_block with the defuser cancels its countdown and removes the
 * charge block. Optionally consumes the defuser item.
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
     * Right-click on a Breach Charge custom_block with the defuser in hand.
     * The block-key check against the manager is the source of truth — if the
     * clicked block is a tracked active charge, defuse it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        DefuserMechanic defuser = getMechanic(hand);
        if (defuser == null) return;

        BreachChargeMechanicFactory chargeFactory = BreachChargeMechanicFactory.instance();
        if (chargeFactory == null) return;
        String key = BreachChargeMechanicFactory.blockKey(clicked.getLocation());
        if (!chargeFactory.manager().isActive(key)) return;

        chargeFactory.defuse(key, player, true);
        event.setCancelled(true);

        if (defuser.consumeOnUse() && player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.6f);
    }
}