package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The world-state contract on a hand-faked Folia server, plus Folia's threading rule: each player is read on their
 * own entity scheduler, the spawn is read on the global region, and the Bukkit scheduler is never called.
 */
class FoliaServerAdapterWorldStateTest extends WorldStateContract {

    FakeFoliaServer fakeServer;

    @Override
    ServerAdapter openAdapter() {
        fakeServer = new FakeFoliaServer();
        return new FoliaServerAdapter(fakeServer.plugin());
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
    void worldSpawnIsReadOnTheGlobalRegion() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setSpawn(worldKey, 8, 70, -8);

        CompletableFuture<Optional<BlockPosition>> read = adapter().worldSpawn(worldKey);

        assertFalse(read.isDone(), "the read waits for a global region tick");
        assertEquals(Optional.of(new BlockPosition(8, 70, -8)), runUntilDone(read));
        List<FakeFoliaWorld.WorldCall> reads = fakeServer.world(worldKey).calls().stream()
                .filter(call -> call.method().equals("getSpawnLocation")).toList();
        assertEquals(1, reads.size(), "read the spawn once: " + reads);
        assertEquals(Optional.of(new FakeFoliaTicks.Global()), reads.get(0).owner(), reads.get(0).toString());
    }

    @Test
    void loadedWorldsAreListedOnTheGlobalRegion() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);

        CompletableFuture<List<LoadedWorld>> read = adapter().loadedWorlds();

        assertFalse(read.isDone(), "the read waits for a global region tick");
        assertEquals(List.of(new LoadedWorld(worldKey, "world-" + worldKey)), runUntilDone(read));
    }

    @Test
    void loadedChunksAreListedOnceWithoutWaitingForATick() {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(worldKey, 4, 4, ChunkState.LOADED);

        CompletableFuture<Set<ChunkPosition>> read = adapter().loadedChunks(worldKey);

        assertEquals(Set.of(new ChunkPosition(4, 4)), read.getNow(null), "complete before any tick");
        List<FakeFoliaWorld.WorldCall> reads = fakeServer.world(worldKey).calls().stream()
                .filter(call -> call.method().equals("getLoadedChunks")).toList();
        assertEquals(1, reads.size(), "listed the loaded chunks once: " + reads);
    }

    @Test
    void eachPlayerIsReadOnTheirOwnEntityScheduler() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        List<UUID> players = List.of(addPlayer(world, 1.0, 64.0, 1.0), addPlayer(world, 300.0, -10.0, -300.0));

        runUntilDone(adapter().playerPositions(world));

        for (UUID player : players) {
            List<FakeFoliaPlayer.StateRead> reads = fakeServer.player(player).reads();
            assertFalse(reads.isEmpty(), "read the player's state");
            for (FakeFoliaPlayer.StateRead read : reads) {
                assertEquals(Optional.of(new FakeFoliaTicks.Entity(player)), read.owner(), read.toString());
            }
        }
    }

    @Test
    void aPlayerWhoLeavesDuringTheGatherIsLeftOutAndTheGatherCompletes() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID staying = addPlayer(world, 1.0, 64.0, 1.0);
        UUID leaving = addPlayer(world, 2.0, 64.0, 2.0);

        CompletableFuture<List<PlayerPosition>> gather = adapter().playerPositions(world);
        fakeServer.removePlayer(leaving);

        assertEquals(List.of(new PlayerPosition(staying, 1.0, 64.0, 1.0)), runUntilDone(gather));
    }

    @Test
    void aPlayerWhoseEntitySchedulerHasAlreadyRetiredIsLeftOutAndTheGatherCompletes() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID staying = addPlayer(world, 1.0, 64.0, 1.0);
        UUID leaving = addPlayer(world, 2.0, 64.0, 2.0);
        fakeServer.player(leaving).scheduler().retire();

        assertEquals(List.of(new PlayerPosition(staying, 1.0, 64.0, 1.0)),
                runUntilDone(adapter().playerPositions(world)));
    }

    @Test
    void aPlayerWhoChangesWorldDuringTheGatherIsLeftOut() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID otherWorld = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID staying = addPlayer(world, 1.0, 64.0, 1.0);
        UUID moving = addPlayer(world, 2.0, 64.0, 2.0);

        CompletableFuture<List<PlayerPosition>> gather = adapter().playerPositions(world);
        fakeServer.movePlayer(moving, otherWorld);

        assertEquals(List.of(new PlayerPosition(staying, 1.0, 64.0, 1.0)), runUntilDone(gather));
    }
}
