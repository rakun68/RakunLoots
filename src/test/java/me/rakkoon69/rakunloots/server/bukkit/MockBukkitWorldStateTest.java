package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.ServerAdapter;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world-state contract on a recording MockBukkit server, for the adapters that read world state on the Bukkit
 * main thread. It adds their threading rule: every read runs on the main thread.
 */
abstract class MockBukkitWorldStateTest extends WorldStateContract {

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
    UUID createWorld(int minHeight, int maxHeight) {
        return fakeServer.createWorld(minHeight, maxHeight);
    }

    @Override
    void setChunkState(UUID world, int chunkX, int chunkZ, ChunkState state) {
        fakeServer.world(world).setChunkState(chunkX, chunkZ, state);
    }

    @Override
    UUID addPlayer(UUID world, double x, double y, double z) {
        return fakeServer.addPlayer(world, x, y, z);
    }

    @Override
    void setSpawn(UUID world, int x, int y, int z) {
        fakeServer.world(world).setSpawn(x, y, z);
    }

    @Override
    <T> T runUntilDone(CompletableFuture<T> future) throws Exception {
        return fakeServer.runUntilDone(future);
    }

    @Test
    void loadedChunksAreReadOnTheMainThreadInOneTask() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, 4, 4, ChunkState.LOADED);

        OptionalInt task = readInOneMainThreadTask(adapter().loadedChunks(worldKey));

        List<FakeChunkWorld.WorldCall> reads = world.calls().stream()
                .filter(call -> call.method().equals("getLoadedChunks")).toList();
        assertEquals(1, reads.size(), "read the loaded chunks once: " + world.calls());
        assertTrue(reads.get(0).mainThread(), reads.get(0).toString());
        assertEquals(task, reads.get(0).task(), reads.get(0).toString());
    }

    @Test
    void playerPositionsAreReadOnTheMainThreadInOneTask() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        addPlayer(worldKey, 1.0, 64.0, 1.0);
        addPlayer(worldKey, 2.0, -10.0, 2.0);
        List<RecordingPlayer> players = fakeServer.players();
        List<Integer> readsBefore = players.stream().map(player -> player.locationReads().size()).toList();

        OptionalInt task = readInOneMainThreadTask(adapter().playerPositions(worldKey));

        List<FakeChunkWorld.WorldCall> playerLists = world.calls().stream()
                .filter(call -> call.method().equals("getPlayers")).toList();
        assertEquals(1, playerLists.size(), "listed the world's players once: " + world.calls());
        assertTrue(playerLists.get(0).mainThread(), playerLists.get(0).toString());
        assertEquals(task, playerLists.get(0).task(), playerLists.get(0).toString());
        for (int i = 0; i < players.size(); i++) {
            List<RecordingPlayer.LocationRead> reads = players.get(i).locationReads();
            List<RecordingPlayer.LocationRead> during = reads.subList(readsBefore.get(i), reads.size());
            assertFalse(during.isEmpty(), "read player " + i + "'s location");
            for (RecordingPlayer.LocationRead read : during) {
                assertTrue(read.mainThread(), "player " + i + ": " + read);
                assertEquals(task, read.task(), "player " + i + ": " + read);
            }
        }
    }

    @Test
    void worldSpawnIsReadOnTheMainThreadInOneTask() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        setSpawn(worldKey, 8, 70, -8);

        OptionalInt task = readInOneMainThreadTask(adapter().worldSpawn(worldKey));

        List<FakeChunkWorld.WorldCall> reads = world.calls().stream()
                .filter(call -> call.method().equals("getSpawnLocation")).toList();
        assertEquals(1, reads.size(), "read the spawn once: " + world.calls());
        assertTrue(reads.get(0).mainThread(), reads.get(0).toString());
        assertEquals(task, reads.get(0).task(), reads.get(0).toString());
    }

    @Test
    void loadedWorldsAreListedOnTheMainThread() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        RecordingServerMock server = fakeServer.server();
        int runsBefore = server.getScheduler().runs().size();

        List<LoadedWorld> loaded = runUntilDone(adapter().loadedWorlds());

        List<RecordingScheduler.TaskRun> runs = server.getScheduler().runs();
        assertEquals(runsBefore + 1, runs.size(), "ran one task");
        assertTrue(runs.get(runs.size() - 1).mainThread(), "the task ran on the main thread");
        assertTrue(loaded.stream().anyMatch(world -> world.key().equals(worldKey)));
    }

    @Test
    void unloadedWorldIsLookedUpOnTheMainThread() throws Exception {
        UUID unloaded = UUID.randomUUID();

        readInOneMainThreadTask(adapter().loadedChunks(unloaded));
        readInOneMainThreadTask(adapter().playerPositions(unloaded));
        readInOneMainThreadTask(adapter().worldSpawn(unloaded));
    }

    /**
     * Runs the read and checks it ran as one main-thread task that looked the world up once.
     *
     * @return that task
     */
    private OptionalInt readInOneMainThreadTask(CompletableFuture<?> read) throws Exception {
        RecordingServerMock server = fakeServer.server();
        int runsBefore = server.getScheduler().runs().size();
        int lookupsBefore = server.worldLookups().size();

        runUntilDone(read);

        List<RecordingScheduler.TaskRun> runs = server.getScheduler().runs();
        assertEquals(runsBefore + 1, runs.size(), "ran one task");
        RecordingScheduler.TaskRun run = runs.get(runs.size() - 1);
        assertTrue(run.mainThread(), "the task ran on the main thread");
        List<RecordingServerMock.WorldLookup> lookups =
                server.worldLookups().subList(lookupsBefore, server.worldLookups().size());
        assertEquals(1, lookups.size(), "looked the world up once: " + lookups);
        assertTrue(lookups.get(0).mainThread(), lookups.get(0).toString());
        assertEquals(OptionalInt.of(run.task()), lookups.get(0).task(), lookups.get(0).toString());
        return OptionalInt.of(run.task());
    }
}
