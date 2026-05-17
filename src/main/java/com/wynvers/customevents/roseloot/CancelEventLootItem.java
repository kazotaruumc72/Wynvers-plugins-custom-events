package com.wynvers.customevents.roseloot;

import dev.rosewood.roseloot.loot.context.LootContext;
import dev.rosewood.roseloot.loot.item.LootItem;
import dev.rosewood.roseloot.loot.item.TriggerableLootItem;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * RoseLoot loot item that cancels the originating Bukkit event and suppresses
 * any block drops, so the loot effectively does not happen.
 *
 * <pre>
 * type: cancel-event
 * </pre>
 *
 * <p>Currently wires up {@link BlockBreakEvent} (the {@code type: BLOCK}
 * trigger). The event reference is exposed by {@link BlockEventTracker},
 * which captures the in-flight event at {@code LOWEST} priority — RoseLoot's
 * own {@code HIGHEST} handler (which invokes {@code triggerExtras()} and
 * thus this loot item) runs strictly between LOWEST and MONITOR, so the
 * ThreadLocal is guaranteed populated when {@link #trigger} fires.
 */
public final class CancelEventLootItem implements TriggerableLootItem {

    @Override
    public void trigger(LootContext context, Location location) {
        BlockBreakEvent event = BlockEventTracker.current();
        if (event == null) return;

        ((Cancellable) event).setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    public static LootItem fromSection(ConfigurationSection section) {
        return new CancelEventLootItem();
    }
}