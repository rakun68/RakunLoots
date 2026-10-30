package me.rakkoon69.rakunloots.server;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a player sender stands: the runtime key of the world and the position in it.
 */
public record SenderLocation(UUID worldKey, double x, double y, double z) {

    public SenderLocation {
        Objects.requireNonNull(worldKey, "worldKey");
    }
}
