package me.rakkoon69.rakunloots.claim;

import me.rakkoon69.rakunloots.config.Loot;
import org.bukkit.inventory.ItemStack;

/**
 * Makes a loot's summon item from the loot as it is now, so an edit of the loot reaches items already owed. Called on
 * the async scheduler.
 */
@FunctionalInterface
public interface SummonItemFactory {

    /**
     * One summon item of the loot. The caller sets how many to give; the amount of the returned stack is ignored.
     */
    ItemStack create(Loot loot);
}
