package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.config.MessageRenderer;
import me.rakkoon69.rakunloots.text.MessageText;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The summon items a player is owed and has not taken (ADR 0013): the claim, and the join message that says they are
 * waiting.
 * <p>
 * A claim reads what the player is owed, then for each loot makes the loot's current summon item, asks how many fit,
 * lowers the stored amount by that many and commits it, and only then gives the items. A crash between the commit
 * and the give loses those items; it never gives them twice. A give that puts in fewer than asked, because the
 * inventory changed in between, writes the rest back. A row whose loot is missing stays as it is and the claim says
 * so. Storage runs on the async scheduler, and the inventory and every message on the player's thread, through the
 * server adapter.
 */
public final class UnclaimedSummonItems {

    static final String KEY_WAITING = "unclaimed_summon_items_waiting";
    static final String KEY_NOTHING = "claim_nothing";
    static final String KEY_GIVEN = "claim_given";
    static final String KEY_KEPT = "claim_kept";
    static final String KEY_LOOT_MISSING = "claim_loot_missing";

    private final ServerAdapter adapter;
    private final Supplier<ConfigSnapshot> config;
    private final LootBoxStore store;
    private final SummonItemFactory items;
    private final BiConsumer<String, Throwable> failures;

    /**
     * @param config   the current config, read once per call, so a reload reaches the next call
     * @param failures told what failed and why, from any thread, for the caller to log at severe
     */
    public UnclaimedSummonItems(ServerAdapter adapter, Supplier<ConfigSnapshot> config, LootBoxStore store,
                                SummonItemFactory items, BiConsumer<String, Throwable> failures) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
        this.items = Objects.requireNonNull(items, "items");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /**
     * Reads what the player is owed, on the async scheduler, in storage order. A row whose loot is missing is flagged.
     * The future completes exceptionally when storage fails; then the failure sink was told too.
     */
    public CompletableFuture<List<UnclaimedSummonItem>> owed(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return owed(playerId, config.get());
    }

    /**
     * Reads what the player is owed against one config snapshot, for a caller that also renders from that snapshot.
     */
    CompletableFuture<List<UnclaimedSummonItem>> owed(UUID playerId, ConfigSnapshot snapshot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        CompletableFuture<List<UnclaimedSummonItem>> result = new CompletableFuture<>();
        adapter.async().run(() -> {
            try {
                result.complete(store.unclaimedSummonItems(playerId, snapshot));
            } catch (Throwable failure) {
                fail(result, "could not read the unclaimed summon items of " + playerId, failure);
            }
        });
        return result;
    }

    /**
     * Gives the player the summon items they are owed, as many as fit, and tells them what was given, what stays,
     * and which loots are missing. The future completes with what was done, or exceptionally when storage or a give
     * fails; then the failure sink was told too.
     */
    public CompletableFuture<ClaimResult> claim(UUID playerId) {
        return claim(playerId, null);
    }

    /**
     * Gives the player the summon items they are owed for one loot, with the same read, storage lowering, give, write
     * back, and messages as {@link #claim(UUID)}. A missing loot is left as it is and the claim says so.
     */
    public CompletableFuture<ClaimResult> claim(UUID playerId, String lootId) {
        Objects.requireNonNull(playerId, "playerId");
        CompletableFuture<ClaimResult> result = new CompletableFuture<>();
        adapter.async().run(() -> {
            try {
                ConfigSnapshot snapshot = config.get();
                List<UnclaimedSummonItem> owed = store.unclaimedSummonItems(playerId, snapshot);
                if (lootId != null) {
                    owed = owed.stream().filter(row -> row.lootId().equals(lootId)).toList();
                }
                new Claim(playerId, snapshot, owed, result).next(0);
            } catch (Throwable failure) {
                fail(result, "could not read the unclaimed summon items of " + playerId, failure);
            }
        });
        return result;
    }

    /**
     * Tells a player who just joined that summon items are waiting, with the claim command's name. Reads on the
     * async scheduler and sends on the player's thread. A player owed nothing gets nothing.
     */
    public void onJoin(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        adapter.async().run(() -> {
            try {
                ConfigSnapshot snapshot = config.get();
                int owed = store.unclaimedSummonItems(playerId, snapshot).stream()
                        .mapToInt(UnclaimedSummonItem::amount).sum();
                if (owed == 0) {
                    return;
                }
                String message = render(snapshot, "", KEY_WAITING, owed, "");
                if (!message.isEmpty()) {
                    adapter.forPlayer(playerId).run(() -> adapter.sendMessage(playerId, message));
                }
            } catch (Throwable failure) {
                failures.accept("could not read the unclaimed summon items of " + playerId + " on join", failure);
            }
        });
    }

    private void fail(CompletableFuture<?> result, String what, Throwable failure) {
        failures.accept(what, failure);
        result.completeExceptionally(failure);
    }

