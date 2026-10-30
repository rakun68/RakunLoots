package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.SummonSearchSettings;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonSearchSmartTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final UUID NEAR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FAR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    private static final int[] SEEDS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20
    };

    @Test
    void pickBiasCapsNearestVersusFarthestShare() {
        UUID middle = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<PlayerPosition> players = List.of(
                new PlayerPosition(NEAR, 0, 64, 0),
                new PlayerPosition(middle, 100, 64, 0),
                new PlayerPosition(FAR, 400, 64, 0)
        );
        int nearPicks = 0;
        int farPicks = 0;
        for (int seed : SEEDS) {
            Random random = new Random(seed);
            for (int attempt = 0; attempt < 400; attempt++) {
                PlayerPosition picked = RingMath.pickWeightedPlayer(players, 100, 0, 20, random);
                if (picked.playerId().equals(middle)) {
                    nearPicks++;
                } else if (picked.playerId().equals(FAR)) {
                    farPicks++;
                }
            }
        }
        assertTrue(farPicks > 0, "far picks=" + farPicks);
        double ratio = nearPicks / (double) farPicks;
        assertTrue(ratio > 18.0 && ratio < 24.0, "ratio=" + ratio + " near=" + nearPicks + " far=" + farPicks);
    }

    @Test
    void everyPlayerCanBePickedAcrossSeeds() {
        boolean sawNear = false;
        boolean sawFar = false;
        for (int seed : SEEDS) {
            FakeServerAdapter adapter = worldWithPlayers();
            SummonSearch search = new SummonSearch(adapter, smartConfig(20, 70, SummonSearchSettings.WhenAllAbsent.NONE),
                    new FakeSearchClock(), new Random(seed));
            for (int attempt = 0; attempt < 100; attempt++) {
                SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
                if (result.isHit()) {
                    int cx = result.spot().orElseThrow().x();
                    if (cx < 200) {
                        sawNear = true;
                    } else {
                        sawFar = true;
                    }
                }
            }
            if (sawNear && sawFar) {
                break;
            }
        }
        assertTrue(sawNear && sawFar);
    }

    @Test
    void medianPullMatchesConfiguredShare() {
        int facing = 0;
        int total = 0;
        for (int seed : SEEDS) {
            FakeServerAdapter adapter = worldWithPlayers();
            SummonSearch search = new SummonSearch(adapter, smartConfig(20, 70, SummonSearchSettings.WhenAllAbsent.NONE),
                    new FakeSearchClock(), new Random(seed));
            for (int attempt = 0; attempt < 150; attempt++) {
                SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
                if (result.isHit()) {
                    Spot spot = result.spot().orElseThrow();
                    double medianX = (100.0 + 300.0) / 2.0;
                    double medianZ = 0.0;
                    int centerX = spot.x() < 200 ? 100 : 300;
                    if (facesMedian(centerX, 0, spot.x(), spot.z(), medianX, medianZ)) {
                        facing++;
                    }
                    total++;
                }
            }
        }
        assertTrue(total > 0);
        double share = facing / (double) total;
        assertTrue(share > 0.55 && share < 0.85, "share=" + share);
    }

    @Test
    void medianPullOf50IsEven() {
        int facing = 0;
        int total = 0;
        for (int seed : SEEDS) {
            FakeServerAdapter adapter = worldWithPlayers();
            SummonSearch search = new SummonSearch(adapter, smartConfig(0, 50, SummonSearchSettings.WhenAllAbsent.NONE),
                    new FakeSearchClock(), new Random(seed));
            for (int attempt = 0; attempt < 40; attempt++) {
                SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
                if (result.isHit()) {
                    Spot spot = result.spot().orElseThrow();
                    int centerX = spot.x() < 200 ? 100 : 300;
                    if (facesMedian(centerX, 0, spot.x(), spot.z(), 200, 0)) {
                        facing++;
                    }
                    total++;
                }
            }
        }
        assertTrue(total > 0);
        double share = facing / (double) total;
        assertTrue(share > 0.35 && share < 0.65, "share=" + share);
    }

    @Test
    void medianPullOf100AimsAtTheMedian() {
        PlayerPosition player = new PlayerPosition(NEAR, 0, 64, 0);
        for (int seed : SEEDS) {
            double angle = RingMath.sampleSmartAngle(player, 100, 0, 100, new Random(seed));
            assertFalse(Double.isNaN(angle));
            assertEquals(0.0, angle, 1e-9);
        }
    }

    @Test
    void spotStaysOutsideSmartRingMinimum() {
        for (int seed : SEEDS) {
            FakeServerAdapter adapter = worldWithPlayers();
            SummonSearch search = new SummonSearch(adapter, smartConfig(20, 70, SummonSearchSettings.WhenAllAbsent.NONE),
                    new FakeSearchClock(), new Random(seed));
            SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
            if (result.isHit()) {
                Spot spot = result.spot().orElseThrow();
                int centerX = spot.x() < 200 ? 100 : 300;
                assertTrue(RingMath.chebyshev(centerX, 0, spot.x(), spot.z()) >= 16);
            }
        }
    }

    @Test
    void emptyWorldFollowsWhenAllAbsent() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(adapter, -3, 3, -3, 3);
        SummonSearch none = new SummonSearch(adapter, smartConfig(20, 70, SummonSearchSettings.WhenAllAbsent.NONE),
                new FakeSearchClock(), new Random(1));
        assertEquals(MissReason.NO_PLAYERS, none.serverSummon(WORLD_KEY, "crate", FREE).join().missReason().orElseThrow());

        SummonSearch spawn = new SummonSearch(adapter, smartConfig(20, 70, SummonSearchSettings.WhenAllAbsent.SPAWN),
                new FakeSearchClock(), new Random(1));
        assertTrue(spawn.serverSummon(WORLD_KEY, "crate", FREE).join().isHit());
    }

    private static FakeServerAdapter worldWithUnevenPlayers() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(adapter, -2, 28, -2, 2);
        adapter.setPlayers(List.of(
                new PlayerPosition(NEAR, 0, 64, 0),
                new PlayerPosition(FAR, 100, 64, 0),
                new PlayerPosition(UUID.fromString("00000000-0000-0000-0000-000000000003"), 400, 64, 0)
        ));
        return adapter;
    }

    private static FakeServerAdapter worldWithPlayers() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(adapter, 0, 22, -2, 2);
        adapter.setPlayers(List.of(
                new PlayerPosition(NEAR, 100, 64, 0),
                new PlayerPosition(FAR, 300, 64, 0)
        ));
        return adapter;
    }

    private static me.rakkoon69.rakunloots.config.ConfigSnapshot smartConfig(
            int pickBias,
            int medianPull,
            SummonSearchSettings.WhenAllAbsent whenAllAbsent
    ) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds("""
                        worlds:
                          world:
                            summon_search:
                              mode: smart
                              unloaded_chunks: load
                              spawn_ring:
                                min: 0
                                max: 40
                              smart:
                                ring:
                                  min: 16
                                  max: 32
                                pick_bias: %d
                                median_pull: %d
                                when_all_absent: %s
                        """.formatted(pickBias, medianPull, whenAllAbsent.name().toLowerCase()))
                .loot("crate", SummonSearchFixtures.topSurfaceLoot(20, null))
                .build(), written -> Optional.of(written), WarningKinds.all());
    }

    private static boolean facesMedian(int centerX, int centerZ, int spotX, int spotZ, double medianX, double medianZ) {
        double toMedianX = medianX - centerX;
        double toMedianZ = medianZ - centerZ;
        double toSpotX = spotX - centerX;
        double toSpotZ = spotZ - centerZ;
        return toMedianX * toSpotX + toMedianZ * toSpotZ >= 0.0;
    }
}
