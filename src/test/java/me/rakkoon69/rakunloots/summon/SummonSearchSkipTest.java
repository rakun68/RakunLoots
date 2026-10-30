package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonSearchSkipTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    @Test
    void skipReadsOnlyLoadedChunksUnderSkipPolicy() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(adapter, 0, 2, 0, 2);
        adapter.putChunk(SummonTestWorlds.flatChunkView(3, 0), false, true);
        SummonSearch search = new SummonSearch(adapter, configWithAttempts(30), new FakeSearchClock(), new Random(2));

        search.serverSummon(WORLD_KEY, "crate", FREE).join();

        assertTrue(adapter.reads().stream().allMatch(read -> read.policy() == UnloadedChunkPolicy.SKIP));
        assertTrue(adapter.reads().stream().noneMatch(read -> read.chunkX() == 3));
    }

    @Test
    void firstScannedChunkWithSpotWins() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        adapter.putChunk(SummonTestWorlds.flatChunkView(1, 0), true, true);
        adapter.putChunk(SummonTestWorlds.flatChunkView(2, 0), true, true);
        SummonSearch search = new SummonSearch(adapter, configWithAttempts(1), new FakeSearchClock(), new Random(4));

        SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();

        assertTrue(result.isHit());
        Spot spot = result.spot().orElseThrow();
        int distance = RingMath.chebyshev(0, 0, spot.x(), spot.z());
        assertTrue(distance >= 8 && distance <= 40, "distance=" + distance);
        assertEquals(1, adapter.reads().size());
        assertEquals(adapter.reads().get(0).chunkX(), result.spot().orElseThrow().x() >> 4);
    }

    @Test
    void noLoadedChunksInRingIsItsOwnMissReason() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        adapter.putChunk(SummonTestWorlds.flatChunkView(20, 20), true, true);
        SummonSearch search = new SummonSearch(adapter, configWithAttempts(5), new FakeSearchClock(), new Random(1));

        SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();

        assertEquals(MissReason.NO_LOADED_CHUNKS_IN_RING, result.missReason().orElseThrow());
    }

    @Test
    void oneAttemptIsOneScannedChunk() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        // Empty air columns: no valid top-surface spot, so each attempt scans and moves on.
        adapter.putChunk(new FakeColumn(WORLD_KEY, 1, 0, 0, 256).view(), true, true);
        adapter.putChunk(new FakeColumn(WORLD_KEY, 2, 0, 0, 256).view(), true, true);
        SummonSearch search = new SummonSearch(adapter, configWithAttempts(2), new FakeSearchClock(), new Random(8));

        search.serverSummon(WORLD_KEY, "crate", FREE).join();

        assertEquals(2, adapter.reads().size());
    }

    private static me.rakkoon69.rakunloots.config.ConfigSnapshot configWithAttempts(int attempts) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds("""
                        worlds:
                          world:
                            summon_search:
                              mode: spawn
                              unloaded_chunks: skip
                              spawn_ring:
                                min: 8
                                max: 40
                        """)
                .loot("crate", SummonSearchFixtures.topSurfaceLoot(attempts, null))
                .build(), written -> Optional.of(written), WarningKinds.all());
    }
}
