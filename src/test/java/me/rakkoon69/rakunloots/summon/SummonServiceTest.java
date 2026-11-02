package me.rakkoon69.rakunloots.summon;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.lootbox.BoxObserver;
import me.rakkoon69.rakunloots.lootbox.LifecycleFixture;
import me.rakkoon69.rakunloots.lootbox.LifecycleWarning;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.lootbox.SummonGiveBack;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.Message;
import me.rakkoon69.rakunloots.summon.search.SearchMiss;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.summon.search.SpotFinder;
import me.rakkoon69.rakunloots.summon.search.SummonResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.position;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The summon service through the lifecycle fixture, the fake world, and its hand-stepped scheduler. Every test checks
 * what the world and the player see: blocks written, boxes placed, messages sent, items given back.
 */
class SummonServiceTest {

    private static final UUID FIRST = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SECOND = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final long DELAY = 100;

    private static final String SLOW = """
            language: en
            open_mode: drop
            block: barrel
            summon_delay: %d
            roll:
              entries:
                one:
                  item:
                    id: diamond
            """.formatted(DELAY);
    private static final String SLOW_REFUSED = """
            language: en
            open_mode: drop
            block: player_head
            head_data: 'not base64 !'
            summon_delay: %d
            roll:
              entries:
                one:
                  item:
                    id: diamond
            """.formatted(DELAY);

    /**
     * The shipped airdrop with no summon delay, for the summons that write at once.
     */
    private static final String INSTANT = LifecycleFixture.resource("/loots/airdrops.yml")
            .replaceFirst("(?m)^summon_delay: \\d+", "summon_delay: 0");

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;
    private LootBoxLifecycle lifecycle;
    private final Finder finder = new Finder();
    private final RecordingGiveBack giveBack = new RecordingGiveBack();
    private final List<String> schedulesAt = new CopyOnWriteArrayList<>();
    private SummonService service;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        fixture.adapter.addOnlinePlayer(FIRST);
        fixture.adapter.addOnlinePlayer(SECOND);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    private ConfigSnapshot start(Random random) {
        return start(random, "load");
    }

    private ConfigSnapshot start(Random random, String unloadedChunks) {
        ConfigSnapshot config = shippedConfig(Map.of("slow", SLOW, "slow_refused", SLOW_REFUSED, "instant", INSTANT),
                unloadedChunks);
        lifecycle = new LootBoxLifecycle(recording(fixture.adapter), config, ItemSourceRegistry.create(), store, random,
                fixture.warnings);
        service = new SummonService(recording(fixture.adapter), config, lifecycle, finder, giveBack, fixture.warnings);
        return config;
    }

    private ConfigSnapshot start() {
        return start(new Random(1));
    }

