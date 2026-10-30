package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.summon.ChunkHolds;
import me.rakkoon69.rakunloots.summon.MissReason;
import me.rakkoon69.rakunloots.summon.OccupiedCheck;
import me.rakkoon69.rakunloots.summon.Spot;
import me.rakkoon69.rakunloots.summon.SpotFinder;
import me.rakkoon69.rakunloots.summon.SummonResult;
import me.rakkoon69.rakunloots.summon.SummonSearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.SERVER_LIKE;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.flatChunk;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR 0015's amendment: the summon search takes the chunk hold in the read that loaded the chunk and hands it to the
 * pending summon. A real search, the real lifecycle and summon service, and the shipped worlds.yml and airdrops loot,
 * over the fake server adapter, whose chunks start generated and unloaded, as in a world no player is near.
 */
class SummonChunkHandoffTest {

    private static final long DELAY = 60;

    /**
     * The shipped airdrops loot with no summon delay, for the summons that write at once.
     */
    private static final String INSTANT = resource("/loots/airdrops.yml")
            .replaceFirst("(?m)^summon_delay: \\d+", "summon_delay: 0");

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;
    private LootBoxLifecycle lifecycle;
    private SummonService service;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        fixture.adapter.setSpawn(0, 64, 0);
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                fixture.adapter.putChunk(flatChunk(chunkX, chunkZ), false, true);
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * The shipped worlds.yml with its world on a spawn ring of 20 blocks and the given policy, the shipped airdrops
     * loot, and the same loot with no delay as {@code instant}.
     */
    private ConfigSnapshot config(String policy) {
        String worlds = resource("/worlds.yml")
                .replaceAll("unloaded_chunks: \\w+", "unloaded_chunks: " + policy)
                .replace("mode: smart", "mode: spawn")
                .replace("max: 800", "max: 20");
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(worlds)
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .loot("instant", INSTANT)
                .build(), SERVER_LIKE, WarningKinds.all());
    }

    private UnloadingFinder startWithRealSearch(String policy) {
        ConfigSnapshot config = config(policy);
        lifecycle = new LootBoxLifecycle(fixture.adapter, config, ItemSourceRegistry.create(), store, new Random(1),
                fixture.warnings);
        UnloadingFinder finder = new UnloadingFinder(new SummonSearch(fixture.adapter, config, () -> 0L, new Random(3)));
        service = new SummonService(fixture.adapter, config, lifecycle, finder, (player, lootId, reason) -> {
        }, fixture.warnings);
        return finder;
    }

    private CompletableFuture<SummonReport> adminSummon(String lootId) {
        return service.summon(lootId, SummonTarget.world(WORLD), Optional.empty(), SummonSource.adminCommand());
    }

    private void step(long ticks) {
        fixture.adapter.globalScheduler().stepTicks(ticks);
    }

    /**
     * Acceptance: the chunk the search loaded unloads before the pending summon's hold lands, and a summon with no
     * delay is abandoned as CHUNK_UNLOADED on the code before ADR 0015's amendment, and places its box after it.
     */
    @Test
    void aChunkThatUnloadsBetweenTheSearchAndTheHoldDoesNotAbandonAnInstantSummonUnderLoad() {
        instantSummonSurvivesTheUnloadGap("load");
    }

    @Test
    void aChunkThatUnloadsBetweenTheSearchAndTheHoldDoesNotAbandonAnInstantSummonUnderGenerate() {
        instantSummonSurvivesTheUnloadGap("generate");
    }

    private void instantSummonSurvivesTheUnloadGap(String policy) {
        startWithRealSearch(policy);

        CompletableFuture<SummonReport> report = adminSummon("instant");
        fixture.adapter.landHolds();

        SummonReport done = report.join();
        assertTrue(done.box().isPresent(), "abandoned " + done.abandoned());
        assertEquals(1, fixture.adapter.blockWrites().size());
    }

    /**
     * The same gap for a summon with a delay: the unload event reaches the plugin once the summon is pending, and
     * abandons it on the code before the amendment. The unload only happens when nothing holds the chunk.
     */
    @Test
    void aChunkThatUnloadsBetweenTheSearchAndTheHoldDoesNotAbandonADelayedSummon() {
        UnloadingFinder finder = startWithRealSearch("load");

        CompletableFuture<SummonReport> report = adminSummon("airdrops");
        if (finder.unloaded) {
            service.chunkUnloaded(WORLD, finder.spotChunk);
        }
        fixture.adapter.landHolds();
        step(DELAY);

        SummonReport done = report.join();
        assertTrue(done.box().isPresent(), "abandoned " + done.abandoned());
        assertEquals(Set.of(finder.spotChunk), fixture.adapter.heldChunks(), "the box holds its chunk");
    }

    /**
     * Acceptance: a summon with no delay writes the block only after the hold has landed. The finder is a stub, so
     * the search holds nothing and the reservation asks for its own hold, which lands later.
     */
    @Test
    void aSummonWithNoDelayWritesTheBlockOnlyAfterItsHoldHasLanded() {
        ConfigSnapshot config = config("load");
        lifecycle = new LootBoxLifecycle(fixture.adapter, config, ItemSourceRegistry.create(), store, new Random(1),
                fixture.warnings);
        Spot spot = airSpot(3, 4);
        SpotFinder stub = new SpotFinder() {
            @Override
            public CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied) {
                return CompletableFuture.completedFuture(SummonResult.hit(spot));
            }

            @Override
            public CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                                OccupiedCheck occupied) {
                return CompletableFuture.completedFuture(SummonResult.miss(MissReason.ATTEMPTS_USED_UP));
            }
        };
        service = new SummonService(fixture.adapter, config, lifecycle, stub, (player, lootId, reason) -> {
        }, fixture.warnings);
        fixture.adapter.deferHolds();

        CompletableFuture<SummonReport> report = adminSummon("instant");

        assertFalse(report.isDone(), "waiting for the hold");
        assertTrue(fixture.adapter.blockWrites().isEmpty(), "no block before the hold has landed");
        assertTrue(lifecycle.occupiedCheck().isOccupied(WORLD, 3, 64, 4), "the spot is held meanwhile");

        fixture.adapter.landHolds();

        assertTrue(report.join().box().isPresent());
        assertEquals(List.of(new BlockPosition(3, 64, 4)),
                fixture.adapter.blockWrites().stream().map(write -> write.position()).toList());
        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks());
    }

    /**
     * Acceptance: the reservation adopts the hold the search took, instead of asking for its own. One hold call for
     * the spot's chunk, kept through the delay, and handed on to the box.
     */
    @Test
    void theReservationAdoptsTheSearchsHoldAndTheBoxKeepsItWithNoReleaseBetween() {
        UnloadingFinder finder = startWithRealSearch("load");

        CompletableFuture<SummonReport> report = adminSummon("airdrops");

        ChunkPosition chunk = finder.spotChunk;
        assertFalse(finder.unloaded, "the search's hold keeps the chunk from unloading");
        assertEquals(List.of(chunk), fixture.adapter.holdCalls(), "one hold, not one for the search and one more");
        assertEquals(Set.of(chunk), fixture.adapter.heldChunks());
        step(DELAY);
        assertTrue(report.join().box().isPresent());
        assertEquals(Set.of(chunk), fixture.adapter.heldChunks());
        assertEquals(List.of(), fixture.adapter.releaseCalls(), "no gap where the chunk can unload");
    }

    /**
     * Acceptance: a chunk the search read but did not return gives its hold back, so no ticket is left behind. Only
     * chunk (0, 0) has a spot; the others are solid stone.
     */
    @Test
    void chunksTheSearchReadButDidNotReturnGiveTheirHoldsBack() {
        startWithRealSearch("generate");
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                if (chunkX != 0 || chunkZ != 0) {
                    fixture.adapter.putChunk(new me.rakkoon69.rakunloots.server.ChunkView(WORLD, chunkX, chunkZ, 0, 256,
                            (x, y, z) -> "minecraft:stone"), false, true);
                }
            }
        }

        CompletableFuture<SummonReport> report = adminSummon("airdrops");

        Set<ChunkPosition> held = new java.util.HashSet<>(fixture.adapter.holdCalls());
        assertTrue(held.size() > 1, "the search read other chunks first: " + held);
        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks());
        held.remove(new ChunkPosition(0, 0));
        assertEquals(held, new java.util.HashSet<>(fixture.adapter.releaseCalls()), "each other chunk is released");
        step(DELAY);
        assertTrue(report.join().box().isPresent());
        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks());
    }

    /**
     * Acceptance: a search that finds no spot leaves no ticket behind.
     */
    @Test
    void aSearchThatFindsNoSpotLeavesNoTicketBehind() {
        startWithRealSearch("load");
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                fixture.adapter.putChunk(new me.rakkoon69.rakunloots.server.ChunkView(WORLD, chunkX, chunkZ, 0, 256,
                        (x, y, z) -> "minecraft:stone"), false, true);
            }
        }

        SummonReport report = adminSummon("airdrops").join();

        assertEquals(Optional.of(AbandonReason.NO_SPOT_FOUND), report.abandoned());
        assertFalse(fixture.adapter.holdCalls().isEmpty(), "the search held what it read");
        assertEquals(Set.of(), fixture.adapter.heldChunks());
        assertEquals(fixture.adapter.holdCalls().stream().distinct().count(),
                fixture.adapter.releaseCalls().stream().distinct().count());
    }

    /**
     * Acceptance: under skip nothing is held, as before. The search reads loaded chunks only.
     */
    @Test
    void underSkipNothingIsHeldBySearchOrSummon() {
        startWithRealSearch("skip").armed = false;
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                fixture.adapter.putChunk(flatChunk(chunkX, chunkZ), true, true);
            }
        }

        CompletableFuture<SummonReport> report = adminSummon("airdrops");
        step(DELAY);

        assertTrue(report.join().box().isPresent(), "abandoned " + report.join().abandoned());
        assertEquals(List.of(), fixture.adapter.holdCalls());
        assertEquals(Set.of(), fixture.adapter.heldChunks());
    }

    /**
     * Acceptance: a hold the search took for a spot that is already held is given back, so the spot's chunk is
     * released when its real holder lets go.
     */
    @Test
    void aReservationRefusedForAHeldSpotGivesTheSearchsHoldBack() {
        startWithRealSearch("load");
        BlockPosition spot = new BlockPosition(3, 64, 4);
        assertTrue(lifecycle.chunkHolds().take(WORLD, 0, 0).join());
        assertTrue(lifecycle.reserveSpot(WORLD, spot, true));
        assertTrue(lifecycle.chunkHolds().take(WORLD, 0, 0).join());

        assertFalse(lifecycle.reserveSpot(WORLD, spot, true), "the spot is held already");
        assertEquals(Set.of(new ChunkPosition(0, 0)), fixture.adapter.heldChunks(), "the first holder keeps it");
        lifecycle.releaseSpot(WORLD, spot);

        assertEquals(Set.of(), fixture.adapter.heldChunks(), "nothing is left behind");
        assertEquals(List.of(new ChunkPosition(0, 0)), fixture.adapter.releaseCalls());
    }

    /**
     * A hold the server refuses, as for a chunk that is not generated, is not counted, so it needs no give back.
     */
    @Test
    void aHoldTheServerRefusesIsNotCounted() {
        startWithRealSearch("load");
        fixture.adapter.putChunk(flatChunk(9, 9), false, false);

        assertFalse(lifecycle.chunkHolds().take(WORLD, 9, 9).join());
        assertTrue(lifecycle.chunkHolds().take(WORLD, 0, 0).join());
        lifecycle.chunkHolds().give(WORLD, 0, 0);

        assertEquals(List.of(new ChunkPosition(0, 0)), fixture.adapter.releaseCalls());
    }

    /**
     * Wraps the real search. When it returns a spot, the server unloads that spot's chunk before the pending summon
     * can hold it, if nothing holds the chunk, and from then on a hold takes time to land.
     */
    private final class UnloadingFinder implements SpotFinder {

        private final SpotFinder real;
        private final List<Spot> found = new CopyOnWriteArrayList<>();
        volatile boolean armed = true;
        volatile boolean unloaded;
        volatile ChunkPosition spotChunk;

        private UnloadingFinder(SpotFinder real) {
            this.real = real;
        }

        @Override
        public CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied) {
            return real.serverSummon(worldKey, lootId, occupied).thenApply(this::unloadThen);
        }

        @Override
        public CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                            OccupiedCheck occupied) {
            return real.aroundCenter(worldKey, lootId, x, y, z, occupied).thenApply(this::unloadThen);
        }

        @Override
        public CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied,
                                                            ChunkHolds holds) {
            return real.serverSummon(worldKey, lootId, occupied, holds).thenApply(this::unloadThen);
        }

        @Override
        public CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                            OccupiedCheck occupied, ChunkHolds holds) {
            return real.aroundCenter(worldKey, lootId, x, y, z, occupied, holds).thenApply(this::unloadThen);
        }

        private SummonResult unloadThen(SummonResult result) {
            if (!armed) {
                return result;
            }
            result.spot().ifPresent(spot -> {
                found.add(spot);
                spotChunk = new ChunkPosition(spot.x() >> 4, spot.z() >> 4);
                unloaded = fixture.adapter.unloadChunk(spotChunk.chunkX(), spotChunk.chunkZ());
                if (unloaded) {
                    fixture.adapter.deferHolds();
                }
            });
            return result;
        }
    }
}
