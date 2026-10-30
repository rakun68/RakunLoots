package me.rakkoon69.rakunloots.summon;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How a loot picks a spot in one column.
 */
public enum PlacementRule {
    TOP_SURFACE("top_surface", 30, List.of("@non_solid")),
    ANY_SURFACE("any_surface", 40, List.of("minecraft:air")),
    BURIED("buried", 40, List.of("@natural_ground")),
    DEEP_BURIED("deep_buried", 60, List.of("@natural_ground")),
    UNDERWATER("underwater", 80, List.of()),
    ANYWHERE("anywhere", 30, List.of("minecraft:air"));

    private final String key;
    private final int defaultAttempts;
    private final List<String> defaultReplace;

    PlacementRule(String key, int defaultAttempts, List<String> defaultReplace) {
        this.key = key;
        this.defaultAttempts = defaultAttempts;
        this.defaultReplace = defaultReplace;
    }

    public String key() {
        return key;
    }

    public int defaultAttempts() {
        return defaultAttempts;
    }

    public List<String> defaultReplace() {
        return defaultReplace;
    }

    public static Optional<PlacementRule> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String trimmed = key.trim().toLowerCase(Locale.ROOT);
        for (PlacementRule rule : values()) {
            if (rule.key.equals(trimmed)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }
}
