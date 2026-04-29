package com.wynvers.customevents.nexo.farmer;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Real Nexo {@link Mechanic} implementing the {@code farmer} block –
 * an extended version of Nexo's native {@code evolution} mechanic.
 *
 * <p>YAML example (top-level inside {@code Mechanics:}, like {@code wither_properties}):
 * <pre>
 * barley_seed:
 *   Mechanics:
 *     furniture:
 *       properties:
 *         display_transform: NONE
 *     farmer:
 *       delay: 10s
 *       probability_percent: 100
 *       next_stage: barley_plant_stage1
 *       light_boost: true
 *       requires_farmland: true
 *       biome_whitelist: [plains]
 *       biome_blacklist: [desert]
 *       min_y: 60
 *       max_y: 200
 *       on_grow:
 *         - "console say {item} -> {next} @ {x},{y},{z}"
 *       on_final:
 *         - "console execute in {world} run particle happy_villager {x} {y} {z} 0.4 0.4 0.4 0 12"
 *       boss:
 *         chance_percent: 5
 *         actions:
 *           - "console say Un Boss apparait en {x},{y},{z} !"
 *           - "console execute in {world} run summon zombie {x} {y} {z}"
 * </pre>
 *
 * <p>Placeholders supported in actions:
 * {@code {x} {y} {z} {world} {item} {next}}.
 *
 * <p>Action format reuses the {@code ActionExecutor} grammar without a
 * player – currently {@code "console <command>"}.
 */
public class FarmerMechanic extends Mechanic {

    public static final int LIGHT_THRESHOLD = 9;
    public static final int LIGHT_BOOST_BONUS_PERCENT = 25;

    private final long delayTicks;
    private final int probabilityPercent;
    private final boolean lightBoost;
    private final String nextStageItemId;
    private final boolean requiresFarmland;
    private final Set<String> biomeWhitelist;
    private final Set<String> biomeBlacklist;
    private final int minY;
    private final int maxY;
    private final List<String> onGrowActions;
    private final List<String> onFinalActions;
    private final int bossChancePercent;
    private final List<String> bossActions;

    public FarmerMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);

        this.delayTicks = parseDurationTicks(section.getString("delay", "10s"));

        int probDefault = (int) Math.round(section.getDouble("probability", 1.0) * 100);
        this.probabilityPercent = clamp(
                section.getInt("probability_percent", probDefault), 0, 100);

        this.lightBoost = section.getBoolean("light_boost", false);
        this.nextStageItemId = section.getString("next_stage");
        this.requiresFarmland = section.getBoolean("requires_farmland", true);
        this.biomeWhitelist = lcSet(section.getStringList("biome_whitelist"));
        this.biomeBlacklist = lcSet(section.getStringList("biome_blacklist"));
        this.minY = section.getInt("min_y", Integer.MIN_VALUE);
        this.maxY = section.getInt("max_y", Integer.MAX_VALUE);
        this.onGrowActions = section.getStringList("on_grow");
        this.onFinalActions = section.getStringList("on_final");

        ConfigurationSection boss = section.getConfigurationSection("boss");
        if (boss != null) {
            this.bossChancePercent = clamp(boss.getInt("chance_percent", 0), 0, 100);
            this.bossActions = boss.getStringList("actions");
        } else {
            this.bossChancePercent = 0;
            this.bossActions = Collections.emptyList();
        }
    }

    public long delayTicks()                  { return delayTicks; }
    public int probabilityPercent()           { return probabilityPercent; }
    public boolean lightBoost()               { return lightBoost; }
    public String nextStageItemId()           { return nextStageItemId; }
    public boolean hasNextStage()             { return nextStageItemId != null && !nextStageItemId.isBlank(); }
    public boolean requiresFarmland()         { return requiresFarmland; }
    public Set<String> biomeWhitelist()       { return biomeWhitelist; }
    public Set<String> biomeBlacklist()       { return biomeBlacklist; }
    public int minY()                         { return minY; }
    public int maxY()                         { return maxY; }
    public List<String> onGrowActions()       { return onGrowActions; }
    public List<String> onFinalActions()      { return onFinalActions; }
    public int bossChancePercent()            { return bossChancePercent; }
    public List<String> bossActions()         { return bossActions; }

    // -------------------------------------------------------------------------

    private static Set<String> lcSet(List<String> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptySet();
        Set<String> out = new HashSet<>(raw.size());
        for (String s : raw) {
            if (s != null && !s.isBlank()) out.add(s.trim().toLowerCase());
        }
        return out;
    }

    private static long parseDurationTicks(String raw) {
        if (raw == null || raw.isBlank()) return 200L;
        String s = raw.trim().toLowerCase();
        char last = s.charAt(s.length() - 1);
        long value;
        long mult;
        try {
            if (Character.isDigit(last)) {
                value = Long.parseLong(s);
                mult = 20L;
            } else {
                value = Long.parseLong(s.substring(0, s.length() - 1));
                mult = switch (last) {
                    case 't' -> 1L;
                    case 's' -> 20L;
                    case 'm' -> 20L * 60L;
                    case 'h' -> 20L * 60L * 60L;
                    default  -> 20L;
                };
            }
        } catch (NumberFormatException e) {
            return 200L;
        }
        return Math.max(1L, value * mult);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}

