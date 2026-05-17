package com.wynvers.customevents.worldedit;

import com.fastasyncworldedit.core.extension.factory.parser.AliasedParser;
import com.sk89q.worldedit.WorldEdit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.List;
import java.util.Locale;

/**
 * FAWE-aware variant of {@link OrestackGeneratorPatternParser}.
 *
 * <p>FAWE wraps the pattern factory with a {@code RichPatternParser} that
 * runs before any {@code InputParser} registered on the factory. For inputs
 * that don't start with one of FAWE's special characters
 * ({@code #}, {@code $}, {@code ^}, {@code *}, {@code [}, {@code %}),
 * {@code RichPatternParser} skips the parsers list entirely and delegates
 * directly to {@code BlockFactory.parseFromInput}, which throws "Does not
 * match a valid block type" for {@code orestackgenerator:teak}.
 *
 * <p>The only escape hatch in {@code RichPatternParser} is
 * {@code PatternFactory.containsAlias(input)} — when it returns {@code true}
 * for the full input, the rich parser switches to
 * {@code parseWithoutRich(input, context)} which iterates the parsers list
 * (and therefore our parser).
 *
 * <p>{@code containsAlias} checks
 * {@code aliasedParser.getMatchedAliases().contains(input)}. We return a
 * <em>view list</em> whose {@code contains(Object)} answers {@code true} for
 * any string starting with one of our prefixes, so any
 * {@code orestackgenerator:&lt;anything&gt;} input forces FAWE into the
 * non-rich code path.
 *
 * <p>This class references {@code AliasedParser} (a FAWE-only class) at the
 * bytecode level. It must therefore <strong>only be loaded when FAWE is on
 * the runtime classpath</strong> — see
 * {@link WorldEditOrestackIntegration#register}.
 */
public final class FaweOrestackGeneratorPatternParser
        extends OrestackGeneratorPatternParser
        implements AliasedParser {

    /**
     * Prefix-aware "list" that satisfies FAWE's
     * {@code List.contains(input)} probe without enumerating every possible
     * generator name. Iteration and indexing return only the canonical
     * prefixes so tab-completion etc. behave sensibly.
     */
    private static final List<String> PREFIX_LIST = new AbstractList<>() {
        private final String[] visible = {PREFIX, SHORT_PREFIX};

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof String s)) return false;
            String lower = s.toLowerCase(Locale.ROOT);
            return lower.startsWith(PREFIX) || lower.startsWith(SHORT_PREFIX);
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

    public FaweOrestackGeneratorPatternParser(@NotNull WorldEdit worldEdit,
                                              @NotNull Plugin plugin) {
        super(worldEdit, plugin);
    }

    @Override
    public List<String> getMatchedAliases() {
        return PREFIX_LIST;
    }
}