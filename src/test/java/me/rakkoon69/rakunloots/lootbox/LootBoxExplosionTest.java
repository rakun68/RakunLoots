package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.BlockWrite;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.Drop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.GROUND_Y;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An explosion keeps every locked loot box, ends an unlocked one whose loot allows explosions, and keeps the rest.
 * A box the explosion ends is still kept out of the explosion's block list: the lifecycle removes its block, so the
 * server never drops the block as its own item.
 */
class LootBoxExplosionTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final long SECOND = LootBoxLifecycle.TICK_PERIOD_TICKS;
    private static final long SAVE_TICKS = LootBoxLifecycle.SAVE_PERIOD_TICKS;
    /**
     * Grass beside the boxes, which the explosion may take.
     */
    private static final BlockPosition GRASS = new BlockPosition(8, GROUND_Y, 8);

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
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance: an explosion keeps a locked box, even when its loot allows explosions.
     */
    @Test
    void anExplosionKeepsALockedBoxEvenWhenItsLootAllowsExplosions() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("blast", loot(60, true, true)));
        LootBoxLifecycle lifecycle = started(config, 51);
        LootBox owned = summon(lifecycle, "blast", 2, 2, Optional.of(OWNER));
        LootBox unowned = summon(lifecycle, "blast", 4, 4, Optional.empty());

        Set<BlockPosition> kept = lifecycle.explode(WORLD, List.of(GRASS, owned.position(), unowned.position()));

        assertEquals(Set.of(owned.position(), unowned.position()), kept);
        for (LootBox box : List.of(owned, unowned)) {
            assertEquals(Optional.of(box), lifecycle.boxAt(WORLD, box.position()));
            assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()));
        }
        assertEquals(List.of(), fixture.adapter.drops());
        assertEquals(2, fixture.adapter.blockWrites().size(), "only the summons wrote a block");
        assertEquals(List.of(), fixture.warnings.lifecycle);
        lifecycle.stop();
        assertTrue(fixture.storedBoxes(config).containsKey(owned.boxId()), "the row stays");
    }

    /**
     * Acceptance: an explosion ends an unlocked box whose loot allows it, dropping what is left. The block item drops
     * only under {@code drop_block}. The lifecycle removes the block itself, so the box is kept out of the
     * explosion's block list and the server drops nothing of its own. A block without a box is left to the
     * explosion.
     */
    @Test
    void anExplosionEndsAnUnlockedBoxWhoseLootAllowsItDroppingWhatIsLeft() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of(
                "blast", loot(0, true, false),
                "keepsake", loot(0, true, true)));
        LootBoxLifecycle lifecycle = started(config, 52);
        LootBox plain = summon(lifecycle, "blast", 2, 2, Optional.of(OWNER));
        LootBox keepsake = summon(lifecycle, "keepsake", 4, 4, Optional.empty());
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertEquals(Set.of(plain.boxId(), keepsake.boxId()), fixture.storedBoxes(config).keySet());

        Set<BlockPosition> kept = lifecycle.explode(WORLD, List.of(plain.position(), GRASS, keepsake.position()));

        assertEquals(Set.of(plain.position(), keepsake.position()), kept, "the lifecycle removes the boxes");
        List<Drop> expected = new ArrayList<>(stacksOf(plain));
        expected.addAll(stacksOf(keepsake));
        expected.add(new Drop(keepsake.position(), "minecraft:barrel", 1, Optional.empty()));
        assertEquals(expected, fixture.adapter.drops());
        for (LootBox box : List.of(plain, keepsake)) {
            assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
            assertFalse(lifecycle.isLootBox(WORLD, box.position().x(), box.position().y(), box.position().z()));
        }
        assertEquals(List.of(
                new BlockWrite(plain.position(), "minecraft:barrel", Optional.empty()),
                new BlockWrite(keepsake.position(), "minecraft:barrel", Optional.empty()),
                new BlockWrite(plain.position(), "minecraft:air", Optional.empty()),
                new BlockWrite(keepsake.position(), "minecraft:air", Optional.empty())),
                fixture.adapter.blockWrites());
        assertEquals(List.of(), fixture.warnings.lifecycle);
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertEquals(Map.of(), fixture.storedBoxes(config), "the next save deleted both rows");
    }

    /**
     * Acceptance: an explosion keeps a box whose loot does not allow it. The shipped airdrop leaves
     * {@code allow_explosion} off; once its 10 second lock runs out it is unlocked and still kept.
     */
    @Test
    void anExplosionKeepsAnUnlockedBoxWhoseLootDoesNotAllowIt() {
        LootBoxLifecycle lifecycle = started(shippedConfig(), 53);
        LootBox box = summon(lifecycle, "airdrops", 3, 3, Optional.of(OWNER));
        fixture.adapter.globalScheduler().stepTicks(SECOND * 10);
        assertEquals(0, lifecycle.boxAt(WORLD, box.position()).orElseThrow().remainingLockSeconds());

        Set<BlockPosition> kept = lifecycle.explode(WORLD, List.of(box.position(), GRASS));

        assertEquals(Set.of(box.position()), kept);
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3));
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(), fixture.adapter.drops());
    }

    /**
     * An explosion in another world keeps nothing and ends nothing.
     */
    @Test
    void anExplosionInAnotherWorldLeavesTheBoxes() {
        ConfigSnapshot config = shippedConfig(Map.of("blast", loot(0, true, false)));
        LootBoxLifecycle lifecycle = started(config, 54);
        LootBox box = summon(lifecycle, "blast", 2, 2, Optional.empty());

        assertEquals(Set.of(), lifecycle.explode(UUID.randomUUID(), List.of(box.position())));

        assertTrue(lifecycle.isLootBox(WORLD, 2, 64, 2));
        assertEquals(List.of(), fixture.adapter.drops());
    }

    private LootBoxLifecycle started(ConfigSnapshot config, long seed) {
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, seed);
        lifecycle.start().join();
        return lifecycle;
    }

    private static LootBox summon(LootBoxLifecycle lifecycle, String lootId, int x, int z, Optional<UUID> owner) {
        SummonOutcome outcome = lifecycle.summon(lootId, airSpot(x, z), owner, SummonKind.UNKNOWN).join();
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
     * A drop open barrel loot with two stacks and a life of 360 seconds.
     */
    private static String loot(int lock, boolean allowExplosion, boolean dropBlock) {
        return """
                language: en
                open_mode: drop
                lock: %d
                life: 360
                block: barrel
                loot_end:
                  allow_explosion: %s
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
                """.formatted(lock, allowExplosion, dropBlock);
    }
}
