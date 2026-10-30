package me.rakkoon69.rakunloots.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Who has a permission when no permission plugin says otherwise. The values are the ones Bukkit gives a permission's
 * default, written in a config as {@code true}, {@code false}, {@code op}, and {@code not op}.
 */
public enum DefaultAccess {
    /**
     * Everyone, {@code true}.
     */
    EVERYONE,
    /**
     * Nobody, {@code false}.
     */
    NOBODY,
    /**
     * Operators only, {@code op}.
     */
    OPERATORS,
    /**
     * Everyone but operators, {@code not op}.
     */
    NON_OPERATORS;

    /**
     * The access a config value names, or empty for a value that names none. A yes or no is a {@code true} or
     * {@code false}; text ignores case, spaces, and underscores, and also reads {@code !op} for {@code not op}.
     */
    static Optional<DefaultAccess> parse(Object value) {
        if (value instanceof Boolean flag) {
            return Optional.of(flag ? EVERYONE : NOBODY);
        }
        if (!(value instanceof String text)) {
            return Optional.empty();
        }
        return switch (text.toLowerCase(Locale.ROOT).replaceAll("[^a-z!]", "")) {
            case "true" -> Optional.of(EVERYONE);
            case "false" -> Optional.of(NOBODY);
            case "op" -> Optional.of(OPERATORS);
            case "notop", "!op" -> Optional.of(NON_OPERATORS);
            default -> Optional.empty();
        };
    }
}
