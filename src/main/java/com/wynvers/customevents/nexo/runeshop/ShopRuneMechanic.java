package com.wynvers.customevents.nexo.runeshop;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Real Nexo {@link Mechanic} implementing {@code rune_boost_zshop_sell_buy_percent}.
 *
 * <p>When a rune item carrying this mechanic is dragged onto an armor piece
 * (helmet / chestplate / leggings / boots / elytra / turtle helmet) inside a
 * player's inventory, a random percent within {@link #boostMin}–{@link #boostMax}
 * is rolled and stored on that armor piece. While the armor is equipped, all
 * zShop sell prices are increased by that percent and buy prices are reduced
 * by the same percent. The rune disappears after {@link #durationSeconds}.
 *
 * <p>YAML example (under any Nexo item's {@code Mechanics:} block):
 * <pre>
 * shop_boost_rune:
 *   itemname: "&aRune – Boost Shop"
 *   material: ECHO_SHARD
 *   Mechanics:
 *     rune_boost_zshop_sell_buy_percent:
 *       boost: '1-300'           # range or single value, in percent
 *       time: '86400'            # rune lifetime once applied, in seconds
 *       color_lore_rune: '&amp;a' # color of the rune name + countdown line
 * </pre>
 */
public class ShopRuneMechanic extends Mechanic {

    private final int boostMin;
    private final int boostMax;
    private final long durationSeconds;
    private final String colorCode;

    public ShopRuneMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);

        int[] range = parseRange(section.get("boost"));
        this.boostMin = range[0];
        this.boostMax = range[1];

        Object timeRaw = section.get("time");
        long t = 86400L;
        if (timeRaw instanceof Number n) {
            t = n.longValue();
        } else if (timeRaw != null) {
            try { t = Long.parseLong(timeRaw.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        this.durationSeconds = Math.max(1L, t);

        String c = section.getString("color_lore_rune", "&a");
        if (c == null || c.isEmpty()) c = "&a";
        this.colorCode = c;
    }

    public int boostMin()         { return boostMin; }
    public int boostMax()         { return boostMax; }
    public long durationSeconds() { return durationSeconds; }
    public String colorCode()     { return colorCode; }

    /** Rolls a random percent within {@code [boostMin, boostMax]}. */
    public int rollPercent() {
        if (boostMax <= boostMin) return boostMin;
        return ThreadLocalRandom.current().nextInt(boostMin, boostMax + 1);
    }

    /** Parses {@code "1-300"}, {@code "50"}, or a number into a [min,max] pair. */
    private static int[] parseRange(Object raw) {
        if (raw == null) return new int[]{1, 1};
        if (raw instanceof Number n) {
            int v = Math.max(0, n.intValue());
            return new int[]{v, v};
        }
        String s = raw.toString().trim();
        int dash = s.indexOf('-');
        try {
            if (dash > 0) {
                int a = Integer.parseInt(s.substring(0, dash).trim());
                int b = Integer.parseInt(s.substring(dash + 1).trim());
                if (b < a) { int tmp = a; a = b; b = tmp; }
                return new int[]{Math.max(0, a), Math.max(0, b)};
            }
            int v = Math.max(0, Integer.parseInt(s));
            return new int[]{v, v};
        } catch (NumberFormatException e) {
            return new int[]{1, 1};
        }
    }
}