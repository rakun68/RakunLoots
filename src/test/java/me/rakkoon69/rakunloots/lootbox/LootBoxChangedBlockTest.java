package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.Drop;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A loot box whose block is no longer the block it was placed as ends as a break, without the block item, and warns
 * high. The lifecycle looks when the chunk loads, and on click, break, end of life, and explosion. Only the block id
 * counts: facing or waterlogging is not a changed block. A block is removed only while it is still the placed block.
 */
class LootBoxChangedBlockTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final long SECOND = LootBoxLifecycle.TICK_PERIOD_TICKS;
    private static final long SAVE_TICKS = LootBoxLifecycle.SAVE_PERIOD_TICKS;
    private static final int LIFE = 5;
    private static final String STONE = "minecraft:stone";
    /**
     * The shipped {@code still_locked} in en.yml, with the shipped prefix, in legacy color codes.
     */
    private static final String STILL_LOCKED =
            "§f[§bRakunLoots§f]§7 This loot is still §clocked §7and you are §cnot the owner §7!";

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
     * Acceptance: a replaced block is found at chunk load, and ends the box with what is left dropped, no block item,
     * and the high warning.
     */
    @Test
    void aReplacedBlockIsFoundAtChunkLoad() throws Exception {
        ConfigSnapshot config = config();
        LootBoxLifecycle lifecycle = started(config, 61);
        LootBox box = summon(lifecycle, 3, 3);
        LootBox untouched = summon(lifecycle, 9, 9);
        fixture.adapter.putBlock(box.position(), STONE);

        lifecycle.chunkLoaded(WORLD, new ChunkPosition(0, 0));

        assertEndedAsChanged(config, lifecycle, box);
        assertTrue(lifecycle.isLootBox(WORLD, 9, 64, 9), "a box whose block is still there stays");
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(untouched.position()));
    }

    /**
     * Acceptance: a replaced block is found at a click. The box ends even while locked, since it is no longer a loot
     * box; the click is not cancelled, so the block found keeps its own use.
     */
    @Test
    void aReplacedBlockIsFoundAtAClick() throws Exception {
        ConfigSnapshot config = config();
        LootBoxLifecycle lifecycle = started(config, 62);
        LootBox box = summon(lifecycle, 3, 3);
        fixture.adapter.putBlock(box.position(), STONE);

        assertEquals(InteractionResult.ENDED_CHANGED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK));

        assertEquals(List.of(), fixture.adapter.messages(), "no still_locked");
        assertEquals(List.of(), fixture.adapter.inventoryOpens(), "no shared inventory");
        assertEndedAsChanged(config, lifecycle, box);
    }

    /**
     * Acceptance: a replaced block is found at a break, and ends the box without the block item, even with
     * {@code drop_block} on.
     */
    @Test
    void aReplacedBlockIsFoundAtABreak() throws Exception {
        ConfigSnapshot config = config();
        LootBoxLifecycle lifecycle = started(config, 63);
        LootBox box = summon(lifecycle, 3, 3);
        fixture.adapter.putBlock(box.position(), STONE);

        assertEquals(InteractionResult.ENDED_CHANGED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.BREAK));

        assertEquals(List.of(), fixture.adapter.messages(), "no still_locked");
        assertEndedAsChanged(config, lifecycle, box);
    }

    /**
     * Acceptance: a replaced block is found at the end of life. It ends as a break, so what is left drops even with
     * {@code timeout_spill} off. Acceptance: a box whose block was replaced before its timeout leaves the replacing
     * block in place.
     */
    @Test
    void aBoxWhoseBlockWasReplacedBeforeItsTimeoutLeavesTheReplacingBlockInPlace() throws Exception {
        ConfigSnapshot config = config();
        LootBoxLifecycle lifecycle = started(config, 64);
        LootBox box = summon(lifecycle, 3, 3);
        fixture.adapter.putBlock(box.position(), STONE);

        fixture.adapter.globalScheduler().stepTicks(SECOND * (LIFE - 1));
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3), "1 second left");
        fixture.adapter.globalScheduler().stepTicks(SECOND);

        assertEndedAsChanged(config, lifecycle, box);
    }

    /**
     * Acceptance: a replaced block is found at an explosion. It ends the box, even a locked one whose loot does not
     * allow explosions, and the block found is not kept out of the explosion.
     */
    @Test
    void aReplacedBlockIsFoundAtAnExplosion() throws Exception {
        ConfigSnapshot config = config();
        LootBoxLifecycle lifecycle = started(config, 65);
        LootBox box = summon(lifecycle, 3, 3);
        LootBox untouched = summon(lifecycle, 5, 5);
        fixture.adapter.putBlock(box.position(), STONE);

        Set<BlockPosition> kept = lifecycle.explode(WORLD, List.of(box.position(), untouched.position()));

        assertEquals(Set.of(untouched.position()), kept);
        assertEndedAsChanged(config, lifecycle, box);
    }

    /**
     * Acceptance: a change of facing or waterlogging does not end the box, at chunk load, click, break, explosion,
     * or the tick. The block is still the placed block, so the owner's break removes it and drops the block item.
     */
    @Test
    void aChangeOfFacingOrWaterloggingDoesNotEndTheBox() {
        ConfigSnapshot config = shippedConfig(Map.of(
                "crate", loot("barrel"),
                "coffer", loot("chest")));
        LootBoxLifecycle lifecycle = started(config, 66);
        LootBox barrel = summon(lifecycle, "crate", 3, 3);
        LootBox chest = summon(lifecycle, "coffer", 5, 5);
        fixture.adapter.putBlock(barrel.position(), "minecraft:barrel[facing=down,open=false]");
        fixture.adapter.putBlock(chest.position(), "minecraft:chest[facing=east,type=single,waterlogged=true]");

        lifecycle.chunkLoaded(WORLD, new ChunkPosition(0, 0));
        for (LootBox box : List.of(barrel, chest)) {
            assertEquals(InteractionResult.REFUSED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK));
            assertEquals(InteractionResult.REFUSED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.BREAK));
        }
        assertEquals(Set.of(barrel.position(), chest.position()),
                lifecycle.explode(WORLD, List.of(barrel.position(), chest.position())));
        fixture.adapter.globalScheduler().stepTicks(SECOND);

        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 3));
        assertTrue(lifecycle.isLootBox(WORLD, 5, 64, 5));
        assertEquals(List.of(), fixture.warnings.lifecycle);
        assertEquals(List.of(), fixture.adapter.drops());
        assertEquals(4, fixture.adapter.messages().size(), "the lock still holds");
        assertEquals(new Message(OTHER, STILL_LOCKED), fixture.adapter.messages().get(0));

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OWNER, WORLD, chest.position(), false, Interaction.BREAK));

        assertEquals("minecraft:air", fixture.adapter.blockAt(chest.position()));
        List<Drop> drops = fixture.adapter.drops();
        assertEquals(new Drop(chest.position(), "minecraft:chest", 1, Optional.empty()), drops.get(drops.size() - 1),
                "the block item drops, since the block is still the placed chest");
        assertEquals(List.of(), fixture.warnings.lifecycle);
    }

    /**
     * The box ended as a changed block: gone from the registry and from storage after the next save, what was in it
     * dropped without the block item, the block found left in place, and one high warning with the box id, loot id,
     * world, position, and the block found.
     */
    private void assertEndedAsChanged(ConfigSnapshot config, LootBoxLifecycle lifecycle, LootBox box)
            throws Exception {
        BlockPosition position = box.position();
        assertFalse(lifecycle.isLootBox(WORLD, position.x(), position.y(), position.z()));
        assertEquals(STONE, fixture.adapter.blockAt(position), "the block found stays");
        assertEquals(stacksOf(box), fixture.adapter.drops(), "what is left, and no barrel");
        assertEquals(List.of(LifecycleWarning.Kind.CHANGED_BLOCK), fixture.warnings.lifecycleKinds());
        LifecycleWarning warning = fixture.warnings.lifecycle.get(0);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        assertEquals(Optional.of(box.boxId()), warning.boxId());
        assertEquals(Optional.of("crate"), warning.lootId());
        assertEquals(Optional.of(WORLD), warning.worldKey());
        assertEquals(Optional.of(position), warning.position());
        assertEquals(Optional.of(STONE), warning.blockFound());
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertFalse(fixture.storedBoxes(config).containsKey(box.boxId()), "the next save deleted the row");
    }

    private LootBoxLifecycle started(ConfigSnapshot config, long seed) {
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, seed);
        lifecycle.start().join();
        return lifecycle;
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

    private static ConfigSnapshot config() {
        return shippedConfig(Map.of("crate", loot("barrel")));
    }

    /**
     * A locked inventory open loot with two stacks, a life of {@link #LIFE} seconds, explosions not allowed, the
     * block item on, and timeout spill off, so only a changed block drops what is left at the end of its life.
     */
    private static String loot(String block) {
        return """
                language: en
                open_mode: inventory
                lock: 60
                life: %d
                block: %s
                loot_end:
                  allow_explosion: false
                  drop_block: true
                  timeout_spill: false
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
                """.formatted(LIFE, block);
    }
}
