package me.rakkoon69.rakunloots.roll;

import me.rakkoon69.rakunloots.config.ContentEntry;
import me.rakkoon69.rakunloots.config.Roll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates the exact chance that each content entry of a loot lands in one loot box.
 * Takes no server, no Bukkit types, no randomness, and writes no logs.
 *
 * <p>Uses the method of ADR 0018:
 * A weighted draw without replacement orders entries according to independent exponential clocks
 * with rates equal to their weights. The probability that entry {@code i} is among the first {@code k}
 * drawn entries is given by:
 * <pre>
 *   P(i in first k) = ∫ w_i * e^(-w_i * t) * P(fewer than k others rang by t) dt,  for t in [0, ∞)
 * </pre>
 * By time {@code t}, entry {@code j} has rung with probability {@code p_j(t) = 1 - e^(-w_j * t)}.
 *
 * <p><b>Quadrature and Grid:</b>
 * The integral is evaluated using a substitution {@code x = ln(t)}, so {@code t = exp(x)} and {@code dt = t dx}.
 * The integration domain is bounded by {@code x_min = -ln(max(w_norm)) - C} and {@code x_max = ln(45)},
 * where weights are normalized by the minimum positive weight and
 * {@code C = max(14, (2 ln n + ln(1e11 / 6)) / 3)} for {@code n} drawable entries.
 * At {@code t_min = exp(x_min)}, the lower tail is handled explicitly instead of dropped. For a
 * draw count of one, fewer than one other entry ringing means no other entry rang, so the lower tail
 * is exact: {@code (w_i / W) * (1 - e^(-W t_min))}, where {@code W} is the total normalized weight.
 * For a draw count of at least two, the code adds the unconditional self-ring marginal
 * {@code 1 - e^(-w_i t_min)}, ignoring the other clocks. That includes the exactly-one-other mass;
 * the overcount is only the event that at least {@code k} other clocks have rung. Since
 * {@code k >= 2}, this is contained in the event that at least two others rang:
 * {@code P(>= 2 others) <= sum_{a<b} p_a p_b <= (sum_{j != i} w_j)^2 t^2 / 2}. Integrating the
 * overcount over {@code [0, t_min]} gives at most
 * {@code w_i (sum_{j != i} w_j)^2 t_min^3 / 6 <= n^2 e^(-3C) / 6 <= 1e-11}. The count mixture
 * preserves that bound. Thus only the {@code k = 1} first-draw case has no lower-tail bias; the
 * {@code k >= 2} tail has this bound, and the total quadrature error still remains.
 * For {@code t > 45}, {@code e^(-t) < 3e-20}, so the upper tail is negligible.
 * The quadrature uses composite Simpson's rule, starting at 200 subintervals and doubling until every
 * distinct-weight group's change between two levels is at most {@code 15 * 9.0e-10}; that is, the
 * Richardson estimate of the remaining absolute error is at most {@code 9.0e-10}. The grid is not
 * capped; its size depends on the accuracy the input needs. The absolute-error budget is therefore:
 * lower-tail truncation at most {@code 1e-11}, upper tail at most {@code 3e-20}, quadrature at most
 * {@code 9e-10}, and floating-point roundoff the remainder. The exact-oracle tests at 500 and 10,000
 * entries verify that the combined per-entry error stays below 1e-9.
 *
 * <p><b>Efficiency:</b>
 * - Equal weights are grouped as binomial steps during Poisson-binomial distribution convolution.
 * - The full grouped convolution and the removal of one entry per distinct weight cost at most
 *   {@code O(n^2 + g*n)} per quadrature point, where {@code g} is the number of distinct weights.
 *   The total also depends on the grid the accuracy bound needs.
 * - Removal runs forward when {@code p_i <= 1/2} and backward above, dividing by the larger term to
 *   keep the calculation stable. The forward branch sums {@code P(S_{-i} < k)} and the backward
 *   branch sums {@code 1 - P(S_{-i} >= k)}, so each branch uses the tail it built.
 * - Every {@code k} in {@code [min, max]} is averaged from the same coefficients in one quadrature pass.
 */
public final class LootChance {

    public enum Kind {
        ALWAYS,
        DRAWABLE,
        HIDDEN
    }

    public record EntryChance(Kind kind, double probability) {
    }

    private static final int INITIAL_QUADRATURE_STEPS = 200; // Even number for Simpson's rule
    private static final double QUADRATURE_TOLERANCE = 9.0e-10;

    private LootChance() {
    }

