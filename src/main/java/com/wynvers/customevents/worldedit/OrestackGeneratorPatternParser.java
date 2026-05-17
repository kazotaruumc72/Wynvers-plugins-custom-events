package com.wynvers.customevents.worldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.registry.InputParser;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * WorldEdit {@link InputParser} that handles the {@code orestackgenerator:}
 * prefix in pattern strings (e.g. {@code //replace teak_wood
 * orestackgenerator:teak}).
 *
 * <p>Registered with {@code WorldEdit.getInstance().getPatternFactory()} at
 * plugin enable. FAWE re-uses WorldEdit's factory, so the same registration
 * covers both engines.
 */
public class OrestackGeneratorPatternParser extends InputParser<Pattern> {

    /** Canonical prefix users type. */
    public static final String PREFIX = "orestackgenerator:";
    /** Shorter alias accepted as a convenience. */
    public static final String SHORT_PREFIX = "osg:";

    private final Plugin plugin;

    public OrestackGeneratorPatternParser(@NotNull WorldEdit worldEdit,
                                          @NotNull Plugin plugin) {
        super(worldEdit);
        this.plugin = plugin;
    }

    @Override
    public Stream<String> getSuggestions(String input) {
        if (input.isEmpty()) {
            return Stream.of(PREFIX);
        }
        String lower = input.toLowerCase();
        if (PREFIX.startsWith(lower)) {
            return Stream.of(PREFIX);
        }
        if (SHORT_PREFIX.startsWith(lower)) {
            return Stream.of(SHORT_PREFIX);
        }
        return Stream.empty();
    }

    @Override
    public Pattern parseFromInput(String input, ParserContext context) throws InputParseException {
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();

        String name;
        if (lower.startsWith(PREFIX)) {
            name = trimmed.substring(PREFIX.length()).trim();
        } else if (lower.startsWith(SHORT_PREFIX)) {
            name = trimmed.substring(SHORT_PREFIX.length()).trim();
        } else {
            return null; // not our pattern; let other parsers try
        }

        if (name.isEmpty()) {
            throw new InputParseException(
                    "Usage: " + PREFIX + "<generator-name> (e.g. " + PREFIX + "teak)");
        }

        if (context.getWorld() == null) {
            throw new InputParseException(
                    PREFIX + " requires a world context (run from in-game).");
        }

        if (!OrestackGeneratorPattern.isKnownGenerator(name)) {
            throw new InputParseException(
                    "Unknown OreStack generator: '" + name + "'. "
                            + "Check that a template with this name is loaded "
                            + "in plugins/Orestack/generators.");
        }

        return new OrestackGeneratorPattern(plugin, context.getWorld(), name);
    }
}