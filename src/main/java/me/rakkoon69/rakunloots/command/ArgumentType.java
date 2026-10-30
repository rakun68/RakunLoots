package me.rakkoon69.rakunloots.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One kind of argument. It parses a token into a value or a failure that says why, completes a partial token, and
 * names itself for the "expected" half of a failure and for help.
 */
public interface ArgumentType<T> {

    /**
     * Message key of the type's name, such as {@code cmd_type_loot}.
     */
    String typeKey();

    /**
     * Placeholder values for the type's name, such as the bounds of an integer.
     */
    default Map<String, String> typeValues() {
        return Map.of();
    }

    /**
     * The one word a sender must type, when the type accepts exactly one. Usage and help show that word instead of
     * the argument's name.
     */
    default Optional<String> fixedWord() {
        return Optional.empty();
    }

    ParseResult<T> parse(String token, ArgumentContext context);

    /**
     * Completions for a partial token. The command system keeps those that start with the partial token, unless the
     * type is {@link #greedy()}.
     */
    List<String> complete(String partial, ArgumentContext context);

    /**
     * A greedy type takes the rest of the line, spaces included, and must be the last argument. It receives the whole
     * rest as one token, and completes only the last word of it.
     */
    default boolean greedy() {
        return false;
    }
}
