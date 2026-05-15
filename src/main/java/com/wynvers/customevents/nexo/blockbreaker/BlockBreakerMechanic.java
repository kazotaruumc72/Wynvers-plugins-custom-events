package com.wynvers.customevents.nexo.blockbreaker;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Real Nexo {@link Mechanic} for the BlockBreaker custom_block.
 *
 * <p>The block can be activated on each of its 6 faces by right-clicking it
 * with the configured activator item. Every {@code break_interval_seconds},
 * each active face breaks the block immediately adjacent on that side (within
 * {@code break_distance}).
 *
 * <p>Shift+right-clicking the block (with no activator) opens a 3-row upgrade
 * GUI loaded from zMenu's {@code blockbreaker.yml}. The 5 center slots
 * (WCE_UPGRADE_SLOT) accept upgrade items; the bottom-row WCE_VALIDATION
 * button commits them.
 *
 * <p>YAML example:
 * <pre>
 * wynvers_block_breaker:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 80
 *     block_breaker:
 *       gui_title: "&8Upgrades du Breaker"
 *       break_interval_seconds: 5
 *       break_distance: 1
 *       max_upgrades: 5
 *       activator_item: "wynvers_breaker_remote"   # Nexo item id OR material name
 *       blacklist_materials:
 *         - BEDROCK
 *         - BARRIER
 *         - COMMAND_BLOCK
 *       blacklist_nexo_blocks: []
 *       particle: CRIT
 *       sound: BLOCK_PISTON_EXTEND
 * </pre>
 */
public class BlockBreakerMechanic extends Mechanic {

    private final String guiTitle;
    private final String zmenuInventory;
    private final String variantBase;
    private final String variantSeparator;
    private final int breakIntervalSeconds;
    private final int breakDistance;
    private final int maxUpgrades;
    private final String activatorItem;
    private final Set<Material> blacklistMaterials;
    private final Set<String> blacklistNexoBlocks;
    private final Particle particle;
    private final Sound sound;
    private final boolean dropAsItems;

    private final boolean hasActiveTextures;

    public BlockBreakerMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.guiTitle = section.getString("gui_title", "&8Upgrades du Breaker");
        this.zmenuInventory = section.getString("zmenu_inventory", "blockbreaker.yml");
        this.variantBase = section.getString("variant_base", "");
        this.variantSeparator = section.getString("variant_separator", "_");
        this.hasActiveTextures = section.getConfigurationSection("active_textures") != null;
        this.breakIntervalSeconds = Math.max(1, section.getInt("break_interval_seconds", 5));
        this.breakDistance = Math.max(1, section.getInt("break_distance", 1));
        this.maxUpgrades = Math.max(1, Math.min(5, section.getInt("max_upgrades", 5)));
        this.activatorItem = section.getString("activator_item", "");
        this.dropAsItems = section.getBoolean("drop_as_items", true);

        Set<Material> mats = new LinkedHashSet<>();
        mats.add(Material.BEDROCK); // hard-coded safety
        for (String name : section.getStringList("blacklist_materials")) {
            try { mats.add(Material.valueOf(name.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
        }
        this.blacklistMaterials = Collections.unmodifiableSet(mats);

        Set<String> nexo = new LinkedHashSet<>();
        for (String id : section.getStringList("blacklist_nexo_blocks")) {
            if (id != null && !id.isEmpty()) nexo.add(id.toLowerCase(Locale.ROOT));
        }
        this.blacklistNexoBlocks = Collections.unmodifiableSet(nexo);

        this.particle = parseParticle(section.getString("particle", "CRIT"));
        this.sound = parseSound(section.getString("sound", "BLOCK_PISTON_EXTEND"));
    }

    public String guiTitle()              { return guiTitle; }
    public String zmenuInventory()        { return zmenuInventory; }
    /**
     * Variant id base — explicit {@code variant_base} wins; otherwise, when
     * {@code active_textures} is configured, falls back to this item's own
     * Nexo id so the generated sibling variants are named consistently.
     */
    public String variantBase()           {
        if (variantBase != null && !variantBase.isEmpty()) return variantBase;
        if (hasActiveTextures) return getItemID();
        return "";
    }
    public String variantSeparator()      { return variantSeparator; }
    public boolean hasVariants()          {
        return (variantBase != null && !variantBase.isEmpty()) || hasActiveTextures;
    }
    public int breakIntervalSeconds()     { return breakIntervalSeconds; }
    public int breakDistance()            { return breakDistance; }
    public int maxUpgrades()              { return maxUpgrades; }
    public String activatorItem()         { return activatorItem; }
    public boolean dropAsItems()          { return dropAsItems; }
    public Set<Material> blacklistMaterials()   { return blacklistMaterials; }
    public Set<String> blacklistNexoBlocks()    { return blacklistNexoBlocks; }
    public Particle particle()            { return particle; }
    public Sound sound()                  { return sound; }

    public boolean isBlocked(Material m) {
        return blacklistMaterials.contains(m);
    }

    public boolean isBlocked(String nexoId) {
        return nexoId != null && blacklistNexoBlocks.contains(nexoId.toLowerCase(Locale.ROOT));
    }

    /**
     * Computes the Nexo item id of the variant that visually matches the
     * given active-face set. Returns {@code null} when no {@code variant_base}
     * is configured (the breaker keeps a single static texture in that case).
     *
     * <p>Format: {@code <variant_base><variant_separator><sorted-initials>}
     * where each active face contributes one lowercase character — D, E, N, S,
     * U, W. When no face is active, the id is just {@code variant_base}.
     *
     * <p>Examples (base {@code wynvers_block_breaker}, separator {@code _}):
     * <ul>
     *   <li>no faces       → {@code wynvers_block_breaker}</li>
     *   <li>north only     → {@code wynvers_block_breaker_n}</li>
     *   <li>up + east      → {@code wynvers_block_breaker_eu}</li>
     *   <li>all six faces  → {@code wynvers_block_breaker_densuw}</li>
     * </ul>
     */
    public String computeVariantId(Set<BlockFace> activeFaces) {
        if (!hasVariants()) return null;
        String base = variantBase();
        if (base == null || base.isEmpty()) return null;
        if (activeFaces.isEmpty()) return base;
        List<Character> initials = new ArrayList<>(activeFaces.size());
        for (BlockFace f : activeFaces) {
            Character c = faceChar(f);
            if (c != null) initials.add(c);
        }
        Collections.sort(initials);
        StringBuilder suffix = new StringBuilder(initials.size());
        for (Character c : initials) suffix.append(c);
        return base + variantSeparator + suffix;
    }

    private static Character faceChar(BlockFace face) {
        return switch (face) {
            case NORTH -> 'n';
            case SOUTH -> 's';
            case EAST  -> 'e';
            case WEST  -> 'w';
            case UP    -> 'u';
            case DOWN  -> 'd';
            default    -> null;
        };
    }

    private static Particle parseParticle(String name) {
        try { return Particle.valueOf(name); } catch (IllegalArgumentException e) { return Particle.CRIT; }
    }

    private static Sound parseSound(String name) {
        try { return Sound.valueOf(name); } catch (IllegalArgumentException e) { return Sound.BLOCK_PISTON_EXTEND; }
    }
}