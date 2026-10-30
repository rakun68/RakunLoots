package me.rakkoon69.rakunloots.summon;

import java.util.UUID;

/**
 * Read-only check for whether a block already holds a loot box. The summon search caller supplies it.
 */
@FunctionalInterface
public interface OccupiedCheck {

    boolean isOccupied(UUID worldKey, int x, int y, int z);
}
