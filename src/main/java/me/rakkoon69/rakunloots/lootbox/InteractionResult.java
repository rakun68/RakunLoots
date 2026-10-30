package me.rakkoon69.rakunloots.lootbox;

/**
 * What the lifecycle decided about a click or break on a block.
 */
public enum InteractionResult {
    /**
     * A live box took the interaction. A click is cancelled; a break is not, and the caller turns the server's own
     * drops off.
     */
    ALLOWED,
    /**
     * A live box refused the interaction, such as while its lock runs. The event is cancelled.
     */
    REFUSED,
    /**
     * The box's block was no longer the placed block, so the box ended as a changed block. The event is not
     * cancelled and its drops are left alone, since the block is no longer a loot box.
     */
    ENDED_CHANGED,
    /**
     * No loot box is at the block. Nothing happens.
     */
    NOT_A_BOX
}
