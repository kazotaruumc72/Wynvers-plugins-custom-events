package com.wynvers.customevents.worldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.MaskFactory;
import com.sk89q.worldedit.extension.factory.PatternFactory;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.registry.InputParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Entry point for the WorldEdit / FAWE integration. Registers:
 * <ul>
 *   <li>The {@code orestackgenerator:&lt;name&gt;} <b>Pattern</b> parser
 *       (used as {@code <to>} in {@code //replace}).</li>
 *   <li>The {@code nexo:&lt;id&gt;} <b>Mask</b> parser (used as
 *       {@code <from>} in {@code //replace}).</li>
 * </ul>
 *
 * <p>Activation is conditional on <b>WorldEdit (or FAWE)</b> being loaded.
 * The pattern additionally requires OreStack; the mask additionally
 * requires Nexo.
 *
 * <p>The default {@code SingleBlockPatternParser} (and the default block
 * mask parser) throw an {@code InputParseException} for unknown block IDs,
 * and {@code AbstractFactory.parseFromInput} propagates the first exception
 * instead of continuing through the remaining parsers. To make sure our
 * prefix wins, we install each parser at index 0 of the internal parsers
 * list via reflection rather than the public {@code register()} method
 * (which appends to the end).
 *
 * <p>FAWE additionally wraps the factories with a rich parser that
 * short-circuits the parsers list for inputs without one of FAWE's special
 * prefix characters. To survive that, the FAWE-specific subclasses
 * implement {@code AliasedParser} with a prefix-matching
 * {@code List.contains()} so FAWE re-routes our input through
 * {@code parseWithoutRich}.
 */
public final class WorldEditOrestackIntegration {

    private WorldEditOrestackIntegration() {}

    /**
     * Attempts to register the {@code orestackgenerator:} pattern parser
     * and the {@code nexo:} mask parser.
     *
     * @param plugin the host plugin (used for the async→sync scheduler)
     * @return {@code true} if at least one parser was registered,
     *         {@code false} if WorldEdit/FAWE is unavailable
     */
    public static boolean register(@NotNull Plugin plugin) {
        boolean hasWorldEdit =
                Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null
                        || Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
        if (!hasWorldEdit) {
            plugin.getLogger().info(
                    "WorldEdit/FAWE not found — 'orestackgenerator:' / 'nexo:' parsers disabled.");
            return false;
        }

        boolean hasFawe =
                Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
        boolean hasOrestack = Bukkit.getPluginManager().getPlugin("Orestack") != null;
        boolean hasNexo = Bukkit.getPluginManager().getPlugin("Nexo") != null;

        WorldEdit worldEdit = WorldEdit.getInstance();
        boolean any = false;

        if (hasOrestack) {
            any |= registerPatternParser(plugin, worldEdit, hasFawe);
        } else {
            plugin.getLogger().info(
                    "OreStack not found — 'orestackgenerator:' pattern disabled.");
        }

        if (hasNexo) {
            any |= registerMaskParser(plugin, worldEdit, hasFawe);
        } else {
            plugin.getLogger().info(
                    "Nexo not found — 'nexo:' mask disabled.");
        }

        return any;
    }

    // -------------------------------------------------------------------------
    // Pattern parser
    // -------------------------------------------------------------------------

    private static boolean registerPatternParser(@NotNull Plugin plugin,
                                                 @NotNull WorldEdit worldEdit,
                                                 boolean hasFawe) {
        try {
            PatternFactory factory = worldEdit.getPatternFactory();

            OrestackGeneratorPatternParser parser;
            if (hasFawe) {
                try {
                    parser = new FaweOrestackGeneratorPatternParser(worldEdit, plugin);
                } catch (Throwable t) {
                    plugin.getLogger().warning(
                            "FastAsyncWorldEdit is loaded but the FAWE-aware pattern "
                                    + "parser could not be initialized (" + t.getMessage()
                                    + ") — falling back to the plain WorldEdit parser.");
                    parser = new OrestackGeneratorPatternParser(worldEdit, plugin);
                }
            } else {
                parser = new OrestackGeneratorPatternParser(worldEdit, plugin);
            }

            if (!insertParserAtHead(factory, parser)) {
                factory.register(parser);
                plugin.getLogger().warning(
                        "Could not insert pattern parser at head of WorldEdit's "
                                + "parsers list — falling back to register().");
            }

            plugin.getLogger().info(
                    "Registered " + (hasFawe ? "FAWE" : "WorldEdit") + " pattern '"
                            + OrestackGeneratorPatternParser.PREFIX + "<name>' (alias: '"
                            + OrestackGeneratorPatternParser.SHORT_PREFIX + "<name>').");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "Failed to register WorldEdit pattern parser: " + t.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Mask parser
    // -------------------------------------------------------------------------

    private static boolean registerMaskParser(@NotNull Plugin plugin,
                                              @NotNull WorldEdit worldEdit,
                                              boolean hasFawe) {
        try {
            MaskFactory factory = worldEdit.getMaskFactory();

            NexoBlockMaskParser parser;
            if (hasFawe) {
                try {
                    parser = new FaweNexoBlockMaskParser(worldEdit);
                } catch (Throwable t) {
                    plugin.getLogger().warning(
                            "FastAsyncWorldEdit is loaded but the FAWE-aware mask "
                                    + "parser could not be initialized (" + t.getMessage()
                                    + ") — falling back to the plain WorldEdit parser.");
                    parser = new NexoBlockMaskParser(worldEdit);
                }
            } else {
                parser = new NexoBlockMaskParser(worldEdit);
            }

            if (!insertMaskParserAtHead(factory, parser)) {
                factory.register(parser);
                plugin.getLogger().warning(
                        "Could not insert mask parser at head of WorldEdit's "
                                + "parsers list — falling back to register().");
            }

            plugin.getLogger().info(
                    "Registered " + (hasFawe ? "FAWE" : "WorldEdit") + " mask '"
                            + NexoBlockMaskParser.PREFIX + "<id>' (alias: '"
                            + NexoBlockMaskParser.ALT_PREFIX + "<id>').");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "Failed to register WorldEdit mask parser: " + t.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static boolean insertParserAtHead(@NotNull PatternFactory factory,
                                              @NotNull InputParser<Pattern> parser) {
        return insertAtHead(factory, parser);
    }

    @SuppressWarnings("unchecked")
    private static boolean insertMaskParserAtHead(@NotNull MaskFactory factory,
                                                  @NotNull InputParser<Mask> parser) {
        return insertAtHead(factory, parser);
    }

    /**
     * Adds {@code parser} at index 0 of {@code AbstractFactory#parsers} on
     * any factory subclass. The field is private in upstream WorldEdit and
     * protected in FAWE; either way, reflection works.
     */
    @SuppressWarnings("unchecked")
    private static boolean insertAtHead(@NotNull Object factory,
                                        @NotNull InputParser<?> parser) {
        try {
            Field f = factory.getClass().getSuperclass().getDeclaredField("parsers");
            f.setAccessible(true);
            Object raw = f.get(factory);
            if (!(raw instanceof List)) return false;
            List<Object> list = (List<Object>) raw;
            list.add(0, parser);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}