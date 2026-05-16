package com.wynvers.customevents.nexo.chunkbreaker;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Real Nexo {@link Mechanic} for the ChunkBreaker custom_block.
 *
 * <p>The block is consumed the moment it is placed: it triggers a one-shot
 * "raze the whole chunk" operation. Every block in the chunk that is NOT
 * bedrock (or otherwise blacklisted) is removed — including water and lava.
 * Vanilla drops are computed with a synthetic netherite pickaxe; Nexo
 * custom_blocks are removed via {@code NexoBlocks.remove} so RoseLoot loot
 * tables still fire. All resulting Item entities are swept and re-spawned
 * stacked at the chunk's centre, on top of the highest bedrock block.
 *
 * <p>YAML example:
 * <pre>
 * wynvers_chunk_breaker:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 400
 *     chunk_breaker:
 *       batch_y_slices: 32         # Y-layers processed per tick (perf knob)
 *       silk_touch: false
 *       fortune_level: 0
 *       blacklist_materials:
 *         - BEDROCK
 *         - BARRIER
 *       blacklist_nexo_blocks: []
 *       start_particle: EXPLOSION_EMITTER
 *       start_sound: ENTITY_GENERIC_EXPLODE
 *       finish_sound: BLOCK_BEACON_ACTIVATE
 * </pre>
 */
public class ChunkBreakerMechanic extends Mechanic {

    private final int batchYSlices;
    private final boolean silkTouch;
    private final int fortuneLevel;
    private final Set<Material> blacklistMaterials;
    private final Set<String> blacklistNexoBlocks;
    private final Particle startParticle;
    private final Sound startSound;
    private final Sound finishSound;

    public ChunkBreakerMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.batchYSlices = Math.max(1, section.getInt("batch_y_slices", 32));
        this.silkTouch = section.getBoolean("silk_touch", false);
        this.fortuneLevel = Math.max(0, section.getInt("fortune_level", 0));

        Set<Material> mats = new LinkedHashSet<>();
        mats.add(Material.BEDROCK); // hard-coded safety
        for (String name : section.getStringList("blacklist_materials")) {
            try { mats.add(Material.valueOf(name.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) {}
        }
        this.blacklistMaterials = Collections.unmodifiableSet(mats);

        Set<String> nexo = new LinkedHashSet<>();
        for (String id : section.getStringList("blacklist_nexo_blocks")) {
            if (id != null && !id.isEmpty()) nexo.add(id.toLowerCase(Locale.ROOT));
        }
        this.blacklistNexoBlocks = Collections.unmodifiableSet(nexo);

        this.startParticle = parseParticle(section.getString("start_particle", "EXPLOSION_EMITTER"));
        this.startSound = parseSound(section.getString("start_sound", "ENTITY_GENERIC_EXPLODE"));
        this.finishSound = parseSound(section.getString("finish_sound", "BLOCK_BEACON_ACTIVATE"));
    }

    public int batchYSlices()                 { return batchYSlices; }
    public boolean silkTouch()                { return silkTouch; }
    public int fortuneLevel()                 { return fortuneLevel; }
    public Set<Material> blacklistMaterials() { return blacklistMaterials; }
    public Set<String> blacklistNexoBlocks()  { return blacklistNexoBlocks; }
    public Particle startParticle()           { return startParticle; }
    public Sound startSound()                 { return startSound; }
    public Sound finishSound()                { return finishSound; }

    public boolean isBlocked(Material m) {
        return blacklistMaterials.contains(m);
    }

    public boolean isBlocked(String nexoId) {
        return nexoId != null && blacklistNexoBlocks.contains(nexoId.toLowerCase(Locale.ROOT));
    }

    private static Particle parseParticle(String name) {
        try { return Particle.valueOf(name); }
        catch (IllegalArgumentException e) { return Particle.EXPLOSION_EMITTER; }
    }

    private static Sound parseSound(String name) {
        try { return Sound.valueOf(name); }
        catch (IllegalArgumentException e) { return Sound.ENTITY_GENERIC_EXPLODE; }
    }
}