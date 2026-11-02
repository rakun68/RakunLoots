package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduler every server adapter passes, for each of the four kinds of task. Each adapter's test extends it
 * with its own fake server and adds its threading rule.
 */
abstract class SchedulerContract {

    /**
     * The four kinds of task a server adapter schedules.
     */
    enum Kind {
        LOCATION,
        PLAYER,
        GLOBAL,
        ASYNC
    }

    static final int BLOCK_X = -37;
    static final int BLOCK_Z = 150;

    private final AtomicLong tick = new AtomicLong();
    private ServerAdapter adapter;
    private UUID world;
    private UUID player;

    /**
     * Starts a fake server with no worlds and no players and returns the adapter under test, bound to it.
     */
    abstract ServerAdapter openAdapter();

    /**
     * Adds a loaded world.
     *
     * @return the new world's key
     */
    abstract UUID createWorld();

    /**
     * Adds an online player standing in a world at a block.
     *
     * @return the player's UUID
     */
    abstract UUID addPlayer(UUID world, int blockX, int blockZ);

    /**
     * Runs one tick of the fake server, and waits until the async tasks it started have finished.
     */
    abstract void advanceOneTick() throws Exception;

    @BeforeEach
    void openContract() {
        adapter = openAdapter();
        world = createWorld();
        player = addPlayer(world, BLOCK_X, BLOCK_Z);
    }

    /**
     * The adapter's scheduler for one kind of task, at the contract's world block or for its player.
     */
    TaskScheduler scheduler(Kind kind) {
        return switch (kind) {
            case LOCATION -> adapter.atLocation(world, BLOCK_X, BLOCK_Z);
            case PLAYER -> adapter.forPlayer(player);
            case GLOBAL -> adapter.global();
            case ASYNC -> adapter.async();
        };
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void runRunsTheTaskOnceWithinOneTickAndNotInline(Kind kind) throws Exception {
        List<Long> runs = new CopyOnWriteArrayList<>();
        Thread caller = Thread.currentThread();
        AtomicBoolean returned = new AtomicBoolean();
        AtomicBoolean ranInline = new AtomicBoolean();

        scheduler(kind).run(() -> {
            ranInline.compareAndSet(false, Thread.currentThread() == caller && !returned.get());
            runs.add(tick.get());
        });
        returned.set(true);
        advanceTicks(5);

        assertEquals(1, runs.size(), "ran once: " + runs);
        assertTrue(runs.get(0) <= 1, "ran within one tick: " + runs);
        assertFalse(ranInline.get(), "ran on the calling thread before run returned");
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void runLaterRunsTheTaskOnceOnTheDelayTick(Kind kind) throws Exception {
        List<Long> runs = new CopyOnWriteArrayList<>();
        advanceTicks(2);

        scheduler(kind).runLater(() -> runs.add(tick.get()), 3);
        advanceTicks(8);

        assertEquals(List.of(5L), runs);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void repeatRunsTheTaskOnTheDelayTickAndThenEveryPeriod(Kind kind) throws Exception {
        List<Long> runs = new CopyOnWriteArrayList<>();
        advanceTicks(1);

        scheduler(kind).repeat(() -> runs.add(tick.get()), 2, 3);
        advanceTicks(10);

        assertEquals(List.of(3L, 6L, 9L), runs);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void cancelStopsARepeat(Kind kind) throws Exception {
        List<Long> runs = new CopyOnWriteArrayList<>();
        TaskHandle handle = scheduler(kind).repeat(() -> runs.add(tick.get()), 1, 2);
        advanceTicks(3);

        handle.cancel();
        advanceTicks(10);

        assertEquals(List.of(1L, 3L), runs);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void aRepeatCanCancelItselfFromInsideItsRun(Kind kind) throws Exception {
        List<Long> runs = new CopyOnWriteArrayList<>();
        AtomicReference<TaskHandle> handle = new AtomicReference<>();
        handle.set(scheduler(kind).repeat(() -> {
            runs.add(tick.get());
            if (runs.size() == 2) {
                handle.get().cancel();
            }
        }, 1, 1));

        advanceTicks(10);

        assertEquals(List.of(1L, 2L), runs);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void cancelBeforeTheDelayTickStopsALaterTask(Kind kind) throws Exception {
        List<Long> runs = new CopyOnWriteArrayList<>();
        TaskHandle handle = scheduler(kind).runLater(() -> runs.add(tick.get()), 4);
        advanceTicks(2);

        handle.cancel();
        handle.cancel();
        advanceTicks(10);

        assertEquals(List.of(), runs);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void delayAndPeriodBelowOneTickAreRejected(Kind kind) {
        TaskScheduler scheduler = scheduler(kind);
        Runnable task = () -> {
        };

        assertThrows(IllegalArgumentException.class, () -> scheduler.runLater(task, 0));
        assertThrows(IllegalArgumentException.class, () -> scheduler.repeat(task, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> scheduler.repeat(task, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> scheduler.repeat(task, 1, -1));
    }

    /**
     * Runs ticks of the fake server. Tasks read the contract's tick count, which counts from the test's start.
     */
    void advanceTicks(int ticks) throws Exception {
        for (int i = 0; i < ticks; i++) {
            tick.incrementAndGet();
            advanceOneTick();
        }
    }
}
