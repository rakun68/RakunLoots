package me.rakkoon69.rakunloots.animation;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import org.bukkit.Particle;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A particle sink that records each frame it was asked to draw. It refuses what the server adapter refuses: a
 * particle name that is no Bukkit particle. A test can make it fail asynchronously or throw.
 */
public final class RecordingParticleSink implements ParticleSink {

    /**
     * One draw the sink was asked for.
     */
    public record Frame(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws) {

        /**
         * The dust colour of the frame's first point as {@code red:green:blue}, or its particle name when it is not
         * dust. A frame of one animation has one colour, which tells the animations of a loot apart.
         */
        public String colour() {
            var spec = draws.get(0).spec();
            return spec.dust().map(dust -> dust.red() + ":" + dust.green() + ":" + dust.blue())
                    .orElse(spec.particle());
        }

        /**
         * The farthest any point lies from a centre, in blocks.
         */
        public double farthestFrom(double x, double y, double z) {
            return draws.stream().mapToDouble(draw -> Math.sqrt(Math.pow(draw.x() - x, 2)
                    + Math.pow(draw.y() - y, 2) + Math.pow(draw.z() - z, 2))).max().orElse(0);
        }
    }

    private final List<Frame> frames = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failAsync;
    private volatile RuntimeException failNow;

    @Override
    public CompletableFuture<Boolean> draw(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws) {
        for (ParticleDraw draw : draws) {
            particle(draw.spec().particle());
        }
        if (failNow != null) {
            throw failNow;
        }
        frames.add(new Frame(worldKey, owner, List.copyOf(draws)));
        if (failAsync != null) {
            return CompletableFuture.failedFuture(failAsync);
        }
        return CompletableFuture.completedFuture(true);
    }

    public List<Frame> frames() {
        return List.copyOf(frames);
    }

    /**
     * The colours of the frames, in the order they were drawn.
     */
    public List<String> colours() {
        return frames.stream().map(Frame::colour).toList();
    }

    public long count(String colour) {
        return frames.stream().filter(frame -> frame.colour().equals(colour)).count();
    }

    public void clear() {
        frames.clear();
    }

    /**
     * Each later draw is recorded and then completes exceptionally, as a draw task that throws on the region does.
     */
    public void failAsynchronously(RuntimeException failure) {
        failAsync = failure;
    }

    /**
     * Each later draw throws before it is recorded, as the adapter does for a particle name it refuses.
     */
    public void failImmediately(RuntimeException failure) {
        failNow = failure;
    }

    private static void particle(String name) {
        String upper = name.trim().toUpperCase(Locale.ROOT);
        for (String candidate : List.of(upper, upper.equals("REDSTONE") ? "DUST" : upper,
                upper.equals("DUST") ? "REDSTONE" : upper)) {
            try {
                Particle.valueOf(candidate);
                return;
            } catch (IllegalArgumentException unknown) {
                // try the other name
            }
        }
        throw new IllegalArgumentException("not a particle on this server: " + name);
    }
}