    /**
     * The adapter, noting each location a task is scheduled at.
     */
    private ServerAdapter recording(ServerAdapter real) {
        return (ServerAdapter) Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("atLocation")) {
                        schedulesAt.add(args[1] + "," + args[2]);
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private CompletableFuture<SummonReport> adminSummon(String lootId) {
        return service.summon(lootId, SummonTarget.world(WORLD), Optional.empty(), SummonSource.adminCommand());
    }

    private CompletableFuture<SummonReport> itemSummon(String lootId, UUID player) {
        return service.summon(lootId, SummonTarget.world(WORLD), Optional.of(player), SummonSource.summonItem(player));
    }

    private void step(long ticks) {
        fixture.adapter.globalScheduler().stepTicks(ticks);
    }

    private List<String> messagesTo(UUID player) {
        List<String> texts = new ArrayList<>();
        for (Message message : fixture.adapter.messages()) {
            if (message.playerId().equals(player)) {
                texts.add(message.text());
            }
        }
        return texts;
    }

    /**
     * Observers hear a pending summon when its spot is held, before the block is written, and then the box shown when
     * the delay ends. A second observer beside a throwing one hears it too.
     */
    @Test
    void observersHearAPendingSummonThenTheBoxShown() {
        start();
        lifecycle.addObserver(new BoxObserver() {
            @Override
            public void shown(BoxEvent box) {
            }

            @Override
            public void ticked(BoxEvent box) {
            }

            @Override
            public void outOfView(BoxEvent box) {
            }

            @Override
            public void ended(BoxEvent box) {
            }

            @Override
            public void summonPending(Spot spot, String lootId, int delayTicks) {
                throw new IllegalStateException("broken observer");
            }
        });
        List<String> heard = observe();
        finder.spots.add(airSpot(3, 4));

        adminSummon("slow");

        assertEquals(List.of("pending slow 3,64,4 " + DELAY), heard);
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        step(DELAY);
        assertEquals(List.of("pending slow 3,64,4 " + DELAY, "shown slow 3,64,4"), heard);
        assertEquals(1, fixture.warnings.lifecycle.stream()
                .filter(warning -> warning.kind() == LifecycleWarning.Kind.OBSERVER_FAILED).count());
    }

    /**
     * A summon abandoned during its delay is told with its spot, loot, and reason, after its pending; a refusal that
     * never held a spot is told nothing.
     */
    @Test
    void observersHearAnAbandonedSummonWithItsReasonButNotOneThatNeverHeldASpot() {
        start();
        List<String> heard = observe();
        finder.spots.add(airSpot(3, 4));
        itemSummon("slow", FIRST);

        service.chunkUnloaded(WORLD, new ChunkPosition(0, 0));
        itemSummon("slow", FIRST);
        itemSummon("nothing", FIRST);

        assertEquals(List.of("pending slow 3,64,4 " + DELAY, "abandoned slow 3,64,4 CHUNK_UNLOADED"), heard);
    }

    /**
     * A summon refused because its spot is held tells nothing: no pending and no abandoned.
     */
    @Test
    void aSpotHeldRefusalIsNotTold() {
        start();
        List<String> heard = observe();
        finder.spots.add(airSpot(3, 4));
        finder.spots.add(airSpot(3, 4));
        adminSummon("slow");

        itemSummon("slow", SECOND);

        assertEquals(List.of("pending slow 3,64,4 " + DELAY), heard);
    }

    private List<String> observe() {
        List<String> heard = new CopyOnWriteArrayList<>();
        lifecycle.addObserver(new BoxObserver() {
            @Override
            public void shown(BoxEvent box) {
                heard.add("shown " + box.lootId() + " " + box.position().x() + "," + box.position().y() + ","
                        + box.position().z());
            }

            @Override
            public void ticked(BoxEvent box) {
            }

            @Override
            public void outOfView(BoxEvent box) {
            }

            @Override
            public void ended(BoxEvent box) {
            }

            @Override
            public void summonPending(Spot spot, String lootId, int delayTicks) {
                heard.add("pending " + lootId + " " + spot.x() + "," + spot.y() + "," + spot.z() + " " + delayTicks);
            }

            @Override
            public void summonAbandoned(Spot spot, String lootId, AbandonReason reason) {
                heard.add("abandoned " + lootId + " " + spot.x() + "," + spot.y() + "," + spot.z() + " " + reason);
            }
        });
        return heard;
    }

    /**
     * Acceptance: a delay of 0 writes without a wait.
     */
    @Test
    void aDelayOfZeroWritesWithoutAWait() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);

        SummonReport report = adminSummon("instant").join();

