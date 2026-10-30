package me.rakkoon69.rakunloots.server;

import java.util.Objects;

/**
 * One particle spec drawn at a point in a world.
 */
public record ParticleDraw(double x, double y, double z, ParticleSpec spec) {

    public ParticleDraw {
        Objects.requireNonNull(spec, "spec");
        for (double coordinate : new double[]{x, y, z}) {
            if (!Double.isFinite(coordinate)) {
                throw new IllegalArgumentException("a particle point is finite, not " + coordinate);
            }
        }
    }
}
