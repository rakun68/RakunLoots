package me.rakkoon69.rakunloots.server.bukkit;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loads a chunk that is not loaded, the one thing Paper and Spigot do differently (ADR 0020). Paper uses its async
 * chunk call, and Spigot queues the load and runs at most one a tick on the main thread.
 */
interface ChunkLoader {

    /**
     * Loads the chunk, generating it only when asked. Call it on the main thread. The future completes on the main
     * thread with the loaded chunk, or empty when the chunk is not generated and generate is off, or when the world
     * is gone by the time the load runs.
     */
    CompletableFuture<Optional<Chunk>> load(World world, int chunkX, int chunkZ, boolean generate);
}
