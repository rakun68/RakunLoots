package me.rakkoon69.rakunloots.lootbox;

/**
 * Why a summon placed no loot box.
 */
public enum SummonRefusal {

    /**
     * The loot is not in the current config: it does not exist, or it was refused at load, such as for a banned
     * loot block.
     */
    UNKNOWN_LOOT,

    /**
     * A loot box already holds the spot, or another summon is placing one there.
     */
    SPOT_OCCUPIED,

    /**
     * The block at the spot is no longer the one the search found, or its world or chunk is no longer loaded.
     */
    SPOT_CHANGED,

    /**
     * The server refused the loot's presentation block or its head data. The summon warns high.
     */
    BLOCK_REFUSED
}
