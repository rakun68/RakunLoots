package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonSearchIdleTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final UUID IDLE_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID ACTIVE_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    private FakeServerAdapter adapter;
    private FakeSearchClock clock;
    private SummonSearch search;

    @BeforeEach
    void setUp() {
        adapter = new FakeServerAdapter(WORLD_KEY);
        clock = new FakeSearchClock();
        adapter.setSpawn(0, 64, 0);
        SummonTestWorlds.flatRegion(adapter, 0, 22, -2, 2);
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 100, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 300, 64, 0)
        ));
        search = new SummonSearch(adapter, smartConfig(5), clock, new Random(1));
        search.start();
        search.idleTracker().trackWorld(WORLD_KEY, smartConfig(5).world("world").orElseThrow().summonSearch());
        // First sample anchors both players.
        adapter.globalScheduler().stepTicks(600);
    }

    @AfterEach
    void tearDown() {
        search.stop();
    }

    @Test
    void idlePlayerIsNotPickedWhileAnotherIsActive() {
        makeOnlyIdle(IDLE_PLAYER);
        int activePicks = 0;
        for (int attempt = 0; attempt < 80; attempt++) {
            SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
            assertTrue(result.isHit(), () -> "miss=" + result.missReason());
            if (result.spot().orElseThrow().x() > 200) {
                activePicks++;
            }
        }
        assertEquals(80, activePicks);
    }

    @Test
    void movingMoreThanOneBlockBecomesActiveAgain() {
        makeOnlyIdle(IDLE_PLAYER);
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 120, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 300, 64, 0)
        ));
        adapter.globalScheduler().stepTicks(600);
        boolean sawNear = false;
        for (int attempt = 0; attempt < 100; attempt++) {
            SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
            if (result.isHit() && result.spot().orElseThrow().x() < 200) {
                sawNear = true;
                break;
            }
        }
        assertTrue(sawNear);
    }

    @Test
    void whenAllIdleEveryoneIsPickedAgain() {
        // Keep both still across two idle windows so both flag as idle.
        clock.advanceMillis(5_000);
        adapter.globalScheduler().stepTicks(600);
        boolean sawNear = false;
        boolean sawFar = false;
        for (int attempt = 0; attempt < 120; attempt++) {
            SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
            if (result.isHit()) {
                if (result.spot().orElseThrow().x() < 200) {
                    sawNear = true;
                } else {
                    sawFar = true;
                }
            }
        }
        assertTrue(sawNear && sawFar);
    }

    @Test
    void missingPlayerIsForgotten() {
        makeOnlyIdle(IDLE_PLAYER);
        adapter.setPlayers(List.of(new PlayerPosition(ACTIVE_PLAYER, 300, 64, 0)));
        adapter.globalScheduler().stepTicks(600);
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 100, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 300, 64, 0)
        ));
        adapter.globalScheduler().stepTicks(600);
        boolean sawNear = false;
        for (int attempt = 0; attempt < 80; attempt++) {
            SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
            if (result.isHit() && result.spot().orElseThrow().x() < 200) {
                sawNear = true;
                break;
            }
        }
        assertTrue(sawNear);
    }

    @Test
    void startSamplesASmartWorldBeforeItsFirstSearch() {
        search.stop();
        search = new SummonSearch(adapter, smartConfig(5), clock, new Random(1));
        search.start();
        clock.advanceMillis(5_000);
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 100, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 310, 64, 0)
        ));
        adapter.globalScheduler().stepTicks(600);
        int activePicks = 0;
        for (int attempt = 0; attempt < 40; attempt++) {
            SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
            if (result.isHit() && result.spot().orElseThrow().x() > 200) {
                activePicks++;
            }
        }
        assertEquals(40, activePicks);
    }

    @Test
    void reloadReplacesIdleSeconds() {
        search.stop();
        clock = new FakeSearchClock();
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 100, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 300, 64, 0)
        ));
        search = new SummonSearch(adapter, smartConfig(10_000), clock, new Random(1));
        search.start();
        clock.advanceMillis(5_000);
        search.reload(smartConfig(1));
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 100, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 310, 64, 0)
        ));
        adapter.globalScheduler().stepTicks(600);
        int activePicks = 0;
        for (int attempt = 0; attempt < 40; attempt++) {
            SummonResult result = search.serverSummon(WORLD_KEY, "crate", FREE).join();
            if (result.isHit() && result.spot().orElseThrow().x() > 200) {
                activePicks++;
            }
        }
        assertEquals(40, activePicks);
    }

    @Test
    void samplingUsesGlobalSchedulerAndStopsWithSearch() {
        assertTrue(search.idleTracker().isSampling());
        search.stop();
        assertFalse(search.idleTracker().isSampling());
    }

    /**
     * Keeps {@code idlePlayer} still past idle_seconds and bumps the other player so only one is idle.
     */
    private void makeOnlyIdle(UUID idlePlayer) {
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 100, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 310, 64, 0)
        ));
        clock.advanceMillis(5_000);
        adapter.globalScheduler().stepTicks(600);
        // Restore active player near their usual spot for summon geometry.
        adapter.setPlayers(List.of(
                new PlayerPosition(IDLE_PLAYER, 100, 64, 0),
                new PlayerPosition(ACTIVE_PLAYER, 300, 64, 0)
        ));
    }

    private static me.rakkoon69.rakunloots.config.ConfigSnapshot smartConfig(int idleSeconds) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds("""
                        worlds:
                          world:
                            summon_search:
                              mode: smart
                              unloaded_chunks: load
                              smart:
                                ring:
                                  min: 8
                                  max: 24
                                idle_seconds: %d
                        """.formatted(idleSeconds))
                .loot("crate", SummonSearchFixtures.topSurfaceLoot(8, null))
                .build(), written -> Optional.of(written), WarningKinds.all());
    }
}
