package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.summon.HeightRange;
import me.rakkoon69.rakunloots.config.summon.PlacementRule;
import me.rakkoon69.rakunloots.config.summon.PlantsMode;
import me.rakkoon69.rakunloots.config.summon.SummonRules;
import me.rakkoon69.rakunloots.config.summon.UnderwaterPosition;
import me.rakkoon69.rakunloots.fake.FakeColumn;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnCheckTest {

    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    @Test
    void topSurfaceReplaceBreaksTallGrass() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(5, 10, 5, "minecraft:grass_block")
                .set(5, 11, 5, "minecraft:tall_grass")
                .set(5, 12, 5, "minecraft:tall_grass");
        SummonRules rules = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .plantsMode(PlantsMode.REPLACE)
                .replaceableBlocks(Set.of(
                        "minecraft:air",
                        "minecraft:tall_grass",
                        "minecraft:short_grass",
                        "minecraft:bamboo"
                ))
                .heightRange(new HeightRange(0, 31))
                .build();

        Spot spot = ColumnCheck.find(rules, column.view(), 5, 5, FREE, new Random(1)).orElseThrow();

        assertEquals(11, spot.y());
        assertEquals(5, spot.x());
        assertEquals(5, spot.z());
        assertEquals("minecraft:tall_grass", spot.blockId(), "the spot keeps the block summon replaces");
    }

    @Test
    void topSurfaceOnTopLandsAboveBamboo() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(3, 8, 3, "minecraft:grass_block")
                .set(3, 9, 3, "minecraft:bamboo")
                .set(3, 10, 3, "minecraft:bamboo")
                .set(3, 11, 3, "minecraft:bamboo");
        SummonRules rules = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .plantsMode(PlantsMode.ON_TOP)
                .plantsBlocks(Set.of("minecraft:bamboo"))
                .replaceableBlocks(Set.of(
                        "minecraft:air",
                        "minecraft:bamboo",
                        "minecraft:tall_grass"
                ))
                .heightRange(new HeightRange(0, 31))
                .build();

        Spot spot = ColumnCheck.find(rules, column.view(), 3, 3, FREE, new Random(1)).orElseThrow();

        assertEquals(12, spot.y());
    }

    @Test
    void avoidListRefusesSpotOverLava() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(1, 5, 1, "minecraft:lava")
                .set(1, 6, 1, "minecraft:air");
        SummonRules rules = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .replaceableBlocks(Set.of("minecraft:air"))
                .heightRange(new HeightRange(0, 31))
                .build();

        assertTrue(ColumnCheck.find(rules, column.view(), 1, 1, FREE, new Random(1)).isEmpty());
    }

    @Test
    void occupiedSpotAndSpotOnTopOfBoxAreRefused() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(2, 4, 2, "minecraft:stone")
                .set(2, 5, 2, "minecraft:air");
        SummonRules rules = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .replaceableBlocks(Set.of("minecraft:air"))
                .heightRange(new HeightRange(0, 31))
                .build();
        OccupiedCheck occupiedAtSpot = (w, x, y, z) -> x == 2 && y == 5 && z == 2;
        OccupiedCheck occupiedBelow = (w, x, y, z) -> x == 2 && y == 4 && z == 2;

        assertTrue(ColumnCheck.find(rules, column.view(), 2, 2, occupiedAtSpot, new Random(1)).isEmpty());
        assertTrue(ColumnCheck.find(rules, column.view(), 2, 2, occupiedBelow, new Random(1)).isEmpty());
    }

    @Test
    void heightRangeOutsideWorldIsClipped() {
        FakeColumn column = new FakeColumn(0, 0, 0, 16)
                .set(0, 5, 0, "minecraft:stone");
        SummonRules rules = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .replaceableBlocks(Set.of("minecraft:air"))
                .heightRange(new HeightRange(-100, 500))
                .build();

        Spot spot = ColumnCheck.find(rules, column.view(), 0, 0, FREE, new Random(1)).orElseThrow();
        assertEquals(6, spot.y());
    }

    @Test
    void anySurfaceFindsCaveFloorUnderRoof() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(4, 4, 4, "minecraft:stone")
                .set(4, 5, 4, "minecraft:air")
                .set(4, 6, 4, "minecraft:air")
                .set(4, 10, 4, "minecraft:stone");
        for (int y = 11; y < 32; y++) {
            column.set(4, y, 4, "minecraft:stone");
        }
        SummonRules rules = SummonRules.builder(PlacementRule.ANY_SURFACE)
                .floorBlocks(Set.of("minecraft:stone"))
                .replaceableBlocks(Set.of("minecraft:air"))
                .headroom(1)
                .heightRange(new HeightRange(0, 31))
                .build();

        Spot spot = ColumnCheck.find(rules, column.view(), 4, 4, FREE, new Random(1)).orElseThrow();
        assertTrue(spot.y() == 5 || spot.y() == 6);
    }

    @Test
    void anySurfaceRefusesLowHeadroomAndBadFloor() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(4, 4, 4, "minecraft:dirt")
                .set(4, 5, 4, "minecraft:air")
                .set(4, 6, 4, "minecraft:stone");
        for (int y = 7; y < 32; y++) {
            column.set(4, y, 4, "minecraft:stone");
        }
        SummonRules rules = SummonRules.builder(PlacementRule.ANY_SURFACE)
                .floorBlocks(Set.of("minecraft:stone"))
                .replaceableBlocks(Set.of("minecraft:air"))
                .headroom(2)
                .heightRange(new HeightRange(0, 31))
                .build();

        assertTrue(ColumnCheck.find(rules, column.view(), 4, 4, FREE, new Random(1)).isEmpty());
    }

    @Test
    void anywhereLandsInMidAirAndHitsEveryValidHeight() {
        FakeColumn column = new FakeColumn(0, 0, 0, 20);
        SummonRules rules = SummonRules.builder(PlacementRule.ANYWHERE)
                .replaceableBlocks(Set.of("minecraft:air"))
                .heightRange(new HeightRange(5, 9))
                .build();

        Set<Integer> heights = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            ColumnCheck.find(rules, column.view(), 1, 1, FREE, new Random(seed))
                    .ifPresent(spot -> heights.add(spot.y()));
        }
        assertEquals(Set.of(5, 6, 7, 8, 9), heights);
    }

    @Test
    void buriedStaysWithinDepthAndRefusesWrongBlock() {
        // Surface stone at y=10; dirt at 9 and 8; stone at 7 must not be picked.
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(6, 10, 6, "minecraft:stone")
                .set(6, 9, 6, "minecraft:dirt")
                .set(6, 8, 6, "minecraft:dirt")
                .set(6, 7, 6, "minecraft:stone");
        var view = column.view();
        assertEquals("minecraft:stone", view.blockId(6, 10, 6));
        assertEquals("minecraft:dirt", view.blockId(6, 9, 6));
        assertEquals("minecraft:dirt", view.blockId(6, 8, 6));

        SummonRules rules = SummonRules.builder(PlacementRule.BURIED)
                .buriedDepth(1, 2)
                .nonSolidBlocks(Set.of("minecraft:air"))
                .replaceableBlocks(Set.of("minecraft:dirt"))
                .avoidSurfaces(Set.of("minecraft:lava"))
                .heightRange(new HeightRange(0, 31))
                .build();

        Set<Integer> heights = new HashSet<>();
        for (long seed = 1; seed < 10_000; seed += 97) {
            ColumnCheck.find(rules, view, 6, 6, FREE, new Random(seed))
                    .ifPresent(spot -> heights.add(spot.y()));
        }
        assertEquals(Set.of(8, 9), heights, "column blocks y10=" + view.blockId(6, 10, 6)
                + " y9=" + view.blockId(6, 9, 6) + " y8=" + view.blockId(6, 8, 6)
                + " avoid=" + rules.avoidSurfaces());

        SummonRules stoneOnly = SummonRules.builder(PlacementRule.BURIED)
                .buriedDepth(1, 2)
                .nonSolidBlocks(Set.of("minecraft:air"))
                .replaceableBlocks(Set.of("minecraft:cobblestone"))
                .avoidSurfaces(Set.of("minecraft:lava"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertTrue(ColumnCheck.find(stoneOnly, view, 6, 6, FREE, new Random(1)).isEmpty());
    }

    @Test
    void deepBuriedFindsFarBelowAndEnclosedRefusesCaveAndEdge() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 20; y++) {
                    column.set(x, y, z, "minecraft:stone");
                }
            }
        }
        column.set(5, 5, 5, "minecraft:dirt");
        SummonRules deep = SummonRules.builder(PlacementRule.DEEP_BURIED)
                .replaceableBlocks(Set.of("minecraft:dirt"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(5, ColumnCheck.find(deep, column.view(), 5, 5, FREE, new Random(1)).orElseThrow().y());

        // After opening a cave above the dirt, enclosed refuses that column's dirt.
        column.set(5, 6, 5, "minecraft:air");
        SummonRules enclosedDirtOnly = SummonRules.builder(PlacementRule.DEEP_BURIED)
                .deepBuriedEnclosed(true)
                .solidBlocks(Set.of("minecraft:stone"))
                .replaceableBlocks(Set.of("minecraft:dirt"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertTrue(ColumnCheck.find(enclosedDirtOnly, column.view(), 5, 5, FREE, new Random(1)).isEmpty());

        SummonRules enclosed = SummonRules.builder(PlacementRule.DEEP_BURIED)
                .deepBuriedEnclosed(true)
                .solidBlocks(Set.of("minecraft:stone"))
                .replaceableBlocks(Set.of("minecraft:stone"))
                .heightRange(new HeightRange(0, 31))
                .build();

        // Edge column refused when enclosed
        FakeColumn edge = new FakeColumn(0, 0, 0, 32);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    edge.set(x, y, z, "minecraft:stone");
                }
            }
        }
        assertTrue(ColumnCheck.find(enclosed, edge.view(), 0, 5, FREE, new Random(1)).isEmpty());
    }

    @Test
    void underwaterFloatingAndSinkingAndCaveAndMinDepth() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(7, 4, 7, "minecraft:sand")
                .set(7, 5, 7, "minecraft:water")
                .set(7, 6, 7, "minecraft:water")
                .set(7, 7, 7, "minecraft:water");
        SummonRules floating = SummonRules.builder(PlacementRule.UNDERWATER)
                .underwaterPosition(UnderwaterPosition.FLOATING)
                .minWaterDepth(2)
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(7, ColumnCheck.find(floating, column.view(), 7, 7, FREE, new Random(1)).orElseThrow().y());

        SummonRules sinking = SummonRules.builder(PlacementRule.UNDERWATER)
                .underwaterPosition(UnderwaterPosition.SINKING)
                .minWaterDepth(2)
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(5, ColumnCheck.find(sinking, column.view(), 7, 7, FREE, new Random(1)).orElseThrow().y());

        FakeColumn cave = new FakeColumn(0, 0, 0, 32)
                .set(7, 4, 7, "minecraft:sand")
                .set(7, 5, 7, "minecraft:water")
                .set(7, 6, 7, "minecraft:water")
                .set(7, 7, 7, "minecraft:stone");
        assertTrue(ColumnCheck.find(floating, cave.view(), 7, 7, FREE, new Random(1)).isEmpty());
        SummonRules caveOk = SummonRules.builder(PlacementRule.UNDERWATER)
                .underwaterPosition(UnderwaterPosition.FLOATING)
                .caveWater(true)
                .minWaterDepth(2)
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(6, ColumnCheck.find(caveOk, cave.view(), 7, 7, FREE, new Random(1)).orElseThrow().y());

        FakeColumn shallow = new FakeColumn(0, 0, 0, 32)
                .set(7, 5, 7, "minecraft:sand")
                .set(7, 6, 7, "minecraft:water");
        assertTrue(ColumnCheck.find(floating, shallow.view(), 7, 7, FREE, new Random(1)).isEmpty());
    }

    @Test
    void editedPlantsGroupChangesOnTop() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(3, 8, 3, "minecraft:grass_block")
                .set(3, 9, 3, "minecraft:dandelion");
        SummonRules flowerIsPlant = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .plantsMode(PlantsMode.ON_TOP)
                .plantsBlocks(Set.of("minecraft:dandelion"))
                .replaceableBlocks(Set.of("minecraft:air", "minecraft:dandelion"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(10, ColumnCheck.find(flowerIsPlant, column.view(), 3, 3, FREE, new Random(1)).orElseThrow().y());

        SummonRules flowerIsNotPlant = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .plantsMode(PlantsMode.ON_TOP)
                .plantsBlocks(Set.of("minecraft:bamboo"))
                .replaceableBlocks(Set.of("minecraft:air", "minecraft:dandelion"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertTrue(ColumnCheck.find(flowerIsNotPlant, column.view(), 3, 3, FREE, new Random(1)).isEmpty());
    }

    @Test
    void editedNonSolidGroupChangesBuriedSurface() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(6, 7, 6, "minecraft:dirt")
                .set(6, 8, 6, "minecraft:dirt")
                .set(6, 9, 6, "minecraft:oak_leaves")
                .set(6, 10, 6, "minecraft:oak_leaves");
        SummonRules leavesAreAir = SummonRules.builder(PlacementRule.BURIED)
                .buriedDepth(1, 1)
                .nonSolidBlocks(Set.of("minecraft:air", "minecraft:oak_leaves"))
                .replaceableBlocks(Set.of("minecraft:dirt"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(7, ColumnCheck.find(leavesAreAir, column.view(), 6, 6, FREE, new Random(1)).orElseThrow().y());

        SummonRules leavesAreSurface = SummonRules.builder(PlacementRule.BURIED)
                .buriedDepth(1, 1)
                .nonSolidBlocks(Set.of("minecraft:air"))
                .replaceableBlocks(Set.of("minecraft:oak_leaves"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(9, ColumnCheck.find(leavesAreSurface, column.view(), 6, 6, FREE, new Random(1)).orElseThrow().y());
    }

    @Test
    void topSurfaceLandsOnTheHighestSolidInsideTheRange() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(5, 8, 5, "minecraft:grass_block")
                .set(5, 9, 5, "minecraft:air")
                .set(5, 14, 5, "minecraft:oak_leaves");
        SummonRules rules = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .replaceableBlocks(Set.of("minecraft:air"))
                .heightRange(new HeightRange(0, 31))
                .build();
        assertEquals(15, ColumnCheck.find(rules, column.view(), 5, 5, FREE, new Random(1)).orElseThrow().y());
    }

    @Test
    void solidAboveTheSpotRefusesTopSurface() {
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(5, 8, 5, "minecraft:grass_block")
                .set(5, 9, 5, "minecraft:air")
                .set(5, 14, 5, "minecraft:oak_leaves");
        SummonRules rules = SummonRules.builder(PlacementRule.TOP_SURFACE)
                .replaceableBlocks(Set.of("minecraft:air"))
                .heightRange(new HeightRange(0, 12))
                .build();
        assertTrue(ColumnCheck.find(rules, column.view(), 5, 5, FREE, new Random(1)).isEmpty());
    }

    @Test
    void editedBlocksFileChangesTopSurfacePlacement() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .blocks("""
                        groups:
                          plants:
                            - minecraft:dandelion
                        """)
                .loot("demo", """
                        language: en
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        summon_settings:
                          summon_type: top_surface
                          top_surface:
                            plants: on_top
                          replaceable_blocks:
                            - minecraft:air
                            - minecraft:dandelion
                        """)
                .build(), written -> Optional.of(written), WarningKinds.all());

        SummonRules rules = snapshot.summonRules("demo").orElseThrow();
        FakeColumn column = new FakeColumn(0, 0, 0, 32)
                .set(3, 8, 3, "minecraft:grass_block")
                .set(3, 9, 3, "minecraft:dandelion");
        assertEquals(10, ColumnCheck.find(rules, column.view(), 3, 3, FREE, new Random(1)).orElseThrow().y());
    }
}
