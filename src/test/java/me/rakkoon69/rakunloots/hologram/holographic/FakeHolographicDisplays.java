package me.rakkoon69.rakunloots.hologram.holographic;

import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramServer;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HolographicDisplays in memory, with a main thread of its own. Like HolographicDisplays 3.0.5, it refuses every call
 * off the main thread with {@link IllegalStateException} and the same message (its {@code CorePreconditions}); it also
 * refuses a second hologram for one id, and a world it does not know, as an unloaded world.
 *
 * <p>{@link #mainThread()} is the plugin's scheduler for that thread: a task waits until {@link #runTasks()} runs the
 * waiting tasks there in order, as the Bukkit scheduler runs them on the next tick. Once {@link #disablePlugin()} is
 * called it takes no more tasks, as the Bukkit scheduler refuses a disabled plugin.
 */
public final class FakeHolographicDisplays implements HolographicDisplaysCalls {

    /**
     * HolographicDisplays' own message for a call off the main thread.
     */
    public static final String OFF_MAIN_THREAD = "async operation is not supported";

    /**
     * A hologram it draws now: where its lowest line sits, and its lines, top first.
     */
    public record Drawn(HologramPosition position, List<String> lines) {
    }

    /**
     * One call that reached it, and the thread it came on.
     */
    public record Call(String name, UUID hologramId, String thread) {
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Server thread");
        thread.setDaemon(true);
        return thread;
    });
    private final Thread main;
    private final MainThread scheduler = new MainThread();
    private final Map<UUID, Drawn> holograms = new LinkedHashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final List<UUID> worlds = new ArrayList<>();

    public FakeHolographicDisplays(UUID... loadedWorlds) {
        try {
            main = executor.submit(Thread::currentThread).get();
        } catch (InterruptedException | ExecutionException failure) {
            throw new IllegalStateException(failure);
        }
        worlds.addAll(List.of(loadedWorlds));
    }

    /**
     * The real provider over these probes and this fake, scheduling on its main thread.
     */
    public static HolographicDisplaysProvider provider(HologramServer server, FakeHolographicDisplays plugin) {
        return new HolographicDisplaysProvider(server, plugin.mainThread(), plugin::onMainThread, plugin);
    }

    @Override
    public synchronized void create(UUID hologramId, HologramPosition position, List<String> lines) {
        called("create", hologramId);
        Objects.requireNonNull(position, "position");
        if (!worlds.contains(position.worldKey())) {
            throw new IllegalStateException("world " + position.worldKey() + " is not loaded");
        }
        if (holograms.containsKey(hologramId)) {
            throw new IllegalArgumentException("already draws " + hologramId);
        }
        holograms.put(hologramId, new Drawn(position, List.copyOf(lines)));
    }

    @Override
    public synchronized void setLines(UUID hologramId, List<String> lines) {
        called("setLines", hologramId);
        Drawn drawn = holograms.get(hologramId);
        if (drawn != null) {
            holograms.put(hologramId, new Drawn(drawn.position(), List.copyOf(lines)));
        }
    }

    @Override
    public synchronized void remove(UUID hologramId) {
        called("remove", hologramId);
        holograms.remove(hologramId);
    }

    private void called(String name, UUID hologramId) {
        Objects.requireNonNull(hologramId, "hologramId");
        if (!onMainThread()) {
            throw new IllegalStateException(OFF_MAIN_THREAD);
        }
        calls.add(new Call(name, hologramId, Thread.currentThread().getName()));
    }

    public boolean onMainThread() {
        return Thread.currentThread() == main;
    }

    /**
     * The plugin's scheduler for the main thread.
     */
    public TaskScheduler mainThread() {
        return scheduler;
    }

    /**
     * How many tasks wait for the main thread.
     */
    public int waitingTasks() {
        return scheduler.waiting().size();
    }

    /**
     * Runs every waiting task on the main thread, in the order they were scheduled, and returns when they are done. A
     * task that throws is thrown here after the others ran, as the Bukkit scheduler logs it and goes on.
     */
    public void runTasks() {
        List<Runnable> due = scheduler.takeWaiting();
        onMain(() -> {
            RuntimeException first = null;
            for (Runnable task : due) {
                try {
                    task.run();
                } catch (RuntimeException failure) {
                    if (first == null) {
                        first = failure;
                    }
                }
            }
            if (first != null) {
                throw first;
            }
        });
    }

    /**
     * Runs the work on the main thread now, as the server calls the plugin's disable there, and returns when it is
     * done.
     */
    public void onMain(Runnable work) {
        try {
            executor.submit(work).get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(failure.getCause());
        }
    }

    /**
     * From now on the scheduler refuses tasks, and the tasks still waiting are dropped, as the server cancels a
     * disabled plugin's tasks.
     */
    public void disablePlugin() {
        scheduler.disable();
    }

    public synchronized Map<UUID, Drawn> holograms() {
        return Map.copyOf(holograms);
    }

    /**
     * Every call that reached it on the main thread, in order.
     */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    private static final class MainThread implements TaskScheduler {

        private final List<Runnable> waiting = new ArrayList<>();
        private boolean disabled;

        @Override
        public synchronized TaskHandle run(Runnable task) {
            Objects.requireNonNull(task, "task");
            if (disabled) {
                throw new IllegalStateException("Plugin attempted to register task while disabled");
            }
            waiting.add(task);
            return () -> {
                synchronized (this) {
                    waiting.remove(task);
                }
            };
        }

        /**
         * The provider only runs tasks as soon as it can.
         */
        @Override
        public TaskHandle runLater(Runnable task, long delayTicks) {
            throw new UnsupportedOperationException("runLater");
        }

        @Override
        public TaskHandle repeat(Runnable task, long delayTicks, long periodTicks) {
            throw new UnsupportedOperationException("repeat");
        }

        synchronized List<Runnable> waiting() {
            return List.copyOf(waiting);
        }

        synchronized List<Runnable> takeWaiting() {
            List<Runnable> taken = List.copyOf(waiting);
            waiting.clear();
            return taken;
        }

        synchronized void disable() {
            disabled = true;
            waiting.clear();
        }
    }
}
