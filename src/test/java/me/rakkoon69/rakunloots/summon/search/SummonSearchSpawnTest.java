package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonSearchSpawnTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final String WORLD = "world";
    private static final String LOOT = "crate";
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    private FakeServerAdapter adapter;
    private FakeSearchClock clock;
    private SummonSearch search;

    @BeforeEach
    void setUp() {
        adapter = new FakeServerAdapter(WORLD_KEY);
        clock = new FakeSearchClock();
        adapter.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(adapter, -2, 2, -2, 2);
        search = new SummonSearch(adapter, baseConfig("""
                worlds:
                  world:
                    summon_search:
                      mode: spawn
                      unloaded_chunks: load
                      spawn_ring:
                        min: 5
                        max: 20
                """), clock, new Random(42));
        search.start();
    }

    @AfterEach
    void tearDown() {
        search.stop();
    }

    @Test
    void hitLandsInsideSpawnRingAndOutsideMinimum() {
        SummonResult result = search.serverSummon(WORLD_KEY, LOOT, FREE).join();

        Spot spot = result.spot().orElseThrow();
        int distance = RingMath.chebyshev(0, 0, spot.x(), spot.z());
        assertTrue(distance >= 5 && distance <= 20);
    }

    @Test
    void loadPolicyMissesUngeneratedChunk() {
        FakeServerAdapter empty = new FakeServerAdapter(WORLD_KEY);
        empty.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(empty, -2, 2, -2, 2, false, false);
        SummonSearch loadSearch = new SummonSearch(empty, baseConfig("""
                worlds:
                  world:
                    summon_search:
                      mode: spawn
                      unloaded_chunks: load
                      spawn_ring:
                        min: 0
                        max: 8
                """), clock, new Random(42));

        SummonResult result = loadSearch.serverSummon(WORLD_KEY, LOOT, FREE).join();

        assertEquals(SearchMiss.ATTEMPTS_USED_UP, result.missReason().orElseThrow());
    }

    @Test
    void generatePolicyCanHitUngeneratedChunk() {
        FakeServerAdapter gen = new FakeServerAdapter(WORLD_KEY);
        gen.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(gen, -1, 1, -1, 1, false, false);
        SummonSearch generateSearch = new SummonSearch(gen, baseConfig("""
                worlds:
                  world:
                    summon_search:
                      mode: spawn
                      unloaded_chunks: generate
                      spawn_ring:
                        min: 0
                        max: 8
                """), clock, new Random(1));
        generateSearch.start();

        SummonResult result = generateSearch.serverSummon(WORLD_KEY, LOOT, FREE).join();

        assertTrue(result.isHit());
        generateSearch.stop();
    }

    @Test
    void reportsMissReasonsFromTicket() {
        assertEquals(SearchMiss.UNKNOWN_LOOT,
                search.serverSummon(WORLD_KEY, "missing", FREE).join().missReason().orElseThrow());

        adapter.clearSpawn();
        assertEquals(SearchMiss.WORLD_NOT_LOADED,
                search.serverSummon(WORLD_KEY, LOOT, FREE).join().missReason().orElseThrow());
        adapter.setSpawn(0, 64, 0);

        FakeServerAdapter empty = new FakeServerAdapter(WORLD_KEY);
        empty.setSpawn(0, 64, 0);
        SummonSearch exhaustedSearch = new SummonSearch(empty, baseConfig("""
                worlds:
                  world:
                    summon_search:
                      mode: spawn
                      unloaded_chunks: load
                      spawn_ring:
                        min: 0
                        max: 8
                """), clock, new Random(1));
        assertEquals(SearchMiss.ATTEMPTS_USED_UP,
                exhaustedSearch.serverSummon(WORLD_KEY, LOOT, FREE).join().missReason().orElseThrow());
    }

    @Test
    void readsEachChunkAtMostOncePerSearch() {
        search.serverSummon(WORLD_KEY, LOOT, FREE).join();

        Map<String, Integer> counts = new HashMap<>();
        for (FakeServerAdapter.ReadRecord read : adapter.reads()) {
            String key = read.chunkX() + ":" + read.chunkZ() + ":" + read.policy();
            counts.merge(key, 1, Integer::sum);
        }
        for (int count : counts.values()) {
            assertEquals(1, count);
        }
    }

    private static ConfigSnapshot baseConfig(String worlds) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds(worlds)
                .loot(LOOT, SummonSearchFixtures.topSurfaceLoot(30, null))
                .build(), written -> Optional.of(written), WarningKinds.all());
    }
}
