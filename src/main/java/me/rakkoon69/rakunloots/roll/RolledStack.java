package me.rakkoon69.rakunloots.roll;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One built item stack in rolled contents, with an optional slot.
 * The roll sets only fixed slots. Placing the box assigns the rest.
 */
public final class RolledStack {

    private final OptionalInt slot;
    private final ItemStack item;

    public RolledStack(OptionalInt slot, ItemStack item) {
        this.slot = Objects.requireNonNull(slot, "slot");
        this.item = Objects.requireNonNull(item, "item").clone();
    }

    public OptionalInt slot() {
        return slot;
    }

    /**
     * A copy of the stored stack, so callers cannot change these contents.
     */
    public ItemStack item() {
        return item.clone();
    }
}
