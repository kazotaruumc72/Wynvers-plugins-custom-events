package com.wynvers.customevents.worldedit;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.mask.AbstractMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * WorldEdit / FAWE {@link Mask} that matches blocks whose Nexo
 * {@link CustomBlockMechanic} item-id equals the configured value.
 *
 * <p>Usage in-game:
 * <pre>//replace nexo:teak_wood orestackgenerator:teak</pre>
 *
 * <p>{@link #test(BlockVector3)} is the hot path called for every block in
 * the current selection. We resolve the Bukkit {@link Block} via
 * {@link BukkitAdapter} on the stored WorldEdit {@link World}, then ask
 * Nexo for the underlying mechanic. A {@code null} mechanic (i.e. the
 * block is not a Nexo custom block) yields {@code false}.
 *
 * <p>The expected id is stored in lower-case so the comparison is
 * case-insensitive and allocation-free.
 */
public final class NexoBlockMask extends AbstractMask {

    private final World weWorld;
    private final String expectedIdLower;

    public NexoBlockMask(@NotNull World weWorld, @NotNull String expectedId) {
        this.weWorld = weWorld;
        this.expectedIdLower = expectedId.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean test(BlockVector3 vector) {
        org.bukkit.World bukkitWorld = BukkitAdapter.adapt(weWorld);
        if (bukkitWorld == null) return false;

        Block block = bukkitWorld.getBlockAt(vector.getX(), vector.getY(), vector.getZ());
        CustomBlockMechanic mech = NexoBlocks.customBlockMechanic(block);
        if (mech == null) return false;

        String id = mech.getItemID();
        return id != null && id.toLowerCase(Locale.ROOT).equals(expectedIdLower);
    }

    @Override
    public Mask copy() {
        // Stateless aside from the immutable id/world — safe to share.
        return new NexoBlockMask(weWorld, expectedIdLower);
    }
}