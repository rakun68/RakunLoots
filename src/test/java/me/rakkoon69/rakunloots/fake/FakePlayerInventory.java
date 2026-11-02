package me.rakkoon69.rakunloots.fake;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * A player's own inventory as the fake server adapter keeps it: 36 storage slots, as the real main inventory and
 * hotbar. Room and giving work as the real server's do: an empty slot holds a full stack, a stack of the same item
 * fills up to the stack size, and an item is split into stacks of that size.
 */
public final class FakePlayerInventory {

    static final int SLOTS = 36;

    private final ItemStack[] slots = new ItemStack[SLOTS];

    int room(ItemStack item) {
        int max = item.getMaxStackSize();
        int room = 0;
        for (ItemStack slot : slots) {
            if (slot == null) {
                room += max;
            } else if (slot.isSimilar(item)) {
                room += Math.max(0, max - slot.getAmount());
            }
        }
        return room;
    }

    /**
     * Fills the same item first, then empty slots; returns how many went in.
     */
    int give(ItemStack item, int amount) {
        int max = item.getMaxStackSize();
        int left = amount;
        for (int i = 0; i < SLOTS && left > 0; i++) {
            if (slots[i] != null && slots[i].isSimilar(item) && slots[i].getAmount() < max) {
                int moved = Math.min(left, max - slots[i].getAmount());
                slots[i].setAmount(slots[i].getAmount() + moved);
                left -= moved;
            }
        }
        for (int i = 0; i < SLOTS && left > 0; i++) {
            if (slots[i] == null) {
                ItemStack stack = item.clone();
                int moved = Math.min(left, max);
                stack.setAmount(moved);
                slots[i] = stack;
                left -= moved;
            }
        }
        return amount - left;
    }

    /**
     * Fills every slot with a full stack of the item.
     */
    public void fillWith(ItemStack item) {
        Objects.requireNonNull(item, "item");
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = item.clone();
            stack.setAmount(stack.getMaxStackSize());
            slots[i] = stack;
        }
    }

    /**
     * Fills all but the given number of storage slots with a full stack of the item.
     */
    public void fillAllButSlots(ItemStack item, int freeSlots) {
        fillWith(item);
        for (int i = 0; i < freeSlots; i++) {
            slots[i] = null;
        }
    }

    /**
     * How many of the item the inventory holds.
     */
    public int count(ItemStack item) {
        int total = 0;
        for (ItemStack slot : slots) {
            if (slot != null && slot.isSimilar(item)) {
                total += slot.getAmount();
            }
        }
        return total;
    }

    /**
     * How many items of a material the inventory holds, whatever their meta, such as a marked summon item.
     */
    public int countOfType(org.bukkit.Material material) {
        int total = 0;
        for (ItemStack slot : slots) {
            if (slot != null && slot.getType() == material) {
                total += slot.getAmount();
            }
        }
        return total;
    }

    /**
     * The first stack of a material, whatever its meta, or null when there is none.
     */
    public ItemStack firstOfType(org.bukkit.Material material) {
        for (ItemStack slot : slots) {
            if (slot != null && slot.getType() == material) {
                return slot;
            }
        }
        return null;
    }

    /**
     * The amount held in each stack of the item, in slot order.
     */
    public java.util.List<Integer> stackAmounts(ItemStack item) {
        java.util.List<Integer> amounts = new java.util.ArrayList<>();
        for (ItemStack slot : slots) {
            if (slot != null && slot.isSimilar(item)) {
                amounts.add(slot.getAmount());
            }
        }
        return amounts;
    }
}
