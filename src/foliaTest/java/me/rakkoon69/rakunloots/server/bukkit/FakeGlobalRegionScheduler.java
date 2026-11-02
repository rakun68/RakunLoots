package me.rakkoon69.rakunloots.server.bukkit;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A Folia global region scheduler with a thread of its own. Tasks run on the fake server's ticks.
 */
final class FakeGlobalRegionScheduler implements GlobalRegionScheduler {

    private static final FakeFoliaTicks.Global OWNER = new FakeFoliaTicks.Global();

    private final FakeFoliaTicks ticks;

    FakeGlobalRegionScheduler(FakeFoliaTicks ticks) {
        this.ticks = ticks;
    }

    @Override
    public void execute(@NotNull Plugin plugin, @NotNull Runnable run) {
        run(plugin, task -> run.run());
    }

    @Override
    public @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task) {
        return runDelayed(plugin, task, 1);
    }

    @Override
    public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
                                             long delayTicks) {
        return ticks.schedule(plugin, OWNER, task, delayTicks, 0, null);
    }

    @Override
    public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
                                                 long initialDelayTicks, long periodTicks) {
        return ticks.repeat(plugin, OWNER, task, initialDelayTicks, periodTicks, null);
    }

    @Override
    public void cancelTasks(@NotNull Plugin plugin) {
        throw new UnsupportedOperationException("not faked");
    }
}
