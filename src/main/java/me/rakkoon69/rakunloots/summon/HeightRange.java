package me.rakkoon69.rakunloots.summon;

/**
 * Inclusive block y range a loot may land in.
 */
public record HeightRange(int min, int max) {

    public HeightRange {
        if (min > max) {
            throw new IllegalArgumentException("height min must not exceed max");
        }
    }

    public HeightRange clip(int worldMin, int worldMaxExclusive) {
        int clippedMin = Math.max(min, worldMin);
        int clippedMax = Math.min(max, worldMaxExclusive - 1);
        if (clippedMin > clippedMax) {
            return new HeightRange(clippedMin, clippedMin);
        }
        return new HeightRange(clippedMin, clippedMax);
    }
}
