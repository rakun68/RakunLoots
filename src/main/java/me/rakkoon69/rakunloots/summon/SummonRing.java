package me.rakkoon69.rakunloots.summon;

/**
 * A square band between a minimum and a maximum distance from a center. A minimum of zero is a filled square.
 */
public record SummonRing(int min, int max) {

    public SummonRing {
        if (min < 0 || max < 0) {
            throw new IllegalArgumentException("ring distances must not be negative");
        }
        if (min > max) {
            throw new IllegalArgumentException("ring min must not exceed max");
        }
    }
}
