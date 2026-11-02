package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxRecord;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.BlockWrite;
import me.rakkoon69.rakunloots.summon.search.SearchMiss;
import me.rakkoon69.rakunloots.summon.search.SummonResult;
import me.rakkoon69.rakunloots.summon.search.SummonSearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.position;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootBoxSummonTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String HEAD_DATA = Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/5a0b07e3\"}}}"
                    .getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance: summoning a shipped loot onto a fake world writes its presentation block and registers the box.
     */
    @Test
    void summoningTheShippedLootWritesItsBarrelAndRegistersTheBox() {
        ConfigSnapshot config = shippedConfig();
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 1);
        Spot spot = airSpot(3, 4);

        SummonOutcome outcome = lifecycle.summon("airdrops", spot, Optional.of(OWNER), SummonKind.UNKNOWN).join();

        LootBox box = outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
        BlockPosition at = new BlockPosition(3, 64, 4);
        assertEquals(List.of(new BlockWrite(at, "minecraft:barrel", Optional.empty())),
                fixture.adapter.blockWrites());
        assertEquals("minecraft:barrel", box.placedBlockId());
        assertTrue(lifecycle.isLootBox(WORLD, 3, 64, 4));
        assertEquals(Optional.of(box), lifecycle.boxAt(WORLD, at));
        assertEquals("airdrops", box.lootId());
        assertEquals(WORLD, box.worldKey());
        assertEquals(at, box.position());
        assertFalse(box.contents().stacks().isEmpty());
        assertTrue(box.contents().stacks().stream().allMatch(stack -> stack.slot().isPresent()),
                "every stack has a concrete slot");
    }

    /**
     * Acceptance: remaining life starts at the loot's life, remaining lock at its lock length, and the owner is kept
     * or empty. Checked on the summoned box and on the rows the stop flush writes.
     */
    @Test
    void remainingLifeAndLockStartFromTheLootAndTheOwnerIsKeptOrEmpty() throws Exception {
        ConfigSnapshot config = shippedConfig();
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 2);

        LootBox owned = lifecycle.summon("airdrops", airSpot(1, 1), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();
        LootBox unowned = lifecycle.summon("airdrops", airSpot(5, 5), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
        lifecycle.stop();

        assertEquals(OptionalInt.of(360), owned.remainingLifeSeconds());
        assertEquals(10, owned.remainingLockSeconds());
        assertEquals(Optional.of(OWNER), owned.owner());
        assertEquals(Optional.empty(), unowned.owner());
        Map<UUID, LootBoxRecord> stored = fixture.storedBoxes(config);
        assertEquals(OptionalInt.of(360), stored.get(owned.boxId()).remainingLifeSeconds());
        assertEquals(10, stored.get(owned.boxId()).remainingLockSeconds());
        assertEquals(Optional.of(OWNER), stored.get(owned.boxId()).owner());
        assertEquals(Optional.empty(), stored.get(unowned.boxId()).owner());
    }

    /**
     * Acceptance: summon refuses an occupied spot, a spot whose block changed since the search, and an unknown loot,
     * each with its reason. A loot refused at load for a banned block is unknown to summon.
     */
    @Test
    void summonRefusesAnOccupiedSpotAChangedSpotAndAnUnknownLootEachWithItsReason() {
        ConfigSnapshot config = shippedConfig(Map.of("pond", """
                language: en
                open_mode: drop
                block: water
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """));
        assertTrue(config.loot("pond").isEmpty(), "the shipped list bans water");
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 3);
        Spot taken = airSpot(2, 2);
        assertTrue(lifecycle.summon("airdrops", taken, Optional.empty(), SummonKind.UNKNOWN).join().isSummoned());

        SummonOutcome occupied = lifecycle.summon("airdrops", taken, Optional.empty(), SummonKind.UNKNOWN).join();

        Spot changed = airSpot(6, 6);
        fixture.adapter.putBlock(position(changed), "minecraft:stone");
        SummonOutcome changedOutcome = lifecycle.summon("airdrops", changed, Optional.of(OWNER), SummonKind.UNKNOWN).join();

        SummonOutcome unknown = lifecycle.summon("no_such_loot", airSpot(8, 8), Optional.empty(), SummonKind.UNKNOWN).join();
        SummonOutcome banned = lifecycle.summon("pond", airSpot(9, 9), Optional.empty(), SummonKind.UNKNOWN).join();

        assertEquals(Optional.of(SummonRefusal.SPOT_OCCUPIED), occupied.refusal());
        assertEquals(Optional.of(SummonRefusal.SPOT_CHANGED), changedOutcome.refusal());
        assertEquals(Optional.of(SummonRefusal.UNKNOWN_LOOT), unknown.refusal());
        assertEquals(Optional.of(SummonRefusal.UNKNOWN_LOOT), banned.refusal());
        assertEquals(1, fixture.adapter.blockWrites().size(), "only the first summon wrote a block");
        assertEquals("minecraft:stone", fixture.adapter.blockAt(position(changed)), "the changed block stays");
        assertFalse(lifecycle.isLootBox(WORLD, changed.x(), changed.y(), changed.z()));
        assertFalse(lifecycle.isLootBox(WORLD, 8, 64, 8));
    }

    /**
     * A spot whose chunk unloaded since the search is refused as changed, and nothing is written. The world skips
     * unloaded chunks: under {@code load} the reservation's chunk ticket would load the generated chunk (ADR 0015).
     */
    @Test
    void aSpotInAChunkThatUnloadedSinceTheSearchIsRefusedAsChanged() {
        LootBoxLifecycle lifecycle = fixture.lifecycle(shippedConfig(Map.of(), "skip"), store, 4);
        fixture.adapter.putChunk(LifecycleFixture.flatChunk(0, 0), false, true);

        SummonOutcome outcome = lifecycle.summon("airdrops", airSpot(3, 3), Optional.empty(), SummonKind.UNKNOWN).join();

        assertEquals(Optional.of(SummonRefusal.SPOT_CHANGED), outcome.refusal());
        assertEquals(List.of(), fixture.adapter.blockWrites());
        assertFalse(lifecycle.isLootBox(WORLD, 3, 64, 3));
    }

    /**
     * Acceptance: the occupied check reports a registered box's position, and nothing else. The summon search finds
     * a spot, summon takes it, and the same search through the occupied check then refuses it.
     */
    @Test
    void theOccupiedCheckReportsARegisteredBoxsPosition() {
        // A ring of 0 to 0 makes the search look at the one column around its center.
        ConfigSnapshot config = shippedConfig(Map.of("marker", """
                language: en
                open_mode: drop
                block: beacon
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                summon_settings:
                  summon_type: top_surface
                  y_range: '0:255'
                  replaceable_blocks:
                    - minecraft:air
                  player_ring:
                    min: 0
                    max: 0
                """));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 5);
        SummonSearch search = new SummonSearch(fixture.adapter, config, () -> 0L, new Random(1));
        SummonResult found = search.aroundCenter(WORLD, "marker", 3, 64, 4, lifecycle.occupiedCheck()).join();
        Spot spot = found.spot().orElseThrow(() -> new AssertionError("miss " + found.missReason()));
        assertEquals(new Spot(WORLD, 3, 64, 4, "minecraft:air"), spot);

        lifecycle.summon("marker", spot, Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();

        assertTrue(lifecycle.occupiedCheck().isOccupied(WORLD, 3, 64, 4));
        assertFalse(lifecycle.occupiedCheck().isOccupied(WORLD, 3, 65, 4));
        assertFalse(lifecycle.occupiedCheck().isOccupied(WORLD, 4, 64, 4));
        assertFalse(lifecycle.occupiedCheck().isOccupied(UUID.randomUUID(), 3, 64, 4));
        SummonResult again = search.aroundCenter(WORLD, "marker", 3, 64, 4, lifecycle.occupiedCheck()).join();
        assertEquals(Optional.of(SearchMiss.ATTEMPTS_USED_UP), again.missReason());
    }

    /**
     * A player head loot is placed with its head data. Head data the server refuses is caught at summon: nothing is
     * written, the spot stays free, and the summon warns high.
     */
    @Test
    void aPlayerHeadKeepsItsHeadDataAndHeadDataTheServerRefusesIsRefusedWithAHighWarning() {
        ConfigSnapshot config = shippedConfig(Map.of(
                "trophy", headLoot(HEAD_DATA),
                "broken", headLoot("not base64 !")));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 6);

        LootBox trophy = lifecycle.summon("trophy", airSpot(1, 2), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
        SummonOutcome broken = lifecycle.summon("broken", airSpot(2, 1), Optional.empty(), SummonKind.UNKNOWN).join();

        assertEquals("minecraft:player_head", trophy.placedBlockId());
        assertEquals(List.of(new BlockWrite(new BlockPosition(1, 64, 2), "minecraft:player_head",
                Optional.of(HEAD_DATA))), fixture.adapter.blockWrites());
        assertEquals(Optional.of(SummonRefusal.BLOCK_REFUSED), broken.refusal());
        assertFalse(lifecycle.isLootBox(WORLD, 2, 64, 1));
        LifecycleWarning warning = fixture.warnings.lifecycle.stream()
                .filter(each -> each.kind() == LifecycleWarning.Kind.BLOCK_REFUSED).findFirst().orElseThrow();
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        assertEquals(Optional.of("broken"), warning.lootId());
        assertTrue(lifecycle.summon("trophy", airSpot(2, 1), Optional.empty(), SummonKind.UNKNOWN).join().isSummoned(),
                "the refused spot was released");
    }

    /**
     * Acceptance (slot placement, through summon): the shipped loot's fixed slots hold, and a loot of more stacks
     * than 54 slots leaves the rest out with a high warning each.
     */
    @Test
    void summonPlacesFixedSlotsAndLeavesOutWhatDoesNotFit() {
        ConfigSnapshot config = shippedConfig(Map.of("hoard", """
                language: en
                open_mode: inventory
                block: chest
                roll:
                  entries:
                    pile:
                      weight: -1
                      amount: 3840
                      item:
                        id: diamond
                """));
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 7);

        LootBox airdrop = lifecycle.summon("airdrops", airSpot(1, 1), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
        LootBox hoard = lifecycle.summon("hoard", airSpot(4, 4), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();

        // Every shipped entry has a fixed slot: the stack of each entry sits in its slot from loots/airdrops.yml.
        Map<Integer, String> shippedSlots = Map.of(5, "minecraft:diamond x1", 9, "minecraft:cobweb x5",
                15, "minecraft:golden_apple x1", 20, "minecraft:enchanted_golden_apple x1", 7, "minecraft:diamond x1",
                8, "minecraft:emerald x3", 10, "minecraft:amethyst_shard x5");
        for (RolledStack stack : airdrop.contents().stacks()) {
            assertEquals(shippedSlots.get(stack.slot().getAsInt()),
                    stack.item().getType().getKey() + " x" + stack.item().getAmount());
        }
        List<Integer> slots = airdrop.contents().stacks().stream().map(stack -> stack.slot().getAsInt()).toList();
        assertTrue(slots.containsAll(List.of(5, 9)), "the always-included entries: " + slots);
        assertEquals(54, hoard.contents().stacks().size(), "60 stacks rolled, 54 fit");
        assertEquals(54, hoard.contents().stacks().stream().mapToInt(stack -> stack.slot().getAsInt()).distinct()
                .count());
        assertEquals(6, fixture.warnings.lifecycle.stream()
                .filter(warning -> warning.kind() == LifecycleWarning.Kind.NO_ROOM
                        && warning.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && warning.lootId().equals(Optional.of("hoard")))
                .count());
    }

    private static String headLoot(String headData) {
        return """
                language: en
                open_mode: drop
                block: player_head
                head_data: '%s'
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """.formatted(headData);
    }
}
