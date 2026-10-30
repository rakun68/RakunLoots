package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.flatChunk;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ADR 0015: under {@code unloaded_chunks: load} or {@code generate} a live loot box keeps its chunk loaded with a
 * plugin chunk ticket, from the placement or the start, until every loot end, the stop, or a policy change gives it
 * back. Under {@code skip} no ticket is held, and an admin removal loads the chunk for the removal alone. The config
 * is the shipped one, with the shipped world's policy set.
 */
class LootBoxChunkHoldTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final long SECOND = LootBoxLifecycle.TICK_PERIOD_TICKS;
    private static final ChunkPosition CHUNK = new ChunkPosition(0, 0);
    private static final ChunkPosition OTHER_CHUNK = new ChunkPosition(1, 0);

    /**
     * A drop open loot with 3 seconds of life, no lock, and the explosion allowed, so every loot end can reach it.
     */
    private static final String SHORT_LIVED = """
            language: en
            open_mode: drop
            lock: 0
            life: 3
            block: barrel
            loot_end:
              allow_explosion: true
            roll:
              entries:
                gold:
                  amount: 7
                  item:
                    id: gold_ingot
            """;

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

    private static ConfigSnapshot config(String policy) {
        return shippedConfig(Map.of("short_lived", SHORT_LIVED), policy);
    }

    private LootBoxLifecycle started(String policy) {
        LootBoxLifecycle lifecycle = fixture.lifecycle(config(policy), store, 77);
        lifecycle.start().join();
        return lifecycle;
    }

    private LootBox summon(LootBoxLifecycle lifecycle, String loot, int x, int z) {
        return lifecycle.summon(loot, airSpot(x, z), Optional.empty(), SummonKind.UNKNOWN).join().box().orElseThrow();
    }

    /**
     * Acceptance: a box placed under load or generate holds its chunk with a ticket.
     */
    @ParameterizedTest
    @ValueSource(strings = {"load", "generate"})
    void aPlacedBoxHoldsItsChunkUnderLoadAndGenerate(String policy) {
        LootBoxLifecycle lifecycle = started(policy);

        summon(lifecycle, "airdrops", 1, 1);

        assertEquals(Set.of(CHUNK), fixture.adapter.heldChunks());
        assertEquals(List.of(CHUNK), fixture.adapter.holdCalls());
    }

    /**
     * Acceptance: under skip a box holds no ticket, so its chunk unloads as any chunk does.
     */
    @Test
    void aPlacedBoxHoldsNothingUnderSkip() {
        LootBoxLifecycle lifecycle = started("skip");

        summon(lifecycle, "airdrops", 1, 1);

        assertEquals(Set.of(), fixture.adapter.heldChunks());
        assertEquals(List.of(), fixture.adapter.holdCalls());
        assertEquals(true, fixture.adapter.unloadChunk(0, 0), "nothing keeps the chunk");
    }

    /**
     * Acceptance: each loot end gives the ticket back, and only after the block is removed, so the removal still
     * sees a loaded chunk. A changed block removes nothing, so only the release comes.
     */
    @Test
    void everyLootEndReleasesTheTicketAfterTheBlockIsRemoved() {
        LootBoxLifecycle lifecycle = started("load");
        Map<String, BiConsumer<LootBoxLifecycle, LootBox>> ends = new LinkedHashMap<>();
        ends.put("break", (lc, each) -> lc.interact(OWNER, WORLD, each.position(), false, Interaction.BREAK));
        ends.put("drop open click", (lc, each) -> lc.interact(OWNER, WORLD, each.position(), false,
                Interaction.CLICK));
        ends.put("timeout", (lc, each) -> fixture.adapter.globalScheduler().stepTicks(SECOND * 3));
        ends.put("explosion", (lc, each) -> lc.explode(WORLD, List.of(each.position())));
        ends.put("admin removal", (lc, each) -> lc.remove(candidate -> true));
        ends.put("changed block", (lc, each) -> {
            fixture.adapter.putBlock(each.position(), "minecraft:stone");
            lc.interact(OWNER, WORLD, each.position(), false, Interaction.CLICK);
        });

        ends.forEach((name, end) -> {
            LootBox box = summon(lifecycle, "short_lived", 2, 2);
            int releasesBefore = fixture.adapter.releaseCalls().size();
            int orderBefore = fixture.adapter.removalAndReleaseOrder().size();
            assertEquals(Set.of(CHUNK), fixture.adapter.heldChunks(), name + ": held while live");

            end.accept(lifecycle, box);

            assertFalse(lifecycle.isLootBox(WORLD, box.position().x(), box.position().y(), box.position().z()), name);
            assertEquals(Set.of(), fixture.adapter.heldChunks(), name + ": released");
            assertEquals(List.of(CHUNK), fixture.adapter.releaseCalls().subList(releasesBefore,
                    fixture.adapter.releaseCalls().size()), name);
            List<String> order = fixture.adapter.removalAndReleaseOrder()
                    .subList(orderBefore, fixture.adapter.removalAndReleaseOrder().size());
            assertEquals(name.equals("changed block") ? List.of("release") : List.of("remove", "release"), order,
                    name + ": the block goes before the ticket");
        });
    }

    /**
     * A plugin holds one ticket per chunk, so two boxes in a chunk share it: it is released only when the last of
     * them ends.
     */
    @Test
    void twoBoxesInOneChunkShareTheTicketUntilTheLastEnds() {
        LootBoxLifecycle lifecycle = started("load");
        LootBox first = summon(lifecycle, "airdrops", 1, 1);
        LootBox second = summon(lifecycle, "airdrops", 3, 3);
        LootBox elsewhere = summon(lifecycle, "airdrops", 17, 1);
        assertEquals(Set.of(CHUNK, OTHER_CHUNK), fixture.adapter.heldChunks());
        assertEquals(List.of(CHUNK, OTHER_CHUNK), fixture.adapter.holdCalls(), "one hold per chunk");

        lifecycle.remove(box -> box.boxId().equals(first.boxId()));
        assertEquals(Set.of(CHUNK, OTHER_CHUNK), fixture.adapter.heldChunks(), "the second box still needs it");

        lifecycle.remove(box -> box.boxId().equals(second.boxId()));
        assertEquals(Set.of(OTHER_CHUNK), fixture.adapter.heldChunks());

        lifecycle.remove(box -> box.boxId().equals(elsewhere.boxId()));
        assertEquals(Set.of(), fixture.adapter.heldChunks());
    }

    /**
     * Acceptance: the stop gives every ticket back.
     */
    @Test
    void stoppingReleasesEveryTicket() {
        LootBoxLifecycle lifecycle = started("load");
        summon(lifecycle, "airdrops", 1, 1);
        summon(lifecycle, "airdrops", 17, 1);
        assertEquals(Set.of(CHUNK, OTHER_CHUNK), fixture.adapter.heldChunks());

        lifecycle.stop();

        assertEquals(Set.of(), fixture.adapter.heldChunks());
    }

    /**
     * Acceptance: a reload that changes the world's policy applies it to the world's live boxes. Skip gives the
     * tickets back, load or generate takes them again, and a change between load and generate changes nothing.
     */
    @Test
    void aReloadThatChangesThePolicyAddsOrReleasesTheTicketsOfLiveBoxes() {
        LootBoxLifecycle lifecycle = started("load");
        summon(lifecycle, "airdrops", 1, 1);
        summon(lifecycle, "airdrops", 17, 1);
        assertEquals(Set.of(CHUNK, OTHER_CHUNK), fixture.adapter.heldChunks());

        lifecycle.reload(config("generate"));
        assertEquals(Set.of(CHUNK, OTHER_CHUNK), fixture.adapter.heldChunks());
        assertEquals(List.of(), fixture.adapter.releaseCalls(), "load to generate keeps the tickets");

        lifecycle.reload(config("skip"));
        assertEquals(Set.of(), fixture.adapter.heldChunks());

        lifecycle.reload(config("load"));
        assertEquals(Set.of(CHUNK, OTHER_CHUNK), fixture.adapter.heldChunks());
    }

    /**
     * Acceptance: a stored box registered at start gets its ticket, which loads its chunk when the chunk was not
     * loaded. Under skip it gets none.
     */
    @Test
    void aStoredBoxAtStartGetsItsTicketUnderLoadAndNoneUnderSkip() {
        LootBoxLifecycle first = started("load");
        summon(first, "airdrops", 1, 1);
        first.stop();
        assertEquals(Set.of(), fixture.adapter.heldChunks());
        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        int holdsBefore = fixture.adapter.holdCalls().size();

        LootBoxLifecycle second = started("load");

        assertEquals(Set.of(CHUNK), fixture.adapter.heldChunks());
        assertEquals(List.of(CHUNK), fixture.adapter.holdCalls().subList(holdsBefore,
                fixture.adapter.holdCalls().size()));
        assertEquals(false, fixture.adapter.unloadChunk(0, 0), "the held chunk stays loaded");
        second.stop();

        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        int holdsAfterLoad = fixture.adapter.holdCalls().size();
        LootBoxLifecycle third = started("skip");
        assertEquals(Set.of(), fixture.adapter.heldChunks());
        assertEquals(holdsAfterLoad, fixture.adapter.holdCalls().size());
        third.stop();
    }

    /**
     * Acceptance: under skip, an admin removal of a box in an unloaded chunk loads the chunk and removes the block,
     * and holds nothing, so the chunk unloads normally after. This is the bug: the removal used to unregister the
     * box and leave the barrel in the world.
     */
    @Test
    void anAdminRemovalUnderSkipRemovesTheBlockOfABoxInAnUnloadedChunk() {
        LootBoxLifecycle lifecycle = started("skip");
        LootBox box = summon(lifecycle, "airdrops", 1, 1);
        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        BlockPosition position = box.position();
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(position));

        int removed = lifecycle.remove(candidate -> true);

        assertEquals(1, removed);
        assertEquals("minecraft:air", fixture.adapter.blockAt(position), "the barrel is gone from the world");
        assertEquals(List.of(CHUNK), fixture.adapter.chunksLoadedForRemoval());
        assertEquals(Set.of(), fixture.adapter.heldChunks());
        assertFalse(lifecycle.isLootBox(WORLD, position.x(), position.y(), position.z()));
    }

    /**
     * The removal leaves a block that is no longer the box's block, also in an unloaded chunk.
     */
    @Test
    void anAdminRemovalUnderSkipLeavesAnotherBlockInAnUnloadedChunk() {
        LootBoxLifecycle lifecycle = started("skip");
        LootBox box = summon(lifecycle, "airdrops", 1, 1);
        fixture.adapter.putBlock(box.position(), "minecraft:stone");
        fixture.adapter.putChunk(flatChunk(0, 0), false, true);

        lifecycle.remove(candidate -> true);

        assertEquals("minecraft:stone", fixture.adapter.blockAt(box.position()));
    }

    /**
     * Under load the box's chunk is held, so the admin removal finds it loaded and loads nothing.
     */
    @Test
    void anAdminRemovalUnderLoadFindsTheHeldChunkLoaded() {
        LootBoxLifecycle lifecycle = started("load");
        LootBox box = summon(lifecycle, "airdrops", 1, 1);
        assertEquals(false, fixture.adapter.unloadChunk(0, 0), "the held chunk cannot unload");

        lifecycle.remove(candidate -> true);

        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
        assertEquals(List.of(), fixture.adapter.chunksLoadedForRemoval());
    }
}
