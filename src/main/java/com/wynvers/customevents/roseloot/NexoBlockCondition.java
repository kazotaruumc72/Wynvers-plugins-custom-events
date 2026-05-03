package com.wynvers.customevents.roseloot;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import dev.rosewood.roseloot.loot.condition.LootCondition;
import dev.rosewood.roseloot.loot.context.LootContext;
import dev.rosewood.roseloot.loot.context.LootContextParams;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * RoseLoot condition matching the Nexo custom block at the looted location.
 *
 * <p>YAML usage: {@code conditions: - 'nexo-block:<nexo_id>'}
 */
public final class NexoBlockCondition implements LootCondition {

    private static final String PREFIX = "nexo-block:";

    private final String expectedId;

    private NexoBlockCondition(String expectedId) {
        this.expectedId = expectedId;
    }

    @Override
    public boolean check(LootContext context) {
        Optional<Block> looted = context.get(LootContextParams.LOOTED_BLOCK);
        if (looted.isEmpty()) return false;

        CustomBlockMechanic mech = NexoBlocks.customBlockMechanic(looted.get());
        if (mech == null) return false;

        String id = mech.getItemID();
        return id != null && id.toLowerCase(Locale.ROOT).equals(expectedId);
    }

    /** Parser for {@code RoseLootAPI.registerCustomLootCondition}. */
    public static @Nullable LootCondition parse(String tag) {
        if (tag == null) return null;
        String lower = tag.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(PREFIX)) return null;
        String id = lower.substring(PREFIX.length()).trim();
        if (id.isEmpty()) return null;
        return new NexoBlockCondition(id);
    }
}