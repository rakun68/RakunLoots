package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.SummonGiveBack;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Takes back the summon item of an abandoned summon item summon (ADR 0013): into the player's inventory when the
 * player is online and the item fits, otherwise as an unclaimed summon item the player takes with the claim command.
 * Never dropped on the ground. The item is one summon item, built from the loot's current summon item.
 *
 * <p>The item is built and stored on the async scheduler; the inventory is read and filled on the player's thread,
 * through the server adapter. An item the inventory cannot take, whether the player is offline or the inventory is
 * full, becomes an unclaimed summon item for the whole amount. A loot that is missing, or whose summon item cannot be
 * built, also becomes an unclaimed summon item, so nothing a player is owed is lost.
 */
public final class SummonItemGiveBack implements SummonGiveBack {

    private final ServerAdapter adapter;
    private final Supplier<ConfigSnapshot> config;
    private final LootBoxStore store;
    private final SummonItemFactory items;
    private final BiConsumer<String, Throwable> failures;

    /**
     * @param config   the current config, read once per call, so a reload reaches the next call
     * @param failures told what failed and why, from any thread, for the caller to log at severe
     */
    public SummonItemGiveBack(ServerAdapter adapter, Supplier<ConfigSnapshot> config, LootBoxStore store,
                              SummonItemFactory items, BiConsumer<String, Throwable> failures) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
        this.items = Objects.requireNonNull(items, "items");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    @Override
    public void giveBack(UUID playerId, String lootId, AbandonReason reason) {
        give(playerId, lootId, 1);
    }

    /**
     * Gives a player an amount of a loot's summon item the same way a give back does, for an admin give: as many as
     * fit go into the inventory when the player is online, and the rest, or all of them for an offline player or a
     * full inventory, become unclaimed summon items.
     *
     * @param amount at least 1
     */
    public void give(UUID playerId, String lootId, int amount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lootId, "lootId");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
        adapter.async().run(() -> {
            Optional<ItemStack> item = build(lootId);
            if (item.isEmpty()) {
                keep(playerId, lootId, amount);
                return;
            }
            fit(playerId, item.get(), amount).whenComplete((given, failure) -> adapter.async().run(() -> {
                if (failure != null) {
                    failures.accept("could not give " + amount + " summon items of " + lootId + " to " + playerId,
                            failure);
                    keep(playerId, lootId, amount);
                } else if (given < amount) {
                    keep(playerId, lootId, amount - given);
                }
            }));
        });
    }

    private Optional<ItemStack> build(String lootId) {
        try {
            Optional<Loot> loot = config.get().loot(lootId);
            return loot.isEmpty() ? Optional.empty() : Optional.of(items.create(loot.get()));
        } catch (RuntimeException failure) {
            failures.accept("could not build the summon item of " + lootId + " to give it back", failure);
            return Optional.empty();
        }
    }

    /**
     * Gives as many as fit, up to the amount; 0 when none fit or the player is offline.
     */
    private CompletableFuture<Integer> fit(UUID playerId, ItemStack item, int amount) {
        try {
            return adapter.inventoryRoom(playerId, item).thenCompose(room -> room >= 1
                    ? adapter.giveItems(playerId, item, Math.min(room, amount))
                    : CompletableFuture.completedFuture(0));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private void keep(UUID playerId, String lootId, int amount) {
        try {
            store.addUnclaimedSummonItems(playerId, lootId, amount);
        } catch (Throwable failure) {
            failures.accept("could not keep " + amount + " summon items of " + lootId + " for " + playerId
                    + "; they are lost", failure);
        }
    }
}
