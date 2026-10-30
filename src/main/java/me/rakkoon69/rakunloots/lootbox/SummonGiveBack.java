package me.rakkoon69.rakunloots.lootbox;

import java.util.UUID;

/**
 * Takes back the summon item of an abandoned summon item summon: to the player's inventory, or as an unclaimed
 * summon item. Called once for each abandoned summon item summon, never for a server summon.
 */
@FunctionalInterface
public interface SummonGiveBack {

    /**
     * Gives nothing back. For a service whose summons never come from a summon item.
     */
    SummonGiveBack NONE = (playerId, lootId, reason) -> {
    };

    void giveBack(UUID playerId, String lootId, AbandonReason reason);
}
