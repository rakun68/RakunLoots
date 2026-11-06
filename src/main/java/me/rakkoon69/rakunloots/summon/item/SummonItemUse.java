package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.MessageRenderer;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.SummonGiveBack;
import me.rakkoon69.rakunloots.summon.SummonService;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.summon.SummonTarget;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * What a player's summon item does: a use summons its loot around the player through the summon service, and an
 * outdated item is rebuilt from its loot's current summon item. Both run on the thread that owns the player, which is
 * the thread of the click and join events that call them.
 *
 * <p>The item is taken at the click, before the summon searches, so clicking during a summon delay never starts more
 * summons than the player has items. The summon service gives the item back when the summon is abandoned.
 */
public final class SummonItemUse {

    /**
     * The message the player gets when the click takes the item, before the summon searches and waits its delay.
     */
    static final String USED_KEY = "summon_item_used";

    private final SummonItemMaker maker;
    private final Supplier<ConfigSnapshot> config;
    private final SummonService service;
    private final SummonGiveBack giveBack;
    private final BiConsumer<String, Throwable> failures;

    /**
     * @param config   the current config, read once per call, so a reload reaches the next call
     * @param giveBack takes back the item of a summon that failed without the service abandoning it
     * @param failures told what failed and why, from any thread, for the caller to log at severe
     */
    public SummonItemUse(SummonItemMaker maker, Supplier<ConfigSnapshot> config, SummonService service,
                         SummonGiveBack giveBack, BiConsumer<String, Throwable> failures) {
        this.maker = Objects.requireNonNull(maker, "maker");
        this.config = Objects.requireNonNull(config, "config");
        this.service = Objects.requireNonNull(service, "service");
        this.giveBack = Objects.requireNonNull(giveBack, "giveBack");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /**
     * Uses the summon item in the player's main hand, if it is one: takes one, rebuilding the rest first when they are
     * outdated, and summons its loot in the loot's {@code player_ring} around the player, who owns the box. The off
     * hand is never looked at. No cooldown, permission, or world limit applies.
     *
     * @return whether the main hand held a summon item of a loot that exists, so the click is the item's
     */
    public boolean use(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        Optional<String> lootId = maker.lootIdOf(held);
        if (lootId.isEmpty()) {
            return false;
        }
        Optional<Loot> loot = config.get().loot(lootId.get());
        if (loot.isEmpty()) {
            return false;
        }
        ItemStack current = rebuilt(held, loot.get()).orElse(held);
        int left = current.getAmount() - 1;
        if (left <= 0) {
            inventory.setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            ItemStack rest = current.clone();
            rest.setAmount(left);
            inventory.setItemInMainHand(rest);
        }
        UUID playerId = player.getUniqueId();
        String used = MessageRenderer.render(config.get(), lootId.get(), USED_KEY,
                Map.of("box_name", loot.get().displayName()));
        if (!used.isEmpty()) {
            player.sendMessage(used);
        }
        service.summon(lootId.get(), SummonTarget.player(player.getWorld().getUID(), playerId), Optional.of(playerId),
                SummonSource.summonItem(playerId)).whenComplete((report, failure) -> {
            if (failure != null) {
                failures.accept("a summon item summon of " + lootId.get() + " by " + playerId + " failed", failure);
                giveBack.giveBack(playerId, lootId.get(), AbandonReason.ROLL_FAILED);
            }
        });
        return true;
    }

    /**
     * Rebuilds every outdated summon item in the player's inventory from its loot's current summon item, at the same
     * amount. An item whose loot is missing, or whose summon item cannot be built, is left as it is.
     *
     * @return how many stacks were rebuilt
     */
    public int refresh(Player player) {
        Objects.requireNonNull(player, "player");
        ConfigSnapshot snapshot = config.get();
        PlayerInventory inventory = player.getInventory();
        int rebuilt = 0;
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            Optional<ItemStack> replacement = rebuilt(storage[slot], snapshot);
            if (replacement.isPresent()) {
                inventory.setItem(slot, replacement.get());
                rebuilt++;
            }
        }
        Optional<ItemStack> offHand = rebuilt(inventory.getItemInOffHand(), snapshot);
        if (offHand.isPresent()) {
            inventory.setItemInOffHand(offHand.get());
            rebuilt++;
        }
        return rebuilt;
    }

    private Optional<ItemStack> rebuilt(ItemStack stack, ConfigSnapshot snapshot) {
        return maker.lootIdOf(stack).flatMap(snapshot::loot).flatMap(loot -> rebuilt(stack, loot));
    }

    /**
     * The summon item rebuilt at the stack's amount, or empty when it is current or cannot be rebuilt.
     */
    private Optional<ItemStack> rebuilt(ItemStack stack, Loot loot) {
        if (!maker.isOutdated(stack, loot)) {
            return Optional.empty();
        }
        try {
            ItemStack fresh = maker.create(loot);
            fresh.setAmount(stack.getAmount());
            return Optional.of(fresh);
        } catch (RuntimeException cannotBuild) {
            failures.accept("could not rebuild the summon item of " + loot.id(), cannotBuild);
            return Optional.empty();
        }
    }
}
