package me.rakkoon69.rakunloots.server.bukkit;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * The tick clock behind the fake Folia schedulers. Each task belongs to one owner: a region, an entity, the global
 * region, or the async scheduler. Region, entity, and global tasks run on a thread of their owner's own; async tasks
 * run on a shared pool. A task scheduled with a delay of one tick runs on the next {@link #tick()}. Code a task runs
 * can ask which owner it runs for.
 */
final class FakeFoliaTicks {

    /**
     * Which Folia scheduler a task was scheduled on, and for what.
     */
    sealed interface Owner permits Region, Entity, Global, Async {
    }

    /**
     * The region that owns one chunk of a world. In the fake, each chunk is its own region.
     */
    record Region(UUID world, int chunkX, int chunkZ) implements Owner {
    }

    /**
     * The entity scheduler of one entity.
     */
    record Entity(UUID entity) implements Owner {
    }

    /**
     * The global region scheduler.
     */
    record Global() implements Owner {
    }

    /**
     * The async scheduler.
     */
    record Async() implements Owner {
    }

    private static final ThreadLocal<Owner> CURRENT = new ThreadLocal<>();

    private final Object lock = new Object();
    private long tick;
    private final List<FakeTask> pending = new ArrayList<>();
    private final Map<Owner, ExecutorService> threads = new ConcurrentHashMap<>();
    private final ExecutorService asyncPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "fake async");
        thread.setDaemon(true);
        return thread;
    });
    private final Queue<Future<?>> running = new ConcurrentLinkedQueue<>();

    /**
     * The owner of the task running on this thread now, if any.
     */
    static Optional<Owner> currentOwner() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Schedules a task to first run the given number of ticks from now.
     *
     * @param periodTicks the period of a repeating task, or 0 for a task that runs once
     */
    FakeTask schedule(Plugin plugin, Owner owner, Consumer<ScheduledTask> task, long delayTicks, long periodTicks,
                      Runnable retired) {
        if (delayTicks < 1 || periodTicks < 0) {
            throw new IllegalArgumentException("delay " + delayTicks + ", period " + periodTicks);
        }
        FakeTask scheduled = new FakeTask(plugin, owner, task, periodTicks, retired);
        synchronized (lock) {
            scheduled.due = tick + delayTicks;
            pending.add(scheduled);
        }
        return scheduled;
    }

    /**
     * Schedules a task to first run the given number of ticks from now, and then every period.
     *
     * @throws IllegalArgumentException when the period is below 1, as Folia's schedulers do
     */
    FakeTask repeat(Plugin plugin, Owner owner, Consumer<ScheduledTask> task, long delayTicks, long periodTicks,
                    Runnable retired) {
        if (periodTicks < 1) {
            throw new IllegalArgumentException("period " + periodTicks);
        }
        return schedule(plugin, owner, task, delayTicks, periodTicks, retired);
    }

    /**
     * Starts a task on its owner's thread now, outside any tick. The next {@link #tick()} waits for it.
     */
    FakeTask startNow(Plugin plugin, Owner owner, Consumer<ScheduledTask> task) {
        FakeTask scheduled = new FakeTask(plugin, owner, task, 0, null);
        running.add(submit(owner, scheduled::runOnce));
        return scheduled;
    }

    /**
     * Retires every pending task of one owner: none of them runs, and each retired callback runs on the owner's
     * thread. The next {@link #tick()} waits for those callbacks.
     */
    void retire(Owner owner) {
        List<FakeTask> retired = new ArrayList<>();
        synchronized (lock) {
            for (FakeTask task : List.copyOf(pending)) {
                if (task.owner.equals(owner)) {
                    pending.remove(task);
                    task.state = ScheduledTask.ExecutionState.CANCELLED;
                    retired.add(task);
                }
            }
        }
        for (FakeTask task : retired) {
            if (task.retired != null) {
                running.add(submit(owner, task.retired));
            }
        }
    }

    /**
     * Runs one tick: every task due now runs on its owner's thread. Returns once they, and every task started
     * outside a tick, have finished.
     */
    void tick() throws Exception {
        List<FakeTask> due = new ArrayList<>();
        synchronized (lock) {
            tick++;
            for (FakeTask task : List.copyOf(pending)) {
                if (task.due <= tick) {
                    pending.remove(task);
                    due.add(task);
                }
            }
        }
        for (FakeTask task : due) {
            running.add(submit(task.owner, task::runOnce));
        }
        for (Future<?> run = running.poll(); run != null; run = running.poll()) {
            run.get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Ticks until the future completes, for at most five seconds, and returns its value.
     */
    <T> T runUntilDone(CompletableFuture<T> future) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone()) {
            if (System.nanoTime() > deadline) {
                throw new TimeoutException("the future did not complete");
            }
            tick();
            if (!future.isDone()) {
                Thread.sleep(1);
            }
        }
        return future.get();
    }

    void shutdown() {
        threads.values().forEach(ExecutorService::shutdownNow);
        asyncPool.shutdownNow();
    }

    private Future<?> submit(Owner owner, Runnable run) {
        ExecutorService executor = owner instanceof Async ? asyncPool : threads.computeIfAbsent(owner,
                key -> Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "fake " + key);
                    thread.setDaemon(true);
                    return thread;
                }));
        return executor.submit(() -> {
            CURRENT.set(owner);
            try {
                run.run();
            } finally {
                CURRENT.remove();
            }
        });
    }

    /**
     * One task scheduled on a fake Folia scheduler. Cancelling it follows {@link ScheduledTask#cancel()}.
     */
    final class FakeTask implements ScheduledTask {

        private final Plugin plugin;
        private final Owner owner;
        private final Consumer<ScheduledTask> task;
        private final long periodTicks;
        private final Runnable retired;
        private long due;
        private ExecutionState state = ExecutionState.IDLE;

        private FakeTask(Plugin plugin, Owner owner, Consumer<ScheduledTask> task, long periodTicks, Runnable retired) {
            this.plugin = plugin;
            this.owner = owner;
            this.task = task;
            this.periodTicks = periodTicks;
            this.retired = retired;
        }

        private void runOnce() {
            synchronized (lock) {
                if (state != ExecutionState.IDLE) {
                    return;
                }
                state = ExecutionState.RUNNING;
            }
            try {
                task.accept(this);
            } finally {
                synchronized (lock) {
                    if (state == ExecutionState.CANCELLED_RUNNING) {
                        state = ExecutionState.CANCELLED;
                    } else if (periodTicks > 0) {
                        state = ExecutionState.IDLE;
                        due += periodTicks;
                        pending.add(this);
                    } else {
                        state = ExecutionState.FINISHED;
                    }
                }
            }
        }

        @Override
        public @NotNull Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return periodTicks > 0;
        }

        @Override
        public @NotNull CancelledState cancel() {
            synchronized (lock) {
                return switch (state) {
                    case IDLE -> {
                        pending.remove(this);
                        state = ExecutionState.CANCELLED;
                        yield CancelledState.CANCELLED_BY_CALLER;
                    }
                    case RUNNING -> {
                        if (periodTicks == 0) {
                            yield CancelledState.RUNNING;
                        }
                        state = ExecutionState.CANCELLED_RUNNING;
                        yield CancelledState.NEXT_RUNS_CANCELLED;
                    }
                    case FINISHED -> CancelledState.ALREADY_EXECUTED;
                    case CANCELLED -> CancelledState.CANCELLED_ALREADY;
                    case CANCELLED_RUNNING -> CancelledState.NEXT_RUNS_CANCELLED_ALREADY;
                };
            }
        }

        @Override
        public @NotNull ExecutionState getExecutionState() {
            synchronized (lock) {
                return state;
            }
        }
    }
}
