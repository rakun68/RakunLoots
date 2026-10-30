package me.rakkoon69.rakunloots.roll;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Calculates timer chance for loots in a world.
 */
public final class TimerChance {

    private TimerChance() {
    }

    /**
     * Calculates the timer chance of each eligible loot in a world: weight over total of eligible loots.
     * Must match {@code WorldTimers.pickLoot}: loot must be in the loaded set, and weight must be above zero.
     */
    public static Map<String, Double> calculate(Map<String, Integer> loots, Set<String> loadedLootIds) {
        Objects.requireNonNull(loots, "loots");
        Objects.requireNonNull(loadedLootIds, "loadedLootIds");

        long total = 0;
        for (Map.Entry<String, Integer> entry : loots.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0 && loadedLootIds.contains(entry.getKey())) {
                total += entry.getValue();
            }
        }
        if (total == 0) {
            return Collections.emptyMap();
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : loots.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0 && loadedLootIds.contains(entry.getKey())) {
                result.put(entry.getKey(), (double) entry.getValue() / total);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
