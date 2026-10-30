package me.rakkoon69.rakunloots.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The animation ids a loot names for each moment, in the order written. Held beside the loot, never on it. A moment
 * the loot leaves out has no ids. An id is not checked here against {@code animations.yml}; the snapshot's load does
 * that and warns.
 */
public final class LootAnimations {

    /**
     * A loot that names no animation.
     */
    public static final LootAnimations NONE = new LootAnimations(Map.of());

    private final Map<AnimationMoment, List<String>> ids;

    LootAnimations(Map<AnimationMoment, List<String>> ids) {
        Objects.requireNonNull(ids, "ids");
        Map<AnimationMoment, List<String>> copy = new EnumMap<>(AnimationMoment.class);
        for (AnimationMoment moment : AnimationMoment.values()) {
            copy.put(moment, List.copyOf(ids.getOrDefault(moment, List.of())));
        }
        this.ids = Collections.unmodifiableMap(copy);
    }

    /**
     * The ids for a moment, in the order written; empty when the loot names none.
     */
    public List<String> ids(AnimationMoment moment) {
        return ids.get(Objects.requireNonNull(moment, "moment"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LootAnimations animations && ids.equals(animations.ids);
    }

    @Override
    public int hashCode() {
        return ids.hashCode();
    }

    @Override
    public String toString() {
        return ids.toString();
    }
}
