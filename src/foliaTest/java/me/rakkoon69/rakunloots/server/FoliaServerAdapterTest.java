package me.rakkoon69.rakunloots.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chunk-read contract on a hand-faked Folia server, plus Folia's threading rule.
 */
class FoliaServerAdapterTest extends ChunkReadContract {

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

    @Test
    void everyChunkReadRunsOnTheRegionSchedulerForThatChunk() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeFoliaWorld world = fakeServer.world(worldKey);
        FakeFoliaTicks.Region region = new FakeFoliaTicks.Region(worldKey, CHUNK_X, CHUNK_Z);
        record Case(ChunkState state, UnloadedChunkPolicy policy, String expectedCall) {
        }
        List<Case> cases = List.of(
                new Case(ChunkState.LOADED, UnloadedChunkPolicy.SKIP, "getChunkSnapshot"),
                new Case(ChunkState.LOADED, UnloadedChunkPolicy.LOAD, "getChunkSnapshot"),
                new Case(ChunkState.LOADED, UnloadedChunkPolicy.GENERATE, "getChunkSnapshot"),
                new Case(ChunkState.GENERATED, UnloadedChunkPolicy.SKIP, "isChunkLoaded"),
                new Case(ChunkState.GENERATED, UnloadedChunkPolicy.LOAD, "getChunkSnapshot"),
                new Case(ChunkState.UNGENERATED, UnloadedChunkPolicy.SKIP, "isChunkLoaded"),
                new Case(ChunkState.UNGENERATED, UnloadedChunkPolicy.LOAD, "getChunkAtAsync"),
                new Case(ChunkState.UNGENERATED, UnloadedChunkPolicy.GENERATE, "getChunkSnapshot"));

        for (Case each : cases) {
            setChunkState(worldKey, CHUNK_X, CHUNK_Z, each.state());
            int scheduledBefore = fakeServer.regionScheduler().scheduled().size();
            int callsBefore = world.calls().size();

            read(worldKey, each.policy());

            List<FakeFoliaTicks.Region> scheduled = fakeServer.regionScheduler().scheduled()
                    .subList(scheduledBefore, fakeServer.regionScheduler().scheduled().size());
            List<FakeFoliaWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size());
            assertFalse(scheduled.isEmpty(), each + " scheduled a region task");
            for (FakeFoliaTicks.Region task : scheduled) {
                assertEquals(region, task, each + " scheduled on the region for that chunk");
            }
            assertTrue(calls.stream().anyMatch(call -> call.method().equals(each.expectedCall())),
                    each + " made " + each.expectedCall() + ": " + calls);
            for (FakeFoliaWorld.WorldCall call : calls) {
                assertEquals(Optional.of(region), call.owner(), each + ": " + call);
            }
        }
        assertEquals(List.of(), fakeServer.bukkitSchedulerCalls(), "the Bukkit scheduler was called");
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
    ChunkState chunkState(UUID world, int chunkX, int chunkZ) {
        return fakeServer.world(world).chunkState(chunkX, chunkZ);
    }

    @Override
    void setBlock(UUID world, int x, int y, int z, String blockId) {
        fakeServer.setBlock(world, x, y, z, blockId);
    }

    @Override
    <T> T runUntilDone(CompletableFuture<T> future) throws Exception {
        return fakeServer.runUntilDone(future);
    }
}
