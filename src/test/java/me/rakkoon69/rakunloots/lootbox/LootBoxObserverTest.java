package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.fake.FakeSharedInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.flatChunk;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle reports each box to its observer: shown when summoned or when its chunk loads, ticked after each
 * 1 second countdown, out of view when its chunk unloads, and ended at every loot end, each once.
 */
class LootBoxObserverTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final long SECOND = LootBoxLifecycle.TICK_PERIOD_TICKS;
    private static final ChunkPosition CHUNK = new ChunkPosition(0, 0);

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;
    private final Recorder events = new Recorder();

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        fixture.adapter.addOnlinePlayer(OWNER);
        fixture.adapter.addOnlinePlayer(OTHER);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance: summoning a box fires shown once, carrying the box id, loot id, position, owner, remaining lock, and
     * remaining life. The shipped airdrop has lock 10 and life 360.
     */
    @Test
    void summoningABoxFiresShownOnce() {
        LootBoxLifecycle lifecycle = observed(shippedConfig(), 61);

        LootBox box = summon(lifecycle, "airdrops", 2, 3, Optional.of(OWNER));

        BoxEvent shown = new BoxEvent(box.boxId(), "airdrops", WORLD, new BlockPosition(2, 64, 3),
                Optional.of(OWNER), 10, OptionalInt.of(360));
        assertEquals(List.of(new Event(Kind.SHOWN, shown)), events.all);
    }

    /**
     * Acceptance: the event carries how the box was summoned, so a debug log or a later rule can read it.
     */
    @Test
    void theShownEventCarriesTheSummonSource() {
        LootBoxLifecycle lifecycle = observed(shippedConfig(), 63);

        lifecycle.summon("airdrops", airSpot(2, 3), Optional.empty(), SummonKind.WORLD_TIMER).join();

        assertEquals(List.of(SummonKind.WORLD_TIMER), events.all.stream().map(event -> event.box().source()).toList());
    }

    /**
     * A refused summon shows nothing.
     */
    @Test
    void aRefusedSummonFiresNothing() {
        LootBoxLifecycle lifecycle = observed(shippedConfig(), 62);
        summon(lifecycle, "airdrops", 2, 3, Optional.empty());
        events.all.clear();

        lifecycle.summon("airdrops", airSpot(2, 3), Optional.empty(), SummonKind.UNKNOWN).join();
        lifecycle.summon("nothing", airSpot(4, 4), Optional.empty(), SummonKind.UNKNOWN).join();

        assertEquals(List.of(), events.all);
    }

    /**
     * Acceptance: unloading the chunk that holds a box fires out of view once, and loading it again fires shown once.
     * Another chunk's load or unload fires nothing for the box, and a second unload or load of the same chunk does not
     * repeat the event.
     */
    @Test
    void unloadingAChunkFiresOutOfViewOnceAndLoadingItFiresShownOnce() {
        LootBoxLifecycle lifecycle = observed(shippedConfig(), 63);
        LootBox box = summon(lifecycle, "airdrops", 2, 3, Optional.of(OWNER));
        events.all.clear();

        lifecycle.chunkUnloaded(WORLD, new ChunkPosition(1, 0));
        lifecycle.chunkUnloaded(UUID.randomUUID(), CHUNK);
        assertEquals(List.of(), events.all, "another chunk, another world");

        unload(0, 0);
        lifecycle.chunkUnloaded(WORLD, CHUNK);
        lifecycle.chunkUnloaded(WORLD, CHUNK);

        assertEquals(List.of(new Event(Kind.OUT_OF_VIEW, BoxEvent.of(box))), events.all);
        assertTrue(lifecycle.isLootBox(WORLD, 2, 64, 3), "the box still lives");

        events.all.clear();
        lifecycle.chunkLoaded(WORLD, new ChunkPosition(1, 0));
        assertEquals(List.of(), events.all, "another chunk");

        load(0, 0);
        lifecycle.chunkLoaded(WORLD, CHUNK);
        lifecycle.chunkLoaded(WORLD, CHUNK);

        assertEquals(List.of(new Event(Kind.SHOWN, BoxEvent.of(box))), events.all);
    }

    /**
     * Acceptance: loading a chunk that holds a stored box fires shown once. After a restart with the chunk unloaded,
     * nothing is shown until the chunk loads. Under skip, since load and generate hold the chunk from the start
     * (ADR 0015).
     */
    @Test
    void loadingAChunkThatHoldsAStoredBoxFiresShownOnce() {
        ConfigSnapshot config = shippedConfig(Map.of(), "skip");
        LootBoxLifecycle first = fixture.lifecycle(config, store, 64);
        first.start().join();
        LootBox box = summon(first, "airdrops", 2, 3, Optional.empty());
        first.stop();
        unload(0, 0);

        LootBoxLifecycle second = observed(config, 65);
        second.start().join();
        assertEquals(List.of(), events.all, "its chunk is not loaded");

        load(0, 0);
        second.chunkLoaded(WORLD, CHUNK);

        assertEquals(List.of(new Event(Kind.SHOWN, BoxEvent.of(box))), events.all);
    }

    /**
     * After a restart, a stored box whose chunk is already loaded is shown once when the lifecycle starts, since no
     * chunk load will come for it. A box in an unloaded chunk is not, under skip, since load and generate hold the
     * chunk from the start (ADR 0015).
     */
    @Test
    void aStoredBoxInALoadedChunkIsShownOnceAtStart() {
        ConfigSnapshot config = shippedConfig(Map.of(), "skip");
        LootBoxLifecycle first = fixture.lifecycle(config, store, 66);
        LootBox loaded = summon(first, "airdrops", 2, 3, Optional.empty());
        LootBox unloaded = summon(first, "airdrops", 20, 3, Optional.empty());
        first.stop();
        unload(1, 0);

        LootBoxLifecycle second = observed(config, 67);
        second.start().join();

        assertEquals(List.of(new Event(Kind.SHOWN, BoxEvent.of(loaded))), events.all);

        load(1, 0);
        second.chunkLoaded(WORLD, new ChunkPosition(1, 0));
        second.chunkLoaded(WORLD, CHUNK);

        assertEquals(List.of(new Event(Kind.SHOWN, BoxEvent.of(loaded)), new Event(Kind.SHOWN, BoxEvent.of(unloaded))),
                events.all);
    }

    /**
     * Acceptance: each 1 second countdown fires ticked with the new remaining lock and life, for every live box,
     * shown or not. Unending life stays unending.
     */
    @Test
    void eachCountdownFiresTickedWithTheNewRemainingLockAndLife() {
        ConfigSnapshot config = shippedConfig(Map.of("forever", loot("drop", 2, -1, false)));
        LootBoxLifecycle lifecycle = observed(config, 68);
        lifecycle.start().join();
        LootBox airdrop = summon(lifecycle, "airdrops", 2, 3, Optional.of(OWNER));
        LootBox forever = summon(lifecycle, "forever", 20, 3, Optional.empty());
        unload(1, 0);
        lifecycle.chunkUnloaded(WORLD, new ChunkPosition(1, 0));
        events.all.clear();

        fixture.adapter.globalScheduler().stepTicks(SECOND - 1);
        assertEquals(List.of(), events.all, "no second has passed");

        fixture.adapter.globalScheduler().stepTicks(1);
        assertEquals(List.of(OptionalInt.of(359)), events.ticked(airdrop, Event::life));
        assertEquals(List.of(9), events.ticked(airdrop, Event::lock));
        assertEquals(List.of(OptionalInt.empty()), events.ticked(forever, Event::life), "out of view still ticks");
        assertEquals(List.of(1), events.ticked(forever, Event::lock));

        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);

        assertEquals(List.of(OptionalInt.of(359), OptionalInt.of(358), OptionalInt.of(357)),
                events.ticked(airdrop, Event::life));
        assertEquals(List.of(9, 8, 7), events.ticked(airdrop, Event::lock));
        assertEquals(List.of(1, 0, 0), events.ticked(forever, Event::lock));
        assertEquals(6, events.all.size(), "ticked only");
        assertEquals(new BoxEvent(airdrop.boxId(), "airdrops", WORLD, airdrop.position(), Optional.of(OWNER), 7,
                OptionalInt.of(357)), events.ticked(airdrop, Event::box).get(2));
    }

    /**
     * Acceptance: a drop open click fires ended once, carrying the box as it was last.
     */
    @Test
    void aDropOpenFiresEndedOnce() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 60, false)));
        LootBoxLifecycle lifecycle = observed(config, 69);
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        LootBox other = summon(lifecycle, "crate", 4, 3, Optional.empty());

        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);
        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);

        assertEquals(List.of(BoxEvent.of(box)), events.ended());
        assertTrue(lifecycle.isLootBox(WORLD, 4, 64, 3));
        assertFalse(events.ended().contains(BoxEvent.of(other)));
    }

    /**
     * Acceptance: a break fires ended once. A refused break fires nothing.
     */
    @Test
    void aBreakFiresEndedOnce() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("inventory", 5, 60, false)));
        LootBoxLifecycle lifecycle = observed(config, 70);
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.of(OWNER));

        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.BREAK);
        assertEquals(List.of(), events.ended(), "the lock refused the break");

        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.BREAK);
        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.BREAK);

        assertEquals(List.of(BoxEvent.of(box)), events.ended());
    }

    /**
     * Acceptance: an explosion that ends an unlocked box whose loot allows it fires ended once. A kept box fires
     * nothing.
     */
    @Test
    void anExplosionFiresEndedOnce() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", """
                language: en
                open_mode: drop
                lock: 0
                life: 60
                block: barrel
                loot_end:
                  allow_explosion: true
                roll:
                  entries:
                    gold:
                      weight: -1
                      amount: 2
                      item:
                        id: gold_ingot
                """));
        LootBoxLifecycle lifecycle = observed(config, 71);
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        LootBox kept = summon(lifecycle, "airdrops", 3, 3, Optional.empty());

        lifecycle.explode(WORLD, List.of(box.position(), kept.position()));
        lifecycle.explode(WORLD, List.of(box.position(), kept.position()));

        assertEquals(List.of(BoxEvent.of(box)), events.ended());
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3), "the shipped airdrop does not allow explosions");
    }

    /**
     * Acceptance: a changed block fires ended once, whether a click or a chunk load finds it.
     */
    @Test
    void aChangedBlockFiresEndedOnce() {
        LootBoxLifecycle lifecycle = observed(shippedConfig(), 72);
        LootBox clicked = summon(lifecycle, "airdrops", 2, 3, Optional.empty());
        LootBox loaded = summon(lifecycle, "airdrops", 4, 3, Optional.empty());
        fixture.adapter.putBlock(clicked.position(), "minecraft:stone");
        fixture.adapter.putBlock(loaded.position(), "minecraft:stone");

        lifecycle.interact(OTHER, WORLD, clicked.position(), false, Interaction.CLICK);
        lifecycle.interact(OTHER, WORLD, clicked.position(), false, Interaction.CLICK);
        assertEquals(List.of(BoxEvent.of(clicked)), events.ended());

        lifecycle.chunkLoaded(WORLD, CHUNK);
        lifecycle.chunkLoaded(WORLD, CHUNK);

        assertEquals(List.of(BoxEvent.of(clicked), BoxEvent.of(loaded)), events.ended());
    }

    /**
     * Acceptance: a timeout fires ended once, after the tick that took the life to 0, carrying life 0.
     */
    @Test
    void aTimeoutFiresEndedOnce() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, false)));
        LootBoxLifecycle lifecycle = observed(config, 73);
        lifecycle.start().join();
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());

        fixture.adapter.globalScheduler().stepTicks(SECOND * 5);

        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 3));
        List<Kind> kinds = events.all.stream().map(Event::kind).toList();
        assertEquals(List.of(Kind.SHOWN, Kind.TICKED, Kind.TICKED, Kind.ENDED), kinds);
        assertEquals(OptionalInt.of(0), events.ended().get(0).remainingLifeSeconds());
        assertEquals(box.boxId(), events.ended().get(0).boxId());
    }

    /**
     * Acceptance: a timeout in an unloaded chunk fires ended once, when the chunk loads, after shown.
     */
    @Test
    void aTimeoutInAnUnloadedChunkFiresEndedOnceWhenTheChunkLoads() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, false)));
        LootBoxLifecycle lifecycle = observed(config, 74);
        lifecycle.start().join();
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        unload(0, 0);
        lifecycle.chunkUnloaded(WORLD, CHUNK);
        fixture.adapter.globalScheduler().stepTicks(SECOND * 5);
        assertEquals(List.of(), events.ended());
        events.all.clear();

        load(0, 0);
        lifecycle.chunkLoaded(WORLD, CHUNK);
        fixture.adapter.globalScheduler().stepTicks(SECOND);

        assertEquals(List.of(Kind.SHOWN, Kind.ENDED), events.all.stream().map(Event::kind).toList());
        assertEquals(box.boxId(), events.ended().get(0).boxId());
    }

    /**
     * Acceptance: an empty removal fires ended once, when the last viewer closes the emptied inventory.
     */
    @Test
    void anEmptyRemovalFiresEndedOnce() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("inventory", 0, 60, true)));
        LootBoxLifecycle lifecycle = observed(config, 75);
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.CLICK);
        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);
        FakeSharedInventory inventory = fixture.adapter.inventoryOpens().get(0).inventory();
        inventory.contents().keySet().forEach(slot -> inventory.take(OWNER, slot));

        lifecycle.inventoryClosed(inventory, OWNER);
        assertEquals(List.of(), events.ended(), "another viewer still has it open");

        lifecycle.inventoryClosed(inventory, OTHER);
        lifecycle.inventoryClosed(inventory, OTHER);

        assertEquals(1, events.ended().size());
        assertEquals(box.boxId(), events.ended().get(0).boxId());
        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 3));
    }

    /**
     * An ended box is never shown or out of view again: its chunk's unload and load fire nothing.
     */
    @Test
    void anEndedBoxIsNeverShownAgain() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 60, false)));
        LootBoxLifecycle lifecycle = observed(config, 76);
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);
        events.all.clear();

        unload(0, 0);
        lifecycle.chunkUnloaded(WORLD, CHUNK);
        load(0, 0);
        lifecycle.chunkLoaded(WORLD, CHUNK);

        assertEquals(List.of(), events.all);
    }

    /**
     * Acceptance: with no observer set, the lifecycle behaves as before. A chunk unload changes nothing: the box stays
     * and keeps counting, no block is read or written, and a later timeout ends it as before.
     */
    @Test
    void withNoObserverSetTheLifecycleBehavesAsBefore() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 3, false)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 77);
        lifecycle.start().join();
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        int writes = fixture.adapter.blockWrites().size();

        lifecycle.chunkUnloaded(WORLD, CHUNK);
        fixture.adapter.globalScheduler().stepTicks(SECOND);

        assertEquals(OptionalInt.of(2), lifecycle.boxAt(WORLD, box.position()).orElseThrow().remainingLifeSeconds());
        assertEquals(List.of(), fixture.adapter.blockReads());
        assertEquals(writes, fixture.adapter.blockWrites().size());

        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);

        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 3));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(), fixture.warnings.lifecycle);
    }

    /**
     * An observer that throws is reported as a high warning with the box, and the lifecycle goes on: every box still
     * counts down and a timeout still ends its box.
     */
    @Test
    void anObserverThatThrowsIsWarnedAndTheLifecycleGoesOn() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, false)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 78);
        RuntimeException failure = new IllegalStateException("broken observer");
        lifecycle.addObserver(new BoxObserver() {
            @Override
            public void shown(BoxEvent box) {
                throw failure;
            }

            @Override
            public void ticked(BoxEvent box) {
                throw failure;
            }

            @Override
            public void outOfView(BoxEvent box) {
                throw failure;
            }

            @Override
            public void ended(BoxEvent box) {
                throw failure;
            }
        });
        lifecycle.start().join();
        LootBox first = summon(lifecycle, "crate", 2, 3, Optional.empty());
        LootBox second = summon(lifecycle, "crate", 4, 3, Optional.empty());

        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);

        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 3), "both boxes counted down and ended");
        assertFalse(lifecycle.isLootBox(WORLD, 4, 64, 3));
        assertEquals("minecraft:air", fixture.adapter.blockAt(first.position()));
        assertEquals("minecraft:air", fixture.adapter.blockAt(second.position()));
        List<LifecycleWarning> failed = fixture.warnings.lifecycle.stream()
                .filter(warning -> warning.kind() == LifecycleWarning.Kind.OBSERVER_FAILED)
                .toList();
        assertEquals(8, failed.size(), "2 shown, 4 ticked, 2 ended");
        LifecycleWarning warning = failed.get(0);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        assertEquals(Optional.of(first.boxId()), warning.boxId());
        assertEquals(Optional.of("crate"), warning.lootId());
        assertEquals(Optional.of(failure), warning.cause());
    }

    /**
     * An observer built against an API the server lacks throws a linkage error, not a runtime exception. It is warned
     * about the same way: every box still counts down and a due box still ends.
     */
    @Test
    void anObserverThatFailsToLinkIsWarnedAndTheLifecycleGoesOn() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, false)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 79);
        LinkageError failure = new NoSuchMethodError("missing");
        lifecycle.addObserver(new BoxObserver() {
            @Override
            public void shown(BoxEvent box) {
            }

            @Override
            public void ticked(BoxEvent box) {
                throw failure;
            }

            @Override
            public void outOfView(BoxEvent box) {
            }

            @Override
            public void ended(BoxEvent box) {
            }
        });
        lifecycle.start().join();
        LootBox first = summon(lifecycle, "crate", 2, 3, Optional.empty());
        LootBox second = summon(lifecycle, "crate", 4, 3, Optional.empty());

        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);

        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 3), "both boxes counted down and ended");
        assertFalse(lifecycle.isLootBox(WORLD, 4, 64, 3));
        assertEquals("minecraft:air", fixture.adapter.blockAt(first.position()));
        assertEquals("minecraft:air", fixture.adapter.blockAt(second.position()));
        assertEquals(4, fixture.warnings.lifecycle.stream()
                .filter(warning -> warning.kind() == LifecycleWarning.Kind.OBSERVER_FAILED
                        && warning.cause().equals(Optional.of(failure)))
                .count(), "2 boxes ticked twice");
    }

    /**
     * Acceptance: every added observer hears every event in the order added, and one that throws is warned about
     * without stopping the observers after it.
     */
    @Test
    void eachObserverHearsEveryEventAndAThrowingOneDoesNotStopTheOthers() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, false)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 80);
        Recorder first = new Recorder();
        Recorder second = new Recorder();
        RuntimeException failure = new IllegalStateException("broken observer");
        lifecycle.addObserver(first);
        lifecycle.addObserver(new BoxObserver() {
            @Override
            public void shown(BoxEvent box) {
                throw failure;
            }

            @Override
            public void ticked(BoxEvent box) {
                throw failure;
            }

            @Override
            public void outOfView(BoxEvent box) {
                throw failure;
            }

            @Override
            public void ended(BoxEvent box) {
                throw failure;
            }
        });
        lifecycle.addObserver(second);
        lifecycle.start().join();

        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);

        assertEquals(List.of(Kind.SHOWN, Kind.TICKED, Kind.TICKED, Kind.ENDED),
                second.all.stream().map(Event::kind).toList());
        assertEquals(first.all, second.all);
        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 3));
        assertEquals(4, fixture.warnings.lifecycle.stream()
                .filter(warning -> warning.kind() == LifecycleWarning.Kind.OBSERVER_FAILED
                        && warning.cause().equals(Optional.of(failure))
                        && warning.boxId().equals(Optional.of(box.boxId())))
                .count());
    }

    /**
     * Acceptance: a player opening an inventory open box fires opened with the box and the player, once the server
     * showed them the inventory; a refused click opens nothing.
     */
    @Test
    void openingAnInventoryOpenBoxFiresOpenedWithThePlayer() {
        LootBoxLifecycle lifecycle = observed(shippedConfig(Map.of("crate", loot("inventory", 5, -1, false))), 81);
        lifecycle.start().join();
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.of(OWNER));
        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);
        assertEquals(List.of(), events.opens, "locked: refused");

        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.CLICK);

        assertEquals(List.of(new Opened(OWNER, BoxEvent.of(box))), events.opens);
    }

    /**
     * Acceptance: a click on a drop open box fires opened, then ended, in that order.
     */
    @Test
    void clickingADropOpenBoxFiresOpenedBeforeEnded() {
        LootBoxLifecycle lifecycle = observed(shippedConfig(Map.of("crate", loot("drop", 0, -1, false))), 82);
        lifecycle.start().join();
        LootBox box = summon(lifecycle, "crate", 2, 3, Optional.empty());
        events.all.clear();

        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);

        assertEquals(List.of(new Opened(OTHER, BoxEvent.of(box))), events.opens);
        assertEquals(List.of(Kind.OPENED, Kind.ENDED), events.all.stream().map(Event::kind).toList());
    }

    private LootBoxLifecycle observed(ConfigSnapshot config, long seed) {
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, seed);
        lifecycle.addObserver(events);
        return lifecycle;
    }

    private void unload(int chunkX, int chunkZ) {
        fixture.adapter.putChunk(flatChunk(chunkX, chunkZ), false, true);
    }

    private void load(int chunkX, int chunkZ) {
        fixture.adapter.putChunk(flatChunk(chunkX, chunkZ), true, true);
    }

    private static LootBox summon(LootBoxLifecycle lifecycle, String lootId, int x, int z, Optional<UUID> owner) {
        SummonOutcome outcome = lifecycle.summon(lootId, airSpot(x, z), owner, SummonKind.UNKNOWN).join();
        return outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
    }

    /**
     * A barrel loot with 7 gold. A negative life is unending.
     */
    private static String loot(String openMode, int lock, int life, boolean emptyRemoval) {
        return """
                language: en
                open_mode: %s
                lock: %d
                life: %d
                block: barrel
                loot_end:
                  empty_removal: %s
                roll:
                  entries:
                    gold:
                      weight: -1
                      amount: 7
                      item:
                        id: gold_ingot
                """.formatted(openMode, lock, life, emptyRemoval);
    }

    private enum Kind {
        SHOWN, TICKED, OUT_OF_VIEW, ENDED, OPENED
    }

    private record Opened(UUID player, BoxEvent box) {
    }

    private record Event(Kind kind, BoxEvent box) {

        OptionalInt life() {
            return box.remainingLifeSeconds();
        }

        int lock() {
            return box.remainingLockSeconds();
        }
    }

    /**
     * Records every event in the order it arrived, from any thread.
     */
    private static final class Recorder implements BoxObserver {

        final List<Event> all = new CopyOnWriteArrayList<>();
        final List<Opened> opens = new CopyOnWriteArrayList<>();

        @Override
        public void opened(BoxEvent box, UUID playerId) {
            all.add(new Event(Kind.OPENED, box));
            opens.add(new Opened(playerId, box));
        }

        @Override
        public void shown(BoxEvent box) {
            all.add(new Event(Kind.SHOWN, box));
        }

        @Override
        public void ticked(BoxEvent box) {
            all.add(new Event(Kind.TICKED, box));
        }

        @Override
        public void outOfView(BoxEvent box) {
            all.add(new Event(Kind.OUT_OF_VIEW, box));
        }

        @Override
        public void ended(BoxEvent box) {
            all.add(new Event(Kind.ENDED, box));
        }

        List<BoxEvent> ended() {
            return all.stream().filter(event -> event.kind() == Kind.ENDED).map(Event::box).toList();
        }

        <T> List<T> ticked(LootBox box, Function<Event, T> value) {
            return all.stream()
                    .filter(event -> event.kind() == Kind.TICKED && event.box().boxId().equals(box.boxId()))
                    .map(value)
                    .toList();
        }
    }
}
