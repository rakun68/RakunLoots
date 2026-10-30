package me.rakkoon69.rakunloots.server;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;


/**
 * Reads and fills a player's own inventory for the Paper, Spigot, and Folia adapters. Call each on the thread that
 * owns the player. Only the storage slots count: the main inventory and the hotbar, never armor or the off hand.
 */
final class BukkitInventories {

    private BukkitInventories() {
    }

    /**
     * How many of the item fit now: each empty slot holds a full stack, and each slot of the same item fills up to
     * the stack size.
     */
    static int room(Player player, ItemStack item) {
        int max = item.getMaxStackSize();
        long room = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                room += max;
            } else if (slot.isSimilar(item)) {
                room += Math.max(0, max - slot.getAmount());
            }
        }
        return (int) Math.min(room, Integer.MAX_VALUE);
    }

    /**
     * Puts the amount of the item into the player's storage slots, into stacks of the same item first and then into
     * empty slots, from the first slot on. What does not fit stays out of the world. Only the storage slots are
     * written, one slot at a time, so armor and the off hand are never touched.
     *
     * @return how many went in
     */
    static int give(Player player, ItemStack item, int amount) {
        int max = item.getMaxStackSize();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] slots = inventory.getStorageContents();
        int left = amount;
        for (int slot = 0; slot < slots.length && left > 0; slot++) {
            ItemStack held = slots[slot];
            if (held != null && !held.getType().isAir() && held.isSimilar(item) && held.getAmount() < max) {
                int moved = Math.min(left, max - held.getAmount());
                held.setAmount(held.getAmount() + moved);
                left -= moved;
            }
        }
        for (int slot = 0; slot < slots.length && left > 0; slot++) {
            if (slots[slot] == null || slots[slot].getType().isAir()) {
                ItemStack stack = item.clone();
                stack.setAmount(Math.min(left, max));
                slots[slot] = stack;
                left -= stack.getAmount();
            }
        }
        for (int slot = 0; slot < slots.length; slot++) {
            inventory.setItem(slot, slots[slot]);
        }
        return amount - left;
    }
}
