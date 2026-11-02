package me.rakkoon69.rakunloots.lootbox;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The chunk holds a summon search takes while it reads chunks no player keeps loaded, through the lifecycle's per-chunk
 * count (ADR 0015). The lifecycle is the real one; a search with no lifecycle holds nothing through {@link #NONE}.
 */
public interface ChunkHolds {

    /**
     * Holds nothing: every {@link #take} says the chunk is not held, and {@link #give} does nothing.
     */
    ChunkHolds NONE = new ChunkHolds() {
        @Override
        public CompletableFuture<Boolean> take(UUID worldKey, int chunkX, int chunkZ) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public void give(UUID worldKey, int chunkX, int chunkZ) {
        }
    };

    /**
     * Counts one more holder of the chunk, and asks the server to keep it loaded when it is the first. Never blocks
     * the calling thread.
     *
     * @return whether the chunk is held now, and counted: false, and not counted, when the server cannot hold it
     * (the world is not loaded, or the chunk is not generated), or the holder is stopped
     */
    CompletableFuture<Boolean> take(UUID worldKey, int chunkX, int chunkZ);

    /**
     * Gives back a hold {@link #take} answered true for, and releases the chunk when no other holder keeps it.
     */
    void give(UUID worldKey, int chunkX, int chunkZ);
}
