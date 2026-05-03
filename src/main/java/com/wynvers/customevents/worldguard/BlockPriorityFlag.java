package com.wynvers.customevents.worldguard;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

/**
 * Registers and exposes the WorldGuard {@code block-priority} custom flag.
 *
 * <p>The flag is a {@link SetFlag} of strings: each entry identifies a block
 * that should remain breakable inside the region regardless of the region's
 * {@code block-break} flag, as if the block had its own block-sized region
 * with higher priority. Entries are matched against:
 * <ul>
 *   <li>the lowercased Bukkit {@code Material} name (e.g. {@code stone},
 *       {@code iron_ore}), optionally prefixed with {@code minecraft:};</li>
 *   <li>the Nexo custom block id (e.g. {@code enchanted_cobblestone}),
 *       optionally prefixed with {@code nexo:}.</li>
 * </ul>
 *
 * <p>Flag registration <strong>must</strong> happen during the plugin's
 * {@code onLoad()} — WorldGuard rejects new flags once it has finished
 * loading regions.
 */
public final class BlockPriorityFlag {

    public static final String FLAG_NAME = "block-priority";

    private static SetFlag<String> flag;

    private BlockPriorityFlag() {}

    public static void register() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        SetFlag<String> created = new SetFlag<>(FLAG_NAME, new StringFlag(null));
        try {
            registry.register(created);
            flag = created;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof SetFlag<?> set && set.getType() instanceof StringFlag) {
                @SuppressWarnings("unchecked")
                SetFlag<String> casted = (SetFlag<String>) existing;
                flag = casted;
            } else {
                throw e;
            }
        }
    }

    public static SetFlag<String> get() {
        return flag;
    }
}