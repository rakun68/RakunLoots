package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.items.ItemReference;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One item reference in a roll, with an amount and a weight.
 * A weight below zero is always included, outside the roll's count.
 * A slot, when set, fixes where it sits.
 */
public final class ContentEntry {

    private final String id;
    private final ItemReference item;
    private final int amount;
    private final int weight;
    private final OptionalInt slot;

    ContentEntry(String id, ItemReference item, int amount, int weight, OptionalInt slot) {
        this.id = Objects.requireNonNull(id, "id");
        this.item = Objects.requireNonNull(item, "item");
        this.amount = amount;
        this.weight = weight;
        this.slot = Objects.requireNonNull(slot, "slot");
    }

    public String id() {
        return id;
    }

    public ItemReference item() {
        return item;
    }

    public int amount() {
        return amount;
    }

    public int weight() {
        return weight;
    }

    public OptionalInt slot() {
        return slot;
    }
}
