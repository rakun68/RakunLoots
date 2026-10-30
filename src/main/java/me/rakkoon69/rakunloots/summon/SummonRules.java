package me.rakkoon69.rakunloots.summon;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Immutable summon rules for one loot id. Held on the config snapshot beside the loot, never on it.
 */
public final class SummonRules {

    public static final Set<String> DEFAULT_AVOID = Set.of(
            "minecraft:lava",
            "minecraft:fire",
            "minecraft:campfire",
            "minecraft:soul_fire",
            "minecraft:soul_campfire"
    );

    private final PlacementRule rule;
    private final PlantsMode plantsMode;
    private final Set<String> floorBlocks;
    private final int headroom;
    private final int buriedDepthMin;
    private final int buriedDepthMax;
    private final boolean deepBuriedEnclosed;
    private final UnderwaterPosition underwaterPosition;
    private final boolean caveWater;
    private final int minWaterDepth;
    private final HeightRange heightRange;
    private final Set<String> replaceableBlocks;
    private final Set<String> avoidSurfaces;
    private final int attempts;
    private final SummonRing playerRing;
    private final OptionalInt verticalOffset;
    private final Set<String> solidBlocks;
    private final Set<String> nonSolidBlocks;
    private final Set<String> plantsBlocks;
    private final Set<String> naturalGroundBlocks;

    private SummonRules(Builder builder) {
        this.rule = Objects.requireNonNull(builder.rule, "rule");
        this.plantsMode = Objects.requireNonNull(builder.plantsMode, "plantsMode");
        this.floorBlocks = freeze(builder.floorBlocks);
        this.headroom = builder.headroom;
        this.buriedDepthMin = builder.buriedDepthMin;
        this.buriedDepthMax = builder.buriedDepthMax;
        this.deepBuriedEnclosed = builder.deepBuriedEnclosed;
        this.underwaterPosition = Objects.requireNonNull(builder.underwaterPosition, "underwaterPosition");
        this.caveWater = builder.caveWater;
        this.minWaterDepth = builder.minWaterDepth;
        this.heightRange = Objects.requireNonNull(builder.heightRange, "heightRange");
        this.replaceableBlocks = freeze(builder.replaceableBlocks);
        this.avoidSurfaces = freeze(builder.avoidSurfaces);
        this.attempts = builder.attempts;
        this.playerRing = Objects.requireNonNull(builder.playerRing, "playerRing");
        OptionalInt offset = Objects.requireNonNull(builder.verticalOffset, "verticalOffset");
        this.verticalOffset = builder.verticalOffsetEnabled ? offset : OptionalInt.empty();
        this.solidBlocks = freeze(builder.solidBlocks);
        this.nonSolidBlocks = freeze(builder.nonSolidBlocks);
        this.plantsBlocks = freeze(builder.plantsBlocks);
        this.naturalGroundBlocks = freeze(builder.naturalGroundBlocks);
    }

    private static Set<String> freeze(Set<String> ids) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    public PlacementRule rule() {
        return rule;
    }

    public PlantsMode plantsMode() {
        return plantsMode;
    }

    public Set<String> floorBlocks() {
        return floorBlocks;
    }

    public int headroom() {
        return headroom;
    }

    public int buriedDepthMin() {
        return buriedDepthMin;
    }

    public int buriedDepthMax() {
        return buriedDepthMax;
    }

    public boolean deepBuriedEnclosed() {
        return deepBuriedEnclosed;
    }

    public UnderwaterPosition underwaterPosition() {
        return underwaterPosition;
    }

    public boolean caveWater() {
        return caveWater;
    }

    public int minWaterDepth() {
        return minWaterDepth;
    }

    public HeightRange heightRange() {
        return heightRange;
    }

    public Set<String> replaceableBlocks() {
        return replaceableBlocks;
    }

    public Set<String> avoidSurfaces() {
        return avoidSurfaces;
    }

    public int attempts() {
        return attempts;
    }

    public SummonRing playerRing() {
        return playerRing;
    }

    public boolean verticalOffsetEnabled() {
        return verticalOffset.isPresent();
    }

    public OptionalInt verticalOffset() {
        return verticalOffset;
    }

    public Set<String> solidBlocks() {
        return solidBlocks;
    }

    public Set<String> nonSolidBlocks() {
        return nonSolidBlocks;
    }

    public Set<String> plantsBlocks() {
        return plantsBlocks;
    }

    public Set<String> naturalGroundBlocks() {
        return naturalGroundBlocks;
    }

    public SummonRules withHeightRange(HeightRange heightRange) {
        return toBuilder().heightRange(heightRange).build();
    }

