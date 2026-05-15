package com.wynvers.customevents.nexo.harvestermachine;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.nexomc.nexo.api.events.custom_block.NexoBlockPlaceEvent;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
 * Factory + listener for the Harvester Storage block. Open it with a normal
 * right-click; on break, drop its contents on the ground.
 */
public class HarvesterStorageMechanicFactory extends MechanicFactory implements Listener {

    public static final String MECHANIC_ID = "harvester_storage";

    private static HarvesterStorageMechanicFactory instance;

    private final JavaPlugin plugin;
    private final HarvesterStorageManager manager;

    public HarvesterStorageMechanicFactory(JavaPlugin plugin) {
        super(MECHANIC_ID);
        instance = this;
        this.plugin = plugin;
        this.manager = new HarvesterStorageManager(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static HarvesterStorageMechanicFactory instance() { return instance; }
    public HarvesterStorageManager manager() { return manager; }

    @Override
    public @Nullable HarvesterStorageMechanic getMechanic(String itemId) {
        Mechanic m = super.getMechanic(itemId);
        return (m instanceof HarvesterStorageMechanic s) ? s : null;
    }

    @Override
    public @Nullable HarvesterStorageMechanic getMechanic(ItemStack itemStack) {
        Mechanic m = super.getMechanic(itemStack);
        return (m instanceof HarvesterStorageMechanic s) ? s : null;
    }

    @Override
    public @Nullable Mechanic parse(@NotNull ConfigurationSection section) {
        HarvesterStorageMechanic mechanic = new HarvesterStorageMechanic(this, section);
        addToImplemented(mechanic);
        return mechanic;
    }

    public void shutdown() {
        manager.shutdown();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getPlayer().isSneaking()) return; // allow shift+rightclick to place items on top

        CustomBlockMechanic cb = NexoBlocks.customBlockMechanic(block);
        if (cb == null) return;
        HarvesterStorageMechanic mech = getMechanic(cb.getItemID());
        if (mech == null) return;

        event.setCancelled(true);
        manager.openFor(event.getPlayer(), block, mech);
    }

    /**
     * Auto-register the storage block in the manager the moment it's placed
     * (otherwise the manager only knows about a storage once a player opens
     * it via right-click — meaning a freshly-placed storage would be invisible
     * to nearby Harvester Machines).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(NexoBlockPlaceEvent event) {
        String id = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        HarvesterStorageMechanic mech = getMechanic(id);
        if (mech == null) return;
        manager.getOrCreate(event.getBlock(), mech);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(NexoBlockBreakEvent event) {
        Block b = event.getBlock();
        if (b == null) return;
        HarvesterStorageManager.StorageHolder removed = manager.removeAt(b.getLocation());
        if (removed == null || removed.inventory == null) return;
        // Drop contents on the ground so nothing is lost.
        Location dropLoc = b.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack stack : removed.inventory.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                b.getWorld().dropItemNaturally(dropLoc, stack);
            }
        }
    }
}