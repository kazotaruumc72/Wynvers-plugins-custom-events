package com.wynvers.customevents.roseloot;

import com.nexomc.nexo.api.NexoBlocks;
import dev.rosewood.roseloot.loot.RelativeTo;
import dev.rosewood.roseloot.loot.context.LootContext;
import dev.rosewood.roseloot.loot.context.LootContextParams;
import dev.rosewood.roseloot.loot.item.LootItem;
import dev.rosewood.roseloot.loot.item.TriggerableLootItem;
import dev.rosewood.roseloot.provider.NumberProvider;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * RoseLoot loot item that places a Nexo custom block, mirroring the YAML
 * shape of the built-in {@code set_block} type but resolving {@code block:}
 * as a Nexo item ID instead of a Bukkit Material.
 *
 * <pre>
 * type: set_nexo_block
 * block: amethyst_stone_ore           # Nexo ID; "nexo-block:" prefix tolerated
 * relative-to: looter                 # LOOTED (default) or LOOTER
 * x: '%x%'
 * y: '%y%'
 * z: '%z%'
 * replace: true                       # default false (skip if non-air)
 * </pre>
 */
public final class SetNexoBlockLootItem implements TriggerableLootItem {

    private static final String PREFIX = "nexo-block:";

    private final String nexoId;
    private final RelativeTo relativeTo;
    private final NumberProvider xOffset;
    private final NumberProvider yOffset;
    private final NumberProvider zOffset;
    private final boolean replace;

    private SetNexoBlockLootItem(String nexoId, RelativeTo relativeTo,
                                 NumberProvider xOffset, NumberProvider yOffset, NumberProvider zOffset,
                                 boolean replace) {
        this.nexoId = nexoId;
        this.relativeTo = relativeTo;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
        this.replace = replace;
    }

    @Override
    public void trigger(LootContext context, Location location) {
        Location base = switch (this.relativeTo) {
            case LOOTER -> context.get(LootContextParams.LOOTER)
                    .map(Entity::getLocation).orElse(null);
            default -> location;
        };
        if (base == null) return;

        Location target = base.clone().add(
                this.xOffset.getDouble(context),
                this.yOffset.getDouble(context),
                this.zOffset.getDouble(context));

        Block block = target.getBlock();
        if (!this.replace && !block.getType().isAir()) return;

        NexoBlocks.place(this.nexoId, target);
    }

    /** Parser for {@code RoseLootAPI.registerCustomLootItem}. */
    public static @Nullable LootItem fromSection(ConfigurationSection section) {
        String raw = section.getString("block");
        if (raw == null) return null;

        String id = raw.trim();
        if (id.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            id = id.substring(PREFIX.length()).trim();
        }
        if (id.isEmpty() || !NexoBlocks.isCustomBlock(id)) return null;

        RelativeTo rel = parseRelativeTo(section.getString("relative-to"));
        NumberProvider x = NumberProvider.fromSection(section, "x", 0);
        NumberProvider y = NumberProvider.fromSection(section, "y", 0);
        NumberProvider z = NumberProvider.fromSection(section, "z", 0);
        boolean replace = section.getBoolean("replace", false);

        return new SetNexoBlockLootItem(id, rel, x, y, z, replace);
    }

    private static RelativeTo parseRelativeTo(String value) {
        if (value == null) return RelativeTo.LOOTED;
        try {
            return RelativeTo.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RelativeTo.LOOTED;
        }
    }
}