    public Builder toBuilder() {
        return builder(rule)
                .plantsMode(plantsMode)
                .floorBlocks(floorBlocks)
                .headroom(headroom)
                .buriedDepth(buriedDepthMin, buriedDepthMax)
                .deepBuriedEnclosed(deepBuriedEnclosed)
                .underwaterPosition(underwaterPosition)
                .caveWater(caveWater)
                .minWaterDepth(minWaterDepth)
                .heightRange(heightRange)
                .replaceableBlocks(replaceableBlocks)
                .avoidSurfaces(avoidSurfaces)
                .attempts(attempts)
                .playerRing(playerRing)
                .verticalOffset(verticalOffset.isPresent(), verticalOffset)
                .solidBlocks(solidBlocks)
                .nonSolidBlocks(nonSolidBlocks)
                .plantsBlocks(plantsBlocks)
                .naturalGroundBlocks(naturalGroundBlocks);
    }

    public static Builder builder(PlacementRule rule) {
        return new Builder(rule);
    }

    public static final class Builder {
        private final PlacementRule rule;
        private PlantsMode plantsMode = PlantsMode.REPLACE;
        private Set<String> floorBlocks = Set.of();
        private int headroom = 1;
        private int buriedDepthMin = 1;
        private int buriedDepthMax = 3;
        private boolean deepBuriedEnclosed = false;
        private UnderwaterPosition underwaterPosition = UnderwaterPosition.SINKING;
        private boolean caveWater = false;
        private int minWaterDepth = 2;
        private HeightRange heightRange = new HeightRange(-64, 319);
        private Set<String> replaceableBlocks = Set.of();
        private Set<String> avoidSurfaces = DEFAULT_AVOID;
        private int attempts;
        private SummonRing playerRing = new SummonRing(0, 16);
        private boolean verticalOffsetEnabled = false;
        private OptionalInt verticalOffset = OptionalInt.empty();
        private Set<String> solidBlocks = Set.of();
        private Set<String> nonSolidBlocks = Set.of();
        private Set<String> plantsBlocks = Set.of();
        private Set<String> naturalGroundBlocks = Set.of();

        private Builder(PlacementRule rule) {
            this.rule = Objects.requireNonNull(rule, "rule");
            this.attempts = rule.defaultAttempts();
        }

        public Builder plantsMode(PlantsMode plantsMode) {
            this.plantsMode = Objects.requireNonNull(plantsMode, "plantsMode");
            return this;
        }

        public Builder floorBlocks(Set<String> floorBlocks) {
            this.floorBlocks = Objects.requireNonNull(floorBlocks, "floorBlocks");
            return this;
        }

        public Builder headroom(int headroom) {
            this.headroom = headroom;
            return this;
        }

        public Builder buriedDepth(int min, int max) {
            this.buriedDepthMin = min;
            this.buriedDepthMax = max;
            return this;
        }

        public Builder deepBuriedEnclosed(boolean enclosed) {
            this.deepBuriedEnclosed = enclosed;
            return this;
        }

        public Builder underwaterPosition(UnderwaterPosition position) {
            this.underwaterPosition = Objects.requireNonNull(position, "position");
            return this;
        }

        public Builder caveWater(boolean caveWater) {
            this.caveWater = caveWater;
            return this;
        }

        public Builder minWaterDepth(int minWaterDepth) {
            this.minWaterDepth = minWaterDepth;
            return this;
        }

        public Builder heightRange(HeightRange heightRange) {
            this.heightRange = Objects.requireNonNull(heightRange, "heightRange");
            return this;
        }

        public Builder replaceableBlocks(Set<String> replaceableBlocks) {
            this.replaceableBlocks = Objects.requireNonNull(replaceableBlocks, "replaceableBlocks");
            return this;
        }

        public Builder avoidSurfaces(Set<String> avoidSurfaces) {
            this.avoidSurfaces = Objects.requireNonNull(avoidSurfaces, "avoidSurfaces");
            return this;
        }

        public Builder attempts(int attempts) {
            this.attempts = attempts;
            return this;
        }

        public Builder playerRing(SummonRing playerRing) {
            this.playerRing = Objects.requireNonNull(playerRing, "playerRing");
            return this;
        }

        public Builder verticalOffset(boolean enabled, OptionalInt offset) {
            this.verticalOffsetEnabled = enabled;
            this.verticalOffset = Objects.requireNonNull(offset, "offset");
            return this;
        }

        public Builder solidBlocks(Set<String> solidBlocks) {
            this.solidBlocks = Objects.requireNonNull(solidBlocks, "solidBlocks");
            return this;
        }

        public Builder nonSolidBlocks(Set<String> nonSolidBlocks) {
            this.nonSolidBlocks = Objects.requireNonNull(nonSolidBlocks, "nonSolidBlocks");
            return this;
        }

        public Builder plantsBlocks(Set<String> plantsBlocks) {
            this.plantsBlocks = Objects.requireNonNull(plantsBlocks, "plantsBlocks");
            return this;
        }

        public Builder naturalGroundBlocks(Set<String> naturalGroundBlocks) {
            this.naturalGroundBlocks = Objects.requireNonNull(naturalGroundBlocks, "naturalGroundBlocks");
            return this;
        }

        public SummonRules build() {
            return new SummonRules(this);
        }
    }
}
