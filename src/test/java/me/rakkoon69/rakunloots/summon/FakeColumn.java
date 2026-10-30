package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.server.ChunkView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a one-column chunk view for placement tests. Unset blocks are air.
 */
final class FakeColumn {

    private final UUID worldKey;
    private final int chunkX;
    private final int chunkZ;
    private final int minHeight;
    private final int maxHeight;
    private final Map<Long, String> blocks = new HashMap<>();

    FakeColumn(int chunkX, int chunkZ, int minHeight, int maxHeight) {
        this(UUID.fromString("00000000-0000-0000-0000-000000000001"), chunkX, chunkZ, minHeight, maxHeight);
    }

    FakeColumn(UUID worldKey, int chunkX, int chunkZ, int minHeight, int maxHeight) {
        this.worldKey = worldKey;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    FakeColumn set(int localX, int y, int localZ, String blockId) {
        blocks.put(key(localX, y, localZ), blockId);
        return this;
    }

    ChunkView view() {
        return new ChunkView(worldKey, chunkX, chunkZ, minHeight, maxHeight, (x, y, z) ->
                blocks.getOrDefault(key(x, y, z), "minecraft:air"));
    }

    UUID worldKey() {
        return worldKey;
    }

    private static long key(int x, int y, int z) {
        return (((long) x & 0xFF) << 48) | (((long) (y + 512) & 0xFFFF) << 16) | ((long) z & 0xFF);
    }
}
