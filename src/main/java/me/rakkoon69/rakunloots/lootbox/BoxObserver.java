package me.rakkoon69.rakunloots.lootbox;

import java.util.UUID;

/**
 * Follows what happens to each loot box, so a feature such as holograms can track boxes without the lifecycle knowing
 * about it. The lifecycle fans each event out to every observer added with {@link LootBoxLifecycle#addObserver}, in
 * the order they were added.
 *
 * <p>For one box the events arrive in order: shown, then ticked each second, then out of view or ended; after out of
 * view, shown again when its chunk loads. Shown never follows ended. Ticked arrives for every live box, shown or not.
 * Opened arrives for a live box each time a player opens it, and for a drop open box before its ended.
 *
 * <p>A summon is told apart from a box: summon pending arrives when a summon holds its spot, and then either shown
 * for the box it places or summon abandoned, never both. A summon abandoned before it held a spot, such as when the
 * search found none, was never pending and is not told.
 *
 * <p>Each call runs on the thread that caused it, while the lifecycle holds its lock: summon on the thread that set
 * the block, a chunk load or unload on the chunk's thread, the tick on the global scheduler, and an end on the thread
 * of whatever ended the box. So an observer must be safe on any thread, return quickly, and never wait for another
 * thread. A summon event runs on the thread that holds or releases the spot, without the lifecycle's lock. A call
 * that throws is reported to the lifecycle's warnings and stops neither the lifecycle nor the other observers.
 */
public interface BoxObserver {

    /**
     * Does nothing.
     */
    BoxObserver NONE = new BoxObserver() {
        @Override
        public void shown(BoxEvent box) {
        }

        @Override
        public void ticked(BoxEvent box) {
        }

        @Override
        public void outOfView(BoxEvent box) {
        }

        @Override
        public void ended(BoxEvent box) {
        }
    };

    /**
     * A box is in a loaded chunk: it was just summoned, its chunk loaded, or the lifecycle started and found its
     * chunk loaded.
     */
    void shown(BoxEvent box);

    /**
     * One second passed, and the box carries its lowered remaining lock and life.
     */
    void ticked(BoxEvent box);

    /**
     * The chunk of a shown box unloaded. The box still lives, and is shown again when its chunk loads.
     */
    void outOfView(BoxEvent box);

    /**
     * A loot end: the box left the world, whatever ended it. It carries the box as it was last.
     */
    void ended(BoxEvent box);

    /**
     * A player opened a live box, as inventory open once the server showed them the inventory, or as drop open at the
     * click, before the box ends.
     */
    default void opened(BoxEvent box, UUID playerId) {
    }

    /**
     * A summon holds its spot and waits out the loot's summon delay. The block is not written yet.
     *
     * @param spot       the spot the search found and the summon holds
     * @param lootId     the loot summoned
     * @param delayTicks the loot's summon delay in ticks; 0 writes the block at once
     */
    default void summonPending(Spot spot, String lootId, int delayTicks) {
    }

    /**
     * A pending summon placed no box and released its spot.
     */
    default void summonAbandoned(Spot spot, String lootId, AbandonReason reason) {
    }
}
