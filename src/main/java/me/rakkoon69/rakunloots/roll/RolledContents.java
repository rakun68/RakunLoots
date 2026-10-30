package me.rakkoon69.rakunloots.roll;

import java.util.List;
import java.util.Objects;

/**
 * The items one loot box received from its roll: an ordered list of stacks, each with an optional slot.
 * A restart keeps them. It never rolls again.
 */
public final class RolledContents {

    private final List<RolledStack> stacks;

    public RolledContents(List<RolledStack> stacks) {
        this.stacks = List.copyOf(Objects.requireNonNull(stacks, "stacks"));
    }

    public List<RolledStack> stacks() {
        return stacks;
    }
}
