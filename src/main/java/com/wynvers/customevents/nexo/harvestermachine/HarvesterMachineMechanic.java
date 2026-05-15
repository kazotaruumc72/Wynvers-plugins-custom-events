package com.wynvers.customevents.nexo.harvestermachine;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Real Nexo {@link Mechanic} for the Harvester Machine custom_block.
 *
 * <p>YAML example (top-level inside the item's {@code Mechanics:}):
 * <pre>
 * harvester_machine:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 350
 *     harvester_machine:
 *       radius: 3                  # crop scan radius (cube half-edge)
 *       harvest_interval_seconds: 6
 *       fuel_item: wynvers_harvester_fuel
 *       fuel_duration: 5m          # 30s / 5m / 1h / 2d
 *       feed_radius: 1.5           # detection radius for fuel-item drops on top
 *       require_water_below: true
 *       active_variant: wynvers_harvester_machine_on
 *       storage_search_radius: 5
 *       whitelist_nexo_crops:
 *         # mapping: final-stage Nexo id → first-stage Nexo id (replanted after harvest)
 *         fv_wheat_stage_7: fv_wheat_stage_0
 *         fv_carrot_stage_5: fv_carrot_stage_0
 *       particle: HAPPY_VILLAGER
 *       harvest_sound: BLOCK_CROP_BREAK
 *       fuel_sound: BLOCK_FURNACE_FIRE_CRACKLE
 * </pre>
 */
public class HarvesterMachineMechanic extends Mechanic {

    private final int radius;
    private final int harvestIntervalSeconds;
    private final String fuelItem;
    private final long fuelDurationTicks;
    private final double feedRadius;
    private final boolean requireWaterBelow;
    private final String activeVariant;
    private final int storageSearchRadius;
    /** Final-stage Nexo id → first-stage Nexo id (used for replanting). */
    private final Map<String, String> whitelistNexoCrops;
    private final Particle particle;
    private final Sound harvestSound;
    private final Sound fuelSound;

    public HarvesterMachineMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.radius = Math.max(1, section.getInt("radius", 3));
        this.harvestIntervalSeconds = Math.max(1, section.getInt("harvest_interval_seconds", 6));
        this.fuelItem = section.getString("fuel_item", "");
        this.fuelDurationTicks = parseDurationTicks(section.getString("fuel_duration", "5m"));
        this.feedRadius = Math.max(0.5, section.getDouble("feed_radius", 1.5));
        this.requireWaterBelow = section.getBoolean("require_water_below", true);
        this.activeVariant = section.getString("active_variant", "");
        this.storageSearchRadius = Math.max(1, Math.min(10, section.getInt("storage_search_radius", 5)));

        Map<String, String> crops = new LinkedHashMap<>();
        ConfigurationSection cropSec = section.getConfigurationSection("whitelist_nexo_crops");
        if (cropSec != null) {
            for (String key : cropSec.getKeys(false)) {
                String first = cropSec.getString(key);
                if (first != null && !first.isBlank()) {
                    crops.put(key.toLowerCase(Locale.ROOT), first.toLowerCase(Locale.ROOT));
                }
            }
        }
        this.whitelistNexoCrops = java.util.Collections.unmodifiableMap(crops);

        this.particle = parseParticle(section.getString("particle", "HAPPY_VILLAGER"));
        this.harvestSound = parseSound(section.getString("harvest_sound", "BLOCK_CROP_BREAK"));
        this.fuelSound = parseSound(section.getString("fuel_sound", "BLOCK_FURNACE_FIRE_CRACKLE"));
    }

    public int radius()                  { return radius; }
    public int harvestIntervalSeconds()  { return harvestIntervalSeconds; }
    public String fuelItem()             { return fuelItem; }
    public long fuelDurationTicks()      { return fuelDurationTicks; }
    public double feedRadius()           { return feedRadius; }
    public boolean requireWaterBelow()   { return requireWaterBelow; }
    public String activeVariant()        { return activeVariant; }
    public int storageSearchRadius()     { return storageSearchRadius; }
    public Map<String, String> whitelistNexoCrops() { return whitelistNexoCrops; }
    public Particle particle()           { return particle; }
    public Sound harvestSound()          { return harvestSound; }
    public Sound fuelSound()             { return fuelSound; }

    private static long parseDurationTicks(String raw) {
        if (raw == null || raw.length() < 2) return 20L * 300;  // 5 min default
        char unit = Character.toLowerCase(raw.charAt(raw.length() - 1));
        long value;
        try { value = Long.parseLong(raw.substring(0, raw.length() - 1).trim()); }
        catch (NumberFormatException e) { return 20L * 300; }
        long seconds = switch (unit) {
            case 's' -> value;
            case 'm' -> value * 60L;
            case 'h' -> value * 3600L;
            case 'd' -> value * 86400L;
            default  -> 300L;
        };
        return Math.max(20L, seconds * 20L);
    }

    private static Particle parseParticle(String name) {
        try { return Particle.valueOf(name); } catch (IllegalArgumentException e) { return Particle.HAPPY_VILLAGER; }
    }

    private static Sound parseSound(String name) {
        try { return Sound.valueOf(name); } catch (IllegalArgumentException e) { return Sound.BLOCK_CROP_BREAK; }
    }
}
