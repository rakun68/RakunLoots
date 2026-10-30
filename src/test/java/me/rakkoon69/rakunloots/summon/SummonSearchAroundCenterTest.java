package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonSearchAroundCenterTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    @Test
    void spotUsesPlayerRingAroundCenter() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        SummonTestWorlds.flatRegion(adapter, 0, 5, 0, 5);
        ConfigSnapshot snapshot = config(false);
        assertEquals(10, snapshot.summonRules("crate").orElseThrow().playerRing().min());
        assertEquals(30, snapshot.summonRules("crate").orElseThrow().playerRing().max());
        SummonSearch search = new SummonSearch(adapter, snapshot, new FakeSearchClock(), new Random(7));

        SummonResult result = search.aroundCenter(WORLD_KEY, "crate", 48, 70, 48, FREE).join();

        Spot spot = result.spot().orElseThrow(() -> new AssertionError("miss=" + result.missReason()));
        int distance = RingMath.chebyshev(48, 48, spot.x(), spot.z());
        assertTrue(distance >= 10 && distance <= 30, "distance=" + distance);
        assertTrue(adapter.reads().stream().allMatch(read -> read.policy() == UnloadedChunkPolicy.LOAD));
        assertEquals(java.util.List.of(), adapter.blockWrites(), "summon search writes no block");
    }

    @Test
    void aroundCenterUsesTheWorldUnloadedChunkPolicy() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        SummonTestWorlds.flatRegion(adapter, 0, 5, 0, 5);
        SummonSearch search = new SummonSearch(adapter, configWithPolicy("generate"), new FakeSearchClock(), new Random(3));

        search.aroundCenter(WORLD_KEY, "crate", 48, 70, 48, FREE).join();

        assertTrue(adapter.reads().stream().allMatch(read -> read.policy() == UnloadedChunkPolicy.GENERATE));
    }

    @Test
    void verticalOffsetNarrowsHeightWhenEnabled() {
        FakeServerAdapter low = new FakeServerAdapter(WORLD_KEY);
        SummonTestWorlds.flatRegion(low, 0, 5, 0, 5);
        ConfigSnapshot narrowed = config(true);
        assertTrue(narrowed.summonRules("crate").orElseThrow().verticalOffsetEnabled());
        assertEquals(8, narrowed.summonRules("crate").orElseThrow().verticalOffset().orElseThrow());

        boolean sawNarrowHit = false;
        for (int seed = 1; seed < 200; seed++) {
            SummonResult result = new SummonSearch(low, narrowed, new FakeSearchClock(), new Random(seed))
                    .aroundCenter(WORLD_KEY, "crate", 48, 70, 48, FREE).join();
            if (result.isHit()) {
                int y = result.spot().orElseThrow().y();
                assertTrue(y >= 62 && y <= 78, "narrowed height " + y);
                sawNarrowHit = true;
                break;
            }
        }
        assertTrue(sawNarrowHit);

        FakeServerAdapter high = new FakeServerAdapter(WORLD_KEY);
        for (int cx = 0; cx <= 5; cx++) {
            for (int cz = 0; cz <= 5; cz++) {
                FakeColumn column = new FakeColumn(WORLD_KEY, cx, cz, 0, 256);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        column.set(x, 90, z, "minecraft:grass_block").set(x, 91, z, "minecraft:air");
                    }
                }
                high.putChunk(column.view(), true, true);
            }
        }
        ConfigSnapshot wide = config(false);
        boolean sawHighSpot = false;
        for (int seed = 1; seed < 200; seed++) {
            SummonResult result = new SummonSearch(high, wide, new FakeSearchClock(), new Random(seed))
                    .aroundCenter(WORLD_KEY, "crate", 48, 70, 48, FREE).join();
            if (result.isHit() && result.spot().orElseThrow().y() > 78) {
                sawHighSpot = true;
                break;
            }
        }
        assertTrue(sawHighSpot, "expected a high spot with vertical offset disabled");

        boolean offsetMissesHigh = true;
        for (int seed = 1; seed < 100; seed++) {
            SummonResult result = new SummonSearch(high, narrowed, new FakeSearchClock(), new Random(seed))
                    .aroundCenter(WORLD_KEY, "crate", 48, 70, 48, FREE).join();
            if (result.isHit() && result.spot().orElseThrow().y() > 78) {
                offsetMissesHigh = false;
                break;
            }
        }
        assertTrue(offsetMissesHigh);
    }

    @Test
    void verticalOffsetOutsideTheLootRangeMisses() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        SummonTestWorlds.flatRegion(adapter, 0, 5, 0, 5);
        SummonSearch search = new SummonSearch(adapter, config(true), new FakeSearchClock(), new Random(1));

        SummonResult result = search.aroundCenter(WORLD_KEY, "crate", 48, 0, 48, FREE).join();

        assertEquals(MissReason.EMPTY_HEIGHT_RANGE, result.missReason().orElseThrow());
    }

    private static ConfigSnapshot config(boolean verticalOffset) {
        return config(verticalOffset, "load");
    }

    private static ConfigSnapshot configWithPolicy(String policy) {
        return config(false, policy);
    }

    private static ConfigSnapshot config(boolean verticalOffset, String policy) {
        String offsetBlock = verticalOffset
                ? """
                          vertical_offset_enable: true
                          vertical_offset: 8
                        """
                : """
                          vertical_offset_enable: false
                        """;
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds("""
                        worlds:
                          world:
                            summon_search:
                              unloaded_chunks: %s
                        """.formatted(policy))
                .loot("crate", """
                        language: en
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        summon_settings:
                          summon_type: top_surface
                          attempts: 40
                          y_range: '60:100'
                          replaceable_blocks:
                            - minecraft:air
                          player_ring:
                            min: 10
                            max: 30
                        """ + offsetBlock)
                .build(), written -> Optional.of(written), WarningKinds.all());
    }
}
