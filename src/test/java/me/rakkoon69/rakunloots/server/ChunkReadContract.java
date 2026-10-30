package me.rakkoon69.rakunloots.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chunk read every server adapter passes. Each adapter's test extends it with its own fake server and adds
 * its threading rule.
 */
abstract class ChunkReadContract {

    static final int MIN_HEIGHT = -64;
    static final int MAX_HEIGHT = 320;
    static final int CHUNK_X = -2;
    static final int CHUNK_Z = 3;

    private ServerAdapter adapter;

    /**
     * Starts a fake server with no worlds and returns the adapter under test, bound to it.
     */
    abstract ServerAdapter openAdapter();

    /**
     * Adds a loaded world with every chunk ungenerated and every block air.
     *
     * @return the new world's key
     */
    abstract UUID createWorld(int minHeight, int maxHeight);

    abstract void setChunkState(UUID world, int chunkX, int chunkZ, ChunkState state);

    abstract ChunkState chunkState(UUID world, int chunkX, int chunkZ);

    /**
     * Sets one block, in world block coordinates, whatever state its chunk is in.
     */
    abstract void setBlock(UUID world, int x, int y, int z, String blockId);

    /**
     * Runs the fake server until the future completes and returns its value.
     */
    abstract <T> T runUntilDone(CompletableFuture<T> future) throws Exception;

    @BeforeEach
    void openContract() {
        adapter = openAdapter();
    }

    @Test
    void loadedChunkReturnsBlockIdsAtSeveralHeightsIncludingBelowZero() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.LOADED);
        setBlock(world, blockX(5), -60, blockZ(9), "minecraft:diamond_ore");
        setBlock(world, blockX(0), -1, blockZ(15), "minecraft:deepslate");
        setBlock(world, blockX(15), 64, blockZ(0), "minecraft:oak_log");
        setBlock(world, blockX(7), MAX_HEIGHT - 1, blockZ(7), "minecraft:glass");

        ChunkView view = read(world, UnloadedChunkPolicy.SKIP).orElseThrow();

        assertEquals(world, view.worldKey());
        assertEquals(CHUNK_X, view.chunkX());
        assertEquals(CHUNK_Z, view.chunkZ());
        assertEquals(MIN_HEIGHT, view.minHeight());
        assertEquals(MAX_HEIGHT, view.maxHeight());
        assertEquals("minecraft:diamond_ore", view.blockId(5, -60, 9));
        assertEquals("minecraft:deepslate", view.blockId(0, -1, 15));
        assertEquals("minecraft:oak_log", view.blockId(15, 64, 0));
        assertEquals("minecraft:glass", view.blockId(7, MAX_HEIGHT - 1, 7));
        assertEquals("minecraft:air", view.blockId(5, 0, 9));
        assertEquals(ChunkState.LOADED, chunkState(world, CHUNK_X, CHUNK_Z));
    }

    @Test
    void skipOnUnloadedChunkMissesAndLoadsNothing() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        for (ChunkState unloaded : new ChunkState[]{ChunkState.GENERATED, ChunkState.UNGENERATED}) {
            setChunkState(world, CHUNK_X, CHUNK_Z, unloaded);

            assertMiss(read(world, UnloadedChunkPolicy.SKIP));

            assertEquals(unloaded, chunkState(world, CHUNK_X, CHUNK_Z));
        }
    }

    @Test
    void loadOnUnloadedGeneratedChunkReturnsView() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);
        setBlock(world, blockX(3), -10, blockZ(4), "minecraft:gold_ore");

        ChunkView view = read(world, UnloadedChunkPolicy.LOAD).orElseThrow();

        assertEquals("minecraft:gold_ore", view.blockId(3, -10, 4));
        assertEquals(ChunkState.LOADED, chunkState(world, CHUNK_X, CHUNK_Z));
    }

    @Test
    void loadOnUngeneratedChunkMissesAndGeneratesNothing() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);

        assertMiss(read(world, UnloadedChunkPolicy.LOAD));

        assertEquals(ChunkState.UNGENERATED, chunkState(world, CHUNK_X, CHUNK_Z));
    }

    @Test
    void generateOnUngeneratedChunkReturnsView() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);

        ChunkView view = read(world, UnloadedChunkPolicy.GENERATE).orElseThrow();

        assertEquals(CHUNK_X, view.chunkX());
        assertEquals(CHUNK_Z, view.chunkZ());
        assertEquals("minecraft:air", view.blockId(0, 0, 0));
        assertEquals(ChunkState.LOADED, chunkState(world, CHUNK_X, CHUNK_Z));
    }

    @Test
    void unloadedWorldMisses() throws Exception {
        createWorld(MIN_HEIGHT, MAX_HEIGHT);

        for (UnloadedChunkPolicy policy : UnloadedChunkPolicy.values()) {
            assertMiss(read(UUID.randomUUID(), policy));
        }
    }

    @Test
    void viewReadsOnAnotherThreadAfterReadCompletesAndKeepsWhatItRead() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.LOADED);
        setBlock(world, blockX(8), -30, blockZ(2), "minecraft:emerald_ore");
        ChunkView view = read(world, UnloadedChunkPolicy.SKIP).orElseThrow();
        setBlock(world, blockX(8), -30, blockZ(2), "minecraft:stone");

        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            String read = other.submit(() -> view.blockId(8, -30, 2)).get(5, TimeUnit.SECONDS);

            assertEquals("minecraft:emerald_ore", read);
        } finally {
            other.shutdownNow();
        }
    }

    Optional<ChunkView> read(UUID world, UnloadedChunkPolicy policy) throws Exception {
        return runUntilDone(adapter.readChunk(world, CHUNK_X, CHUNK_Z, policy));
    }

    static int blockX(int localX) {
        return CHUNK_X * 16 + localX;
    }

    static int blockZ(int localZ) {
        return CHUNK_Z * 16 + localZ;
    }

    static void assertMiss(Optional<ChunkView> read) {
        assertTrue(read.isEmpty(), "expected a miss");
    }
}
