package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitServerAdapterPaperTest extends MockBukkitChunkReadTest {

    /**
     * Calls that load a chunk, or read the disk for one, on the calling thread, when the chunk is not loaded.
     */
    private static final Set<String> BLOCKING_LOADS = Set.of("loadChunk", "getChunkAt", "isChunkGenerated");

    private BukkitServerAdapter adapter;

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        adapter = new BukkitServerAdapter(plugin, new PaperChunkLoader());
        return adapter;
    }

    @Test
    void unloadedChunkLoadsThroughTheAsyncChunkCallWithTheGenerateFlagFromThePolicy() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        record Case(ChunkState state, UnloadedChunkPolicy policy, boolean generate) {
        }
        List<Case> cases = List.of(
                new Case(ChunkState.GENERATED, UnloadedChunkPolicy.LOAD, false),
                new Case(ChunkState.UNGENERATED, UnloadedChunkPolicy.LOAD, false),
                new Case(ChunkState.GENERATED, UnloadedChunkPolicy.GENERATE, true),
                new Case(ChunkState.UNGENERATED, UnloadedChunkPolicy.GENERATE, true));

        for (Case each : cases) {
            setChunkState(worldKey, CHUNK_X, CHUNK_Z, each.state());
            int callsBefore = world.calls().size();

            read(worldKey, each.policy());

            List<FakeChunkWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size());
            List<FakeChunkWorld.WorldCall> asyncLoads = calls.stream()
                    .filter(call -> call.method().equals("getChunkAtAsync")).toList();
            assertEquals(1, asyncLoads.size(), each + " loaded through the async chunk call once: " + calls);
            assertEquals(Optional.of(each.generate()), asyncLoads.get(0).generate(), each + ": " + calls);
            for (FakeChunkWorld.WorldCall call : calls) {
                assertFalse(BLOCKING_LOADS.contains(call.method()), each + " ran a blocking chunk load: " + call);
                assertTrue(call.mainThread(), each + ": " + call);
            }
        }
    }

    @Test
    void snapshotIsTakenOnTheMainThreadWhenTheAsyncLoadCompletes() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);

        CompletableFuture<Optional<ChunkView>> read =
                adapter.readChunk(worldKey, CHUNK_X, CHUNK_Z, UnloadedChunkPolicy.LOAD);
        server.getScheduler().performOneTick();

        assertFalse(read.isDone(), "the read waits for the async load");
        assertTrue(world.calls().stream().noneMatch(call -> call.method().equals("getChunkSnapshot")),
                "no snapshot before the load completes: " + world.calls());

        world.finishAsyncLoads();

        assertTrue(read.get(5, TimeUnit.SECONDS).isPresent());
        List<FakeChunkWorld.WorldCall> snapshots = world.calls().stream()
                .filter(call -> call.method().equals("getChunkSnapshot")).toList();
        assertEquals(1, snapshots.size(), "one snapshot: " + world.calls());
        assertTrue(snapshots.get(0).mainThread(), "snapshot on the main thread: " + snapshots.get(0));
    }

    @Test
    void loadedChunkIsReadWithoutALoadUnderEveryPolicy() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, CHUNK_X, CHUNK_Z, ChunkState.LOADED);

        for (UnloadedChunkPolicy policy : UnloadedChunkPolicy.values()) {
            int callsBefore = world.calls().size();

            assertTrue(read(worldKey, policy).isPresent(), policy + " read the loaded chunk");

            List<FakeChunkWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size());
            for (FakeChunkWorld.WorldCall call : calls) {
                assertNotEquals("getChunkAtAsync", call.method(), policy + ": " + call);
                assertNotEquals("loadChunk", call.method(), policy + ": " + call);
                assertNotEquals("isChunkGenerated", call.method(), policy + ": " + call);
                assertTrue(call.mainThread(), policy + ": " + call);
            }
        }
    }

    @Test
    void skipNeverCallsTheAsyncChunkCall() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);

        for (ChunkState state : ChunkState.values()) {
            setChunkState(worldKey, CHUNK_X, CHUNK_Z, state);
            int callsBefore = world.calls().size();

            read(worldKey, UnloadedChunkPolicy.SKIP);

            List<FakeChunkWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size());
            for (FakeChunkWorld.WorldCall call : calls) {
                assertNotEquals("getChunkAtAsync", call.method(), state + " under skip: " + call);
                assertNotEquals("loadChunk", call.method(), state + " under skip: " + call);
                assertTrue(call.mainThread(), state + " under skip: " + call);
            }
        }
    }
}
