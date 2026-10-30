package me.rakkoon69.rakunloots.command;

import java.util.Objects;

/**
 * One argument slot of a command node: its name, its type, and whether it may be left out.
 */
public final class CommandArgument<T> {

    private final String name;
    private final ArgumentType<T> type;
    private final boolean optional;

    private CommandArgument(String name, ArgumentType<T> type, boolean optional) {
        if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("an argument name is one word: '" + name + "'");
        }
        this.name = name;
        this.type = Objects.requireNonNull(type, "type");
        this.optional = optional;
    }

    public static <T> CommandArgument<T> required(String name, ArgumentType<T> type) {
        return new CommandArgument<>(name, type, false);
    }

    public static <T> CommandArgument<T> optional(String name, ArgumentType<T> type) {
        return new CommandArgument<>(name, type, true);
    }

    public String name() {
        return name;
    }

    public ArgumentType<T> type() {
        return type;
    }

    public boolean isOptional() {
        return optional;
    }

    /**
     * How help and usage write the argument: {@code <name>} when required, {@code [name]} when optional. A type with
     * one fixed word shows that word in place of the name.
     */
    String usage() {
        String shown = type.fixedWord().orElse(name);
        if (optional) {
            return "[" + shown + "]";
        }
        return type.fixedWord().isPresent() ? shown : "<" + shown + ">";
    }
}
