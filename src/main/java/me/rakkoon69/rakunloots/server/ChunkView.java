package me.rakkoon69.rakunloots.server;

import java.util.Objects;
import java.util.UUID;

/**
 * One chunk as it was when it was read. Read-only and safe on any thread. It is built from a chunk snapshot and
 * holds no live chunk or world, so later changes to the world do not show in it.
 */
public final class ChunkView {

    /**
     * The namespaced block id at a block of a chunk snapshot, in chunk-local x and z.
     */
    @FunctionalInterface
    public interface BlockIds {
        String at(int x, int y, int z);
    }

    private final UUID worldKey;
    private final int chunkX;
    private final int chunkZ;
    private final int minHeight;
    private final int maxHeight;
    private final BlockIds blockIds;

    /**
     * @param minHeight the world's lowest block y, inclusive
     * @param maxHeight the world's height limit, exclusive
     * @param blockIds  reads the snapshot; it must be safe on any thread and hold no live chunk or world
     */
    public ChunkView(UUID worldKey, int chunkX, int chunkZ, int minHeight, int maxHeight, BlockIds blockIds) {
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        if (minHeight >= maxHeight) {
            throw new IllegalArgumentException("minHeight must be below maxHeight");
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.blockIds = Objects.requireNonNull(blockIds, "blockIds");
    }

    public UUID worldKey() {
        return worldKey;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /**
     * The world's lowest block y, inclusive. Below zero on 1.18 overworlds.
     */
    public int minHeight() {
        return minHeight;
    }

    /**
     * The world's height limit, exclusive.
     */
    public int maxHeight() {
        return maxHeight;
    }

    /**
     * The namespaced block id, such as {@code minecraft:stone}, at one block of this chunk.
     *
     * @param x chunk-local x, 0 to 15
     * @param y block y, from {@link #minHeight()} up to but not including {@link #maxHeight()}
     * @param z chunk-local z, 0 to 15
     */
    public String blockId(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) {
            throw new IllegalArgumentException("x and z must be 0 to 15, got " + x + ", " + z);
        }
        if (y < minHeight || y >= maxHeight) {
            throw new IllegalArgumentException("y must be " + minHeight + " to " + (maxHeight - 1) + ", got " + y);
        }
        return blockIds.at(x, y, z);
    }
}
