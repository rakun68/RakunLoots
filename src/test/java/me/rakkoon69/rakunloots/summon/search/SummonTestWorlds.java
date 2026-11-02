package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.fake.FakeColumn;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.server.ChunkView;

import java.util.UUID;

/**
 * Registers flat grass test terrain on a fake server adapter.
 */
final class SummonTestWorlds {

    static final java.util.UUID WORLD_KEY = java.util.UUID.fromString("00000000-0000-0000-0000-000000000010");

    private SummonTestWorlds() {
    }

    static void flatRegion(FakeServerAdapter adapter, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        flatRegion(adapter, minChunkX, maxChunkX, minChunkZ, maxChunkZ, true, true);
    }

    static void flatRegion(
            FakeServerAdapter adapter,
            int minChunkX,
            int maxChunkX,
            int minChunkZ,
            int maxChunkZ,
            boolean loaded,
            boolean generated
    ) {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                adapter.putChunk(flatChunk(adapter.worldKey(), chunkX, chunkZ).view(), loaded, generated);
            }
        }
    }

    static FakeColumn flatChunk(java.util.UUID worldKey, int chunkX, int chunkZ) {
        FakeColumn column = new FakeColumn(worldKey, chunkX, chunkZ, 0, 256);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                column.set(x, 63, z, "minecraft:grass_block").set(x, 64, z, "minecraft:air");
            }
        }
        return column;
    }

    static ChunkView flatChunkView(java.util.UUID worldKey, int chunkX, int chunkZ) {
        return flatChunk(worldKey, chunkX, chunkZ).view();
    }

    static FakeColumn flatChunk(int chunkX, int chunkZ) {
        return flatChunk(WORLD_KEY, chunkX, chunkZ);
    }

    static ChunkView flatChunkView(int chunkX, int chunkZ) {
        return flatChunkView(WORLD_KEY, chunkX, chunkZ);
    }
}
