package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.lootbox.ChunkHolds;
import me.rakkoon69.rakunloots.lootbox.Spot;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of one summon search.
 */
public final class SummonResult {

    private final Spot spot;
    private final SearchMiss missReason;
    private final boolean chunkHeld;

    private SummonResult(Spot spot, SearchMiss missReason, boolean chunkHeld) {
        this.spot = spot;
        this.missReason = missReason;
        this.chunkHeld = chunkHeld;
    }

    /**
     * A spot whose chunk the search did not hold.
     */
    public static SummonResult hit(Spot spot) {
        return hit(spot, false);
    }

    /**
     * A spot whose chunk the search holds, through its {@link ChunkHolds}, when {@code chunkHeld}. The caller owns
     * that hold now: it adopts it with the spot's reservation, or gives it back.
     */
    public static SummonResult hit(Spot spot, boolean chunkHeld) {
        Objects.requireNonNull(spot, "spot");
        return new SummonResult(spot, null, chunkHeld);
    }

    public static SummonResult miss(SearchMiss reason) {
        Objects.requireNonNull(reason, "reason");
        return new SummonResult(null, reason, false);
    }

    public Optional<Spot> spot() {
        return Optional.ofNullable(spot);
    }

    public Optional<SearchMiss> missReason() {
        return Optional.ofNullable(missReason);
    }

    public boolean isHit() {
        return spot != null;
    }

    /**
     * Whether the search took a hold on the spot's chunk that the caller now owns. False for a miss, for a search
     * under {@code skip}, and for a chunk the server could not hold.
     */
    public boolean chunkHeld() {
        return chunkHeld;
    }
}
