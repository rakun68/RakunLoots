package me.rakkoon69.rakunloots.server;

import org.bukkit.inventory.InventoryHolder;

/**
 * Marks a Bukkit inventory as a menu, so the one menu listener claims every click and drag in it. It is deliberately
 * not a {@link SharedInventory}: a menu close never reaches the loot box lifecycle.
 */
public interface MenuHolder extends InventoryHolder {

    /**
     * The menu this holder belongs to.
     */
    MenuHandle menuHandle();
}
