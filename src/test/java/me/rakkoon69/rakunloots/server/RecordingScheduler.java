package me.rakkoon69.rakunloots.server;

import be.seeseemelk.mockbukkit.scheduler.BukkitSchedulerMock;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MockBukkit scheduler that notes every task it runs and the thread it ran on. Sync tasks run on the test thread,
 * which is the mock server's main thread, when the test ticks the scheduler. Async tasks start on their due tick on
 * a thread of their own, which the test can wait for.
 */
final class RecordingScheduler extends BukkitSchedulerMock {

    /**
     * One run of one scheduled task.
     */
    static final class TaskRun {

        private final int task;
        private final Thread thread;
        private final boolean mainThread;

        private TaskRun(int task, Thread thread, boolean mainThread) {
            this.task = task;
            this.thread = thread;
            this.mainThread = mainThread;
        }

        /**
         * Numbers each scheduled task in the order it was scheduled. Every run of a repeating task shares it.
         */
        int task() {
            return task;
        }

        Thread thread() {
            return thread;
        }

        boolean mainThread() {
            return mainThread;
        }
    }

    private static final ThreadLocal<Integer> RUNNING = new ThreadLocal<>();

    private final AtomicInteger scheduled = new AtomicInteger();
    private final List<TaskRun> runs = new CopyOnWriteArrayList<>();
    private final ExecutorService async = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "RecordingScheduler async");
        thread.setDaemon(true);
        return thread;
    });
    private final Queue<Future<?>> asyncRuns = new ConcurrentLinkedQueue<>();

    /**
     * The recorded task running on this thread now, if any.
     */
    static OptionalInt runningTask() {
        Integer task = RUNNING.get();
        return task == null ? OptionalInt.empty() : OptionalInt.of(task);
    }

    List<TaskRun> runs() {
        return List.copyOf(runs);
    }

    @Override
    public @NotNull BukkitTask runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return super.runTaskLater(plugin, record(task), 1L);
    }

    @Override
    public @NotNull BukkitTask runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return super.runTaskLater(plugin, record(task), delay);
    }

    @Override
    public @NotNull BukkitTask runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        return super.runTaskTimer(plugin, record(task), delay, period);
    }

    /**
     * Starts the task on an async thread on the next tick, as Bukkit does.
     */
    @Override
    public @NotNull BukkitTask runTaskAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task) {
        return runTaskLaterAsynchronously(plugin, task, 1L);
    }

    /**
     * Starts the task on an async thread on the due tick, so {@link #performOneTickAndAwaitAsync()} can wait for it.
     */
    @Override
    public @NotNull BukkitTask runTaskLaterAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return super.runTaskLater(plugin, startAsync(record(task)), delay);
    }

    /**
     * Starts each run on an async thread on its due tick, so {@link #performOneTickAndAwaitAsync()} can wait for it.
     */
    @Override
    public @NotNull BukkitTask runTaskTimerAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task, long delay,
                                                          long period) {
        return super.runTaskTimer(plugin, startAsync(record(task)), delay, period);
    }

    /**
     * Runs one tick, then waits until every async run started so far has finished.
     */
    void performOneTickAndAwaitAsync() throws Exception {
        performOneTick();
        for (Future<?> run = asyncRuns.poll(); run != null; run = asyncRuns.poll()) {
            run.get(5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        async.shutdownNow();
    }

    private Runnable startAsync(Runnable task) {
        return () -> asyncRuns.add(async.submit(task));
    }

    private Runnable record(Runnable task) {
        int number = scheduled.incrementAndGet();
        return () -> {
            runs.add(new TaskRun(number, Thread.currentThread(), Bukkit.isPrimaryThread()));
            RUNNING.set(number);
            try {
                task.run();
            } finally {
                RUNNING.remove();
            }
        };
    }
}
