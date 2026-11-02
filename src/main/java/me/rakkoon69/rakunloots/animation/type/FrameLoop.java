package me.rakkoon69.rakunloots.animation.type;

import me.rakkoon69.rakunloots.animation.AnimationContext;
import me.rakkoon69.rakunloots.animation.RunningAnimation;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import me.rakkoon69.rakunloots.server.TaskHandle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Draws the same frame a number of times on the animation's timer, then finishes. For animation types whose frames
 * repeat. A frame that fails to draw is reported to the context's failures, and a frame that throws stops the loop.
 */
final class FrameLoop implements RunningAnimation {

    private final Object lock = new Object();
    private TaskHandle task;
    private volatile boolean finished;
    private int drawn;

    private FrameLoop() {
    }

    /**
     * Draws {@code frame} {@code loops} times, the first {@code delayTicks} ticks from now, and the rest every
     * {@code intervalTicks}. A delay of 0 draws on the next tick, as a timer cannot draw sooner.
     */
    static FrameLoop start(AnimationContext context, int delayTicks, int intervalTicks, int loops,
                           List<ParticleDraw> frame) {
        Objects.requireNonNull(frame, "frame");
        FrameLoop loop = new FrameLoop();
        TaskHandle handle = context.scheduler().repeat(() -> loop.draw(context, frame, loops),
                Math.max(1, delayTicks), Math.max(1, intervalTicks));
        synchronized (loop.lock) {
            loop.task = handle;
            if (loop.finished) {
                handle.cancel();
            }
        }
        return loop;
    }

    private void draw(AnimationContext context, List<ParticleDraw> frame, int loops) {
        synchronized (lock) {
            if (finished) {
                return;
            }
            drawn++;
            if (drawn >= loops) {
                finish();
            }
        }
        try {
            CompletableFuture<Boolean> result = context.sink().draw(context.worldKey(), context.owner(), frame);
            result.whenComplete((done, failure) -> {
                if (failure != null) {
                    context.failures().accept(failure);
                }
            });
        } catch (RuntimeException failure) {
            stop();
            context.failures().accept(failure);
        }
    }

    private void finish() {
        finished = true;
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            finish();
        }
    }

    @Override
    public boolean finished() {
        return finished;
    }
}
