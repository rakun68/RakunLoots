package me.rakkoon69.rakunloots.server.bukkit;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A Folia async scheduler on a shared pool of threads. Folia counts its delays in time, not ticks. The fake turns
 * time back into ticks of 50 milliseconds, and rejects a time that is not a whole number of ticks, so tasks run on
 * the fake server's ticks. A task run now starts at once, outside any tick.
 */
final class FakeAsyncScheduler implements AsyncScheduler {

    private static final FakeFoliaTicks.Async OWNER = new FakeFoliaTicks.Async();
    private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private final FakeFoliaTicks ticks;

    FakeAsyncScheduler(FakeFoliaTicks ticks) {
        this.ticks = ticks;
    }

    @Override
    public @NotNull ScheduledTask runNow(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task) {
        return ticks.startNow(plugin, OWNER, task);
    }

    @Override
    public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, long delay,
                                             @NotNull TimeUnit unit) {
        return ticks.schedule(plugin, OWNER, task, toTicks(delay, unit), 0, null);
    }

    @Override
    public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
                                                 long initialDelay, long period, @NotNull TimeUnit unit) {
        return ticks.repeat(plugin, OWNER, task, toTicks(initialDelay, unit), toTicks(period, unit), null);
    }

    @Override
    public void cancelTasks(@NotNull Plugin plugin) {
        throw new UnsupportedOperationException("not faked");
    }

    private static long toTicks(long time, TimeUnit unit) {
        long nanos = unit.toNanos(time);
        if (nanos % TICK_NANOS != 0) {
            throw new IllegalArgumentException(time + " " + unit + " is not a whole number of ticks");
        }
        return nanos / TICK_NANOS;
    }
}
