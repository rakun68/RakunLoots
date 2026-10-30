package me.rakkoon69.rakunloots.server;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.rakkoon69.rakunloots.server.FakeFoliaTicks.Region;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A Folia region scheduler where each chunk is its own region, with a thread of its own. Tasks run on the fake
 * server's ticks. It records the region of each task scheduled.
 */
final class FakeRegionScheduler implements RegionScheduler {

    private final FakeFoliaTicks ticks;
    private final List<Region> scheduled = new CopyOnWriteArrayList<>();

    FakeRegionScheduler(FakeFoliaTicks ticks) {
        this.ticks = ticks;
    }

    /**
     * The region of each task scheduled so far, in order.
     */
    List<Region> scheduled() {
        return List.copyOf(scheduled);
    }

    @Override
    public void execute(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Runnable run) {
        run(plugin, world, chunkX, chunkZ, task -> run.run());
    }

    @Override
    public @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ,
                                      @NotNull Consumer<ScheduledTask> task) {
        return runDelayed(plugin, world, chunkX, chunkZ, task, 1);
    }

    @Override
    public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ,
                                             @NotNull Consumer<ScheduledTask> task, long delayTicks) {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("Plugin attempted to register task while disabled");
        }
        return ticks.schedule(plugin, region(world, chunkX, chunkZ), task, delayTicks, 0, null);
    }

    @Override
    public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ,
                                                 @NotNull Consumer<ScheduledTask> task, long initialDelayTicks,
                                                 long periodTicks) {
        return ticks.repeat(plugin, region(world, chunkX, chunkZ), task, initialDelayTicks, periodTicks, null);
    }

    private Region region(World world, int chunkX, int chunkZ) {
        Region region = new Region(world.getUID(), chunkX, chunkZ);
        scheduled.add(region);
        return region;
    }
}
