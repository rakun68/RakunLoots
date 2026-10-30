package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.storage.LootBoxRecord;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.Drop;
import me.rakkoon69.rakunloots.summon.FakeSharedInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.flatChunk;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Life and lock count down on one global 1 second tick, and a box whose life runs out ends at its location, or when
 * its chunk loads, never by loading the chunk.
 */
class LootBoxTimeoutTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final long SECOND = LootBoxLifecycle.TICK_PERIOD_TICKS;
    private static final long SAVE_TICKS = LootBoxLifecycle.SAVE_PERIOD_TICKS;

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;

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
     * The tick period is one second of server ticks, derived from 20 ticks per second.
     */
    @Test
    void oneSecondIsTwentyTicks() {
        assertEquals(20L, SECOND);
    }

    /**
     * Acceptance: stepping the scheduler counts life and lock down by 1 second per 20 ticks. Unending life stays
     * unending. The shipped airdrop (life 360, lock 10) counts on the global scheduler, not the async one; the lock
     * stops at 0.
     */
    @Test
    void steppingTheSchedulerCountsLifeAndLockDownByOneSecondPerTwentyTicks() {
        ConfigSnapshot config = shippedConfig(Map.of("forever", loot("drop", 5, -1, false, false)));
        LootBoxLifecycle lifecycle = started(config, 31);
        LootBox airdrop = summon(lifecycle, "airdrops", 1, 1, Optional.of(OWNER));
        LootBox forever = summon(lifecycle, "forever", 3, 3, Optional.of(OWNER));
        assertEquals(OptionalInt.of(360), airdrop.remainingLifeSeconds());
        assertEquals(10, airdrop.remainingLockSeconds());
        assertEquals(OptionalInt.empty(), forever.remainingLifeSeconds());

        fixture.adapter.asyncScheduler().stepTicks(SECOND * 3);
        fixture.adapter.globalScheduler().stepTicks(SECOND - 1);
        assertTimers(lifecycle, airdrop, OptionalInt.of(360), 10);
        assertTimers(lifecycle, forever, OptionalInt.empty(), 5);

        fixture.adapter.globalScheduler().stepTicks(1);
        assertTimers(lifecycle, airdrop, OptionalInt.of(359), 9);
        assertTimers(lifecycle, forever, OptionalInt.empty(), 4);

        fixture.adapter.globalScheduler().stepTicks(SECOND * 4);
        assertTimers(lifecycle, airdrop, OptionalInt.of(355), 5);
        assertTimers(lifecycle, forever, OptionalInt.empty(), 0);

        fixture.adapter.globalScheduler().stepTicks(SECOND * 20);
        assertTimers(lifecycle, airdrop, OptionalInt.of(335), 0);
        assertTimers(lifecycle, forever, OptionalInt.empty(), 0);
    }

    /**
     * Acceptance: when the lock reaches 0, any player can take. Before, another player gets {@code still_locked}.
     */
    @Test
    void whenTheLockReachesZeroAnyPlayerCanTake() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 2, 60, false, false)));
        LootBoxLifecycle lifecycle = started(config, 32);
        LootBox box = summon(lifecycle, "crate", 2, 2, Optional.of(OWNER));

        fixture.adapter.globalScheduler().stepTicks(SECOND);
        assertEquals(InteractionResult.REFUSED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK));
        assertEquals(1, fixture.adapter.messages().size(), "still locked with 1 second left");
        assertEquals(OTHER, fixture.adapter.messages().get(0).playerId());
        assertEquals(List.of(), fixture.adapter.drops());

        fixture.adapter.globalScheduler().stepTicks(SECOND);
        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK));

        assertEquals(dropsOf(box), fixture.adapter.drops(), "the other player took it");
        assertEquals(1, fixture.adapter.messages().size(), "no second message");
        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 2));
    }

    /**
     * Acceptance: a timeout with an open viewer closes the view before the box ends. Until the close completes, the
     * block stays and nothing drops; then what is left in the shared inventory drops, with spill on.
     */
    @Test
    void aTimeoutWithAnOpenViewerClosesTheViewBeforeTheBoxEnds() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("inventory", 0, 3, true, true)));
        LootBoxLifecycle lifecycle = started(config, 33);
        LootBox box = summon(lifecycle, "crate", 4, 4, Optional.empty());
        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.CLICK);
        FakeSharedInventory inventory = fixture.adapter.inventoryOpens().get(0).inventory();
        inventory.take(OWNER, 12);
        fixture.adapter.holdInventoryCloses();

        fixture.adapter.globalScheduler().stepTicks(SECOND * 3);

        assertEquals(List.of(inventory), fixture.adapter.inventoryCloses(), "the view is closed first");
        assertEquals(Set.of(), inventory.viewers());
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()), "the box waits for the close");
        assertEquals(List.of(), fixture.adapter.drops());

        fixture.adapter.releaseInventoryCloses();

        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(new Drop(box.position(), "minecraft:gold_ingot", 7, Optional.empty())),
                fixture.adapter.drops(), "what is left in the inventory, without the taken sticks or a block item");
        assertFalse(lifecycle.isLootBox(WORLD, 4, 64, 4));
    }

    /**
     * Acceptance: with {@code timeout_spill} on, the stacks drop. The block item never drops, even with
     * {@code drop_block} on. The row is deleted by the next save.
     */
    @Test
    void withTimeoutSpillOnTheStacksDropAndTheBlockItemDoesNot() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, true, true)));
        LootBoxLifecycle lifecycle = started(config, 34);
        LootBox box = summon(lifecycle, "crate", 5, 5, Optional.empty());
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertTrue(fixture.storedBoxes(config).containsKey(box.boxId()));

        fixture.adapter.globalScheduler().stepTicks(SECOND);
        assertTrue(lifecycle.isLootBox(WORLD, 5, 64, 5), "1 second left");

        fixture.adapter.globalScheduler().stepTicks(SECOND);

        assertFalse(lifecycle.isLootBox(WORLD, 5, 64, 5));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(dropsOf(box), fixture.adapter.drops(), "the stacks, and no barrel");
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertFalse(fixture.storedBoxes(config).containsKey(box.boxId()), "the next save deleted the row");
    }

    /**
     * Acceptance: with {@code timeout_spill} off, the stacks are gone, and the block item never drops.
     */
    @Test
    void withTimeoutSpillOffTheStacksAreGone() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("inventory", 0, 2, false, true)));
        LootBoxLifecycle lifecycle = started(config, 35);
        LootBox box = summon(lifecycle, "crate", 6, 6, Optional.empty());

        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);

        assertFalse(lifecycle.isLootBox(WORLD, 6, 64, 6));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(), fixture.adapter.drops());
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertFalse(fixture.storedBoxes(config).containsKey(box.boxId()));
    }

    /**
     * The shipped airdrop, read through the config loader, times out after 360 seconds with spill off: its block goes
     * and nothing drops.
     */
    @Test
    void theShippedAirdropEndsAfterItsLifeWithNothingDropped() {
        LootBoxLifecycle lifecycle = started(shippedConfig(), 36);
        LootBox box = summon(lifecycle, "airdrops", 7, 7, Optional.empty());

        fixture.adapter.globalScheduler().stepTicks(SECOND * 359);
        assertTrue(lifecycle.isLootBox(WORLD, 7, 64, 7));

        fixture.adapter.globalScheduler().stepTicks(SECOND);

        assertFalse(lifecycle.isLootBox(WORLD, 7, 64, 7));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(), fixture.adapter.drops());
    }

    /**
     * Acceptance: a box whose life ends in an unloaded chunk keeps its row at remaining life 0 and ends when the
     * chunk loads. A chunk load elsewhere leaves it, and a box in that chunk with life left stays.
     */
    @Test
    void aBoxWhoseLifeEndsInAnUnloadedChunkKeepsItsRowAtZeroAndEndsWhenTheChunkLoads() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of(
                "crate", loot("drop", 0, 2, true, false),
                "long", loot("drop", 0, 600, true, false)));
        LootBoxLifecycle lifecycle = started(config, 37);
        LootBox box = summon(lifecycle, "crate", 3, 3, Optional.empty());
        LootBox longer = summon(lifecycle, "long", 9, 9, Optional.empty());
        unload(0, 0);

        fixture.adapter.globalScheduler().stepTicks(SECOND * 5);

        assertTimers(lifecycle, box, OptionalInt.of(0), 0);
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(), fixture.adapter.drops());
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        LootBoxRecord stored = fixture.storedBoxes(config).get(box.boxId());
        assertEquals(OptionalInt.of(0), stored.remainingLifeSeconds(), "the row stays at remaining life 0");

        lifecycle.chunkLoaded(WORLD, new ChunkPosition(1, 0));
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3), "another chunk's load leaves it");

        load(0, 0);
        lifecycle.chunkLoaded(WORLD, new ChunkPosition(0, 0));

        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 3));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(dropsOf(box), fixture.adapter.drops(), "spill on drops the stacks once the chunk is there");
        assertTrue(lifecycle.isLootBox(WORLD, 9, 64, 9), "a box with life left in that chunk stays");
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertFalse(fixture.storedBoxes(config).containsKey(box.boxId()));
    }

    /**
     * Acceptance: after a restart, that box still ends when its chunk loads. The new lifecycle keeps it while the
     * chunk stays unloaded. Under skip, since load and generate hold the chunk of a stored box from the start
     * (ADR 0015).
     */
    @Test
    void afterARestartThatBoxStillEndsWhenItsChunkLoads() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, true, false)), "skip");
        LootBoxLifecycle first = started(config, 38);
        LootBox box = summon(first, "crate", 3, 3, Optional.empty());
        unload(0, 0);
        fixture.adapter.globalScheduler().stepTicks(SECOND * 3);
        first.stop();
        store.close();

        store = fixture.openStore();
        LootBoxLifecycle second = started(config, 39);
        assertTimers(second, box, OptionalInt.of(0), 0);
        fixture.adapter.globalScheduler().stepTicks(SECOND * 3);
        assertTrue(second.isLootBox(WORLD, 3, 64, 3), "it waits for its chunk");
        assertEquals(List.of(), fixture.adapter.drops());

        load(0, 0);
        second.chunkLoaded(WORLD, new ChunkPosition(0, 0));

        assertFalse(second.isLootBox(WORLD, 3, 64, 3));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(dropsOf(box), fixture.adapter.drops());
        second.stop();
        assertFalse(fixture.storedBoxes(config).containsKey(box.boxId()));
    }

    /**
     * A stored box whose life is already 0 ends on the first tick when its chunk is loaded, so a crash after its life
     * ran out does not leave it behind as a plain block.
     */
    @Test
    void aStoredBoxAtLifeZeroInALoadedChunkEndsOnTheFirstTick() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, true, false)));
        LootBoxLifecycle first = started(config, 40);
        LootBox box = summon(first, "crate", 3, 3, Optional.empty());
        unload(0, 0);
        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);
        first.stop();
        store.close();
        load(0, 0);

        store = fixture.openStore();
        LootBoxLifecycle second = started(config, 41);
        assertTrue(second.isLootBox(WORLD, 3, 64, 3));

        fixture.adapter.globalScheduler().stepTicks(SECOND);

        assertFalse(second.isLootBox(WORLD, 3, 64, 3));
        assertEquals(dropsOf(box), fixture.adapter.drops());
    }

    /**
     * Acceptance: no chunk is read or loaded to end a timed-out box. While its chunk is unloaded, no chunk view is
     * read, its block is not read, and the chunk stays unloaded.
     */
    @Test
    void noChunkIsReadOrLoadedToEndATimedOutBox() {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 0, 2, true, false)));
        LootBoxLifecycle lifecycle = started(config, 42);
        LootBox box = summon(lifecycle, "crate", 3, 3, Optional.empty());
        unload(0, 0);
        int writes = fixture.adapter.blockWrites().size();

        fixture.adapter.globalScheduler().stepTicks(SECOND * 30);

        assertEquals(List.of(), fixture.adapter.reads(), "no chunk view is read");
        assertEquals(List.of(), fixture.adapter.blockReads(), "the box's block is not read");
        assertFalse(fixture.adapter.loadedChunks(WORLD).join().contains(new ChunkPosition(0, 0)),
                "the chunk stays unloaded");
        assertEquals(writes, fixture.adapter.blockWrites().size());
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3));
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()));
    }

    /**
     * Stop ends the tick: nothing counts down after it.
     */
    @Test
    void stopEndsTheTick() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("crate", loot("drop", 5, 2, true, false)));
        LootBoxLifecycle lifecycle = started(config, 43);
        LootBox box = summon(lifecycle, "crate", 3, 3, Optional.empty());

        lifecycle.stop();
        fixture.adapter.globalScheduler().stepTicks(SECOND * 10);

        assertTimers(lifecycle, box, OptionalInt.of(2), 5);
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()));
        assertEquals(OptionalInt.of(2), fixture.storedBoxes(config).get(box.boxId()).remainingLifeSeconds());
    }

    private LootBoxLifecycle started(ConfigSnapshot config, long seed) {
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, seed);
        lifecycle.start().join();
        return lifecycle;
    }

    private void unload(int chunkX, int chunkZ) {
        fixture.adapter.putChunk(flatChunk(chunkX, chunkZ), false, true);
    }

    private void load(int chunkX, int chunkZ) {
        fixture.adapter.putChunk(flatChunk(chunkX, chunkZ), true, true);
    }

    private static void assertTimers(LootBoxLifecycle lifecycle, LootBox summoned, OptionalInt life, int lock) {
        BlockPosition position = summoned.position();
        LootBox now = lifecycle.boxAt(WORLD, position).orElseThrow(() -> new AssertionError("no box at " + position));
        assertEquals(life, now.remainingLifeSeconds(), "remaining life");
        assertEquals(lock, now.remainingLockSeconds(), "remaining lock");
    }

    private static LootBox summon(LootBoxLifecycle lifecycle, String lootId, int x, int z, Optional<UUID> owner) {
        SummonOutcome outcome = lifecycle.summon(lootId, airSpot(x, z), owner, SummonKind.UNKNOWN).join();
        return outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
    }

    private static List<Drop> dropsOf(LootBox box) {
        return box.contents().stacks().stream()
                .map(RolledStack::item)
                .map(item -> new Drop(box.position(), item.getType().getKey().toString(), item.getAmount(),
                        Optional.empty()))
                .toList();
    }

    /**
     * A barrel loot with two stacks, 7 gold and 3 sticks fixed in slot 12. A negative life is unending.
     */
    private static String loot(String openMode, int lock, int life, boolean timeoutSpill, boolean dropBlock) {
        return """
                language: en
                open_mode: %s
                lock: %d
                life: %d
                block: barrel
                loot_end:
                  timeout_spill: %s
                  drop_block: %s
                roll:
                  entries:
                    gold:
                      weight: -1
                      amount: 7
                      item:
                        id: gold_ingot
                    sticks:
                      weight: -1
                      slot: 12
                      amount: 3
                      item:
                        id: stick
                """.formatted(openMode, lock, life, timeoutSpill, dropBlock);
    }
}
