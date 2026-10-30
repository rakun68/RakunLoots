package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.roll.RolledContents;
import me.rakkoon69.rakunloots.roll.RolledStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Random;

/**
 * Gives every rolled stack a concrete slot when a loot box is placed.
 *
 * <p>The size is the smallest multiple of 9 that holds the highest fixed slot and every stack, from 9 up to 54.
 * Fixed slots go first, in roll order. A fixed slot that an earlier stack took, or that lies outside 0 to 53, moves to
 * a random free slot with a low warning. Every other stack goes into a random free slot, in roll order. A stack with
 * no free slot left is left out with a high warning.
 */
final class SlotPlacement {

    static final int MAX_SIZE = 54;
    private static final int ROW = 9;

    /**
     * The placed contents, in roll order without the stacks left out, and the size they were placed in.
     */
    record Placed(int size, RolledContents contents, List<LifecycleWarning> warnings) {
    }

    private SlotPlacement() {
    }

    static Placed place(String lootId, List<RolledStack> stacks, Random random) {
        Objects.requireNonNull(lootId, "lootId");
        Objects.requireNonNull(stacks, "stacks");
        Objects.requireNonNull(random, "random");

        int size = size(stacks);
        int[] slots = new int[stacks.size()];
        boolean[] taken = new boolean[size];
        List<LifecycleWarning> warnings = new ArrayList<>();
        List<Integer> loose = new ArrayList<>();

        for (int i = 0; i < stacks.size(); i++) {
            OptionalInt fixed = stacks.get(i).slot();
            if (fixed.isEmpty()) {
                loose.add(i);
                continue;
            }
            int slot = fixed.getAsInt();
            if (slot < 0 || slot >= size || taken[slot]) {
                warnings.add(LifecycleWarning.slotMoved(lootId, slot));
                loose.add(i);
                continue;
            }
            taken[slot] = true;
            slots[i] = slot;
        }
        // Loose stacks keep roll order, so a stack left out for lack of room is always a late one.
        boolean[] leftOut = new boolean[stacks.size()];
        for (int i : loose) {
            List<Integer> free = new ArrayList<>();
            for (int slot = 0; slot < size; slot++) {
                if (!taken[slot]) {
                    free.add(slot);
                }
            }
            if (free.isEmpty()) {
                warnings.add(LifecycleWarning.noRoom(lootId));
                leftOut[i] = true;
                continue;
            }
            int slot = free.get(random.nextInt(free.size()));
            taken[slot] = true;
            slots[i] = slot;
        }

        List<RolledStack> placed = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            if (!leftOut[i]) {
                placed.add(new RolledStack(OptionalInt.of(slots[i]), stacks.get(i).item()));
            }
        }
        return new Placed(size, new RolledContents(placed), warnings);
    }

    /**
     * The smallest multiple of 9 that holds the highest fixed slot below 54 and every stack, from 9 up to 54.
     */
    static int size(List<RolledStack> stacks) {
        int needed = stacks.size();
        for (RolledStack stack : stacks) {
            OptionalInt slot = stack.slot();
            if (slot.isPresent() && slot.getAsInt() < MAX_SIZE) {
                needed = Math.max(needed, slot.getAsInt() + 1);
            }
        }
        int rows = Math.max(1, (needed + ROW - 1) / ROW);
        return Math.min(MAX_SIZE, rows * ROW);
    }
}