        assertTrue(report.box().isPresent(), "abandoned " + report.abandoned());
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(position(spot)));
        assertEquals(1, fixture.adapter.blockWrites().size());
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(0, service.pendingCount(WORLD));
    }

    /**
     * Acceptance: the spot is reserved when the search returns it, before the delay, and the occupied check counts it
     * as taken. The block is written only when the delay ends.
     */
    @Test
    void theSpotIsReservedThroughTheDelayAndTheBlockIsWrittenWhenItEnds() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);

        CompletableFuture<SummonReport> report = adminSummon("slow");

        assertFalse(report.isDone());
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        assertTrue(lifecycle.occupiedCheck().isOccupied(WORLD, 3, 64, 4), "held from the search");
        assertEquals(1, service.pendingCount(WORLD));
        step(DELAY - 1);
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        step(1);
        assertTrue(report.isDone());
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(position(spot)));
        assertTrue(report.join().box().isPresent());
        assertEquals(0, service.pendingCount(WORLD));
    }

    /**
     * Acceptance: two summons with a delay whose searches pick the same block place one loot box. The second is
     * refused when it reserves, before its delay runs, and its summon item goes back. A version that waits out the
     * delay before it reserves lets both through the delay, so this check at tick 0 fails there.
     */
    @Test
    void twoSummonsWithADelayThatPickTheSameBlockPlaceOneBox() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        finder.spots.add(spot);

        CompletableFuture<SummonReport> first = itemSummon("slow", FIRST);
        CompletableFuture<SummonReport> second = itemSummon("slow", SECOND);

        assertTrue(second.isDone(), "refused at the search, not after the delay");
        assertEquals(Optional.of(AbandonReason.SPOT_HELD), second.join().abandoned());
        assertEquals(List.of(new Given(SECOND, "slow", AbandonReason.SPOT_HELD)), giveBack.given);
        assertEquals(1, service.pendingCount(WORLD));
        step(DELAY);
        assertTrue(first.join().box().isPresent());
        assertEquals(1, fixture.adapter.blockWrites().size());
        assertEquals(1, lifecycle.boxAt(WORLD, position(spot)).stream().count());
        assertEquals(List.of(new Given(SECOND, "slow", AbandonReason.SPOT_HELD)), giveBack.given);
    }

    /**
     * Acceptance: a summon is abandoned when its spot is already held. A server summon is only refused: nothing is
     * given back and nobody is messaged.
     */
    @Test
    void aServerSummonOnAHeldSpotIsOnlyRefused() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        lifecycle.summon("instant", spot, Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
        finder.spots.add(spot);

        SummonReport report = adminSummon("slow").join();

        assertEquals(Optional.of(AbandonReason.SPOT_HELD), report.abandoned());
        assertTrue(giveBack.given.isEmpty());
        assertTrue(fixture.adapter.messages().isEmpty());
        assertEquals(1, fixture.adapter.blockWrites().size());
        assertEquals(0, service.pendingCount(WORLD));
    }

    /**
     * Acceptance: the block changed during the delay. Nothing is written, the spot is released, the item goes back
     * with the default message, and nothing searches again.
     */
    @Test
    void aBlockThatChangedDuringTheDelayPlacesNothingAndGivesTheItemBack() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        CompletableFuture<SummonReport> report = itemSummon("slow", FIRST);

        fixture.adapter.putBlock(position(spot), "minecraft:stone");
        step(DELAY);

        assertEquals(Optional.of(AbandonReason.BLOCK_CHANGED), report.join().abandoned());
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        assertEquals("minecraft:stone", fixture.adapter.blockAt(position(spot)));
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(List.of(new Given(FIRST, "slow", AbandonReason.BLOCK_CHANGED)), giveBack.given);
        assertEquals(1, messagesTo(FIRST).size());
        assertTrue(messagesTo(FIRST).get(0).contains("changed"), messagesTo(FIRST).get(0));
        assertEquals(1, finder.searches, "an abandoned summon never searches again");
    }

    /**
     * Acceptance: the chunk unloads during the delay. The unload event abandons the summon at once and its delayed
     * write never runs.
     */
    @Test
    void aChunkThatUnloadsDuringTheDelayPlacesNothingAndGivesTheItemBack() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        CompletableFuture<SummonReport> report = itemSummon("slow", FIRST);

        service.chunkUnloaded(WORLD, new ChunkPosition(0, 0));

        assertEquals(Optional.of(AbandonReason.CHUNK_UNLOADED), report.join().abandoned());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4), "the hold is released");
        assertEquals(0, service.pendingCount(WORLD));
        step(DELAY);
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        assertEquals(List.of(new Given(FIRST, "slow", AbandonReason.CHUNK_UNLOADED)), giveBack.given);
        assertEquals(1, messagesTo(FIRST).size());
    }

    /**
     * ADR 0015: under {@code load} a pending summon holds its spot's chunk from the reservation, so the server's
     * unload attempt during the delay fails, the box is placed, and the box's own hold takes over with no release
     * between.
     */
    @Test
    void aDelayedSummonUnderLoadKeepsItsChunkThroughTheDelayAndHandsTheHoldToTheBox() {
        start();
        finder.spots.add(airSpot(3, 4));
        CompletableFuture<SummonReport> report = adminSummon("slow");

        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks(), "held from the reservation");
        assertFalse(fixture.adapter.unloadChunk(0, 0), "the server cannot unload a held chunk");
        step(DELAY);

        assertTrue(report.join().box().isPresent(), "the box is placed");
        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks(), "the box holds it now");
        assertEquals(List.of(new ChunkPosition(0, 0)), fixture.adapter.holdCalls(), "one ticket for both");
        assertEquals(List.of(), fixture.adapter.releaseCalls(), "no gap where the chunk can unload");
    }

    /**
     * An abandoned summon gives its hold back, and a box in the same chunk keeps the shared ticket meanwhile.
     */
    @Test
    void anAbandonedSummonReleasesItsHoldAndSharesTheChunkWithABoxThere() {
        ConfigSnapshot config = start();
        finder.spots.add(airSpot(3, 4));
        CompletableFuture<SummonReport> report = adminSummon("slow");
        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks());

        service.reload(config);

        assertEquals(Optional.of(AbandonReason.RELOAD_OR_STOP), report.join().abandoned());
        assertEquals(Set.of(), fixture.adapter.heldChunks());
        assertEquals(List.of(new ChunkPosition(0, 0)), fixture.adapter.releaseCalls());

        finder.spots.add(airSpot(5, 6));
        assertTrue(adminSummon("instant").join().box().isPresent());
        finder.spots.add(airSpot(7, 8));
        CompletableFuture<SummonReport> second = adminSummon("slow");
        service.reload(config);
        assertEquals(Optional.of(AbandonReason.RELOAD_OR_STOP), second.join().abandoned());
        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks(), "the box still holds the chunk");
    }

    /**
     * Under {@code skip} nothing is held: an unload during the delay goes through and abandons the summon.
     */
    @Test
    void aDelayedSummonUnderSkipHoldsNothingAndIsAbandonedWhenItsChunkUnloads() {
        start(new Random(1), "skip");
        finder.spots.add(airSpot(3, 4));
        CompletableFuture<SummonReport> report = adminSummon("slow");

        assertTrue(fixture.adapter.unloadChunk(0, 0), "nothing keeps the chunk");
        step(DELAY);

        assertEquals(Optional.of(AbandonReason.CHUNK_UNLOADED), report.join().abandoned());
        assertEquals(List.of(), fixture.adapter.holdCalls());
    }

    /**
     * An unload no event reported is still caught when the delay ends: the block cannot be read.
     */
    @Test
    void aChunkFoundUnloadedWhenTheDelayEndsIsAbandonedToo() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        CompletableFuture<SummonReport> report = itemSummon("slow", FIRST);

        fixture.adapter.putChunk(LifecycleFixture.flatChunk(0, 0), false, true);
        step(DELAY);

        assertEquals(Optional.of(AbandonReason.CHUNK_UNLOADED), report.join().abandoned());
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(List.of(new Given(FIRST, "slow", AbandonReason.CHUNK_UNLOADED)), giveBack.given);
    }

    /**
     * Acceptance: a reload during the delay abandons the summon before the lifecycle reloads.
     */
    @Test
    void aReloadDuringTheDelayPlacesNothingAndGivesTheItemBack() {
        ConfigSnapshot config = start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        CompletableFuture<SummonReport> report = itemSummon("slow", FIRST);

        service.reload(config);

        assertEquals(Optional.of(AbandonReason.RELOAD_OR_STOP), report.join().abandoned());
        step(DELAY);
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(List.of(new Given(FIRST, "slow", AbandonReason.RELOAD_OR_STOP)), giveBack.given);
        assertEquals(1, messagesTo(FIRST).size());
    }

    /**
     * Acceptance: a stop during the delay abandons the summon.
     */
    @Test
    void aStopDuringTheDelayPlacesNothingAndGivesTheItemBack() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        CompletableFuture<SummonReport> report = itemSummon("slow", FIRST);

        service.stop();

        assertEquals(Optional.of(AbandonReason.RELOAD_OR_STOP), report.join().abandoned());
        step(DELAY);
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(List.of(new Given(FIRST, "slow", AbandonReason.RELOAD_OR_STOP)), giveBack.given);
    }

    /**
     * Acceptance: the server refuses the block. The refusal is found when the block is written, nothing is placed, and
     * the item goes back.
     */
    @Test
    void aBlockTheServerRefusesPlacesNothingAndGivesTheItemBack() {
        start();
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        CompletableFuture<SummonReport> report = itemSummon("slow_refused", FIRST);

        step(DELAY);

        assertEquals(Optional.of(AbandonReason.BLOCK_REFUSED), report.join().abandoned());
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(List.of(new Given(FIRST, "slow_refused", AbandonReason.BLOCK_REFUSED)), giveBack.given);
        assertEquals(1, messagesTo(FIRST).size());
    }

    /**
     * Acceptance: the roll fails. The block the summon wrote is removed again, nothing is placed, the spot is
     * released, the item goes back, and the failure is warned.
     */
    @Test
    void aRollThatFailsPlacesNothingAndGivesTheItemBack() {
        start(new Random() {
            @Override
            protected int next(int bits) {
                throw new IllegalStateException("roll failed");
            }
        });
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);

        SummonReport report = itemSummon("instant", FIRST).join();

        assertEquals(Optional.of(AbandonReason.ROLL_FAILED), report.abandoned());
        assertEquals("minecraft:air", fixture.adapter.blockAt(position(spot)), "the written block was removed");
        assertTrue(lifecycle.boxAt(WORLD, position(spot)).isEmpty());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(List.of(new Given(FIRST, "instant", AbandonReason.ROLL_FAILED)), giveBack.given);
        assertEquals(1, messagesTo(FIRST).size());
        assertTrue(fixture.warnings.lifecycleKinds().contains(LifecycleWarning.Kind.SUMMON_FAILED));
    }

    /**
     * Acceptance: no spot found. The summon item goes back and the search ran once.
     */
    @Test
    void aSearchThatFindsNoSpotGivesTheItemBackWithTheDefaultMessage() {
        start();
        finder.misses = 1;

        SummonReport report = itemSummon("slow", FIRST).join();

        assertEquals(Optional.of(AbandonReason.NO_SPOT_FOUND), report.abandoned());
        assertEquals(List.of(new Given(FIRST, "slow", AbandonReason.NO_SPOT_FOUND)), giveBack.given);
        assertEquals(1, messagesTo(FIRST).size());
        assertTrue(messagesTo(FIRST).get(0).contains("no suitable location"), messagesTo(FIRST).get(0));
        assertEquals(1, finder.searches);
        assertTrue(fixture.adapter.blockWrites().isEmpty());
    }

    /**
     * Acceptance: every block check and write runs through the server adapter on the region that owns the block: the
     * delayed step is scheduled at the spot, and reads and writes the block through the adapter.
     */
    @Test
    void theDelayedStepIsScheduledAtTheSpotAndChecksAndWritesThroughTheAdapter() {
        start();
        Spot spot = airSpot(25, -12);
        finder.spots.add(spot);

        adminSummon("slow");

        assertTrue(schedulesAt.contains("25,-12"), schedulesAt.toString());
        assertTrue(fixture.adapter.blockReads().isEmpty());
        step(DELAY);
        assertEquals(List.of(position(spot)), fixture.adapter.blockReads().stream().distinct().toList());
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(position(spot)));
        assertEquals(1, fixture.adapter.blockWrites().size());
    }

    /**
     * An admin summon around a player searches around that player's position.
     */
    @Test
    void anAdminSummonAroundAPlayerSearchesAroundThatPlayer() {
        start();
        fixture.adapter.setPlayers(List.of(new PlayerPosition(FIRST, 10.5, 70, -3.5)));
        finder.spots.add(airSpot(12, -3));

        SummonReport report = service.summon("instant", SummonTarget.player(WORLD, FIRST), Optional.of(FIRST),
                SummonSource.adminCommand()).join();

        assertEquals(List.of("around 10,70,-4"), finder.calls);
        assertEquals(Optional.of(FIRST), report.box().orElseThrow().owner());
    }

    /**
     * The pending summons of a world are counted for the spawn limit.
     */
    @Test
    void pendingSummonsAreCountedByWorld() {
        start();
        finder.spots.add(airSpot(1, 1));
        finder.spots.add(airSpot(5, 5));

        adminSummon("slow");
        adminSummon("slow");

        assertEquals(2, service.pendingCount(WORLD));
        assertEquals(0, service.pendingCount(UUID.randomUUID()));
        step(DELAY);
        assertEquals(0, service.pendingCount(WORLD));
    }

    private record Given(UUID player, String lootId, AbandonReason reason) {
    }

    private static final class RecordingGiveBack implements SummonGiveBack {

        final List<Given> given = new CopyOnWriteArrayList<>();

        @Override
        public void giveBack(UUID playerId, String lootId, AbandonReason reason) {
            given.add(new Given(playerId, lootId, reason));
        }
    }

    /**
     * Returns the spots it was given, one per search, then misses; a search is counted and noted.
     */
    private static final class Finder implements SpotFinder {

        final List<Spot> spots = new CopyOnWriteArrayList<>();
        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile int searches;
        volatile int misses;

        private CompletableFuture<SummonResult> next() {
            searches++;
            if (misses > 0 || spots.isEmpty()) {
                return CompletableFuture.completedFuture(SummonResult.miss(SearchMiss.ATTEMPTS_USED_UP));
            }
            return CompletableFuture.completedFuture(SummonResult.hit(spots.remove(0)));
        }

        @Override
        public CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied) {
            calls.add("server");
            return next();
        }

        @Override
        public CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                            OccupiedCheck occupied) {
            calls.add("around " + x + "," + y + "," + z);
            return next();
        }
    }
}
