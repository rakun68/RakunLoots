package me.rakkoon69.rakunloots.lootbox;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a summon searches: a world, by its settings, or around one player in it.
 */
public record SummonTarget(UUID worldKey, Optional<UUID> playerId) {

    public SummonTarget {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(playerId, "playerId");
    }

    public static SummonTarget world(UUID worldKey) {
        return new SummonTarget(worldKey, Optional.empty());
    }

    public static SummonTarget player(UUID worldKey, UUID playerId) {
        return new SummonTarget(worldKey, Optional.of(Objects.requireNonNull(playerId, "playerId")));
    }
}
