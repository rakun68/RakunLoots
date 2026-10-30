package me.rakkoon69.rakunloots.lootbox;

/**
 * Why a pending summon placed no loot box. An abandoned summon releases its spot and never searches again.
 */
public enum AbandonReason {

    /**
     * The spot the search found was already held by a loot box or another summon.
     */
    SPOT_HELD("summon_spot_held"),

    /**
     * The block at the spot was no longer the block the search found when the delay ended.
     */
    BLOCK_CHANGED("summon_block_changed"),

    /**
     * The server refused the loot's presentation block or its head data.
     */
    BLOCK_REFUSED("summon_block_refused"),

    /**
     * The summon failed after the block was written, such as the roll throwing. The block was removed again.
     */
    ROLL_FAILED("summon_failed"),

    /**
     * The spot's chunk unloaded during the delay. A summon never loads a chunk.
     */
    CHUNK_UNLOADED("summon_chunk_unloaded"),

    /**
     * A reload or a stop came during the delay.
     */
    RELOAD_OR_STOP("summon_interrupted"),

    /**
     * The search found no spot.
     */
    NO_SPOT_FOUND("can_not_spawn_loot"),

    /**
     * The loot is not in the current config.
     */
    UNKNOWN_LOOT("loot_not_found");

    private final String messageKey;

    AbandonReason(String messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * The key of this reason's default message, which a loot's message overrides can reword.
     */
    public String messageKey() {
        return messageKey;
    }
}
