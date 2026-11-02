package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;

import java.util.List;
import java.util.Objects;

/**
 * What one claim did, by loot.
 *
 * @param taken   each loot the claim tried, in loot id order
 * @param skipped each owed row whose loot is missing; the claim left it as it is
 */
public record ClaimResult(List<Taken> taken, List<UnclaimedSummonItem> skipped) {

    /**
     * One loot's part of a claim.
     *
     * @param given how many summon items went into the inventory
     * @param kept  how many stay owed because they did not fit
     */
    public record Taken(String lootId, int given, int kept) {

        public Taken {
            Objects.requireNonNull(lootId, "lootId");
        }
    }

    public ClaimResult {
        taken = List.copyOf(Objects.requireNonNull(taken, "taken"));
        skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
    }

    /**
     * How many summon items the claim gave in all.
     */
    public int given() {
        return taken.stream().mapToInt(Taken::given).sum();
    }

    /**
     * How many summon items stay owed because they did not fit.
     */
    public int kept() {
        return taken.stream().mapToInt(Taken::kept).sum();
    }
}
