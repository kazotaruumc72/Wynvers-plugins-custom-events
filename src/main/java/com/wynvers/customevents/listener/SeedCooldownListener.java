package com.wynvers.customevents.listener;

import com.nexomc.nexo.api.NexoItems;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.farmer.FarmerMechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

/**
 * Forces {@code use_cooldown: 0} on all Nexo seed items (items with a
 * {@code farmer} mechanic) so that they place their furniture instantly
 * when the player right-clicks on farmland (no vanilla 4-tick delay).
 *
 * <p>Applied:
 * <ul>
 *   <li>When a player joins (scan inventory)</li>
 *   <li>When a player switches held slot</li>
 *   <li>Just before a player right-clicks (LOWEST priority)</li>
 * </ul>
 */
public class SeedCooldownListener implements Listener {

    private final WynversCustomEvents plugin;

    public SeedCooldownListener(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        for (ItemStack item : p.getInventory().getContents()) {
            applyZeroCooldownIfFarmerSeed(item);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        applyZeroCooldownIfFarmerSeed(item);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Apply zero cooldown right before vanilla/Nexo handles the right-click
        // so that the player places the furniture without any delay.
        ItemStack item = event.getItem();
        applyZeroCooldownIfFarmerSeed(item);
    }

    /**
     * If the given item is a Nexo item with a {@code farmer} mechanic,
     * applies {@code use_cooldown: 0} to its component so it acts
     * instantly when used on a block.
     */
    private void applyZeroCooldownIfFarmerSeed(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        // Nexo not loaded? skip.
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            return;
        }

        try {
            String itemId = NexoItems.idFromItem(item);
            if (itemId == null) {
                return;
            }

            FarmerMechanicFactory factory = FarmerMechanicFactory.instance();
            if (factory == null) {
                return;
            }
            if (factory.getMechanic(itemId) == null) {
                return;
            }

            // Apply use_cooldown = 0 (Minecraft 1.21.2+ component)
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            UseCooldownComponent cooldown = meta.getUseCooldown();
            // Already zero? skip the meta write.
            if (cooldown.getCooldownSeconds() <= 0f) {
                return;
            }
            cooldown.setCooldownSeconds(0f);
            meta.setUseCooldown(cooldown);
            item.setItemMeta(meta);

            if (debug()) {
                plugin.getLogger().info("[SeedCooldown] Applied use_cooldown=0 to '" + itemId + "'.");
            }
        } catch (Throwable t) {
            // Silently ignore – missing API on older Minecraft versions, etc.
            if (debug()) {
                plugin.getLogger().warning("[SeedCooldown] Error: " + t.getMessage());
            }
        }
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("farmer-debug", false);
    }
}

