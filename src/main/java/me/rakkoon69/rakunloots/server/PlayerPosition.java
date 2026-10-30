package me.rakkoon69.rakunloots.server;

import java.util.Objects;
import java.util.UUID;

/**
 * Where one player stood in a world when a player position snapshot was taken.
 */
public record PlayerPosition(UUID playerId, double x, double y, double z) {

    public PlayerPosition {
        Objects.requireNonNull(playerId, "playerId");
    }
}
