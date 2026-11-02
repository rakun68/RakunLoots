package me.rakkoon69.rakunloots.roll;

import me.rakkoon69.rakunloots.config.loot.RawRollFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance test covers brute-force oracles up to 7 entries only. These cases have closed-form
 * last-place probabilities for large equal-weight groups, including near-n draw counts and the
 * maximum 10^6 weight ratio.
 */
class LootChanceLargeGroupAccuracyTest {

    private static final MathContext MC = new MathContext(60);
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Test
    void weightRatioMillionKeepsTheLightEntry() {
        int heavy = 50;
        double rateRatio = 1e-6;
        double probabilityLast = 1.0;
        for (int j = 1; j <= heavy; j++) {
            probabilityLast *= j / (j + rateRatio);
        }
        double expected = 1.0 - probabilityLast;

        int[] weights = new int[heavy + 1];
        java.util.Arrays.fill(weights, 0, heavy, 1_000_000);
        weights[heavy] = 1;
        double actual = LootChance.calculate(RawRollFixture.roll(heavy, heavy, weights))
                .get("e" + heavy).probability();

        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void firstDrawChancesAreRateShares() {
        int heavy = 99;
        int heavyWeight = 100;
        int[] weights = new int[heavy + 1];
        java.util.Arrays.fill(weights, 0, heavy, heavyWeight);
        weights[heavy] = 1;

        Map<String, LootChance.EntryChance> chances =
                LootChance.calculate(RawRollFixture.roll(1, 1, weights));

        double total = (double) heavy * heavyWeight + 1;
        assertEquals(1.0 / total, chances.get("e" + heavy).probability(), 1e-9);
        assertEquals(heavyWeight / total, chances.get("e0").probability(), 1e-9);
    }

    @Test
    void twoLargeEqualWeightGroupsNearFullDraw() {
        int first = 50;
        int second = 50;
        int draw = first + second - 1;
        int[] weights = new int[first + second];
        java.util.Arrays.fill(weights, 0, first, 1000);
        java.util.Arrays.fill(weights, first, first + second, 1);

        Map<String, LootChance.EntryChance> chances =
                LootChance.calculate(RawRollFixture.roll(draw, draw, weights));

        double expectedFirst = 1.0 - lastProbability(first, 1000, second, 1).doubleValue();
        double expectedSecond = 1.0 - lastProbability(second, 1, first, 1000).doubleValue();
        assertEquals(expectedFirst, chances.get("e0").probability(), 1e-9);
        assertEquals(expectedSecond, chances.get("e" + first).probability(), 1e-9);
    }

    @Test
    void midDrawOracleChecksBothLargeWeightGroups() {
        int heavy = 99;
        int heavyWeight = 2;
        int draw = 49;
        int[] weights = new int[heavy + 1];
        java.util.Arrays.fill(weights, 0, heavy, heavyWeight);
        weights[heavy] = 1;

        Map<String, LootChance.EntryChance> chances =
                LootChance.calculate(RawRollFixture.roll(draw, draw, weights));

        double expectedLight = 1.0 - excludedProbability(heavy, heavyWeight, draw).doubleValue();
        double expectedHeavy = (draw - expectedLight) / heavy;
        assertEquals(expectedLight, chances.get("e" + heavy).probability(), 1e-9);
        assertEquals(expectedHeavy, chances.get("e0").probability(), 1e-9);
    }

    @Test
    void fiveHundredEntriesAtRatioTwoConserveTheDrawCount() {
        int half = 250;
        int[] weights = new int[2 * half];
        java.util.Arrays.fill(weights, 0, half, 1);
        java.util.Arrays.fill(weights, half, 2 * half, 2);

        Map<String, LootChance.EntryChance> chances =
                LootChance.calculate(RawRollFixture.roll(half, half, weights));

        // Positive-transition DP at 100 digits; every transition and addition is positive.
        BigDecimal expectedLight = new BigDecimal(
                "0.3819011757791990872427928223493954386171071878239884694100944106895279080331371995134761188860986619");
        BigDecimal expectedHeavy = new BigDecimal(
                "0.618098824220800912757207177650604561382892812176011530589905589310472091966862800486523881113901338");
        assertEquals(expectedLight.doubleValue(), chances.get("e0").probability(), 1e-9);
        assertEquals(expectedHeavy.doubleValue(), chances.get("e250").probability(), 1e-9);

        double total = 0.0;
        for (LootChance.EntryChance chance : chances.values()) {
            assertEquals(LootChance.Kind.DRAWABLE, chance.kind());
            assertTrue(Double.isFinite(chance.probability()));
            assertTrue(chance.probability() >= 0.0 && chance.probability() <= 1.0);
            total += chance.probability();
        }
        assertEquals(half, total, chances.size() * 1e-9);
    }

    @Test
    void tenThousandEntriesFirstDrawMatchesTheExactWeightShare() {
        int light = 9900;
        int n = 10000;
        int[] weights = new int[n];
        java.util.Arrays.fill(weights, 0, light, 1);
        java.util.Arrays.fill(weights, light, n, 2);

        Map<String, LootChance.EntryChance> chances =
                LootChance.calculate(RawRollFixture.roll(1, 1, weights));

        double totalWeight = light + 2.0 * (n - light);
        double maxError = 0.0;
        for (int i = 0; i < n; i++) {
            double expected = (i < light ? 1.0 : 2.0) / totalWeight;
            maxError = Math.max(maxError,
                    Math.abs(expected - chances.get("e" + i).probability()));
        }
        assertEquals(0.0, maxError, 1e-9, "first draw is the exact weighted share");
    }

    @Test
    void hundredEqualWeightsNearFullDraw() {
        int[] weights = new int[100];
        java.util.Arrays.fill(weights, 1);
        for (LootChance.EntryChance chance
                : LootChance.calculate(RawRollFixture.roll(99, 99, weights)).values()) {
            assertEquals(0.99, chance.probability(), 1e-12);
        }
    }

    /**
     * The chance the light entry is outside the first {@code draw} entries when {@code heavy}
     * entries of weight {@code heavyWeight} race against it. Expand the binomial upper tail and
     * integrate each exponential term exactly.
     */
    private static BigDecimal excludedProbability(int heavy, double heavyWeight, int draw) {
        BigDecimal w = BigDecimal.valueOf(heavyWeight);
        BigDecimal sum = BigDecimal.ZERO;
        for (int r = draw; r <= heavy; r++) {
            BigDecimal heavyWays = combination(heavy, r);
            for (int s = 0; s <= r; s++) {
                BigDecimal denominator = ONE.add(w.multiply(BigDecimal.valueOf(heavy - r + s)));
                BigDecimal term = heavyWays.multiply(combination(r, s)).divide(denominator, MC);
                sum = ((s & 1) == 0) ? sum.add(term) : sum.subtract(term);
            }
        }
        return sum;
    }

    /**
     * The chance an entry from the first group is last when that group has {@code first} entries
     * and the second group has {@code second} entries. Expanding the product of
     * {@code (1 - e^(-w t))} gives an exact alternating sum of rational terms.
     */
    static BigDecimal lastProbability(int first, double firstWeight, int second, double secondWeight) {
        BigDecimal wa = BigDecimal.valueOf(firstWeight);
        BigDecimal wb = BigDecimal.valueOf(secondWeight);
        BigDecimal sum = BigDecimal.ZERO;
        for (int r = 0; r <= first - 1; r++) {
            for (int s = 0; s <= second; s++) {
                BigDecimal denominator = wa.multiply(BigDecimal.valueOf(1L + r))
                        .add(wb.multiply(BigDecimal.valueOf(s)));
                BigDecimal term = combination(first - 1, r)
                        .multiply(combination(second, s))
                        .multiply(wa)
                        .divide(denominator, MC);
                sum = ((r + s) & 1) == 0 ? sum.add(term) : sum.subtract(term);
            }
        }
        return sum;
    }

    private static BigDecimal combination(int n, int k) {
        BigDecimal value = BigDecimal.ONE;
        for (int i = 0; i < k; i++) {
            value = value.multiply(BigDecimal.valueOf(n - i))
                    .divide(BigDecimal.valueOf(i + 1), MC);
        }
        return value;
    }
}
