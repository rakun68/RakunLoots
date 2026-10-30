package me.rakkoon69.rakunloots.server;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Particle draws for the server adapters. The names resolve on the calling thread, before any task is scheduled; the
 * draw runs on the thread that owns the spot, and never loads a chunk.
 */
final class BukkitParticles {

    private BukkitParticles() {
    }

    /**
     * A draw whose particle is resolved.
     */
    record Resolved(Particle particle, double x, double y, double z, int count, Object data) {
    }

    /**
     * Resolves each particle name. {@code REDSTONE} and {@code DUST} name the same particle, which the server calls
     * by only one of them.
     *
     * @throws IllegalArgumentException when a name is no particle on this server
     */
    static List<Resolved> resolve(List<ParticleDraw> draws) {
        Objects.requireNonNull(draws, "draws");
        List<Resolved> resolved = new ArrayList<>(draws.size());
        for (ParticleDraw draw : draws) {
            ParticleSpec spec = draw.spec();
            Particle particle = particle(spec.particle());
            Object data = spec.dust()
                    .map(dust -> (Object) new Particle.DustOptions(
                            Color.fromRGB(dust.red(), dust.green(), dust.blue()), dust.size()))
                    .orElse(null);
            resolved.add(new Resolved(particle, draw.x(), draw.y(), draw.z(), spec.count(), data));
        }
        return resolved;
    }

    private static Particle particle(String name) {
        String upper = name.trim().toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(upper);
        } catch (IllegalArgumentException unknown) {
            String other = switch (upper) {
                case "REDSTONE" -> "DUST";
                case "DUST" -> "REDSTONE";
                default -> null;
            };
            if (other != null) {
                try {
                    return Particle.valueOf(other);
                } catch (IllegalArgumentException alsoUnknown) {
                    // the failure below names the written name
                }
            }
            throw new IllegalArgumentException("not a particle on this server: " + name);
        }
    }

    /**
     * Spawns the particles. Call it on the thread that owns the block at the owner position.
     *
     * @param world the world, or null when it is not loaded
     * @return whether they were spawned; false when the world or the owner's chunk is not loaded
     */
    static boolean draw(World world, BlockPosition owner, List<Resolved> draws) {
        if (world == null || !world.isChunkLoaded(owner.x() >> 4, owner.z() >> 4)) {
            return false;
        }
        for (Resolved draw : draws) {
            world.spawnParticle(draw.particle(), draw.x(), draw.y(), draw.z(), draw.count(), draw.data());
        }
        return true;
    }
}
