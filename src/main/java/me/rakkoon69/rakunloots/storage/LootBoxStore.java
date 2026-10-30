package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Saves and loads loot boxes so a restart keeps them. Callers do not know which adapter runs.
 * The store owns no timer and no scheduler. Except where a method says otherwise, every call blocks, so callers
 * keep it off the main thread.
 */
public interface LootBoxStore extends AutoCloseable {

    /**
     * Writes one box, replacing any box saved under the same box id.
     */
    void save(LootBoxRecord box) throws LootBoxStoreException;

    /**
     * Removes one box so it does not load again, and drops any change still pending for it. Removing a box that
     * is not stored does nothing.
     */
    void delete(UUID boxId) throws LootBoxStoreException;

    /**
     * Keeps this state of the box for the next flush. A later mark of the same box id replaces it. Does not block
     * and does not write, so the main thread may call it.
     */
    void markChanged(LootBoxRecord box);

    /**
     * Keeps a delete of this box for the next flush, and drops any change still pending for it. A change marked for
     * it later is dropped too while the delete is pending, since a box that ended does not come back. Does not
     * block and does not write, so the main thread may call it. Queueing a box that is not stored does nothing
     * when flushed.
     */
    void queueDelete(UUID boxId);

    /**
     * Writes every changed box and every queued delete on the given executor and returns without waiting for it.
     * A box or a delete that cannot be written warns at severe and stays pending, so the next flush retries it on
     * this same store. A box marked again while the flush runs stays pending with its newer state.
     */
    CompletableFuture<FlushResult> flush(Executor executor);

    /**
     * Every stored box that can be read, checked against the current config and loaded worlds.
     * A box whose loot is missing or whose world is not loaded loads flagged with a warning. Remaining life is
     * capped by the current loot's life when that loot exists and its life is not unending. A stack that cannot
     * be read is left out with a high warning, and its bytes stay until that box is saved again. Load changes no
     * row.
     */
    LoadResult loadAll(ConfigSnapshot config, Predicate<UUID> worldLoaded) throws LootBoxStoreException;

    /**
     * Keeps summon items a player is owed. Adding to a row of the same player and loot raises its amount.
     *
     * @param amount at least 1
     * @throws IllegalArgumentException when the amount is below 1
     */
    void addUnclaimedSummonItems(UUID playerId, String lootId, int amount) throws LootBoxStoreException;

    /**
     * The summon items a player is owed, one entry per loot, ordered by loot id. An entry whose loot is missing from
     * the config is flagged. Reading changes no row.
     */
    List<UnclaimedSummonItem> unclaimedSummonItems(UUID playerId, ConfigSnapshot config) throws LootBoxStoreException;

    /**
     * Lowers a player's amount of one loot by the given amount, or to 0 when less is stored, and deletes the row at
     * 0. The change is committed when this returns, so a caller gives the items after it and a crash in between
     * never gives them twice.
     *
     * @param amount at least 1
     * @return how much was lowered; 0 when the player is owed none of that loot
     * @throws IllegalArgumentException when the amount is below 1
     */
    int claimUnclaimedSummonItems(UUID playerId, String lootId, int amount) throws LootBoxStoreException;

    /**
     * Copies the store into its backups folder and keeps only the newest {@code backupsKept} copies. A store with
     * no file of its own, such as MySQL, does nothing: its backup stays with the host.
     *
     * @param backupsKept how many copies stay, from {@code backups_kept}; zero writes none and removes the rest
     */
    default void backup(int backupsKept) throws LootBoxStoreException {
        if (backupsKept < 0) {
            throw new IllegalArgumentException("backupsKept must not be negative");
        }
    }

    /**
     * Closes the store. Changes still pending are not written; flush first.
     */
    @Override
    void close() throws LootBoxStoreException;
}
