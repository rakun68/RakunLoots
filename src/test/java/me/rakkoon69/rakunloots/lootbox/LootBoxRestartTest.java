package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.storage.LootBoxRecord;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.LootBoxWarning;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
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
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootBoxRestartTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String CRATE = """
            language: en
            open_mode: inventory
            lock: 30
            block: chest
            roll:
              entries:
                gold:
                  amount: 7
                  item:
                    id: gold_ingot
                sticks:
                  slot: 12
                  amount: 70
                  item:
                    id: stick
            """;

    /**
     * A drop open loot of 5 seconds of life and 4 of lock, with every loot end rule on.
     */
    private static final String GONE = """
            language: en
            open_mode: drop
            lock: 4
            life: 5
            block: barrel
            loot_end:
              allow_explosion: true
              timeout_spill: true
              drop_block: true
              empty_removal: true
            roll:
              entries:
                gold:
                  amount: 7
                  item:
                    id: gold_ingot
                sticks:
                  slot: 12
                  amount: 3
                  item:
                    id: stick
            """;

    @TempDir
    Path folder;

    private LifecycleFixture fixture;

    @BeforeEach
    void setUp() {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Acceptance: a box keeps how it was summoned across a restart, for each source.
     */
    @Test
    void aBoxKeepsItsSummonSourceAcrossARestart() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("crate", CRATE));
        SummonKind[] kinds = SummonKind.values();
        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle first = fixture.lifecycle(config, store, 11);
            first.start().join();
            for (int i = 0; i < kinds.length; i++) {
                assertEquals(kinds[i], first.summon("crate", airSpot(i * 4, 1), Optional.empty(), kinds[i]).join()
                        .box().orElseThrow().source());
            }
            first.stop();
        }

        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle second = fixture.lifecycle(config, store, 99);
            second.start().join();
            for (int i = 0; i < kinds.length; i++) {
                assertEquals(kinds[i], second.boxAt(WORLD, LifecycleFixture.position(airSpot(i * 4, 1))).orElseThrow().source(),
                        kinds[i].name());
            }
        }
    }

    /**
     * Acceptance: stopping and starting a new lifecycle on the same store keeps the rolled items, slots, owner,
     * timers, and placed block. The new lifecycle writes no block: the box is already in the world.
     */
    @Test
    void aNewLifecycleOnTheSameStoreKeepsItemsSlotsOwnerTimersAndPlacedBlock() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("crate", CRATE));
        LootBox owned;
        LootBox unowned;
        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle first = fixture.lifecycle(config, store, 11);
            first.start().join();
            owned = first.summon("airdrops", airSpot(1, 1), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();
            unowned = first.summon("crate", airSpot(-3, 5), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
            first.stop();
        }
        int writes = fixture.adapter.blockWrites().size();

        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle second = fixture.lifecycle(config, store, 99);
            second.start().join();

            for (LootBox before : List.of(owned, unowned)) {
                LootBox after = second.boxAt(WORLD, before.position()).orElseThrow();
                assertEquals(before.boxId(), after.boxId());
                assertEquals(before.lootId(), after.lootId());
                assertEquals(before.owner(), after.owner());
                assertEquals(before.worldKey(), after.worldKey());
                assertEquals(before.placedBlockId(), after.placedBlockId());
                assertEquals(before.remainingLifeSeconds(), after.remainingLifeSeconds());
                assertEquals(before.remainingLockSeconds(), after.remainingLockSeconds());
                assertEquals(describe(before.contents().stacks()), describe(after.contents().stacks()));
                assertEquals(before.contents().stacks().stream().map(RolledStack::item).toList(),
                        after.contents().stacks().stream().map(RolledStack::item).toList());
                assertTrue(second.isLootBox(WORLD, before.position().x(), before.position().y(),
                        before.position().z()));
            }
            second.stop();
        }
        assertEquals("minecraft:chest", unowned.placedBlockId());
        assertEquals(30, unowned.remainingLockSeconds());
        assertEquals(3, unowned.contents().stacks().size(), "7 gold, then 64 and 6 sticks");
        assertEquals(writes, fixture.adapter.blockWrites().size(), "a restart writes no block");
    }

    /**
     * Acceptance (ADR 0014): a stored box whose loot is missing at start registers like any other, and runs on: it
     * keeps its stored life, lock, and stacks and counts them down. A click opens it as inventory open with its
     * stored stacks. At life 0 it ends with every loot end rule off: the loot had timeout spill and drop block on,
     * yet nothing spills and no block item drops. Its row is deleted by the next save.
     */
    @Test
    void aStoredBoxWhoseLootIsMissingAtStartRegistersAndRunsOn() throws Exception {
        ConfigSnapshot withGone = shippedConfig(Map.of("gone", GONE));
        ConfigSnapshot without = shippedConfig();
        LootBox gone;
        LootBox airdrop;
        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle first = fixture.lifecycle(withGone, store, 12);
            gone = first.summon("gone", airSpot(2, 2), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();
            airdrop = first.summon("airdrops", airSpot(4, 4), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
            first.stop();
        }
        fixture.adapter.addOnlinePlayer(OWNER);

        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle restarted = fixture.lifecycle(without, store, 13);
            restarted.start().join();

            assertTrue(restarted.isLootBox(WORLD, 2, 64, 2), "the box whose loot is missing is registered");
            assertTrue(restarted.isLootBox(WORLD, 4, 64, 4));
            LootBox after = restarted.boxAt(WORLD, gone.position()).orElseThrow();
            assertEquals(gone.boxId(), after.boxId());
            assertEquals(gone.remainingLifeSeconds(), after.remainingLifeSeconds());
            assertEquals(gone.remainingLockSeconds(), after.remainingLockSeconds());
            assertEquals(gone.contents().stacks().stream().map(RolledStack::item).toList(),
                    after.contents().stacks().stream().map(RolledStack::item).toList());
            assertTrue(fixture.warnings.store.stream().anyMatch(warning ->
                    warning.kind() == LootBoxWarning.Kind.LOOT_MISSING
                            && warning.boxId().equals(gone.boxId().toString())));

            assertEquals(InteractionResult.ALLOWED,
                    restarted.interact(OWNER, WORLD, gone.position(), false, Interaction.CLICK));
            assertEquals(1, fixture.adapter.inventoryOpens().size(), "inventory open, though the loot was drop open");
            assertTrue(restarted.isLootBox(WORLD, 2, 64, 2));

            fixture.adapter.globalScheduler().stepTicks(LootBoxLifecycle.TICK_PERIOD_TICKS * 3);
            LootBox counted = restarted.boxAt(WORLD, gone.position()).orElseThrow();
            assertEquals(OptionalInt.of(2), counted.remainingLifeSeconds());
            assertEquals(1, counted.remainingLockSeconds());
            fixture.adapter.globalScheduler().stepTicks(LootBoxLifecycle.TICK_PERIOD_TICKS * 2);

            assertFalse(restarted.isLootBox(WORLD, 2, 64, 2), "it ends at life 0");
            assertEquals("minecraft:air", fixture.adapter.blockAt(gone.position()));
            assertEquals(List.of(), fixture.adapter.drops(), "no spill and no block item");
            restarted.stop();
        }
        assertEquals(Set.of(airdrop.boxId()), fixture.storedBoxes(without).keySet(), "the ended box's row is gone");
    }

    /**
     * Acceptance: a box whose world is not loaded stays flagged in storage and is not registered, whether or not its
     * loot is missing too. The new lifecycle never saves over its row or deletes it.
     */
    @Test
    void aBoxWhoseWorldIsMissingStaysFlaggedAndIsNeverSavedOver() throws Exception {
        ConfigSnapshot withCrate = shippedConfig(Map.of("crate", CRATE));
        LootBox crate;
        LootBox airdrop;
        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle first = fixture.lifecycle(withCrate, store, 14);
            crate = first.summon("crate", airSpot(2, 2), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
            airdrop = first.summon("airdrops", airSpot(4, 4), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
            first.stop();
        }
        Map<UUID, LootBoxRecord> before = fixture.storedBoxes(withCrate);

        fixture.adapter.setWorldLoaded(false);
        for (ConfigSnapshot config : List.of(withCrate, shippedConfig())) {
            try (LootBoxStore store = fixture.openStore()) {
                LootBoxLifecycle worldGone = fixture.lifecycle(config, store, 15);
                worldGone.start().join();

                assertFalse(worldGone.isLootBox(WORLD, 2, 64, 2));
                assertFalse(worldGone.isLootBox(WORLD, 4, 64, 4));
                fixture.adapter.globalScheduler().stepTicks(LootBoxLifecycle.TICK_PERIOD_TICKS * 5);
                worldGone.stop();
            }
            Map<UUID, LootBoxRecord> after = fixture.storedBoxes(withCrate);
            assertEquals(Set.of(crate.boxId(), airdrop.boxId()), after.keySet(), "both rows stay");
            before.forEach((id, row) -> {
                assertEquals(row.remainingLifeSeconds(), after.get(id).remainingLifeSeconds());
                assertEquals(row.remainingLockSeconds(), after.get(id).remainingLockSeconds());
            });
        }
    }

    /**
     * Acceptance: the save task writes marked boxes every 60 seconds on the async scheduler. The global scheduler
     * saves nothing, and nothing is written before the first minute.
     */
    @Test
    void theSaveTaskWritesMarkedBoxesEverySixtySecondsOnTheAsyncScheduler() throws Exception {
        ConfigSnapshot config = shippedConfig();
        try (LootBoxStore store = fixture.openStore()) {
            LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 15);
            lifecycle.start().join();
            LootBox first = lifecycle.summon("airdrops", airSpot(1, 1), Optional.empty(), SummonKind.UNKNOWN).join().box()
                    .orElseThrow();

            fixture.adapter.globalScheduler().stepTicks(1200);
            fixture.adapter.asyncScheduler().stepTicks(1199);
            assertEquals(Set.of(), fixture.storedBoxes(config).keySet(), "nothing before 60 seconds");

            fixture.adapter.asyncScheduler().stepTicks(1);
            assertEquals(Set.of(first.boxId()), fixture.storedBoxes(config).keySet(), "saved at 60 seconds");

            LootBox second = lifecycle.summon("airdrops", airSpot(5, 5), Optional.empty(), SummonKind.UNKNOWN).join().box()
                    .orElseThrow();
            fixture.adapter.asyncScheduler().stepTicks(1199);
            assertEquals(Set.of(first.boxId()), fixture.storedBoxes(config).keySet());
            fixture.adapter.asyncScheduler().stepTicks(1);
            assertEquals(Set.of(first.boxId(), second.boxId()), fixture.storedBoxes(config).keySet(),
                    "saved again at 120 seconds");

            lifecycle.stop();
            LootBox late = lifecycle.summon("airdrops", airSpot(7, 7), Optional.empty(), SummonKind.UNKNOWN).join().box()
                    .orElseThrow();
            fixture.adapter.asyncScheduler().stepTicks(2400);
            assertFalse(fixture.storedBoxes(config).containsKey(late.boxId()), "stop ends the save task");
        }
    }

    /**
     * Each stack as its slot, item id, and amount.
     */
    private static List<String> describe(List<RolledStack> stacks) {
        return stacks.stream()
                .map(stack -> stack.slot().getAsInt() + " " + stack.item().getType().getKey() + " x"
                        + stack.item().getAmount())
                .toList();
    }
}
