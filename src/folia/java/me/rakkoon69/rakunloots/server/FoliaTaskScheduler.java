package me.rakkoon69.rakunloots.server;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Schedules through one of Folia's four schedulers for the Folia adapter: the region, entity, global region, or
 * async scheduler. It never calls the Bukkit scheduler, which Folia does not have.
 */
final class FoliaTaskScheduler implements TaskScheduler {

    /**
     * Folia's async scheduler counts time, not ticks. A tick is 50 milliseconds at the normal tick rate.
     */
    private static final long MILLIS_PER_TICK = 50;

    /**
     * One Folia scheduler, bound to what it runs for. Each call returns the scheduled task, or null when it will
     * never run.
     */
    private interface Target {
        ScheduledTask run(Consumer<ScheduledTask> task);

        ScheduledTask runLater(Consumer<ScheduledTask> task, long delayTicks);

        ScheduledTask repeat(Consumer<ScheduledTask> task, long delayTicks, long periodTicks);
    }

    private final Target target;

    private FoliaTaskScheduler(Target target) {
        this.target = target;
    }

    /**
     * Runs tasks on the region scheduler for the chunk that holds the block. The world is looked up when each task
     * is scheduled.
     *
     * @throws IllegalStateException from each scheduling call, when the world is not loaded then
     */
    static TaskScheduler region(Plugin plugin, UUID worldKey, int blockX, int blockZ) {
        Server server = plugin.getServer();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        return new FoliaTaskScheduler(new Target() {
            @Override
            public ScheduledTask run(Consumer<ScheduledTask> task) {
                return server.getRegionScheduler().run(plugin, world(), chunkX, chunkZ, task);
            }

            @Override
            public ScheduledTask runLater(Consumer<ScheduledTask> task, long delayTicks) {
                return server.getRegionScheduler().runDelayed(plugin, world(), chunkX, chunkZ, task, delayTicks);
            }

            @Override
            public ScheduledTask repeat(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
                return server.getRegionScheduler()
                        .runAtFixedRate(plugin, world(), chunkX, chunkZ, task, delayTicks, periodTicks);
            }

            private World world() {
                World world = server.getWorld(worldKey);
                if (world == null) {
                    throw new IllegalStateException("world " + worldKey + " is not loaded");
                }
                return world;
            }
        });
    }

    /**
     * Runs tasks on the entity scheduler of the player, which runs on the region that owns the player. The player
     * is looked up when each task is scheduled. A task never runs for a player who is not online then, and stops
     * when the player leaves.
     */
    static TaskScheduler entity(Plugin plugin, UUID playerId) {
        Server server = plugin.getServer();
        return new FoliaTaskScheduler(new Target() {
            @Override
            public ScheduledTask run(Consumer<ScheduledTask> task) {
                EntityScheduler scheduler = scheduler();
                return scheduler == null ? null : scheduler.run(plugin, task, null);
            }

            @Override
            public ScheduledTask runLater(Consumer<ScheduledTask> task, long delayTicks) {
                EntityScheduler scheduler = scheduler();
                return scheduler == null ? null : scheduler.runDelayed(plugin, task, null, delayTicks);
            }

            @Override
            public ScheduledTask repeat(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
                EntityScheduler scheduler = scheduler();
                return scheduler == null ? null : scheduler.runAtFixedRate(plugin, task, null, delayTicks, periodTicks);
            }

            /**
             * The player's entity scheduler, or null when the player is not online.
             */
            private EntityScheduler scheduler() {
                Player player = server.getPlayer(playerId);
                return player == null ? null : player.getScheduler();
            }
        });
    }

    /**
     * Runs tasks on the global region scheduler.
     */
    static TaskScheduler global(Plugin plugin) {
        Server server = plugin.getServer();
        return new FoliaTaskScheduler(new Target() {
            @Override
            public ScheduledTask run(Consumer<ScheduledTask> task) {
                return server.getGlobalRegionScheduler().run(plugin, task);
            }

            @Override
            public ScheduledTask runLater(Consumer<ScheduledTask> task, long delayTicks) {
                return server.getGlobalRegionScheduler().runDelayed(plugin, task, delayTicks);
            }

            @Override
            public ScheduledTask repeat(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
                return server.getGlobalRegionScheduler().runAtFixedRate(plugin, task, delayTicks, periodTicks);
            }
        });
    }

    /**
     * Runs tasks on the async scheduler, turning ticks into milliseconds.
     */
    static TaskScheduler async(Plugin plugin) {
        Server server = plugin.getServer();
        return new FoliaTaskScheduler(new Target() {
            @Override
            public ScheduledTask run(Consumer<ScheduledTask> task) {
                return server.getAsyncScheduler().runNow(plugin, task);
            }

            @Override
            public ScheduledTask runLater(Consumer<ScheduledTask> task, long delayTicks) {
                return server.getAsyncScheduler().runDelayed(plugin, task, millis(delayTicks), TimeUnit.MILLISECONDS);
            }

            @Override
            public ScheduledTask repeat(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
                return server.getAsyncScheduler().runAtFixedRate(plugin, task, millis(delayTicks), millis(periodTicks),
                        TimeUnit.MILLISECONDS);
            }
        });
    }

    @Override
    public TaskHandle run(Runnable task) {
        Objects.requireNonNull(task, "task");
        return handle(target.run(scheduled -> task.run()));
    }

    @Override
    public TaskHandle runLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        SchedulerTicks.requireAtLeastOne("delayTicks", delayTicks);
        return handle(target.runLater(scheduled -> task.run(), delayTicks));
    }

    @Override
    public TaskHandle repeat(Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        SchedulerTicks.requireAtLeastOne("delayTicks", delayTicks);
        SchedulerTicks.requireAtLeastOne("periodTicks", periodTicks);
        return handle(target.repeat(scheduled -> task.run(), delayTicks, periodTicks));
    }

    /**
     * A handle that cancels the Folia task. A task that will never run has nothing to cancel.
     */
    private static TaskHandle handle(ScheduledTask scheduled) {
        return scheduled == null ? () -> {
        } : scheduled::cancel;
    }

    private static long millis(long ticks) {
        return Math.multiplyExact(ticks, MILLIS_PER_TICK);
    }
}
