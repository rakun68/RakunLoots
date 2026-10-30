package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance: slot placement holds size, fixed slots, and random free slots over a seeded random, including a clash
 * and a full inventory.
 */
class SlotPlacementTest {

    @BeforeEach
    void setUp() {
        StreamableServerMock.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sizeIsTheSmallestMultipleOfNineHoldingTheHighestFixedSlotAndEveryStackUpTo54() {
        assertEquals(9, SlotPlacement.place("loot", List.of(), new Random(1)).size(), "empty still shows a row");
        assertEquals(9, SlotPlacement.place("loot", loose(9), new Random(1)).size());
        assertEquals(18, SlotPlacement.place("loot", loose(10), new Random(1)).size());
        assertEquals(9, SlotPlacement.place("loot", List.of(fixed(8)), new Random(1)).size());
        assertEquals(18, SlotPlacement.place("loot", List.of(fixed(9)), new Random(1)).size());
        List<RolledStack> fixedHighFewStacks = new ArrayList<>(loose(2));
        fixedHighFewStacks.add(fixed(20));
        assertEquals(27, SlotPlacement.place("loot", fixedHighFewStacks, new Random(1)).size());
        assertEquals(54, SlotPlacement.place("loot", loose(60), new Random(1)).size(), "capped at 54");
        assertEquals(9, SlotPlacement.place("loot", List.of(fixed(70)), new Random(1)).size(),
                "a fixed slot outside the largest inventory does not size it");
    }

    @Test
    void fixedSlotsStayAndOtherStacksGoIntoRandomFreeSlotsOverASeededRandom() {
        List<RolledStack> stacks = new ArrayList<>(loose(5));
        stacks.add(1, fixed(3));
        stacks.add(fixed(17));
        Set<Integer> firstLooseSlots = new HashSet<>();
        Random random = new Random(42);
        Random sameSeed = new Random(42);

        for (int summon = 0; summon < 200; summon++) {
            SlotPlacement.Placed placed = SlotPlacement.place("loot", stacks, random);

            List<Integer> slots = slots(placed);
            assertEquals(18, placed.size());
            assertEquals(7, slots.size());
            assertEquals(3, slots.get(1), "a fixed slot stays");
            assertEquals(17, slots.get(6), "a fixed slot stays");
            assertEquals(7, new HashSet<>(slots).size(), "no two stacks share a slot");
            assertTrue(slots.stream().allMatch(slot -> slot >= 0 && slot < 18), "every slot is inside the size");
            assertEquals(List.of(), placed.warnings());
            assertEquals(slots, slots(SlotPlacement.place("loot", stacks, sameSeed)), "one seed places one way");
            firstLooseSlots.add(slots.get(0));
        }
        Set<Integer> free = new HashSet<>(IntStream.range(0, 18).boxed().toList());
        free.removeAll(Set.of(3, 17));
        assertEquals(free, firstLooseSlots, "a loose stack can land in every free slot");
    }

    @Test
    void aClashingFixedSlotMovesToARandomFreeSlotWithALowWarning() {
        List<RolledStack> stacks = List.of(fixed(4), fixed(4), fixed(60));
        Set<Integer> movedTo = new HashSet<>();
        Random random = new Random(7);

        for (int summon = 0; summon < 100; summon++) {
            SlotPlacement.Placed placed = SlotPlacement.place("loot", stacks, random);

            List<Integer> slots = slots(placed);
            assertEquals(4, slots.get(0), "the earlier stack keeps the slot");
            assertFalse(slots.get(1) == 4);
            assertEquals(3, new HashSet<>(slots).size());
            assertEquals(List.of(OptionalInt.of(4), OptionalInt.of(60)),
                    placed.warnings().stream().map(LifecycleWarning::slot).toList());
            assertTrue(placed.warnings().stream().allMatch(warning -> warning.kind() == LifecycleWarning.Kind.SLOT_MOVED
                    && warning.severity() == me.rakkoon69.rakunloots.warning.Severity.LOW
                    && warning.lootId().orElseThrow().equals("loot")));
            movedTo.add(slots.get(1));
        }
        assertTrue(movedTo.size() > 1, "the moved stack lands in a random free slot: " + movedTo);
    }

    @Test
    void aFullInventoryKeepsFixedSlotsFirstAndLeavesTheLastLooseStacksOutWithAHighWarningEach() {
        List<RolledStack> stacks = new ArrayList<>();
        for (int i = 0; i < 59; i++) {
            stacks.add(new RolledStack(OptionalInt.empty(), new ItemStack(Material.STONE, i + 1)));
        }
        stacks.add(fixed(53));

        SlotPlacement.Placed placed = SlotPlacement.place("loot", stacks, new Random(9));

        assertEquals(54, placed.size());
        List<RolledStack> kept = placed.contents().stacks();
        assertEquals(54, kept.size());
        assertEquals(53, kept.get(53).slot().getAsInt(), "the fixed stack keeps its slot");
        assertEquals(Material.DIAMOND, kept.get(53).item().getType());
        assertEquals(IntStream.rangeClosed(1, 53).boxed().toList(),
                kept.subList(0, 53).stream().map(stack -> stack.item().getAmount()).toList(),
                "the first 53 loose stacks fit, in roll order");
        assertEquals(6, placed.warnings().size());
        assertTrue(placed.warnings().stream().allMatch(warning -> warning.kind() == LifecycleWarning.Kind.NO_ROOM
                && warning.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    private static RolledStack fixed(int slot) {
        return new RolledStack(OptionalInt.of(slot), new ItemStack(Material.DIAMOND));
    }

    private static List<RolledStack> loose(int count) {
        List<RolledStack> stacks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            stacks.add(new RolledStack(OptionalInt.empty(), new ItemStack(Material.STONE, i + 1)));
        }
        return stacks;
    }

    private static List<Integer> slots(SlotPlacement.Placed placed) {
        return placed.contents().stacks().stream().map(stack -> stack.slot().getAsInt()).toList();
    }
}
