package com.wynvers.customevents.nexo.baseclaimprotector;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real Nexo {@link Mechanic} for the Base Claim Protector furniture block.
 *
 * <p>YAML example (top-level inside {@code Mechanics:} of an item):
 * <pre>
 * base_claim_protector:
 *   Mechanics:
 *     furniture:
 *       type: ITEM_DISPLAY
 *       hitbox: { height: 1.0, width: 1.0 }
 *     base_claim_protector:
 *       radius: 3                          # claim radius in chunks (3 = 7x7)
 *       food:                              # list of recipes (Nexo items only)
 *         - "item:nexo:basic_fuel, time:3h"
 *         - "item:nexo:rich_fuel, amount:5, time:1d"
 *       feed_radius: 1.5                   # detection radius above the block
 *       consume_on_expire: true            # remove furniture when bonus expires
 *       particle: HEART
 *       feed_sound: ENTITY_GENERIC_EAT
 *       claim_sound: BLOCK_BEACON_ACTIVATE
 *       expire_sound: BLOCK_BEACON_DEACTIVATE
 * </pre>
 */
public class BaseClaimProtectorMechanic extends Mechanic {

    public static final class FoodRecipe {
        public final String nexoId;
        public final int amount;
        public final long durationTicks;

        public FoodRecipe(String nexoId, int amount, long durationTicks) {
            this.nexoId = nexoId;
            this.amount = amount;
            this.durationTicks = durationTicks;
        }
    }

    private final int radius;
    /** Keyed by Nexo item id, in declaration order. */
    private final Map<String, FoodRecipe> recipes;
    private final boolean consumeOnExpire;
    private final String depletedNexoId;
    private final double feedRadius;
    private final Particle particle;
    private final Sound feedSound;
    private final Sound claimSound;
    private final Sound expireSound;

    public BaseClaimProtectorMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.radius = Math.max(1, section.getInt("radius", 3));
        this.consumeOnExpire = section.getBoolean("consume_on_expire", false);
        this.depletedNexoId = section.getString("depleted_nexo_id", "");
        this.feedRadius = Math.max(0.5, section.getDouble("feed_radius", 1.5));
        this.particle = parseParticle(section.getString("particle", "HEART"));
        this.feedSound = parseSound(section.getString("feed_sound", "ENTITY_GENERIC_EAT"));
        this.claimSound = parseSound(section.getString("claim_sound", "BLOCK_BEACON_ACTIVATE"));
        this.expireSound = parseSound(section.getString("expire_sound", "BLOCK_BEACON_DEACTIVATE"));
        this.recipes = parseRecipes(section.getStringList("food"));
    }

    public int radius() { return radius; }
    public Map<String, FoodRecipe> recipes() { return recipes; }
    public boolean consumeOnExpire() { return consumeOnExpire; }
    public String depletedNexoId() { return depletedNexoId; }
    public double feedRadius() { return feedRadius; }
    public Particle particle() { return particle; }
    public Sound feedSound() { return feedSound; }
    public Sound claimSound() { return claimSound; }
    public Sound expireSound() { return expireSound; }

    private static Map<String, FoodRecipe> parseRecipes(List<String> lines) {
        Map<String, FoodRecipe> out = new LinkedHashMap<>();
        if (lines == null) return out;
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String nexoId = null;
            int amount = 1;
            long ticks = 0;
            for (String pair : line.split(",")) {
                String p = pair.trim();
                int colon = p.indexOf(':');
                if (colon < 0) continue;
                String key = p.substring(0, colon).trim().toLowerCase();
                String value = p.substring(colon + 1).trim();
                switch (key) {
                    case "item" -> {
                        // Accept "nexo:<id>" or a bare "<id>".
                        if (value.toLowerCase().startsWith("nexo:")) {
                            nexoId = value.substring(5).trim();
                        } else {
                            nexoId = value;
                        }
                    }
                    case "amount" -> {
                        try { amount = Math.max(1, Integer.parseInt(value)); }
                        catch (NumberFormatException ignored) {}
                    }
                    case "time", "duration" -> ticks = parseDurationTicks(value);
                }
            }
            if (nexoId == null || nexoId.isBlank() || ticks <= 0) continue;
            out.put(nexoId, new FoodRecipe(nexoId, amount, ticks));
        }
        return out;
    }

    /** Parses {@code 30s}, {@code 5m}, {@code 2h}, {@code 1d} into ticks. */
    private static long parseDurationTicks(String raw) {
        if (raw == null || raw.length() < 2) return 0;
        char unit = Character.toLowerCase(raw.charAt(raw.length() - 1));
        long value;
        try {
            value = Long.parseLong(raw.substring(0, raw.length() - 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
        long seconds = switch (unit) {
            case 's' -> value;
            case 'm' -> value * 60L;
            case 'h' -> value * 3600L;
            case 'd' -> value * 86400L;
            default -> -1L;
        };
        if (seconds <= 0) return 0;
        return seconds * 20L;
    }

    private static Particle parseParticle(String name) {
        try { return Particle.valueOf(name); }
        catch (IllegalArgumentException e) { return Particle.HEART; }
    }

    private static Sound parseSound(String name) {
        try { return Sound.valueOf(name); }
        catch (IllegalArgumentException e) { return Sound.ENTITY_GENERIC_EAT; }
    }
}
