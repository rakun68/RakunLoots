package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The chunk-read contract on a recording MockBukkit server with fakeServer chunk worlds, for the adapters that run on
 * the Bukkit main thread.
 */
abstract class MockBukkitChunkReadTest extends ChunkReadContract {

    MockBukkitWorlds fakeServer;
    RecordingServerMock server;

    /**
     * The adapter under test, bound to the mock server.
     */
    abstract ServerAdapter newAdapter(Plugin plugin);

    @Override
    final ServerAdapter openAdapter() {
        fakeServer = MockBukkitWorlds.start();
        server = fakeServer.server();
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
