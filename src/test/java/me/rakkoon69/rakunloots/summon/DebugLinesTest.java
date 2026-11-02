package me.rakkoon69.rakunloots.summon;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.Interaction;
import me.rakkoon69.rakunloots.lootbox.LifecycleFixture;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The debug lines the summon service, the world timers, and the lifecycle write through the warnings sink while debug
 * mode is on, and that they write none while it is off. Over the fake world, with each summon landing at once.
 */
class DebugLinesTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

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
        ConfigSnapshot config = TimerFixture.config(TimerFixture.worlds("time_between_loot_spawn: 420\n"
                + "minimum_player_to_spawn_loot: 0\nloot_spawn_limit: 1\nloots:\n  fast: 100"),
                Map.of("fast", TimerFixture.FAST));
        fixture = new TimerFixture(store, base, config, 7);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    private List<String> lines() {
        return base.warnings.debug;
    }

    /**
     * Acceptance: with debug on, a placed summon logs its loot, world, position, and source; an abandoned one logs
     * its loot and reason.
     */
    @Test
    void aPlacedAndAnAbandonedSummonAreLoggedWithDebugOn() {
        base.warnings.debugOn = true;
        fixture.finder.spots.add(airSpot(3, 4));

        SummonReport placed = fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join();
        SummonReport abandoned = fixture.service.summon("fast", SummonTarget.world(WORLD), Optional.empty(),
                SummonSource.summonItem(PLAYER)).join();

        assertTrue(placed.box().isPresent());
        assertEquals(AbandonReason.NO_SPOT_FOUND, abandoned.abandoned().orElseThrow());
        assertEquals(List.of(
                "summon placed loot=fast world=world x=3 y=64 z=4 source=admin command",
                "summon abandoned loot=fast reason=NO_SPOT_FOUND source=summon item " + PLAYER), lines());
    }

    /**
     * Acceptance: with debug on, a world timer run logs that it summoned, or why it refused.
     */
    @Test
    void aWorldTimerRunIsLoggedAsSummonedOrRefusedWithItsReason() {
        base.warnings.debugOn = true;
        fixture.finder.spots.add(airSpot(5, 6));

        fixture.timers.summonNow("world", false).join();
        fixture.timers.summonNow("world", false).join();
        fixture.timers.summonNow("nowhere", false).join();

        assertEquals(List.of(
                "summon placed loot=fast world=world x=5 y=64 z=6 source=world timer",
                "timer world=world summoned loot=fast x=5 y=64 z=6",
                "timer world=world refused reason=AT_SPAWN_LIMIT",
                "timer world=nowhere refused reason=WORLD_UNKNOWN"), lines());
    }

    /**
     * Acceptance: with debug on, a loot end logs the box, its loot, and how it ended.
     */
    @Test
    void aLootEndIsLoggedWithTheBoxItsLootAndHowItEnded() {
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.finder.spots.add(airSpot(8, 8));
        LootBox removed = fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join()
                .box().orElseThrow();
        LootBox broken = fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join()
                .box().orElseThrow();
        base.warnings.debugOn = true;

        fixture.lifecycle.interact(PLAYER, WORLD, broken.position(), true, Interaction.BREAK);
        fixture.lifecycle.remove(box -> box.boxId().equals(removed.boxId()));

        assertEquals(List.of(
                "loot end box=" + broken.boxId() + " loot=fast world=world x=8 y=64 z=8 how=broken by a player",
                "loot end box=" + removed.boxId() + " loot=fast world=world x=3 y=64 z=4 how=admin removal"),
                lines());
    }

    /**
     * Acceptance: with debug off, nothing extra is logged, and the same events happen.
     */
    @Test
    void withDebugOffNothingIsLogged() {
        fixture.finder.spots.add(airSpot(3, 4));

        LootBox box = fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join()
                .box().orElseThrow();
        fixture.service.adminSummon("fast", SummonTarget.world(WORLD), Optional.empty()).join();
        fixture.timers.summonNow("nowhere", false).join();
        fixture.lifecycle.remove(found -> found.boxId().equals(box.boxId()));

        assertEquals(List.of(), lines());
        assertEquals(new BlockPosition(3, 64, 4), box.position());
    }
}
