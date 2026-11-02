package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Schedules through the Bukkit scheduler for the Paper and Spigot adapters, either on the main thread or async.
 */
final class BukkitTaskScheduler implements TaskScheduler {

    private final Plugin plugin;
    private final boolean async;

    private BukkitTaskScheduler(Plugin plugin, boolean async) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.async = async;
    }

    /**
     * Runs tasks on the main thread.
     */
    static TaskScheduler mainThread(Plugin plugin) {
        return new BukkitTaskScheduler(plugin, false);
    }

    /**
     * Runs tasks on the Bukkit async scheduler's threads.
     */
    static TaskScheduler async(Plugin plugin) {
        return new BukkitTaskScheduler(plugin, true);
    }

    @Override
    public TaskHandle run(Runnable task) {
        Objects.requireNonNull(task, "task");
        BukkitTask scheduled = async
                ? Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
                : Bukkit.getScheduler().runTask(plugin, task);
        return scheduled::cancel;
    }

    @Override
    public TaskHandle runLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        SchedulerTicks.requireAtLeastOne("delayTicks", delayTicks);
        BukkitTask scheduled = async
                ? Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks)
                : Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return scheduled::cancel;
    }

    @Override
    public TaskHandle repeat(Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        SchedulerTicks.requireAtLeastOne("delayTicks", delayTicks);
        SchedulerTicks.requireAtLeastOne("periodTicks", periodTicks);
        BukkitTask scheduled = async
                ? Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
                : Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return scheduled::cancel;
    }
}
