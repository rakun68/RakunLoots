package me.rakkoon69.rakunloots.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world-state reads every server adapter passes: a world's loaded chunks, its player positions, and its spawn.
 * Each adapter's test extends it with its own fake server and adds its threading rule.
 */
abstract class WorldStateContract {

    static final int MIN_HEIGHT = -64;
    static final int MAX_HEIGHT = 320;

    private ServerAdapter adapter;

    /**
     * Starts a fake server with no worlds and no players and returns the adapter under test, bound to it.
     */
    abstract ServerAdapter openAdapter();

    /**
     * Adds a loaded world with every chunk ungenerated.
     *
     * @return the new world's key
     */
    abstract UUID createWorld(int minHeight, int maxHeight);

    abstract void setChunkState(UUID world, int chunkX, int chunkZ, ChunkState state);

    /**
     * Adds an online player standing at a position in a world.
     *
     * @return the player's UUID
     */
    abstract UUID addPlayer(UUID world, double x, double y, double z);

    /**
     * Moves a world's spawn to a block.
     */
    abstract void setSpawn(UUID world, int x, int y, int z);

    /**
     * Runs the fake server until the future completes and returns its value.
     */
    abstract <T> T runUntilDone(CompletableFuture<T> future) throws Exception;

    @BeforeEach
    void openContract() {
        adapter = openAdapter();
    }

    /**
     * The adapter from {@link #openAdapter()}.
     */
    ServerAdapter adapter() {
        return adapter;
    }

    @Test
    void loadedChunksMatchTheFakeWorld() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID otherWorld = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, 0, 0, ChunkState.LOADED);
        setChunkState(world, -3, 7, ChunkState.LOADED);
        setChunkState(world, 12, -40, ChunkState.LOADED);
        setChunkState(world, 1, 1, ChunkState.GENERATED);
        setChunkState(otherWorld, 5, 5, ChunkState.LOADED);

        Set<ChunkPosition> loaded = runUntilDone(adapter.loadedChunks(world));

        assertEquals(Set.of(new ChunkPosition(0, 0), new ChunkPosition(-3, 7), new ChunkPosition(12, -40)), loaded);
    }

    @Test
    void worldWithNoLoadedChunksHasNone() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, 2, 2, ChunkState.GENERATED);

        assertTrue(runUntilDone(adapter.loadedChunks(world)).isEmpty());
    }

    @Test
    void playerPositionsMatchTheFakePlayersInThatWorldOnly() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID otherWorld = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID deep = addPlayer(world, 10.5, -40.0, -3.25);
        UUID high = addPlayer(world, -200.0, 150.5, 64.0);
        addPlayer(otherWorld, 1.0, 70.0, 1.0);

        List<PlayerPosition> positions = runUntilDone(adapter.playerPositions(world));

        assertEquals(Set.of(new PlayerPosition(deep, 10.5, -40.0, -3.25), new PlayerPosition(high, -200.0, 150.5, 64.0)),
                Set.copyOf(positions));
        assertEquals(2, positions.size(), "one position per player: " + positions);
    }

    @Test
    void worldWithNoPlayersHasNoPositions() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID otherWorld = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        addPlayer(otherWorld, 1.0, 70.0, 1.0);

        assertTrue(runUntilDone(adapter.playerPositions(world)).isEmpty());
    }

    @Test
    void unloadedWorldReturnsEmptyForBoth() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, 0, 0, ChunkState.LOADED);
        addPlayer(world, 0.0, 64.0, 0.0);
        UUID unloaded = UUID.randomUUID();

        assertTrue(runUntilDone(adapter.loadedChunks(unloaded)).isEmpty());
        assertTrue(runUntilDone(adapter.playerPositions(unloaded)).isEmpty());
    }

    @Test
    void worldSpawnMatchesTheFakeWorld() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID otherWorld = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setSpawn(world, -120, -20, 37);
        setSpawn(otherWorld, 5, 70, 5);

        assertEquals(Optional.of(new BlockPosition(-120, -20, 37)), runUntilDone(adapter.worldSpawn(world)));
    }

    @Test
    void unloadedWorldHasNoSpawn() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setSpawn(world, 0, 64, 0);

        assertEquals(Optional.empty(), runUntilDone(adapter.worldSpawn(UUID.randomUUID())));
    }

    @Test
    void loadedWorldsListsEveryLoadedWorld() throws Exception {
        UUID first = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        UUID second = createWorld(MIN_HEIGHT, MAX_HEIGHT);

        List<LoadedWorld> loaded = runUntilDone(adapter.loadedWorlds());

        assertEquals(Set.of(first, second), loaded.stream().map(LoadedWorld::key).collect(java.util.stream.Collectors.toSet()));
        assertTrue(loaded.stream().allMatch(world -> !world.name().isBlank()));
    }
}
