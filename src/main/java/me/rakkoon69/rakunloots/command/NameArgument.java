package me.rakkoon69.rakunloots.command;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.rakkoon69.rakunloots.match.CloseMatcher;

/**
 * An argument that names one thing from a list the server knows: a loot id, a world, a player. Matching ignores case
 * and gives back the name as the server spells it. A miss offers up to 3 close matches.
 */
abstract class NameArgument implements ArgumentType<String> {

    private final String typeKey;
    private final String unknownReasonKey;

    NameArgument(String typeKey, String unknownReasonKey) {
        this.typeKey = typeKey;
        this.unknownReasonKey = unknownReasonKey;
    }

    abstract Collection<String> names(ArgumentContext context);

    @Override
    public String typeKey() {
        return typeKey;
    }

    @Override
    public ParseResult<String> parse(String token, ArgumentContext context) {
        Collection<String> names = names(context);
        for (String name : names) {
            if (name.equalsIgnoreCase(token)) {
                return ParseResult.ok(name);
            }
        }
        return ParseResult.fail(unknownReasonKey, Map.of("value", token),
                CloseMatcher.of(names).closest(token));
    }

    @Override
    public List<String> complete(String partial, ArgumentContext context) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return names(context).stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
