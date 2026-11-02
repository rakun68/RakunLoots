package me.rakkoon69.rakunloots.server.bukkit;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loads through Paper's async chunk call, so a load never blocks the main thread. Paper completes the call on the
 * main thread, and without generate it completes with null for a chunk that is not generated.
 */
final class PaperChunkLoader implements ChunkLoader {

    @Override
    public CompletableFuture<Optional<Chunk>> load(World world, int chunkX, int chunkZ, boolean generate) {
        return world.getChunkAtAsync(chunkX, chunkZ, generate).thenApply(Optional::ofNullable);
    }
}
