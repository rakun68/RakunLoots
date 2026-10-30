package me.rakkoon69.rakunloots.storage;

import java.util.Objects;

/**
 * Summon items a player is owed for one loot.
 *
 * @param lootMissing whether the config no longer has the loot; such a row is kept and a claim skips it
 */
public record UnclaimedSummonItem(String lootId, int amount, boolean lootMissing) {

    public UnclaimedSummonItem {
        Objects.requireNonNull(lootId, "lootId");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
    }
}
