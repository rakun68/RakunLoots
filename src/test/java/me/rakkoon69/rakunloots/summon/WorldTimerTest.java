package me.rakkoon69.rakunloots.summon;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.LifecycleFixture;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.lootbox.Spot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static me.rakkoon69.rakunloots.summon.TimerFixture.END;
import static me.rakkoon69.rakunloots.summon.TimerFixture.NETHER;
import static me.rakkoon69.rakunloots.summon.TimerFixture.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world timer through the fake world and its hand-stepped global scheduler. Every test checks what the world and
 * the finder see: searches made, blocks written, boxes placed.
 */
class WorldTimerTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SECOND_PLAYER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final long TICKS = 20;

    @TempDir
    Path folder;

    private LifecycleFixture base;
    private LootBoxStore store;
    private TimerFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        base = new LifecycleFixture(folder);
        store = base.openStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    private TimerFixture start(ConfigSnapshot config) {
        base = new LifecycleFixture(folder);
        fixture = new TimerFixture(store, base, config, 7);
        return fixture;
    }

    private static ConfigSnapshot custom(String worldKeys) {
        return TimerFixture.config(TimerFixture.worlds(worldKeys), Map.of("fast", TimerFixture.FAST,
                "slow", TimerFixture.SLOW));
    }

    /**
     * Acceptance: the timer summons every time_between_loot_spawn seconds on the server adapter's global scheduler,
     * and starts full: nothing is summoned before the first whole period. Shipped worlds.yml and airdrops.yml.
     */
    @Test
    void theTimerSummonsEveryTimeBetweenLootSpawnSecondsOnTheGlobalScheduler() {
        start(shippedConfig());
        fixture.playerInWorld(PLAYER);
        Spot first = airSpot(3, 4);
        Spot second = airSpot(9, 9);
        fixture.finder.spots.add(first);
        fixture.finder.spots.add(second);
        long period = 420 * TICKS;

        fixture.timers.start();
        fixture.step(period - 1);

        assertEquals(0, fixture.finder.searches, "starts full");
        fixture.step(1);
        assertEquals(1, fixture.finder.searches);
        assertEquals(1, fixture.service.pendingCount(WORLD), "the airdrop's summon delay holds the spot");
        fixture.step(60);
        assertTrue(fixture.lifecycle.isLootBox(WORLD, 3, 64, 4));

        fixture.step(period - 61);
        assertEquals(1, fixture.finder.searches, "not before the next period");
        fixture.step(1);
        assertEquals(2, fixture.finder.searches);
        assertTrue(fixture.adapterCalls.contains("global"));
        assertFalse(fixture.adapterCalls.contains("async"), "the timer never uses the async scheduler");
    }

    /**
     * Acceptance: the timer is never under 5 seconds.
     */
    @Test
    void theTimerNeverRunsMoreOftenThanEveryFiveSeconds() {
        for (String written : new String[]{"0", "1", "4"}) {
            start(custom("time_between_loot_spawn: " + written + "\nminimum_player_to_spawn_loot: 0\n"
                    + "loot_spawn_limit: 99\nloots:\n  fast: 100"));
            fixture.finder.spots.add(airSpot(3, 4));

            fixture.timers.start();
            fixture.step(5 * TICKS - 1);
            assertEquals(0, fixture.finder.searches, written);
            fixture.step(1);

            assertEquals(1, fixture.finder.searches, written);
        }
    }


    /**
     * Acceptance: the timer starts full on enable and on reload. A reload part-way through a period restarts it.
     */
    @Test
    void aReloadStartsTheTimerFullAgain() {
        ConfigSnapshot config = custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\n"
                + "loot_spawn_limit: 99\nloots:\n  fast: 100");
        start(config);
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.finder.spots.add(airSpot(5, 5));
        fixture.timers.start();

        fixture.step(10 * TICKS - 1);
        fixture.timers.reload(config);
        fixture.step(10 * TICKS - 1);
        assertEquals(0, fixture.finder.searches, "the reload restarted the countdown");
        fixture.step(1);

        assertEquals(1, fixture.finder.searches);
    }

    @Test
    void aStoppedTimerSummonsNothing() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 99\n"
                + "loots:\n  fast: 100"));
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.timers.start();

        fixture.timers.stop();
        fixture.step(100 * TICKS);

        assertEquals(0, fixture.finder.searches);
    }

    /**
     * Acceptance: it summons only with minimum_player_to_spawn_loot players, counting also_count_players_from. A
     * world the list does not name counts none.
     */
    @Test
    void theTimerSummonsOnlyWithEnoughPlayersCountingTheListedWorlds() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 2\n"
                + "also_count_players_from:\n  - world_nether\n  - world_the_end\nloot_spawn_limit: 99\n"
                + "loots:\n  fast: 100"));
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.timers.start();
        fixture.playerInWorld(PLAYER);
        fixture.playerIn(OTHER, SECOND_PLAYER);

        fixture.step(10 * TICKS);
        assertEquals(0, fixture.finder.searches, "one player here, one in a world that does not count");

        fixture.playerIn(END, UUID.randomUUID());
        fixture.step(10 * TICKS);
        assertEquals(1, fixture.finder.searches, "the end's player counts");
    }

    @Test
    void theTimerOutcomeSaysWhyItRefused() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 1\nloot_spawn_limit: 99\n"
                + "loots:\n  fast: 100"));

        assertEquals(TimerRefusal.BELOW_MINIMUM_PLAYERS, fixture.timers.summonNow("world", false).join()
                .refusal().orElseThrow());
        assertEquals(TimerRefusal.WORLD_UNKNOWN, fixture.timers.summonNow("elsewhere", false).join()
                .refusal().orElseThrow());
        base.adapter.setWorldLoaded(false);
        assertEquals(TimerRefusal.WORLD_NOT_LOADED, fixture.timers.summonNow("world", false).join()
                .refusal().orElseThrow());
    }

    /**
     * Acceptance: loot_spawn_limit counts every live loot box and every pending summon in that world. Here nothing is
     * live and one summon is pending, so a version that counts only live boxes lets the timer through.
     */
    @Test
    void theSpawnLimitCountsPendingSummons() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 1\n"
                + "loots:\n  fast: 100"));
        fixture.finder.spots.add(airSpot(3, 4));
        CompletableFuture<SummonReport> pending = fixture.service.adminSummon("slow",
                SummonTarget.world(WORLD), Optional.empty());
        assertEquals(1, fixture.service.pendingCount(WORLD));
        assertEquals(0, fixture.lifecycle.liveBoxCount(WORLD));
        fixture.finder.spots.add(airSpot(8, 8));

        TimerOutcome outcome = fixture.timers.summonNow("world", false).join();

        assertEquals(TimerRefusal.AT_SPAWN_LIMIT, outcome.refusal().orElseThrow());
        assertEquals(1, fixture.finder.searches, "the timer searched nothing");
        assertFalse(pending.isDone());
    }

    /**
     * Acceptance: the limit counts every live box. One live box, limit 1.
     */
    @Test
    void theSpawnLimitCountsLiveBoxes() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 1\n"
                + "loots:\n  fast: 100"));
        fixture.finder.spots.add(airSpot(3, 4));
        assertTrue(fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join()
                .box().isPresent());
        fixture.finder.spots.add(airSpot(8, 8));

        assertEquals(TimerRefusal.AT_SPAWN_LIMIT, fixture.timers.summonNow("world", false).join()
                .refusal().orElseThrow());
        assertEquals(1, fixture.finder.searches);
        assertEquals(1, fixture.lifecycle.liveBoxCount(WORLD));
        assertEquals(0, fixture.lifecycle.liveBoxCount(NETHER), "another world's boxes are not counted");
    }

    /**
     * Acceptance: the spawn limit applies to timer summons only. At the limit, an admin summon and a summon item
     * summon still place their boxes.
     */
    @Test
    void theSpawnLimitDoesNotApplyToSummonItemsOrAdminSummons() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 1\n"
                + "loots:\n  fast: 100"));
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.finder.spots.add(airSpot(5, 5));
        fixture.finder.spots.add(airSpot(7, 7));
        assertTrue(fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join()
                .box().isPresent());

        SummonReport admin = fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join();
        SummonReport item = fixture.service.summon("fast", SummonTarget.world(WORLD), Optional.of(PLAYER),
                SummonSource.summonItem(PLAYER)).join();

        assertTrue(admin.box().isPresent());
        assertTrue(item.box().isPresent());
        assertEquals(3, fixture.lifecycle.liveBoxCount(WORLD));
        assertEquals(TimerRefusal.AT_SPAWN_LIMIT, fixture.timers.summonNow("world", false).join()
                .refusal().orElseThrow());
    }

    /**
     * Acceptance: a box records how it was summoned: the timer, an admin command, or a summon item.
     */
    @Test
    void aBoxRecordsWhichSourceSummonedIt() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 9\n"
                + "loots:\n  fast: 100"));
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.finder.spots.add(airSpot(5, 5));
        fixture.finder.spots.add(airSpot(7, 7));

        LootBox timer = fixture.timers.summonNow("world", false).join().report().orElseThrow().box().orElseThrow();
        LootBox admin = fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join()
                .box().orElseThrow();
        LootBox item = fixture.service.summon("fast", SummonTarget.world(WORLD), Optional.of(PLAYER),
                SummonSource.summonItem(PLAYER)).join().box().orElseThrow();

        assertEquals(SummonKind.WORLD_TIMER, timer.source());
        assertEquals(SummonKind.ADMIN_COMMAND, admin.source());
        assertEquals(SummonKind.SUMMON_ITEM, item.source());
    }

    /**
     * An admin forcing a timer summon skips the minimum player count and the spawn limit.
     */
    @Test
    void aForcedTimerSummonSkipsThePlayerCountAndTheSpawnLimit() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 5\nloot_spawn_limit: 0\n"
                + "loots:\n  fast: 100"));
        fixture.finder.spots.add(airSpot(3, 4));

        TimerOutcome outcome = fixture.timers.summonNow("world", true).join();

        assertTrue(outcome.report().orElseThrow().box().isPresent());
    }

    /**
     * Acceptance: a timer summon on a held spot is refused. The held spot is another summon's pending spot, so the
     * timer's summon is abandoned as spot held and places nothing.
     */
    @Test
    void aTimerSummonOnAHeldSpotIsRefused() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 99\n"
                + "loots:\n  fast: 100"));
        Spot spot = airSpot(3, 4);
        fixture.finder.spots.add(spot);
        fixture.service.adminSummon("slow", SummonTarget.world(WORLD), Optional.empty());
        fixture.finder.spots.add(spot);

        SummonReport report = fixture.timers.summonNow("world", false).join().report().orElseThrow();

        assertEquals(AbandonReason.SPOT_HELD, report.abandoned().orElseThrow());
        assertTrue(report.box().isEmpty());
        assertEquals(1, fixture.service.pendingCount(WORLD), "the first summon still holds the spot");
        assertTrue(base.adapter.blockWrites().isEmpty());
        fixture.step(100);
        assertEquals(1, base.adapter.blockWrites().size());
        assertEquals(new BlockPosition(3, 64, 4), base.adapter.blockWrites().get(0).position());
    }

    /**
     * The loot is chosen from the world's list by chance. A loot with a chance of 0 is never chosen, and the loot
     * that is, is summoned.
     */
    @Test
    void theTimerSummonsALootFromTheWorldsList() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 99\n"
                + "loots:\n  slow: 0\n  missing: 50\n  fast: 1"));
        for (int index = 0; index < 5; index++) {
            fixture.finder.spots.add(airSpot(index * 3, 4));
        }

        for (int index = 0; index < 5; index++) {
            SummonReport report = fixture.timers.summonNow("world", false).join().report().orElseThrow();
            assertEquals("fast", report.box().orElseThrow().lootId());
        }
    }

    @Test
    void aWorldWithNoUsableLootRefusesWithNoLoot() {
        start(custom("time_between_loot_spawn: 10\nminimum_player_to_spawn_loot: 0\nloot_spawn_limit: 99\n"
                + "loots:\n  missing: 50\n  fast: 0"));

        assertEquals(TimerRefusal.NO_LOOT, fixture.timers.summonNow("world", false).join().refusal().orElseThrow());
        assertEquals(0, fixture.finder.searches);
    }
}
