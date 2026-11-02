package me.rakkoon69.rakunloots.config.loot;

import me.rakkoon69.rakunloots.roll.LootChance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Raw roll ranges are averaged before each count is capped at the drawable entry count. These cases
 * keep always-included and hidden entries beside distinct weights and a max above the entry count.
 */
class RawRollExtremeChanceTest {

    @Test
    @Timeout(5)
    void rawIntegerMaxFoldsTheCappedTailWithoutIteratingToMax() {
        Roll roll = RawRollFixture.roll(0, Integer.MAX_VALUE, -5, 0, 1, 2, 3, 4);
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        assertEquals(LootChance.Kind.ALWAYS, chances.get("e0").kind());
        assertEquals(1.0, chances.get("e0").probability());
        assertEquals(LootChance.Kind.HIDDEN, chances.get("e1").kind());
        assertEquals(0.0, chances.get("e1").probability());

        int[] drawable = {1, 2, 3, 4};
        long numK = (long) Integer.MAX_VALUE + 1L;
        double[] expected = new double[drawable.length];
        for (int k = 0; k < drawable.length; k++) {
            double[] inclusion = fixedCountInclusion(drawable, k);
            for (int i = 0; i < drawable.length; i++) {
                expected[i] += inclusion[i] / numK;
            }
        }
        double cappedTailMass = (numK - drawable.length) / (double) numK;
        for (int i = 0; i < drawable.length; i++) {
            expected[i] += cappedTailMass;
            assertEquals(expected[i], chances.get("e" + (i + 2)).probability(), 1e-9);
        }
    }

    @Test
    void rawMaxAboveEntryCountAveragesTheCappedCounts() {
        Roll roll = RawRollFixture.roll(0, 8, -5, 0, 1, 2, 3);
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(roll);

        assertEquals(LootChance.Kind.ALWAYS, chances.get("e0").kind());
        assertEquals(1.0, chances.get("e0").probability());
        assertEquals(LootChance.Kind.HIDDEN, chances.get("e1").kind());
        assertEquals(0.0, chances.get("e1").probability());

        int[] drawable = {1, 2, 3};
        double[] expected = new double[drawable.length];
        for (int k = 0; k < drawable.length; k++) {
            double[] inclusion = fixedCountInclusion(drawable, k);
            for (int i = 0; i < drawable.length; i++) {
                expected[i] += inclusion[i] / 9.0;
            }
        }
        for (int i = 0; i < drawable.length; i++) {
            expected[i] += 6.0 / 9.0;
            assertEquals(expected[i], chances.get("e" + (i + 2)).probability(), 1e-9);
        }
    }

    @Test
    void rawRollTenThousandTwoDrawsMatchesClosedFormInclusion() {
        int light = 9999;
        int n = 10000;
        int[] weights = new int[n];
        java.util.Arrays.fill(weights, 0, light, 1);
        weights[light] = 2;

        Map<String, LootChance.EntryChance> chances =
                LootChance.calculate(RawRollFixture.roll(2, 2, weights));

        double totalWeight = light + 2.0;
        double expectedLight = 1.0 / totalWeight
                + (light - 1.0) / totalWeight * (1.0 / (totalWeight - 1.0))
                + 2.0 / totalWeight * (1.0 / (totalWeight - 2.0));
        double expectedHeavy = 2.0 / totalWeight
                + light / totalWeight * (2.0 / (totalWeight - 1.0));

        for (int i = 0; i < light; i++) {
            assertEquals(expectedLight, chances.get("e" + i).probability(), 1e-9,
                    "light entry " + i);
        }
        assertEquals(expectedHeavy, chances.get("e" + light).probability(), 1e-9, "heavy entry");
    }

    private static double[] fixedCountInclusion(int[] weights, int count) {
        double[] result = new double[weights.length];
        enumerate(weights, new boolean[weights.length], count, 1.0, result);
        return result;
    }

    private static void enumerate(int[] weights, boolean[] picked, int remaining,
                                  double probability, double[] result) {
        if (remaining == 0) {
            for (int i = 0; i < weights.length; i++) {
                if (picked[i]) {
                    result[i] += probability;
                }
            }
            return;
        }
        long total = 0;
        for (int i = 0; i < weights.length; i++) {
            if (!picked[i]) {
                total += weights[i];
            }
        }
        for (int i = 0; i < weights.length; i++) {
            if (!picked[i]) {
                picked[i] = true;
                enumerate(weights, picked, remaining - 1, probability * weights[i] / total, result);
                picked[i] = false;
            }
        }
    }
}
