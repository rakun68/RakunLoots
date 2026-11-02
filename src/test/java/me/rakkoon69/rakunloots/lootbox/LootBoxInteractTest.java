package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.BlockWrite;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.Drop;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.Message;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taking from a drop open loot box, breaking any loot box, and the lock that guards both. The lifecycle takes no
 * sneak state, so sneaking cannot change an outcome: only the owner and the bypass permission pass the lock.
 */
class LootBoxInteractTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final UUID STAFF = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    /**
     * The shipped {@code still_locked} in en.yml, with the shipped prefix, in legacy color codes.
     */
    private static final String STILL_LOCKED =
            "§f[§bRakunLoots§f]§7 This loot is still §clocked §7and you are §cnot the owner §7!";
    private static final String HEAD_DATA = Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/5a0b07e3\"}}}"
                    .getBytes(StandardCharsets.UTF_8));
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
        for (UUID player : List.of(OWNER, OTHER, STAFF)) {
            fixture.adapter.addOnlinePlayer(player);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance: a click on an unlocked drop open box drops every stack and removes the box and its row.
     */
    @Test
    void aClickOnAnUnlockedDropOpenBoxDropsEveryStackAndRemovesTheBoxAndItsRow() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("open_airdrop", unlockedDropLoot(false)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 11);
        lifecycle.start().join();
        LootBox box = lifecycle.summon("open_airdrop", airSpot(3, 4), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertTrue(fixture.storedBoxes(config).containsKey(box.boxId()), "the save wrote the row");

        InteractionResult result = lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);

        assertEquals(InteractionResult.ALLOWED, result, "the barrel's own use never runs on a loot box");
        assertEquals(stacksOf(box), fixture.adapter.drops());
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(new BlockWrite(box.position(), "minecraft:barrel", Optional.empty()),
                new BlockWrite(box.position(), "minecraft:air", Optional.empty())), fixture.adapter.blockWrites());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, box.position()));
        assertEquals(List.of(), fixture.adapter.messages());
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertFalse(fixture.storedBoxes(config).containsKey(box.boxId()), "the next save deleted the row");

        assertEquals(InteractionResult.NOT_A_BOX, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK),
                "a click where the box was is left alone");
        assertEquals(stacksOf(box), fixture.adapter.drops(), "nothing dropped twice");
    }

    /**
     * Acceptance: while locked, the owner and a bypass player can click and break. The shipped loot is drop open
     * with a lock of 10 seconds.
     */
    @Test
    void whileLockedTheOwnerAndABypassPlayerCanClickAndBreak() {
        LootBoxLifecycle lifecycle = fixture.lifecycle(shippedConfig(), store, 12);
        LootBox ownerClick = summon(lifecycle, "airdrops", 1, 1, Optional.of(OWNER));
        LootBox staffClick = summon(lifecycle, "airdrops", 3, 3, Optional.of(OWNER));
        LootBox ownerBreak = summon(lifecycle, "airdrops", 5, 5, Optional.of(OWNER));
        LootBox staffBreak = summon(lifecycle, "airdrops", 7, 7, Optional.of(OWNER));
        assertEquals(10, ownerClick.remainingLockSeconds(), "the shipped loot starts locked");

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OWNER, WORLD, ownerClick.position(), false, Interaction.CLICK));
        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(STAFF, WORLD, staffClick.position(), true, Interaction.CLICK));
        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OWNER, WORLD, ownerBreak.position(), false, Interaction.BREAK),
                "an allowed break is not cancelled");
        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(STAFF, WORLD, staffBreak.position(), true, Interaction.BREAK),
                "an allowed break is not cancelled");

        List<Drop> expected = new ArrayList<>();
        for (LootBox box : List.of(ownerClick, staffClick, ownerBreak, staffBreak)) {
            expected.addAll(stacksOf(box));
            assertFalse(lifecycle.isLootBox(WORLD, box.position().x(), box.position().y(), box.position().z()));
            assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        }
        assertEquals(expected, fixture.adapter.drops());
        assertEquals(List.of(), fixture.adapter.messages());
    }

    /**
     * Acceptance: while locked, another player is cancelled and gets {@code still_locked}, on a click and on a
     * break. Sneaking is not an input to the lifecycle, so it cannot let them through.
     */
    @Test
    void whileLockedAnotherPlayerIsCancelledAndGetsStillLocked() throws Exception {
        ConfigSnapshot config = shippedConfig();
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 13);
        LootBox box = summon(lifecycle, "airdrops", 2, 2, Optional.of(OWNER));
        lifecycle.stop();

        assertEquals(InteractionResult.REFUSED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK));
        assertEquals(InteractionResult.REFUSED, lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.BREAK));

        assertEquals(List.of(new Message(OTHER, STILL_LOCKED), new Message(OTHER, STILL_LOCKED)),
                fixture.adapter.messages());
        assertEquals(List.of(), fixture.adapter.drops());
        assertEquals(1, fixture.adapter.blockWrites().size(), "only the summon wrote a block");
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(box.position()));
        assertEquals(Optional.of(box), lifecycle.boxAt(WORLD, box.position()));
        lifecycle.stop();
        assertTrue(fixture.storedBoxes(config).containsKey(box.boxId()), "the row stays");
    }

    /**
     * Acceptance: a locked box with no owner admits only bypass.
     */
    @Test
    void aLockedBoxWithNoOwnerAdmitsOnlyBypass() {
        LootBoxLifecycle lifecycle = fixture.lifecycle(shippedConfig(), store, 14);
        LootBox clicked = summon(lifecycle, "airdrops", 1, 6, Optional.empty());
        LootBox broken = summon(lifecycle, "airdrops", 6, 1, Optional.empty());

        for (UUID player : List.of(OWNER, OTHER)) {
            assertEquals(InteractionResult.REFUSED, lifecycle.interact(player, WORLD, clicked.position(), false, Interaction.CLICK));
            assertEquals(InteractionResult.REFUSED, lifecycle.interact(player, WORLD, broken.position(), false, Interaction.BREAK));
        }
        assertEquals(List.of(new Message(OWNER, STILL_LOCKED), new Message(OWNER, STILL_LOCKED),
                new Message(OTHER, STILL_LOCKED), new Message(OTHER, STILL_LOCKED)), fixture.adapter.messages());
        assertEquals(List.of(), fixture.adapter.drops());
        assertTrue(lifecycle.isLootBox(WORLD, 1, 64, 6));
        assertTrue(lifecycle.isLootBox(WORLD, 6, 64, 1));

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(STAFF, WORLD, clicked.position(), true, Interaction.CLICK));
        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(STAFF, WORLD, broken.position(), true, Interaction.BREAK));

        List<Drop> expected = new ArrayList<>(stacksOf(clicked));
        expected.addAll(stacksOf(broken));
        assertEquals(expected, fixture.adapter.drops());
        assertFalse(lifecycle.isLootBox(WORLD, 1, 64, 6));
        assertFalse(lifecycle.isLootBox(WORLD, 6, 64, 1));
    }

    /**
     * Acceptance: a break drops what is left. With {@code drop_block} on it also drops the block item. With it off
     * it does not. The shipped loot leaves {@code drop_block} off.
     */
    @Test
    void aBreakDropsWhatIsLeftAndTheBlockItemOnlyWithDropBlockOn() throws Exception {
        ConfigSnapshot config = shippedConfig(Map.of("keepsake", unlockedDropLoot(true)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 15);
        lifecycle.start().join();
        LootBox off = summon(lifecycle, "airdrops", 2, 7, Optional.of(OWNER));
        LootBox on = summon(lifecycle, "keepsake", 7, 2, Optional.empty());
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OWNER, WORLD, off.position(), false, Interaction.BREAK));
        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OTHER, WORLD, on.position(), false, Interaction.BREAK));

        List<Drop> expected = new ArrayList<>(stacksOf(off));
        expected.addAll(stacksOf(on));
        expected.add(new Drop(on.position(), "minecraft:barrel", 1, Optional.empty()));
        assertEquals(expected, fixture.adapter.drops());
        for (LootBox box : List.of(off, on)) {
            assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
            assertFalse(lifecycle.isLootBox(WORLD, box.position().x(), box.position().y(), box.position().z()));
        }
        fixture.adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        assertEquals(Map.of(), fixture.storedBoxes(config), "both rows are gone");
    }

    /**
     * Acceptance: a player head block item keeps its head data.
     */
    @Test
    void aPlayerHeadBlockItemKeepsItsHeadData() {
        ConfigSnapshot config = shippedConfig(Map.of("trophy", """
                language: en
                open_mode: drop
                lock: 0
                block: player_head
                head_data: '%s'
                loot_end:
                  drop_block: true
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """.formatted(HEAD_DATA)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 16);
        LootBox trophy = summon(lifecycle, "trophy", 4, 4, Optional.empty());

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OTHER, WORLD, trophy.position(), false, Interaction.BREAK));

        List<Drop> drops = fixture.adapter.drops();
        assertEquals(new Drop(trophy.position(), "minecraft:player_head", 1, Optional.of(HEAD_DATA)),
                drops.get(drops.size() - 1));
        assertEquals("minecraft:air", fixture.adapter.blockAt(trophy.position()));
    }

    /**
     * A player ends the box by a drop open click as by a break: with {@code drop_block} on the click spills the
     * contents and drops the block item, with it off the contents alone.
     */
    @Test
    void aDropOpenClickDropsTheBlockItemOnlyWithDropBlockOn() {
        ConfigSnapshot config = shippedConfig(Map.of("keepsake", unlockedDropLoot(true), "plain", unlockedDropLoot(false)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 17);
        LootBox on = summon(lifecycle, "keepsake", 5, 2, Optional.empty());
        LootBox off = summon(lifecycle, "plain", 2, 5, Optional.empty());

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OTHER, WORLD, on.position(), false, Interaction.CLICK));
        List<Drop> expectedOn = new ArrayList<>(stacksOf(on));
        expectedOn.add(new Drop(on.position(), "minecraft:barrel", 1, Optional.empty()));
        assertEquals(expectedOn, fixture.adapter.drops());

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OTHER, WORLD, off.position(), false, Interaction.CLICK));
        List<Drop> expected = new ArrayList<>(expectedOn);
        expected.addAll(stacksOf(off));
        assertEquals(expected, fixture.adapter.drops());
    }

    /**
     * A player head block item dropped by a drop open click keeps its head data.
     */
    @Test
    void aDropOpenClickOnAPlayerHeadDropsTheHeadItemWithItsHeadData() {
        ConfigSnapshot config = shippedConfig(Map.of("trophy", """
                language: en
                open_mode: drop
                lock: 0
                block: player_head
                head_data: '%s'
                loot_end:
                  drop_block: true
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """.formatted(HEAD_DATA)));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 19);
        LootBox trophy = summon(lifecycle, "trophy", 4, 4, Optional.empty());

        assertEquals(InteractionResult.ALLOWED, lifecycle.interact(OTHER, WORLD, trophy.position(), false, Interaction.CLICK));

        List<Drop> drops = fixture.adapter.drops();
        assertEquals(new Drop(trophy.position(), "minecraft:player_head", 1, Optional.of(HEAD_DATA)),
                drops.get(drops.size() - 1));
        assertEquals("minecraft:air", fixture.adapter.blockAt(trophy.position()));
    }

    /**
     * Safe removal: a block that is no longer the box's placed block is left in place when the box ends.
     */
    @Test
    void aBreakLeavesABlockThatIsNoLongerThePlacedBlock() {
        LootBoxLifecycle lifecycle = fixture.lifecycle(shippedConfig(), store, 18);
        LootBox box = summon(lifecycle, "airdrops", 6, 6, Optional.of(OWNER));
        fixture.adapter.putBlock(box.position(), "minecraft:stone");

        assertEquals(InteractionResult.ENDED_CHANGED, lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.BREAK));

        assertEquals("minecraft:stone", fixture.adapter.blockAt(box.position()));
        assertEquals(1, fixture.adapter.blockWrites().size(), "only the summon wrote a block");
    }

    private static LootBox summon(LootBoxLifecycle lifecycle, String lootId, int x, int z, Optional<UUID> owner) {
        SummonOutcome outcome = lifecycle.summon(lootId, airSpot(x, z), owner, SummonKind.UNKNOWN).join();
        return outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
    }

    /**
     * Every stack in the box, as the drops the adapter records, in the box's order.
     */
    private static List<Drop> stacksOf(LootBox box) {
        assertFalse(box.contents().stacks().isEmpty(), "the box holds something");
        List<Drop> drops = new ArrayList<>();
        for (RolledStack stack : box.contents().stacks()) {
            ItemStack item = stack.item();
            drops.add(new Drop(box.position(), item.getType().getKey().toString(), item.getAmount(),
                    Optional.empty()));
        }
        return drops;
    }

    private static String unlockedDropLoot(boolean dropBlock) {
        return """
                language: en
                open_mode: drop
                lock: 0
                life: 360
                block: barrel
                loot_end:
                  drop_block: %s
                roll:
                  min: 2
                  max: 2
                  entries:
                    diamonds:
                      amount: 2
                      item:
                        id: diamond
                    emeralds:
                      amount: 3
                      item:
                        id: emerald
                """.formatted(dropBlock);
    }
}
