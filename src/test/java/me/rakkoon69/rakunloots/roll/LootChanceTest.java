package me.rakkoon69.rakunloots.roll;

import me.rakkoon69.rakunloots.config.RawRollFixture;
import me.rakkoon69.rakunloots.config.Roll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootChanceTest {

    /**
     * A loot of 100 entries with 100 distinct weights and max 50 finishes in under 50 ms.
     * Measured 23 ms on the test machine: three warm-up runs, then one measured run.
     */
    @Test
    void performance100Entries100DistinctWeightsMax50() {
        int[] weights = new int[100];
        for (int i = 0; i < 100; i++) {
            weights[i] = i + 1;
        }
        Roll roll = RawRollFixture.roll(0, 50, weights);

        // Warm up JIT
        for (int i = 0; i < 3; i++) {
            LootChance.calculate(roll);
        }

        long start = System.nanoTime();
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(100, chances.size());
        assertTrue(elapsedMs < 50, "Expected under 50 ms but was " + elapsedMs + " ms");
    }

    @Test
    void equalWeightsFixedKMatchesFraction() {
        // n entries of weight 1 with fixed k gives each entry exactly k/n within 1e-12
        int n = 5;
        int k = 2;
        int[] weights = new int[n];
        for (int i = 0; i < n; i++) {
            weights[i] = 1;
        }
        Roll roll = RawRollFixture.roll(k, k, weights);
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        assertEquals(n, chances.size());
        double expected = (double) k / n;
        for (LootChance.EntryChance chance : chances.values()) {
            assertEquals(LootChance.Kind.DRAWABLE, chance.kind());
            assertEquals(expected, chance.probability(), 1e-12);
        }

        // Another check: n = 10, k = 3
        int n2 = 10;
        int k2 = 3;
        int[] weights2 = new int[n2];
        for (int i = 0; i < n2; i++) {
            weights2[i] = 1;
        }
        Roll roll2 = RawRollFixture.roll(k2, k2, weights2);
        Map<String, LootChance.EntryChance> chances2 = LootChance.calculate(roll2);
        double expected2 = (double) k2 / n2;
        for (LootChance.EntryChance chance : chances2.values()) {
            assertEquals(expected2, chance.probability(), 1e-12);
        }
    }

    @Test
    void groupedWeightsProduceIdenticalChances() {
        // Entries with identical weights among distinct weights have identical chances
        Roll roll = RawRollFixture.roll(2, 3, 10, 10, 20, 20, 50);
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        assertEquals(5, chances.size());
        assertEquals(chances.get("e0").probability(), chances.get("e1").probability(), 1e-12);
        assertEquals(chances.get("e2").probability(), chances.get("e3").probability(), 1e-12);
        assertTrue(chances.get("e0").probability() < chances.get("e2").probability());
        assertTrue(chances.get("e2").probability() < chances.get("e4").probability());
    }

    @Test
    void exactEdgesAndSpecialEntries() {
        // min >= n returns exactly 1.0 (assert ==, not tolerance)
        Roll rollAll = RawRollFixture.roll(3, 4, 1, 2, 3);
        Map<String, LootChance.EntryChance> allChances = LootChance.calculate(rollAll);
        for (LootChance.EntryChance ec : allChances.values()) {
            assertEquals(1.0, ec.probability());
        }

        // max = 0 returns exactly 0.0 (assert ==, not tolerance)
        Roll rollNone = RawRollFixture.roll(0, 0, 1, 2, 3);
        Map<String, LootChance.EntryChance> noneChances = LootChance.calculate(rollNone);
        for (LootChance.EntryChance ec : noneChances.values()) {
            assertEquals(0.0, ec.probability());
        }

        // One drawable entry with min 0 and max 1 gives 0.5. With min 1 it gives exactly 1.0.
        Roll rollSingleMin0 = RawRollFixture.roll(0, 1, 5);
        assertEquals(0.5, LootChance.calculate(rollSingleMin0).get("e0").probability(), 1e-12);

        Roll rollSingleMin1 = RawRollFixture.roll(1, 1, 5);
        assertEquals(1.0, LootChance.calculate(rollSingleMin1).get("e0").probability());

        // A weight below zero returns always, and weight zero returns hidden, whatever the count
        Roll rollSpecial0 = RawRollFixture.roll(0, 0, -1, 0, 10);
        Map<String, LootChance.EntryChance> special0 = LootChance.calculate(rollSpecial0);
        assertEquals(LootChance.Kind.ALWAYS, special0.get("e0").kind());
        assertEquals(1.0, special0.get("e0").probability());
        assertEquals(LootChance.Kind.HIDDEN, special0.get("e1").kind());
        assertEquals(0.0, special0.get("e1").probability());
        assertEquals(0.0, special0.get("e2").probability());

        Roll rollSpecialHigh = RawRollFixture.roll(5, 5, -1, 0, 10);
        Map<String, LootChance.EntryChance> specialHigh = LootChance.calculate(rollSpecialHigh);
        assertEquals(LootChance.Kind.ALWAYS, specialHigh.get("e0").kind());
        assertEquals(1.0, specialHigh.get("e0").probability());
        assertEquals(LootChance.Kind.HIDDEN, specialHigh.get("e1").kind());
        assertEquals(0.0, specialHigh.get("e1").probability());
        assertEquals(1.0, specialHigh.get("e2").probability());
    }
}
