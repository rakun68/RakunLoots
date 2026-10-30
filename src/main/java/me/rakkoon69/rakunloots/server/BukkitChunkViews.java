package me.rakkoon69.rakunloots.server;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import java.util.UUID;

/**
 * Builds chunk views from Bukkit chunks for the server adapters.
 */
final class BukkitChunkViews {

    private BukkitChunkViews() {
    }

    /**
     * Snapshots a loaded chunk. Call it on the thread that owns the chunk.
     */
    static ChunkView of(UUID worldKey, World world, Chunk chunk) {
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        return new ChunkView(worldKey, chunk.getX(), chunk.getZ(), world.getMinHeight(), world.getMaxHeight(),
                (x, y, z) -> snapshot.getBlockType(x, y, z).getKey().toString());
    }
}
