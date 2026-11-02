package me.rakkoon69.rakunloots.roll;

import me.rakkoon69.rakunloots.config.loot.ContentEntry;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.config.loot.Roll;
import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.items.ItemWarning;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

/**
 * Rolls one loot into rolled contents. Takes no server and does not write to the log.
 *
 * <p>The count is uniform in [min, max], capped by the drawable entries (weight above zero).
 * Draws are weighted and without repeats. An entry with a weight below zero is always included,
 * outside the count. Weight zero is never drawn. Each included entry resolves through the item
 * source registry and becomes stacks of at most the item's max stack size; a fixed slot goes on
 * the first stack only. A miss is left out with a high warning, and no replacement is drawn.
 * Stacks follow the loot's entry order.
 */
public final class LootRoller {

    private LootRoller() {
    }

    public static RollResult roll(Loot loot, ItemSourceRegistry registry, Random random) {
        Objects.requireNonNull(loot, "loot");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(random, "random");

        Roll roll = loot.roll();
        Set<ContentEntry> included = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ContentEntry> pool = new ArrayList<>();
        for (ContentEntry entry : roll.entries()) {
            if (entry.weight() < 0) {
                included.add(entry);
            } else if (entry.weight() > 0) {
                pool.add(entry);
            }
        }

        int count = Math.min(drawCount(roll, random), pool.size());
        for (int i = 0; i < count; i++) {
            included.add(pool.remove(drawIndex(pool, random)));
        }

        List<RolledStack> stacks = new ArrayList<>();
        List<RollWarning> warnings = new ArrayList<>();
        for (ContentEntry entry : roll.entries()) {
            if (!included.contains(entry)) {
                continue;
            }
            ItemResolveResult resolved = registry.resolve(entry.item());
            if (resolved.isMiss()) {
                // A miss always carries exactly one warning. No replacement is drawn.
                warnings.add(RollWarning.missedEntry(loot.id(), entry.id(), resolved.warnings().get(0)));
                continue;
            }
            for (ItemWarning warning : resolved.warnings()) {
                warnings.add(RollWarning.itemWarning(loot.id(), entry.id(), warning));
            }
            addSplit(stacks, entry, resolved.item().orElseThrow());
        }
        return new RollResult(new RolledContents(stacks), warnings);
    }

    /**
     * Adds the entry's amount as stacks of at most the item's max stack size.
     * A fixed slot goes on the first stack only.
     */
    private static void addSplit(List<RolledStack> stacks, ContentEntry entry, ItemStack item) {
        int maxStackSize = Math.max(1, item.getMaxStackSize());
        int remaining = entry.amount();
        OptionalInt slot = entry.slot();
        while (remaining > 0) {
            int size = Math.min(remaining, maxStackSize);
            ItemStack stack = item.clone();
            stack.setAmount(size);
            stacks.add(new RolledStack(slot, stack));
            slot = OptionalInt.empty();
            remaining -= size;
        }
    }

    /**
     * Uniform in [min, max]. The loader keeps min at or below max; a reversed pair uses max.
     */
    private static int drawCount(Roll roll, Random random) {
        int min = Math.max(0, roll.min());
        int max = Math.max(0, roll.max());
        if (min >= max) {
            return max;
        }
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Weighted pick over a pool of entries whose weights are all above zero.
     */
    private static int drawIndex(List<ContentEntry> pool, Random random) {
        long total = 0;
        for (ContentEntry entry : pool) {
            total += entry.weight();
        }
        long ticket = random.nextLong(total);
        for (int i = 0; i < pool.size(); i++) {
            ticket -= pool.get(i).weight();
            if (ticket < 0) {
                return i;
            }
        }
        return pool.size() - 1;
    }
}
