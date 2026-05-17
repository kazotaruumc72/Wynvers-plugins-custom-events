package com.wynvers.customevents.roseloot;

import dev.rosewood.roseloot.loot.context.LootContext;
import dev.rosewood.roseloot.loot.item.LootItem;
import dev.rosewood.roseloot.loot.item.TriggerableLootItem;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * RoseLoot loot item that suppresses the vanilla drops and experience of the
 * originating {@link BlockBreakEvent} without cancelling the event itself
 * (so the block still breaks).
 *
 * <pre>
 * type: cancel-drops
 * </pre>
 *
 * <p>Note: only vanilla drops and XP are suppressed. Static loot items
 * declared earlier in the same RoseLoot entry have already been dropped by
 * the time this trigger fires and cannot be retracted — put {@code cancel-drops}
 * in entries that contain no {@code item}/{@code custom_item} drops.
 */
public final class CancelDropsLootItem implements TriggerableLootItem {

    @Override
    public void trigger(LootContext context, Location location) {
        BlockBreakEvent event = BlockEventTracker.current();
        if (event == null) return;

        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    public static LootItem fromSection(ConfigurationSection section) {
        return new CancelDropsLootItem();
    }
}