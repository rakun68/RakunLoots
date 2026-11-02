package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The scheduler contract on a hand-faked Folia server, plus Folia's threading rule: location tasks run on the region
 * scheduler for that block, player tasks on the player's entity scheduler, global tasks on the global region
 * scheduler, and async tasks on the async scheduler. The Bukkit scheduler is never called.
 */
class FoliaServerAdapterSchedulerTest extends SchedulerContract {

    FakeFoliaServer fakeServer;
    private ServerAdapter adapter;
    private UUID world;
    private UUID player;

    @Override
    ServerAdapter openAdapter() {
        fakeServer = new FakeFoliaServer();
        adapter = new FoliaServerAdapter(fakeServer.plugin());
        return adapter;
    }

    @AfterEach
    void bukkitSchedulerWasNeverCalled() {
        try {
            assertEquals(List.of(), fakeServer.bukkitSchedulerCalls(), "the Bukkit scheduler was called");
        } finally {
            fakeServer.shutdown();
        }
    }

    @Override
    UUID createWorld() {
        world = fakeServer.createWorld(-64, 320);
        return world;
    }

    @Override
    UUID addPlayer(UUID world, int blockX, int blockZ) {
        player = fakeServer.addPlayer(world, blockX + 0.5, 64.0, blockZ + 0.5);
        return player;
    }

    @Override
    void advanceOneTick() throws Exception {
        fakeServer.tick();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void eachKindRunsOnItsMatchingFoliaScheduler(Kind kind) throws Exception {
        FakeFoliaTicks.Owner expected = switch (kind) {
            case LOCATION -> new FakeFoliaTicks.Region(world, Math.floorDiv(BLOCK_X, 16), Math.floorDiv(BLOCK_Z, 16));
            case PLAYER -> new FakeFoliaTicks.Entity(player);
            case GLOBAL -> new FakeFoliaTicks.Global();
            case ASYNC -> new FakeFoliaTicks.Async();
        };
        TaskScheduler scheduler = scheduler(kind);
        List<Optional<FakeFoliaTicks.Owner>> owners = new CopyOnWriteArrayList<>();
        Runnable task = () -> owners.add(FakeFoliaTicks.currentOwner());

        scheduler.run(task);
        scheduler.runLater(task, 2);
        TaskHandle repeat = scheduler.repeat(task, 3, 1);
        advanceTicks(4);
        repeat.cancel();
        advanceTicks(2);

        assertEquals(List.of(Optional.of(expected), Optional.of(expected), Optional.of(expected),
                Optional.of(expected)), owners);
    }

    @Test
    void aPlayerTaskStopsWhenThePlayerLeavesAndNeverRunsForAPlayerWhoIsGone() throws Exception {
        List<String> runs = new CopyOnWriteArrayList<>();
        TaskScheduler forPlayer = scheduler(Kind.PLAYER);
        forPlayer.repeat(() -> runs.add("repeat"), 1, 1);
        advanceTicks(2);

        fakeServer.removePlayer(player);
        forPlayer.run(() -> runs.add("after leaving")).cancel();
        forPlayer.runLater(() -> runs.add("after leaving"), 1);
        advanceTicks(3);

        assertEquals(List.of("repeat", "repeat"), runs);
    }

    @Test
    void aLocationTaskInAWorldThatIsNotLoadedIsRejected() {
        TaskScheduler nowhere = adapter.atLocation(UUID.randomUUID(), BLOCK_X, BLOCK_Z);

        assertThrows(IllegalStateException.class, () -> nowhere.run(() -> {
        }));
    }
}
