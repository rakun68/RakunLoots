package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotServerAdapterTest extends MockBukkitChunkReadTest {

    private SpigotServerAdapter adapter;

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        adapter = new SpigotServerAdapter(plugin);
        return adapter;
    }

    @Test
    void everyWorldReadAndChunkLoadRunsOnMainThreadInsideTheReadsOwnTasks() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        record Case(ChunkState state, UnloadedChunkPolicy policy, String expectedCall, int tasks) {
        }
        List<Case> cases = List.of(
                new Case(ChunkState.LOADED, UnloadedChunkPolicy.SKIP, "getChunkSnapshot", 1),
                new Case(ChunkState.GENERATED, UnloadedChunkPolicy.SKIP, "isChunkLoaded", 1),
                new Case(ChunkState.GENERATED, UnloadedChunkPolicy.LOAD, "loadChunk", 2),
                new Case(ChunkState.UNGENERATED, UnloadedChunkPolicy.LOAD, "isChunkGenerated", 2),
                new Case(ChunkState.UNGENERATED, UnloadedChunkPolicy.GENERATE, "loadChunk", 2));

        for (Case each : cases) {
            setChunkState(worldKey, CHUNK_X, CHUNK_Z, each.state());
            int runsBefore = server.getScheduler().runs().size();
            int callsBefore = world.calls().size();
            int lookupsBefore = server.worldLookups().size();

            read(worldKey, each.policy());

            List<RecordingScheduler.TaskRun> runs =
                    server.getScheduler().runs().subList(runsBefore, server.getScheduler().runs().size());
            List<FakeChunkWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size());
            assertEquals(each.tasks(), runs.size(), each + " ran " + each.tasks() + " tasks");
            runs.forEach(run -> assertTrue(run.mainThread(), each + " task ran on the main thread"));
            Set<OptionalInt> tasks = runs.stream().map(run -> OptionalInt.of(run.task())).collect(Collectors.toSet());
            List<RecordingServerMock.WorldLookup> lookups =
                    server.worldLookups().subList(lookupsBefore, server.worldLookups().size());
            assertEquals(each.tasks(), lookups.size(), each + " looked the world up once per task: " + lookups);
            for (RecordingServerMock.WorldLookup lookup : lookups) {
                assertTrue(lookup.mainThread(), each + ": " + lookup);
                assertTrue(tasks.contains(lookup.task()), each + ": " + lookup);
            }
            assertTrue(calls.stream().anyMatch(call -> call.method().equals(each.expectedCall())),
                    each + " made " + each.expectedCall() + ": " + calls);
            for (FakeChunkWorld.WorldCall call : calls) {
                assertTrue(call.mainThread(), each + ": " + call);
                assertTrue(tasks.contains(call.task()), each + ": " + call);
                assertNotEquals("getChunkAtAsync", call.method(), each + " called a Paper-only method");
            }
        }
    }

    @Test
    void severalLoadReadsRunOnePerTickInOrderOnTheMainThread() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, 0, 0, ChunkState.GENERATED);
        setChunkState(worldKey, 1, 0, ChunkState.GENERATED);
        setChunkState(worldKey, 2, 0, ChunkState.GENERATED);
        List<CompletableFuture<Optional<ChunkView>>> reads = List.of(
                adapter.readChunk(worldKey, 0, 0, UnloadedChunkPolicy.LOAD),
                adapter.readChunk(worldKey, 1, 0, UnloadedChunkPolicy.LOAD),
                adapter.readChunk(worldKey, 2, 0, UnloadedChunkPolicy.GENERATE),
                adapter.readChunk(worldKey, 3, 0, UnloadedChunkPolicy.GENERATE));

        server.getScheduler().performOneTick();

        assertEquals(0, loads(world).size(), "the first tick only queues the loads: " + world.calls());
        for (int tick = 0; tick < reads.size(); tick++) {
            server.getScheduler().performOneTick();

            assertEquals(tick + 1, loads(world).size(), "one load per tick: " + loads(world));
            for (int each = 0; each < reads.size(); each++) {
                assertEquals(each <= tick, reads.get(each).isDone(), "read " + each + " after load tick " + tick);
            }
        }
        for (int each = 0; each < reads.size(); each++) {
            assertEquals(each, reads.get(each).get(5, TimeUnit.SECONDS).orElseThrow().chunkX());
        }
        List<FakeChunkWorld.WorldCall> loads = loads(world);
        loads.forEach(load -> assertTrue(load.mainThread(), load.toString()));
        assertEquals(reads.size(), loads.stream().map(FakeChunkWorld.WorldCall::task).distinct().count(),
                "each load ran in a task of its own: " + loads);
    }

    @Test
    void aLoadedChunkReadIsNotDelayedBehindQueuedLoads() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(worldKey, 0, 0, ChunkState.GENERATED);
        setChunkState(worldKey, 1, 0, ChunkState.GENERATED);
        setChunkState(worldKey, 2, 0, ChunkState.GENERATED);
        setChunkState(worldKey, 5, 5, ChunkState.LOADED);
        setChunkState(worldKey, 6, 6, ChunkState.LOADED);
        List<CompletableFuture<Optional<ChunkView>>> loadReads = List.of(
                adapter.readChunk(worldKey, 0, 0, UnloadedChunkPolicy.LOAD),
                adapter.readChunk(worldKey, 1, 0, UnloadedChunkPolicy.LOAD),
                adapter.readChunk(worldKey, 2, 0, UnloadedChunkPolicy.LOAD));
        CompletableFuture<Optional<ChunkView>> sameTick = adapter.readChunk(worldKey, 5, 5, UnloadedChunkPolicy.LOAD);

        server.getScheduler().performOneTick();

        assertTrue(sameTick.isDone(), "read in the tick it was looked up");
        assertTrue(sameTick.get().isPresent());
        assertTrue(loadReads.stream().noneMatch(CompletableFuture::isDone), "no load ran yet");

        server.getScheduler().performOneTick();
        CompletableFuture<Optional<ChunkView>> whileQueued =
                adapter.readChunk(worldKey, 6, 6, UnloadedChunkPolicy.GENERATE);
        server.getScheduler().performOneTick();

        assertTrue(whileQueued.isDone(), "read while loads were still queued");
        assertTrue(whileQueued.get().isPresent());
        assertFalse(loadReads.get(2).isDone(), "the last load is still queued");
    }

    @Test
    void skipReadsNeverEnterTheLoadQueue() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, 0, 0, ChunkState.GENERATED);
        setChunkState(worldKey, 1, 0, ChunkState.GENERATED);
        setChunkState(worldKey, 7, 7, ChunkState.GENERATED);
        List<CompletableFuture<Optional<ChunkView>>> loadReads = List.of(
                adapter.readChunk(worldKey, 0, 0, UnloadedChunkPolicy.LOAD),
                adapter.readChunk(worldKey, 1, 0, UnloadedChunkPolicy.LOAD));
        CompletableFuture<Optional<ChunkView>> skip = adapter.readChunk(worldKey, 7, 7, UnloadedChunkPolicy.SKIP);
        int runsBefore = server.getScheduler().runs().size();

        server.getScheduler().performOneTick();

        assertTrue(skip.isDone(), "the skip read did not wait for the queued loads");
        assertEquals(Optional.empty(), skip.get());
        assertTrue(loadReads.stream().noneMatch(CompletableFuture::isDone), "no load ran yet");

        for (CompletableFuture<Optional<ChunkView>> read : loadReads) {
            assertTrue(runUntilDone(read).isPresent());
        }

        assertEquals(ChunkState.GENERATED, chunkState(worldKey, 7, 7));
        assertEquals(2, loads(world).size(), "only the load reads loaded: " + loads(world));
        assertEquals(3 + loadReads.size(), server.getScheduler().runs().size() - runsBefore,
                "one lookup task per read, and one queued task per load read only");
    }

    private static List<FakeChunkWorld.WorldCall> loads(FakeChunkWorld world) {
        return world.calls().stream().filter(call -> call.method().equals("loadChunk")).toList();
    }
}
