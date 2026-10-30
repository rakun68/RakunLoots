package me.rakkoon69.rakunloots.hologram;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a hologram's lowest line sits: a point in a world, in blocks.
 */
public record HologramPosition(UUID worldKey, double x, double y, double z) {

    public HologramPosition {
        Objects.requireNonNull(worldKey, "worldKey");
    }
}
