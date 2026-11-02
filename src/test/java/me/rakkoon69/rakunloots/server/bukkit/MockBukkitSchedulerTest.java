package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scheduler contract on a recording MockBukkit server, for the adapters that schedule through the Bukkit
 * scheduler. It adds their threading rule: location, player, and global tasks run on the main thread, and async
 * tasks never do.
 */
abstract class MockBukkitSchedulerTest extends SchedulerContract {

    MockBukkitWorlds fakeServer;

    /**
     * The adapter under test, bound to the mock server.
     */
    abstract ServerAdapter newAdapter(Plugin plugin);

    @Override
    final ServerAdapter openAdapter() {
        fakeServer = MockBukkitWorlds.start();
        return newAdapter(fakeServer.plugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Override
    UUID createWorld() {
        return fakeServer.createWorld(-64, 320);
    }

    @Override
    UUID addPlayer(UUID world, int blockX, int blockZ) {
        return fakeServer.addPlayer(world, blockX + 0.5, 64.0, blockZ + 0.5);
    }

    @Override
    void advanceOneTick() throws Exception {
        fakeServer.server().getScheduler().performOneTickAndAwaitAsync();
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = {"LOCATION", "PLAYER", "GLOBAL"})
    void locationPlayerAndGlobalTasksRunOnTheMainThread(Kind kind) throws Exception {
        assertEquals(List.of(true, true, true, true), threadsTasksRanOn(kind));
    }

    @Test
    void asyncTasksDoNotRunOnTheMainThread() throws Exception {
        assertEquals(List.of(false, false, false, false), threadsTasksRanOn(Kind.ASYNC));
    }

    /**
     * Runs a task once now, once later, and twice as a repeat.
     *
     * @return for each run, whether it ran on the main thread
     */
    private List<Boolean> threadsTasksRanOn(Kind kind) throws Exception {
        TaskScheduler scheduler = scheduler(kind);
        List<Boolean> mainThread = new CopyOnWriteArrayList<>();
        Runnable task = () -> mainThread.add(Bukkit.isPrimaryThread());

        scheduler.run(task);
        scheduler.runLater(task, 2);
        TaskHandle repeat = scheduler.repeat(task, 3, 1);
        advanceTicks(4);
        repeat.cancel();
        advanceTicks(2);

        return mainThread;
    }
}
