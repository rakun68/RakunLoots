package me.rakkoon69.rakunloots.server;

import java.util.Objects;
import java.util.Optional;

/**
 * One kind of particle: its name, how many it spawns at a point, and for a dust particle, its colour and size. The
 * name is a Bukkit particle name. {@code REDSTONE} and {@code DUST} name the same particle, and the server adapter
 * uses whichever its server has. It takes no server types, so animations stay testable without a server.
 *
 * @param particle the particle name, such as {@code REDSTONE}
 * @param count    at least 1
 * @param dust     the colour and size of a dust particle, empty for any other particle
 */
public record ParticleSpec(String particle, int count, Optional<Dust> dust) {

    /**
     * A dust particle's colour, each channel 0 to 255, and its size, above 0.
     */
    public record Dust(int red, int green, int blue, float size) {

        public Dust {
            for (int channel : new int[]{red, green, blue}) {
                if (channel < 0 || channel > 255) {
                    throw new IllegalArgumentException("a colour channel is 0 to 255, not " + channel);
                }
            }
            if (!(size > 0) || Float.isInfinite(size)) {
                throw new IllegalArgumentException("a dust size is above 0, not " + size);
            }
        }
    }

    public ParticleSpec {
        Objects.requireNonNull(particle, "particle");
        Objects.requireNonNull(dust, "dust");
        if (particle.isBlank()) {
            throw new IllegalArgumentException("a particle needs a name");
        }
        if (count < 1) {
            throw new IllegalArgumentException("a particle count is at least 1, not " + count);
        }
    }
}
