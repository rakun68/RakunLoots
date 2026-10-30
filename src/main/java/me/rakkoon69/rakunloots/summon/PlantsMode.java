package me.rakkoon69.rakunloots.summon;

import java.util.Locale;
import java.util.Optional;

/**
 * Top surface plants handling: replace the plant run, or land on top of plants.
 */
public enum PlantsMode {
    REPLACE,
    ON_TOP;

    public static Optional<PlantsMode> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "replace" -> Optional.of(REPLACE);
            case "on_top" -> Optional.of(ON_TOP);
            default -> Optional.empty();
        };
    }
}
