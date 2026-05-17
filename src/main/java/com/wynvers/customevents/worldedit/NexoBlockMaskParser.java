package com.wynvers.customevents.worldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.registry.InputParser;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * WorldEdit {@link InputParser} that handles the {@code nexo:} prefix in
 * mask strings, e.g. {@code //replace nexo:teak_wood orestackgenerator:teak}.
 *
 * <p>Mirror of {@link OrestackGeneratorPatternParser}: same prefix-matching
 * approach, same FAWE caveat — see {@link FaweNexoBlockMaskParser} for the
 * FAWE-aware subclass.
 */
public class NexoBlockMaskParser extends InputParser<Mask> {

    public static final String PREFIX = "nexo:";
    public static final String ALT_PREFIX = "nexoblock:";

    public NexoBlockMaskParser(@NotNull WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input) {
        if (input.isEmpty()) return Stream.of(PREFIX);
        String lower = input.toLowerCase();
        if (PREFIX.startsWith(lower)) return Stream.of(PREFIX);
        if (ALT_PREFIX.startsWith(lower)) return Stream.of(ALT_PREFIX);
        return Stream.empty();
    }

    @Override
    public Mask parseFromInput(String input, ParserContext context) throws InputParseException {
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();

        String id;
        if (lower.startsWith(ALT_PREFIX)) {
            id = trimmed.substring(ALT_PREFIX.length()).trim();
        } else if (lower.startsWith(PREFIX)) {
            id = trimmed.substring(PREFIX.length()).trim();
        } else {
            return null;
        }

        if (id.isEmpty()) {
            throw new InputParseException(
                    "Usage: " + PREFIX + "<nexo_block_id> (e.g. " + PREFIX + "teak_wood)");
        }

        if (context.getWorld() == null) {
            throw new InputParseException(
                    PREFIX + " requires a world context (run from in-game).");
        }

        return new NexoBlockMask(context.getWorld(), id);
    }
}