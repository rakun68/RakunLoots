package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.Message;
import me.rakkoon69.rakunloots.summon.FakeSharedInventory;
import org.bukkit.inventory.ItemStack;
import java.util.TreeMap;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.Drop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
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
 * A reload hands the lifecycle a new config. Live boxes keep the block they were placed as, and a shorter life caps
 * their remaining life (ADR 0009).
 */
class LootBoxReloadTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final long SECOND = LootBoxLifecycle.TICK_PERIOD_TICKS;

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
     * Acceptance: changing a loot's block and reloading leaves live boxes of that loot alone. The barrel boxes of a
     * loot edited to chest are not changed blocks at chunk load, click, explosion, or the tick. A break drops the
     * barrel they were placed as and removes it, and a new summon places a chest.
     */
    @Test
    void changingALootsBlockAndReloadingLeavesLiveBoxesOfThatLootAlone() {
        LootBoxLifecycle lifecycle = started(shippedConfig(Map.of("crate", loot("barrel", 360))), 71);
        LootBox first = summon(lifecycle, 3, 3);
        LootBox second = summon(lifecycle, 5, 5);

        lifecycle.reload(shippedConfig(Map.of("crate", loot("chest", 360))));
        lifecycle.chunkLoaded(WORLD, new ChunkPosition(0, 0));
        assertEquals(InteractionResult.REFUSED, lifecycle.interact(OTHER, WORLD, first.position(), false, Interaction.CLICK), "still locked");
        assertEquals(Set.of(first.position(), second.position()),
                lifecycle.explode(WORLD, List.of(first.position(), second.position())));
        fixture.adapter.globalScheduler().stepTicks(SECOND);

        for (LootBox box : List.of(first, second)) {
            assertTrue(lifecycle.isLootBox(WORLD, box.position().x(), box.position().y(), box.position().z()));
            assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()));
        }
        assertEquals(List.of(), fixture.warnings.lifecycle);
        assertEquals(List.of(), fixture.adapter.drops());

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OWNER, WORLD, first.position(), false, Interaction.BREAK));

        List<Drop> expected = new ArrayList<>(stacksOf(first));
        expected.add(new Drop(first.position(), "minecraft:barrel", 1, Optional.empty()));
        assertEquals(expected, fixture.adapter.drops(), "the block item is the placed barrel");
        assertEquals("minecraft:air", fixture.adapter.blockAt(first.position()));
        LootBox after = summon(lifecycle, 7, 7);
        assertEquals("minecraft:chest", after.placedBlockId());
        assertEquals("minecraft:chest", fixture.adapter.blockAt(after.position()));
        assertEquals(List.of(), fixture.warnings.lifecycle);
    }

    /**
     * A shorter life after a reload caps the remaining life. A longer one does not raise it, and unending life does
     * not lift a cap. A box with unending life gets the new life.
     */
    @Test
    void aShorterLifeAfterAReloadCapsTheRemainingLifeAndALongerOneDoesNotRaiseIt() {
        LootBoxLifecycle lifecycle = started(shippedConfig(Map.of(
                "crate", loot("barrel", 360),
                "forever", loot("barrel", -1))), 72);
        LootBox crate = summon(lifecycle, "crate", 3, 3);
        LootBox forever = summon(lifecycle, "forever", 5, 5);
        fixture.adapter.globalScheduler().stepTicks(SECOND * 10);
        assertLife(lifecycle, crate, OptionalInt.of(350));
        assertLife(lifecycle, forever, OptionalInt.empty());

        lifecycle.reload(shippedConfig(Map.of("crate", loot("barrel", 100), "forever", loot("barrel", 400))));
        assertLife(lifecycle, crate, OptionalInt.of(100));
        assertLife(lifecycle, forever, OptionalInt.of(400));

        lifecycle.reload(shippedConfig(Map.of("crate", loot("barrel", 500), "forever", loot("barrel", -1))));
        assertLife(lifecycle, crate, OptionalInt.of(100));
        assertLife(lifecycle, forever, OptionalInt.of(400));

        fixture.adapter.globalScheduler().stepTicks(SECOND);
        assertLife(lifecycle, crate, OptionalInt.of(99));
    }

    /**
     * A box that waits at remaining life 0 for its chunk keeps waiting through a reload, even to a longer life, and
     * ends when its chunk loads.
     */
    @Test
    void aReloadLeavesABoxWaitingAtLifeZeroToEndWhenItsChunkLoads() {
        LootBoxLifecycle lifecycle = started(shippedConfig(Map.of("crate", loot("barrel", 2))), 73);
        LootBox box = summon(lifecycle, 3, 3);
        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        fixture.adapter.globalScheduler().stepTicks(SECOND * 3);
        assertLife(lifecycle, box, OptionalInt.of(0));

        lifecycle.reload(shippedConfig(Map.of("crate", loot("barrel", 600))));
        fixture.adapter.globalScheduler().stepTicks(SECOND * 3);
        assertLife(lifecycle, box, OptionalInt.of(0));

        fixture.adapter.putChunk(flatChunk(0, 0), true, true);
        lifecycle.chunkLoaded(WORLD, new ChunkPosition(0, 0));

        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 3));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
    }

    /**
     * Acceptance (ADR 0014): a box whose loot a reload removed stays registered and counts down its stored life and
     * lock. At life 0 it ends with every loot end rule off: the loot had timeout spill and drop block on, yet nothing
     * spills and no block item drops; the block is removed.
     */
    @Test
    void aBoxWhoseLootAReloadRemovedCountsDownItsStoredLifeAndLockAndEndsAtLifeZero() {
        LootBoxLifecycle lifecycle = started(shippedConfig(Map.of("gone", allRulesOnLoot("drop", 10, 5))), 74);
        LootBox box = summon(lifecycle, "gone", 3, 3);
        fixture.adapter.globalScheduler().stepTicks(SECOND * 4);

        lifecycle.reload(shippedConfig());

        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3), "the box stays registered");
        assertLife(lifecycle, box, OptionalInt.of(6));
        assertEquals(1, lifecycle.boxAt(WORLD, box.position()).orElseThrow().remainingLockSeconds());
        fixture.adapter.globalScheduler().stepTicks(SECOND * 2);
        assertLife(lifecycle, box, OptionalInt.of(4));
        assertEquals(0, lifecycle.boxAt(WORLD, box.position()).orElseThrow().remainingLockSeconds());
        assertEquals(Set.of(box.position()), lifecycle.explode(WORLD, List.of(box.position())));
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3), "an unlocked box survives: allow explosion is off");

        fixture.adapter.globalScheduler().stepTicks(SECOND * 4);

        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 3));
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(), fixture.adapter.drops(), "a timeout removes it with no spill and no block item");
    }

    /**
     * Acceptance: a box whose loot was drop open opens as inventory open with its stored stacks, titled with the
     * loot id since the loot is gone. A click does not end it. Its lock still holds with the default message.
     */
    @Test
    void aBoxWhoseDropOpenLootWasRemovedOpensAsInventoryOpenWithItsStoredStacks() {
        LootBoxLifecycle lifecycle = started(shippedConfig(Map.of("gone", allRulesOnLoot("drop", 360, 60))), 75);
        LootBox box = summon(lifecycle, "gone", 3, 3);
        lifecycle.reload(shippedConfig());

        assertEquals(InteractionResult.REFUSED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK));
        assertEquals(List.of(new Message(OTHER, "§f[§bRakunLoots§f]§7 This loot is still §clocked §7and you are §cnot the owner §7!")),
                fixture.adapter.messages());
        assertEquals(List.of(), fixture.adapter.inventoryOpens());

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.CLICK));

        assertEquals(1, fixture.adapter.inventoryOpens().size());
        FakeSharedInventory inventory = fixture.adapter.inventoryOpens().get(0).inventory();
        assertEquals(OWNER, fixture.adapter.inventoryOpens().get(0).playerId());
        assertEquals("gone", inventory.title());
        Map<Integer, ItemStack> stored = new TreeMap<>();
        box.contents().stacks().forEach(stack -> stored.put(stack.slot().getAsInt(), stack.item()));
        assertEquals(stored, inventory.contents());
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3), "a drop open click would have ended it");
        assertEquals(List.of(), fixture.adapter.drops());
    }

    /**
     * Acceptance: a break of such a box drops only what is left in it, and no block item, though its loot had drop
     * block on. Emptying its inventory removes nothing, since empty removal is off.
     */
    @Test
    void aBreakOfABoxWhoseLootWasRemovedDropsOnlyItsContentsAndNoBlockItem() {
        LootBoxLifecycle lifecycle = started(shippedConfig(Map.of("gone", allRulesOnLoot("inventory", 360, 0))), 76);
        LootBox box = summon(lifecycle, "gone", 3, 3);
        lifecycle.reload(shippedConfig());

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.BREAK));

        assertEquals(stacksOf(box), fixture.adapter.drops());
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 3));
    }

    /**
     * Acceptance: such a box has no hologram. The loot drew one while it lived; the next tick after the reload
     * removes it, and a box shown while its loot is gone gets none.
     */
    @Test
    void aBoxWhoseLootWasRemovedHasNoHologram() {
        ConfigSnapshot withLoot = shippedConfig(Map.of("beacon", LifecycleFixture.resource("/loots/airdrops.yml")));
        RecordingHologramProvider provider = new RecordingHologramProvider();
        HologramService holograms = new HologramService(withLoot, provider, id -> Optional.empty());
        LootBoxLifecycle lifecycle = started(withLoot, 77);
        lifecycle.addObserver(holograms);
        LootBox box = summon(lifecycle, "beacon", 3, 3);
        assertEquals(Set.of(box.boxId()), provider.drawn().keySet(), "the loot draws one while it lives");

        ConfigSnapshot without = shippedConfig();
        lifecycle.reload(without);
        holograms.reload(without);
        fixture.adapter.globalScheduler().stepTicks(SECOND);
        assertEquals(Map.of(), provider.drawn());

        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        lifecycle.chunkUnloaded(WORLD, new ChunkPosition(0, 0));
        fixture.adapter.putChunk(flatChunk(0, 0), true, true);
        lifecycle.chunkLoaded(WORLD, new ChunkPosition(0, 0));
        assertEquals(Map.of(), provider.drawn(), "shown again with no loot, it gets none");
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3));
    }

    /**
     * Acceptance: one high warning per reload names how many boxes and which loot ids. A reload that leaves no box
     * without its loot warns nothing; one that still leaves them warns again.
     */
    @Test
    void aReloadWarnsHighOncePerReloadNamingHowManyBoxesAndWhichLootIds() {
        ConfigSnapshot all = shippedConfig(Map.of(
                "zeta", allRulesOnLoot("drop", 360, 0), "alpha", allRulesOnLoot("drop", 360, 0)));
        LootBoxLifecycle lifecycle = started(all, 78);
        summon(lifecycle, "zeta", 3, 3);
        summon(lifecycle, "zeta", 5, 5);
        summon(lifecycle, "alpha", 7, 7);
        summon(lifecycle, "airdrops", 9, 9);

        lifecycle.reload(all);
        assertEquals(List.of(), fixture.warnings.lifecycle, "nothing missing, nothing said");

        lifecycle.reload(shippedConfig());

        assertEquals(1, fixture.warnings.lifecycle.size());
        LifecycleWarning warning = fixture.warnings.lifecycle.get(0);
        assertEquals(LifecycleWarning.Kind.LOOT_MISSING, warning.kind());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        assertEquals(3, warning.boxCount());
        assertEquals(List.of("alpha", "zeta"), warning.lootIds());

        lifecycle.reload(shippedConfig());
        assertEquals(2, fixture.warnings.lifecycle.size(), "every reload that finds them warns once");
    }

    private LootBoxLifecycle started(ConfigSnapshot config, long seed) {
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, seed);
        lifecycle.start().join();
        return lifecycle;
    }

    private static void assertLife(LootBoxLifecycle lifecycle, LootBox summoned, OptionalInt life) {
        BlockPosition position = summoned.position();
        LootBox now = lifecycle.boxAt(WORLD, position).orElseThrow(() -> new AssertionError("no box at " + position));
        assertEquals(life, now.remainingLifeSeconds(), summoned.lootId() + " remaining life");
    }

    private static LootBox summon(LootBoxLifecycle lifecycle, int x, int z) {
        return summon(lifecycle, "crate", x, z);
    }

    private static LootBox summon(LootBoxLifecycle lifecycle, String lootId, int x, int z) {
        SummonOutcome outcome = lifecycle.summon(lootId, airSpot(x, z), Optional.of(OWNER), SummonKind.UNKNOWN).join();
        return outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
    }

    private static List<Drop> stacksOf(LootBox box) {
        assertFalse(box.contents().stacks().isEmpty(), "the box holds something");
        return box.contents().stacks().stream()
                .map(RolledStack::item)
                .map(item -> new Drop(box.position(), item.getType().getKey().toString(), item.getAmount(),
                        Optional.empty()))
                .toList();
    }

    /**
     * A barrel loot with two stacks and every loot end rule on, so a test sees that a box without its loot ends with
     * them off.
     */
    private static String allRulesOnLoot(String openMode, int life, int lock) {
        return """
                language: en
                open_mode: %s
                lock: %d
                life: %d
                block: barrel
                loot_end:
                  allow_explosion: true
                  timeout_spill: true
                  drop_block: true
                  empty_removal: true
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
                """.formatted(openMode, lock, life);
    }

    /**
     * A locked drop open loot with two stacks, the block item on, and explosions not allowed. A negative life is
     * unending.
     */
    private static String loot(String block, int life) {
        return """
                language: en
                open_mode: drop
                lock: 60
                life: %d
                block: %s
                loot_end:
                  allow_explosion: false
                  drop_block: true
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
                """.formatted(life, block);
    }
}
