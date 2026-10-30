package me.rakkoon69.rakunloots.animation;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.TaskScheduler;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Where and how one animation plays: the point it is centred on, the sink it draws through, the scheduler its timer
 * runs on, and where it reports a draw that fails.
 *
 * @param x        the centre, in blocks
 * @param scheduler runs the animation's timer; the sink draws on the thread that owns the spot
 * @param failures told of each draw that fails, from any thread
 */
public record AnimationContext(
        UUID worldKey,
        double x,
        double y,
        double z,
        ParticleSink sink,
        TaskScheduler scheduler,
        Consumer<Throwable> failures
) {

    public AnimationContext {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(failures, "failures");
    }

    /**
     * The block that owns the centre; the sink draws on its thread.
     */
    public BlockPosition owner() {
        return new BlockPosition((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
