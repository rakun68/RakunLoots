package me.rakkoon69.rakunloots.config.summon;

import java.util.Locale;
import java.util.Optional;

/**
 * Underwater position: float at the run's top, or sink to its bottom.
 */
public enum UnderwaterPosition {
    FLOATING,
    SINKING;

    public static Optional<UnderwaterPosition> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "floating" -> Optional.of(FLOATING);
            case "sinking" -> Optional.of(SINKING);
            default -> Optional.empty();
        };
    }
}
