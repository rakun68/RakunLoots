package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.config.summon.HeightRange;
import me.rakkoon69.rakunloots.config.summon.PlantsMode;
import me.rakkoon69.rakunloots.config.summon.SummonRules;
import me.rakkoon69.rakunloots.config.summon.UnderwaterPosition;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.server.ChunkView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Picks a spot in one column of a chunk view, or nothing. Reads the chunk view only.
 */
public final class ColumnCheck {

    private static final String AIR = "minecraft:air";
    private static final String WATER = "minecraft:water";

    private ColumnCheck() {
    }

    public static Optional<Spot> find(
            SummonRules rules,
            ChunkView view,
            int localX,
            int localZ,
            OccupiedCheck occupied,
            Random random
    ) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(occupied, "occupied");
        Objects.requireNonNull(random, "random");
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            throw new IllegalArgumentException("local x and z must be 0 to 15");
        }

        HeightRange range = rules.heightRange().clip(view.minHeight(), view.maxHeight());
        if (range.min() > range.max()) {
            return Optional.empty();
        }

        Column column = new Column(rules, view, localX, localZ, range, occupied, random);
        return switch (rules.rule()) {
            case TOP_SURFACE -> topSurface(column);
            case ANY_SURFACE -> anySurface(column);
            case BURIED -> buried(column);
            case DEEP_BURIED -> deepBuried(column);
            case UNDERWATER -> underwater(column);
            case ANYWHERE -> anywhere(column);
        };
    }

    private static Optional<Spot> topSurface(Column column) {
        SummonRules rules = column.rules;
        HeightRange range = column.range;
        Set<String> replace = rules.replaceableBlocks().isEmpty()
                ? rules.nonSolidBlocks()
                : rules.replaceableBlocks();
        Set<String> plants = rules.plantsBlocks();
        boolean onTop = rules.plantsMode() == PlantsMode.ON_TOP;

        // Scan down to the first block that counts as ground for this plants mode.
        int groundY = Integer.MIN_VALUE;
        for (int y = range.max(); y >= range.min(); y--) {
            String id = column.blockId(y);
            // On top: plants from the resolved group count as ground. Other replaceables are skipped.
            if (isAir(id)) {
                continue;
            }
            if (!replace.contains(id) || (onTop && plants.contains(id))) {
                groundY = y;
                break;
            }
        }
        if (groundY == Integer.MIN_VALUE) {
            return Optional.empty();
        }

        // Replace: the lowest block of the replaceable run above ground. On top: the first air above the plants.
        int spotY = groundY + 1;
        if (spotY > range.max() || spotY >= column.view.maxHeight()) {
            return Optional.empty();
        }
        String atSpot = column.blockId(spotY);
        boolean spotFits = onTop ? AIR.equals(atSpot) : replace.contains(atSpot) || AIR.equals(atSpot);
        if (!spotFits) {
            return Optional.empty();
        }

        // #36 scans down, so the highest non-replaceable block inside the range is the ground.
        // Replaceable blocks above that ground are the run being broken.
        // The first block above that run that is neither replaceable nor air refuses the column.
        for (int above = spotY + 1; above < column.view.maxHeight(); above++) {
            String id = column.blockId(above);
            if (replace.contains(id)) {
                continue;
            }
            if (!AIR.equals(id)) {
                return Optional.empty();
            }
            break;
        }
        return column.accepts(spotY) ? Optional.of(column.spot(spotY)) : Optional.empty();
    }

    /**
     * Air never holds a box up, whatever the loot lists as replaceable.
     */
    private static boolean isAir(String id) {
        return AIR.equals(id) || "minecraft:cave_air".equals(id) || "minecraft:void_air".equals(id);
    }

    private static Optional<Spot> anySurface(Column column) {
        SummonRules rules = column.rules;
        Set<String> replace = rules.replaceableBlocks().isEmpty() ? Set.of(AIR) : rules.replaceableBlocks();
        Set<String> floor = rules.floorBlocks().isEmpty() ? rules.solidBlocks() : rules.floorBlocks();
        int headroom = Math.max(1, rules.headroom());
        List<Integer> candidates = new ArrayList<>();
        for (int y = column.range.min(); y <= column.range.max(); y++) {
            if (!replace.contains(column.blockId(y))) {
                continue;
            }
            int below = y - 1;
            if (below < column.view.minHeight() || !floor.contains(column.blockId(below))) {
                continue;
            }
            boolean room = true;
            for (int up = 0; up < headroom; up++) {
                int check = y + up;
                if (check >= column.view.maxHeight() || !replace.contains(column.blockId(check))) {
                    room = false;
                    break;
                }
            }
            if (room) {
                candidates.add(y);
            }
        }
        return column.pick(candidates);
    }

    private static Optional<Spot> buried(Column column) {
        SummonRules rules = column.rules;
        HeightRange range = column.range;
        Set<String> replace = rules.replaceableBlocks().isEmpty()
                ? rules.naturalGroundBlocks()
                : rules.replaceableBlocks();
        Set<String> nonSolid = rules.nonSolidBlocks();
        int surface = Integer.MIN_VALUE;
        for (int y = range.max(); y >= range.min(); y--) {
            if (!nonSolid.contains(column.blockId(y))) {
                surface = y;
                break;
            }
        }
        if (surface == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        int depthMin = Math.min(rules.buriedDepthMin(), rules.buriedDepthMax());
        int depthMax = Math.max(rules.buriedDepthMin(), rules.buriedDepthMax());
        List<Integer> candidates = new ArrayList<>();
        for (int depth = depthMin; depth <= depthMax; depth++) {
            int y = surface - depth;
            if (y < range.min() || y > range.max()) {
                continue;
            }
            if (replace.contains(column.blockId(y))) {
                candidates.add(y);
            }
        }
        return column.pick(candidates);
    }

    private static Optional<Spot> deepBuried(Column column) {
        SummonRules rules = column.rules;
        Set<String> replace = rules.replaceableBlocks().isEmpty()
                ? rules.naturalGroundBlocks()
                : rules.replaceableBlocks();
        Set<String> solid = rules.solidBlocks();
        // Issue #38: enclosed neighbours must all sit in this chunk view, so columns 0 and 15 are refused.
        boolean enclosed = rules.deepBuriedEnclosed();
        if (enclosed && (column.localX < 1 || column.localX > 14 || column.localZ < 1 || column.localZ > 14)) {
            return Optional.empty();
        }
        List<Integer> candidates = new ArrayList<>();
        for (int y = column.range.min(); y <= column.range.max(); y++) {
            if (!replace.contains(column.blockId(y))) {
                continue;
            }
            if (enclosed && !enclosed(column.view, column.localX, y, column.localZ, solid)) {
                continue;
            }
            candidates.add(y);
        }
        return column.pick(candidates);
    }

    private static boolean enclosed(ChunkView view, int x, int y, int z, Set<String> solid) {
        if (y - 1 < view.minHeight() || y + 1 >= view.maxHeight()) {
            return false;
        }
        return solid.contains(view.blockId(x, y - 1, z))
                && solid.contains(view.blockId(x, y + 1, z))
                && solid.contains(view.blockId(x - 1, y, z))
                && solid.contains(view.blockId(x + 1, y, z))
                && solid.contains(view.blockId(x, y, z - 1))
                && solid.contains(view.blockId(x, y, z + 1));
    }

    private static Optional<Spot> underwater(Column column) {
        SummonRules rules = column.rules;
        HeightRange range = column.range;
        int minDepth = Math.max(1, rules.minWaterDepth());
        List<Integer> candidates = new ArrayList<>();
        int y = range.min();
        while (y <= range.max()) {
            if (!WATER.equals(column.blockId(y))) {
                y++;
                continue;
            }
            int bottom = y;
            while (y <= range.max() && WATER.equals(column.blockId(y))) {
                y++;
            }
            int top = y - 1;
            int depth = top - bottom + 1;
            if (depth < minDepth) {
                continue;
            }
            if (!rules.caveWater()) {
                int above = top + 1;
                if (above < column.view.maxHeight() && !AIR.equals(column.blockId(above))) {
                    continue;
                }
            }
            int spotY = rules.underwaterPosition() == UnderwaterPosition.FLOATING ? top : bottom;
            if (rules.underwaterPosition() == UnderwaterPosition.SINKING) {
                int below = bottom - 1;
                if (below < column.view.minHeight()) {
                    continue;
                }
                if (rules.avoidSurfaces().contains(column.blockId(below))) {
                    continue;
                }
            }
            candidates.add(spotY);
        }
        return column.pick(candidates);
    }

    private static Optional<Spot> anywhere(Column column) {
        SummonRules rules = column.rules;
        Set<String> replace = rules.replaceableBlocks().isEmpty() ? Set.of(AIR) : rules.replaceableBlocks();
        List<Integer> candidates = new ArrayList<>();
        for (int y = column.range.min(); y <= column.range.max(); y++) {
            if (replace.contains(column.blockId(y))) {
                candidates.add(y);
            }
        }
        return column.pick(candidates);
    }

    /**
     * One column of a chunk view under one loot's rules.
     */
    private record Column(
            SummonRules rules,
            ChunkView view,
            int localX,
            int localZ,
            HeightRange range,
            OccupiedCheck occupied,
            Random random
    ) {

        String blockId(int y) {
            return view.blockId(localX, y, localZ);
        }

        Spot spot(int y) {
            return new Spot(view.worldKey(), view.chunkX() * 16 + localX, y, view.chunkZ() * 16 + localZ,
                    blockId(y));
        }

        /**
         * Refuses a spot that holds a loot box, sits on one, or sits on an avoided surface.
         */
        boolean accepts(int y) {
            Spot spot = spot(y);
            if (occupied.isOccupied(spot.worldKey(), spot.x(), spot.y(), spot.z())) {
                return false;
            }
            int belowY = y - 1;
            if (belowY >= view.minHeight()) {
                if (occupied.isOccupied(spot.worldKey(), spot.x(), belowY, spot.z())) {
                    return false;
                }
                return !rules.avoidSurfaces().contains(blockId(belowY));
            }
            return true;
        }

        Optional<Spot> pick(List<Integer> candidates) {
            List<Integer> accepted = new ArrayList<>();
            for (int y : candidates) {
                if (accepts(y)) {
                    accepted.add(y);
                }
            }
            if (accepted.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(spot(accepted.get(random.nextInt(accepted.size()))));
        }
    }
}