    public static Map<String, EntryChance> calculate(Roll roll) {
        Objects.requireNonNull(roll, "roll");

        List<ContentEntry> entries = roll.entries();
        List<ContentEntry> drawable = new ArrayList<>();
        Map<String, EntryChance> result = new LinkedHashMap<>();

        for (ContentEntry entry : entries) {
            if (entry.weight() < 0) {
                result.put(entry.id(), new EntryChance(Kind.ALWAYS, 1.0));
            } else if (entry.weight() == 0) {
                result.put(entry.id(), new EntryChance(Kind.HIDDEN, 0.0));
            } else {
                drawable.add(entry);
            }
        }

        if (drawable.isEmpty()) {
            return Collections.unmodifiableMap(result);
        }

        int n = drawable.size();
        int min = Math.max(0, roll.min());
        int max = Math.max(0, roll.max());
        if (min >= max) {
            min = max;
        }

        // Capped draw counts: each k is capped at n
        // Check exact edges
        if (max == 0) {
            for (ContentEntry entry : drawable) {
                result.put(entry.id(), new EntryChance(Kind.DRAWABLE, 0.0));
            }
            return Collections.unmodifiableMap(result);
        }
        if (min >= n) {
            for (ContentEntry entry : drawable) {
                result.put(entry.id(), new EntryChance(Kind.DRAWABLE, 1.0));
            }
            return Collections.unmodifiableMap(result);
        }

        // Weights of drawable entries
        double[] weights = new double[n];
        double minWeight = Double.POSITIVE_INFINITY;
        double maxWeight = 0.0;
        for (int i = 0; i < n; i++) {
            double w = drawable.get(i).weight();
            weights[i] = w;
            if (w < minWeight) {
                minWeight = w;
            }
            if (w > maxWeight) {
                maxWeight = w;
            }
        }

        // Distinct weight groups for binomial convolution
        Map<Double, Integer> weightCounts = new LinkedHashMap<>();
        for (double w : weights) {
            weightCounts.merge(w, 1, Integer::sum);
        }
        double[] distinctWeights = new double[weightCounts.size()];
        int[] distinctCounts = new int[weightCounts.size()];
        Map<Double, Integer> weightToGroup = new LinkedHashMap<>();
        int idx = 0;
        for (Map.Entry<Double, Integer> e : weightCounts.entrySet()) {
            distinctWeights[idx] = e.getKey();
            distinctCounts[idx] = e.getValue();
            weightToGroup.put(e.getKey(), idx);
            idx++;
        }
        int[] entryGroup = new int[n];
        for (int i = 0; i < n; i++) {
            entryGroup[i] = weightToGroup.get(weights[i]);
        }

        // Count distribution of draws: count_weights[k] is the probability of draw count k
        // for k in 0..n
        double[] countProb = new double[n + 1];
        long numK = (long) max - (long) min + 1L;
        double uniformWeight = 1.0 / numK;
        long individualEnd = Math.min((long) max, (long) n);
        for (long k = min; k <= individualEnd; k++) {
            countProb[(int) k] += uniformWeight;
        }
        if ((long) max > (long) n) {
            long cappedTail = (long) max - (long) n;
            countProb[n] += cappedTail * uniformWeight;
        }

        // If after capping all counts are n, all drawable entries are 1.0
        if (countProb[n] == 1.0) {
            for (ContentEntry entry : drawable) {
                result.put(entry.id(), new EntryChance(Kind.DRAWABLE, 1.0));
            }
            return Collections.unmodifiableMap(result);
        }

        // If all weights are equal, every permutation is equally likely: E[k/n]
        if (minWeight == maxWeight) {
            double avg = 0.0;
            for (int k = 1; k <= n; k++) {
                avg += countProb[k] * ((double) k / n);
            }
            for (ContentEntry entry : drawable) {
                result.put(entry.id(), new EntryChance(Kind.DRAWABLE, avg));
            }
            return Collections.unmodifiableMap(result);
        }

        // Normalization
        double[] normWeights = new double[n];
        for (int i = 0; i < n; i++) {
            normWeights[i] = weights[i] / minWeight;
        }
        double[] normDistinctWeights = new double[distinctWeights.length];
        for (int i = 0; i < distinctWeights.length; i++) {
            normDistinctWeights[i] = distinctWeights[i] / minWeight;
        }
        double maxNormWeight = maxWeight / minWeight;

        // Integration setup. C grows with n so the neglected k >= 2 lower tail stays at most 1e-11.
        double lowerTailDecades = Math.max(14.0,
                (2.0 * Math.log(n) + Math.log(1.0e11 / 6.0)) / 3.0);
        double xMin = -Math.log(maxNormWeight) - lowerTailDecades;
        double xMax = Math.log(45.0);
        double tMin = Math.exp(xMin);

        int minK = Math.max(1, Math.min(min, n));
        int maxK = Math.min(max, n);
        double[] logFactorial = new double[n + 1];
        for (int i = 1; i <= n; i++) {
            logFactorial[i] = logFactorial[i - 1] + Math.log(i);
        }

        PointEvaluator evaluator = new PointEvaluator(n, minK, maxK, countProb,
                normDistinctWeights, distinctCounts, logFactorial);
        double[] groupProbSum = integrate(evaluator, distinctWeights.length, xMin, xMax);

        // Lower tail: k = 0 contributes nothing. For k = 1, fewer than one other ringing means no
        // other rang, so the exact shared rate is the total normalized weight W. For k >= 2, add the
        // unconditional self-ring marginal 1 - exp(-w_i*tMin), ignoring the other clocks. It includes
        // the exactly-one-other mass; the overcount is the >= k-others event, bounded by the pair and
        // integral argument in the class comment.
        double probKPositive = 1.0 - countProb[0];
        if (probKPositive > 0) {
            double totalNormWeight = 0.0;
            for (int g = 0; g < distinctWeights.length; g++) {
                totalNormWeight += normDistinctWeights[g] * distinctCounts[g];
            }
            double probKOne = countProb[1];
            double probKAtLeastTwo = probKPositive - probKOne;
            if (probKAtLeastTwo < 0.0) {
                probKAtLeastTwo = 0.0;
            }
            double kOneTail = -Math.expm1(-totalNormWeight * tMin);
            for (int g = 0; g < distinctWeights.length; g++) {
                double w = normDistinctWeights[g];
                groupProbSum[g] += probKOne * (w / totalNormWeight) * kOneTail
                        + probKAtLeastTwo * (-Math.expm1(-w * tMin));
            }
        }

        for (int i = 0; i < n; i++) {
            double prob = groupProbSum[entryGroup[i]];
            if (prob < 0.0) {
                prob = 0.0;
            } else if (prob > 1.0) {
                prob = 1.0;
            }
            result.put(drawable.get(i).id(), new EntryChance(Kind.DRAWABLE, prob));
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Evaluates the integrand for every distinct weight group at one time.
     */
    private static final class PointEvaluator {
        private final int n;
        private final int minK;
        private final int maxK;
        private final double[] countProb;
        private final double[] normDistinctWeights;
        private final int[] distinctCounts;
        private final double[] logFactorial;
        private final double[] D;
        private final double[] newD;
        private final double[] pGroup;
        private final double[] qGroup;

        private PointEvaluator(int n, int minK, int maxK, double[] countProb,
                               double[] normDistinctWeights, int[] distinctCounts,
                               double[] logFactorial) {
            this.n = n;
            this.minK = minK;
            this.maxK = maxK;
            this.countProb = countProb;
            this.normDistinctWeights = normDistinctWeights;
            this.distinctCounts = distinctCounts;
            this.logFactorial = logFactorial;
            this.D = new double[n + 1];
            this.newD = new double[n + 1];
            int groups = normDistinctWeights.length;
            this.pGroup = new double[groups];
            this.qGroup = new double[groups];
        }

        private void evaluate(double x, double[] out) {
            double t = Math.exp(x);

            // Build total count distribution D using distinct weight groups
            D[0] = 1.0;
            int curDegree = 0;
            for (int g = 0; g < normDistinctWeights.length; g++) {
                double w = normDistinctWeights[g];
                int count = distinctCounts[g];
                double expTerm = Math.exp(-w * t);
                double p = 1.0 - expTerm;
                double q = expTerm;
                pGroup[g] = p;
                qGroup[g] = q;

                if (count == 1) {
                    D[curDegree + 1] = D[curDegree] * p;
                    for (int m = curDegree; m >= 1; m--) {
                        D[m] = D[m] * q + D[m - 1] * p;
                    }
                    D[0] = D[0] * q;
                    curDegree++;
                } else {
                    double[] bPoly = binomial(count, p, q, logFactorial);
                    int nextDegree = curDegree + count;
                    for (int m = 0; m <= nextDegree; m++) {
                        newD[m] = 0.0;
                    }
                    for (int i = 0; i <= curDegree; i++) {
                        double di = D[i];
                        if (di != 0.0) {
                            for (int j = 0; j <= count; j++) {
                                newD[i + j] += di * bPoly[j];
                            }
                        }
                    }
                    System.arraycopy(newD, 0, D, 0, nextDegree + 1);
                    curDegree = nextDegree;
                }
            }

            // For each distinct weight group g, remove g from D
            for (int g = 0; g < normDistinctWeights.length; g++) {
                double pi = pGroup[g];
                double qi = qGroup[g];
                double wi = normDistinctWeights[g];

                double probLessSum = 0.0;
                if (pi <= 0.5) {
                    // Forward deconvolution: P(S_{-i} = m) for m = 0..maxK-1
                    double previous = D[0] / qi;
                    double currentPrefix = previous;
                    if (minK <= 1 && countProb[1] > 0) {
                        probLessSum += countProb[1] * currentPrefix;
                    }
                    for (int k = 2; k <= maxK; k++) {
                        previous = (D[k - 1] - pi * previous) / qi;
                        currentPrefix += previous;
                        if (k >= minK && countProb[k] > 0) {
                            probLessSum += countProb[k] * currentPrefix;
                        }
                    }
                } else {
                    // Backward deconvolution: P(S_{-i} = m) for m = n-1 down to minK
                    double previous = D[n] / pi;
                    double currentSuffix = previous;
                    if (maxK == n && countProb[n] > 0) {
                        probLessSum += countProb[n];
                    }
                    if (minK <= n - 1 && maxK >= n - 1 && countProb[n - 1] > 0) {
                        probLessSum += countProb[n - 1] * (1.0 - currentSuffix);
                    }
                    for (int k = n - 2; k >= minK; k--) {
                        previous = (D[k + 1] - qi * previous) / pi;
                        currentSuffix += previous;
                        if (countProb[k] > 0) {
                            probLessSum += countProb[k] * (1.0 - currentSuffix);
                        }
                    }
                }

                out[g] = wi * qi * probLessSum * t;
            }
        }
    }

    /**
     * Integrates every group over the bounded domain with composite Simpson's rule, doubling the grid
     * until the Richardson error estimate for every group is at most {@link #QUADRATURE_TOLERANCE}.
     */
    private static double[] integrate(PointEvaluator evaluator, int groups, double xMin, double xMax) {
        int steps = INITIAL_QUADRATURE_STEPS;
        double h = (xMax - xMin) / steps;
        double[] values = new double[(steps + 1) * groups];
        double[] point = new double[groups];
        for (int i = 0; i <= steps; i++) {
            evaluator.evaluate(xMin + i * h, point);
            System.arraycopy(point, 0, values, i * groups, groups);
        }
        double[] integral = simpson(values, steps, groups, h);

        while (true) {
            int nextSteps = steps * 2;
            double nextH = h / 2.0;
            double[] nextValues = new double[(nextSteps + 1) * groups];
            for (int i = 0; i <= steps; i++) {
                System.arraycopy(values, i * groups, nextValues, 2 * i * groups, groups);
            }
            for (int i = 1; i < nextSteps; i += 2) {
                evaluator.evaluate(xMin + i * nextH, point);
                System.arraycopy(point, 0, nextValues, i * groups, groups);
            }
            double[] nextIntegral = simpson(nextValues, nextSteps, groups, nextH);

            boolean converged = true;
            for (int g = 0; g < groups; g++) {
                if (Math.abs(nextIntegral[g] - integral[g]) / 15.0 > QUADRATURE_TOLERANCE) {
                    converged = false;
                    break;
                }
            }
            values = nextValues;
            integral = nextIntegral;
            steps = nextSteps;
            h = nextH;
            if (converged) {
                return integral;
            }
        }
    }

    private static double[] simpson(double[] values, int steps, int groups, double h) {
        double[] sum = new double[groups];
        for (int i = 0; i <= steps; i++) {
            double weight = (i == 0 || i == steps) ? 1.0 : ((i & 1) == 1 ? 4.0 : 2.0);
            int base = i * groups;
            for (int g = 0; g < groups; g++) {
                sum[g] += weight * values[base + g];
            }
        }
        double factor = h / 3.0;
        for (int g = 0; g < groups; g++) {
            sum[g] *= factor;
        }
        return sum;
    }

    /**
     * Binomial pmf for {@code count} trials with success chance {@code p}, starting at the mode so a
     * chance near one does not begin from the underflowing {@code q^count} tail. The mode probability
     * is formed in log space from precomputed log factorials, then the pmf is filled outward.
     */
    private static double[] binomial(int count, double p, double q, double[] logFactorial) {
        double[] pmf = new double[count + 1];
        if (p <= 0.0) {
            pmf[0] = 1.0;
            return pmf;
        }
        if (q <= 0.0) {
            pmf[count] = 1.0;
            return pmf;
        }
        int mode = (int) Math.floor((count + 1) * p);
        if (mode > count) {
            mode = count;
        }
        double logMode = logFactorial[count] - logFactorial[mode] - logFactorial[count - mode]
                + mode * Math.log(p) + (count - mode) * Math.log(q);
        double term = Math.exp(logMode);
        pmf[mode] = term;
        for (int m = mode; m < count; m++) {
            term *= (double) (count - m) / (m + 1) * (p / q);
            pmf[m + 1] = term;
        }
        term = pmf[mode];
        for (int m = mode; m > 0; m--) {
            term *= (double) m / (count - m + 1) * (q / p);
            pmf[m - 1] = term;
        }
        return pmf;
    }
}
