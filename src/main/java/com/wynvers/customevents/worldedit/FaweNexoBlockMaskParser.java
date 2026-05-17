package com.wynvers.customevents.worldedit;

import com.fastasyncworldedit.core.extension.factory.parser.AliasedParser;
import com.sk89q.worldedit.WorldEdit;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.List;
import java.util.Locale;

/**
 * FAWE-aware variant of {@link NexoBlockMaskParser}. Same trick as
 * {@link FaweOrestackGeneratorPatternParser}: implement
 * {@link AliasedParser} with a prefix-matching {@code contains()} so FAWE's
 * rich mask parser re-routes our input through the parsers list instead of
 * delegating to the default block mask parser (which throws "Does not match
 * a valid block type").
 *
 * <p>Only loaded when FastAsyncWorldEdit is on the runtime classpath — see
 * {@link WorldEditOrestackIntegration#register}.
 */
public final class FaweNexoBlockMaskParser
        extends NexoBlockMaskParser
        implements AliasedParser {

    private static final List<String> PREFIX_LIST = new AbstractList<>() {
        private final String[] visible = {PREFIX, ALT_PREFIX};

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof String s)) return false;
            String lower = s.toLowerCase(Locale.ROOT);
            return lower.startsWith(PREFIX) || lower.startsWith(ALT_PREFIX);
        }

        @Override
        public String get(int index) {
            return visible[index];
        }

        @Override
        public int size() {
            return visible.length;
        }
    };

    public FaweNexoBlockMaskParser(@NotNull WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public List<String> getMatchedAliases() {
        return PREFIX_LIST;
    }
}