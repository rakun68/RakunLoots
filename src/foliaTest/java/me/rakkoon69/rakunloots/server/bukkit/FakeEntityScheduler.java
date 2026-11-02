package me.rakkoon69.rakunloots.server.bukkit;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * A Folia entity scheduler for one entity, with a thread of its own. Tasks run on the fake server's ticks. Once
 * retired, as when the entity is removed, its pending tasks never run, their retired callbacks run instead, and it
 * schedules nothing more.
 */
final class FakeEntityScheduler implements EntityScheduler {

    private final FakeFoliaTicks ticks;
    private final FakeFoliaTicks.Entity owner;
    private boolean retired;

    FakeEntityScheduler(FakeFoliaTicks ticks, UUID entity) {
        this.ticks = ticks;
        this.owner = new FakeFoliaTicks.Entity(entity);
    }

    synchronized void retire() {
        retired = true;
        ticks.retire(owner);
    }

    @Override
    public synchronized boolean execute(@NotNull Plugin plugin, @NotNull Runnable run, @Nullable Runnable retired,
                                        long delay) {
        if (this.retired) {
            return false;
        }
        ticks.schedule(plugin, owner, task -> {
            if (plugin.isEnabled()) {
                run.run();
            }
        }, Math.max(1, delay), 0, retired == null ? null : () -> {
            if (plugin.isEnabled()) {
                retired.run();
            }
        });
        return true;
    }

    @Override
    public @Nullable ScheduledTask run(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
                                       @Nullable Runnable retired) {
        return runDelayed(plugin, task, retired, 1);
    }

    @Override
    public synchronized @Nullable ScheduledTask runDelayed(@NotNull Plugin plugin,
                                                           @NotNull Consumer<ScheduledTask> task,
                                                           @Nullable Runnable retired, long delayTicks) {
        return this.retired ? null : ticks.schedule(plugin, owner, task, delayTicks, 0, retired);
    }

    @Override
    public synchronized @Nullable ScheduledTask runAtFixedRate(@NotNull Plugin plugin,
                                                               @NotNull Consumer<ScheduledTask> task,
                                                               @Nullable Runnable retired, long initialDelayTicks,
                                                               long periodTicks) {
        return this.retired ? null : ticks.repeat(plugin, owner, task, initialDelayTicks, periodTicks, retired);
    }
}
