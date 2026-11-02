package me.rakkoon69.rakunloots.lootbox;

import java.util.Objects;
import java.util.UUID;

/**
 * One block in a world where a loot box may land, with the block the search found there. Summon replaces that block
 * only while it is still the same block, so a spot taken or changed since the search is refused.
 *
 * @param blockId the namespaced block id at the spot when it was read, such as {@code minecraft:air}
 */
public record Spot(UUID worldKey, int x, int y, int z, String blockId) {

    public Spot {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(blockId, "blockId");
    }
}
