package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.lootbox.ChunkHolds;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Finds one spot for a loot. {@link SummonSearch} is the real one; the summon service asks for a spot through this so
 * a test can hand it a chosen one.
 */
public interface SpotFinder {

    /**
     * Searches a world around its spawn, or around the players, by the world's settings.
     */
    CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied);

    /**
     * Searches around one block, in the loot's player ring.
     */
    CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                 OccupiedCheck occupied);

    /**
     * {@link #serverSummon(UUID, String, OccupiedCheck)}, taking a hold on each chunk it reads through {@code holds}
     * when the world's policy is {@code load} or {@code generate}. The hold on the returned spot's chunk goes to the
     * caller, as {@link SummonResult#chunkHeld()} says; every other hold is given back. A finder that holds nothing
     * ignores {@code holds}.
     */
    default CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied,
                                                         ChunkHolds holds) {
        return serverSummon(worldKey, lootId, occupied);
    }

    /**
     * {@link #aroundCenter(UUID, String, int, int, int, OccupiedCheck)}, holding chunks as
     * {@link #serverSummon(UUID, String, OccupiedCheck, ChunkHolds)} does.
     */
    default CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                         OccupiedCheck occupied, ChunkHolds holds) {
        return aroundCenter(worldKey, lootId, x, y, z, occupied);
    }
}
