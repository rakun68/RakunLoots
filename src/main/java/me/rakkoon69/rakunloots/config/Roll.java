package me.rakkoon69.rakunloots.config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The rule that chooses which content entries a summoned loot box receives, and how many.
 */
public final class Roll {

    private final int min;
    private final int max;
    private final List<ContentEntry> entries;

    Roll(int min, int max, List<ContentEntry> entries) {
        this.min = min;
        this.max = max;
        this.entries = Collections.unmodifiableList(List.copyOf(Objects.requireNonNull(entries, "entries")));
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public List<ContentEntry> entries() {
        return entries;
    }
}
