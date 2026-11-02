package me.rakkoon69.rakunloots.config.loot;

import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.roll.LootChance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RawRollChanceTest {

    private static ContentEntry entry(String id, int weight) {
        return new ContentEntry(id, ItemReference.of("", "diamond"), 1, weight, OptionalInt.empty());
    }

    @Test
    void rawEqualWeightsInclusionFormulaRulingThree() {
        // Binding ruling 3:
        // "For raw equal weights n=2, min=0, max=4, inclusion is (0 + 0.5 + 1 + 1 + 1)/5=0.7,
        // because drawing removes the entry (WITHOUT replacement). Add this test; do not clamp raw max to n before averaging."
        Roll roll = new Roll(0, 4, List.of(entry("e1", 10), entry("e2", 10)));
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        assertEquals(2, chances.size());
        assertEquals(0.7, chances.get("e1").probability(), 1e-12);
        assertEquals(0.7, chances.get("e2").probability(), 1e-12);
    }

    @Test
    void rawReversedPairUsesMax() {
        // Raw reversed pair (min >= max) uses max, clamped at 0
        Roll roll = new Roll(4, 2, List.of(entry("e1", 1), entry("e2", 1), entry("e3", 1), entry("e4", 1)));
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        // Effective count is min=2, max=2 -> each drawn with 2/4 = 0.5
        assertEquals(4, chances.size());
        for (LootChance.EntryChance ec : chances.values()) {
            assertEquals(0.5, ec.probability(), 1e-12);
        }
    }

    @Test
    void rawNegativeMinMaxClampedAtZero() {
        Roll roll = new Roll(-5, -2, List.of(entry("e1", 1), entry("e2", 1)));
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        assertEquals(2, chances.size());
        for (LootChance.EntryChance ec : chances.values()) {
            assertEquals(0.0, ec.probability());
        }
    }

    @Test
    void rawZeroAndNegativeWeights() {
        Roll roll = new Roll(1, 1, List.of(entry("always", -5), entry("hidden", 0), entry("draw", 10)));
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        assertEquals(LootChance.Kind.ALWAYS, chances.get("always").kind());
        assertEquals(1.0, chances.get("always").probability());
        assertEquals(LootChance.Kind.HIDDEN, chances.get("hidden").kind());
        assertEquals(0.0, chances.get("hidden").probability());
        assertEquals(LootChance.Kind.DRAWABLE, chances.get("draw").kind());
        assertEquals(1.0, chances.get("draw").probability());
    }
}