    private static String render(ConfigSnapshot snapshot, String lootId, String key, int amount, String lootName) {
        return MessageRenderer.render(snapshot, lootId, key)
                .replace("{amount}", Integer.toString(amount))
                .replace("{loot}", MessageText.color(lootName))
                .replace("{command}", snapshot.claimCommand().name());
    }

    /**
     * One claim, a row at a time, so the inventory read for one loot sees what the one before gave.
     */
    private final class Claim {

        private final UUID playerId;
        private final ConfigSnapshot snapshot;
        private final List<UnclaimedSummonItem> owed;
        private final CompletableFuture<ClaimResult> result;
        private final List<ClaimResult.Taken> taken = new ArrayList<>();
        private final List<UnclaimedSummonItem> skipped = new ArrayList<>();

        Claim(UUID playerId, ConfigSnapshot snapshot, List<UnclaimedSummonItem> owed,
              CompletableFuture<ClaimResult> result) {
            this.playerId = playerId;
            this.snapshot = snapshot;
            this.owed = owed;
            this.result = result;
        }

        /**
         * Runs on the async scheduler.
         */
        void next(int index) {
            try {
                if (index == owed.size()) {
                    finish();
                    return;
                }
                UnclaimedSummonItem row = owed.get(index);
                if (row.lootMissing()) {
                    skipped.add(row);
                    next(index + 1);
                    return;
                }
                Loot loot = snapshot.loot(row.lootId()).orElseThrow();
                ItemStack template = items.create(loot);
                adapter.inventoryRoom(playerId, template).whenComplete((room, failure) ->
                        adapter.async().run(() -> {
                            if (failure != null) {
                                fail(result, "could not read the inventory of " + playerId, failure);
                                return;
                            }
                            take(index, row, template, room);
                        }));
            } catch (Throwable failure) {
                fail(result, "could not claim the summon items of " + playerId, failure);
            }
        }

        private void take(int index, UnclaimedSummonItem row, ItemStack template, int room) {
            try {
                int wanted = Math.min(row.amount(), room);
                if (wanted == 0) {
                    taken.add(new ClaimResult.Taken(row.lootId(), 0, row.amount()));
                    next(index + 1);
                    return;
                }
                // Committed before the give: a crash from here on loses these items and never gives them twice.
                int lowered = store.claimUnclaimedSummonItems(playerId, row.lootId(), wanted);
                if (lowered == 0) {
                    // Another claim took them first.
                    next(index + 1);
                    return;
                }
                adapter.giveItems(playerId, template, lowered).whenComplete((given, failure) ->
                        adapter.async().run(() -> {
                            if (failure != null) {
                                fail(result, "could not give " + lowered + " summon items of " + row.lootId()
                                        + " to " + playerId + "; they are no longer owed", failure);
                                return;
                            }
                            afterGive(index, row, lowered, given);
                        }));
            } catch (Throwable failure) {
                fail(result, "could not claim the summon items of " + playerId, failure);
            }
        }

        private void afterGive(int index, UnclaimedSummonItem row, int lowered, int given) {
            try {
                if (given < lowered) {
                    store.addUnclaimedSummonItems(playerId, row.lootId(), lowered - given);
                }
                taken.add(new ClaimResult.Taken(row.lootId(), given, row.amount() - given));
                next(index + 1);
            } catch (Throwable failure) {
                fail(result, "could not write back " + (lowered - given) + " summon items of " + row.lootId()
                        + " for " + playerId, failure);
            }
        }

        private void finish() {
            List<String> messages = new ArrayList<>();
            if (taken.isEmpty() && skipped.isEmpty()) {
                collect(messages, render(snapshot, "", KEY_NOTHING, 0, ""));
            }
            for (ClaimResult.Taken part : taken) {
                String name = snapshot.loot(part.lootId()).map(Loot::displayName).orElse(part.lootId());
                if (part.given() > 0) {
                    collect(messages, render(snapshot, part.lootId(), KEY_GIVEN, part.given(), name));
                }
                if (part.kept() > 0) {
                    collect(messages, render(snapshot, part.lootId(), KEY_KEPT, part.kept(), name));
                }
            }
            for (UnclaimedSummonItem row : skipped) {
                collect(messages, render(snapshot, "", KEY_LOOT_MISSING, row.amount(), row.lootId()));
            }
            if (!messages.isEmpty()) {
                // The messages go out on the player's thread, in order, like the join message.
                adapter.forPlayer(playerId).run(() -> messages.forEach(message ->
                        adapter.sendMessage(playerId, message)));
            }
            result.complete(new ClaimResult(taken, skipped));
        }

        private void collect(List<String> messages, String message) {
            if (!message.isEmpty()) {
                messages.add(message);
            }
        }
    }
}
