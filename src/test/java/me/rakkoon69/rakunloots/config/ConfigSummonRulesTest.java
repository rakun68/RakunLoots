package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.summon.PlacementRule;
import me.rakkoon69.rakunloots.config.summon.PlantsMode;
import me.rakkoon69.rakunloots.config.summon.SummonRing;
import me.rakkoon69.rakunloots.config.summon.SummonRules;
import me.rakkoon69.rakunloots.config.summon.UnderwaterPosition;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSummonRulesTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void summonRulesLoadBesideLootAndLeaveLootUnchanged() {
        ConfigSnapshot snapshot = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: buried
                  buried:
                    depth:
                      min: 2
                      max: 4
                  y_range: '10:40'
                  replaceable_blocks:
                    - minecraft:dirt
                  avoid_surfaces:
                    - minecraft:lava
                  player_ring:
                    min: 5
                    max: 20
                  vertical_offset_enable: true
                  vertical_offset: 8
                  attempts: 55
                """).build(), ACCEPTING, WarningKinds.all());

        SummonRules rules = snapshot.summonRules("demo").orElseThrow();
        assertEquals(PlacementRule.BURIED, rules.rule());
        assertEquals(2, rules.buriedDepthMin());
        assertEquals(4, rules.buriedDepthMax());
        assertEquals(10, rules.heightRange().min());
        assertEquals(40, rules.heightRange().max());
        assertEquals(Set.of("minecraft:dirt"), rules.replaceableBlocks());
        assertEquals(Set.of("minecraft:lava"), rules.avoidSurfaces());
        assertEquals(new SummonRing(5, 20), rules.playerRing());
        assertTrue(rules.verticalOffsetEnabled());
        assertEquals(8, rules.verticalOffset().orElseThrow());
        assertEquals(55, rules.attempts());
        assertTrue(snapshot.loot("demo").isPresent());
        assertEquals("barrel", snapshot.loot("demo").orElseThrow().block());
    }

    @Test
    void lootListResolverDropsUnknownGroupWithHighWarning() {
        ConfigSnapshot snapshot = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: top_surface
                  replaceable_blocks:
                    - '@missing_group'
                    - minecraft:air
                """).build(), ACCEPTING, WarningKinds.all());

        assertEquals(Set.of("minecraft:air"), snapshot.summonRules("demo").orElseThrow().replaceableBlocks());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_GROUP
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && "demo".equals(w.lootId().orElse(null))));
    }

    @Test
    void placementAttemptDefaultsAndLootOverride() {
        ConfigSources withOverride = baseLoot("""
                summon_settings:
                  summon_type: underwater
                  attempts: 11
                """).globals("""
                language: en
                placement_attempts:
                  underwater: 99
                """).build();
        assertEquals(11, ConfigLoader.load(withOverride, ACCEPTING, WarningKinds.all()).summonRules("demo").orElseThrow().attempts());

        ConfigSources defaults = baseLoot("""
                summon_settings:
                  summon_type: underwater
                """).build();
        assertEquals(PlacementRule.UNDERWATER.defaultAttempts(),
                ConfigLoader.load(defaults, ACCEPTING, WarningKinds.all()).summonRules("demo").orElseThrow().attempts());
    }

    @Test
    void oldAttemptKeysAndHorizontalOffsetWarn() {
        ConfigSources sources = baseLoot("""
                summon_settings:
                  summon_type: top_surface
                  horizontal_offset: 12
                """).globals("""
                language: en
                player_find_location_max_attempts: 30
                server_find_location_max_attempts: 100
                """).build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertEquals(new SummonRing(0, 12), snapshot.summonRules("demo").orElseThrow().playerRing());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_KEY && "horizontal_offset".equals(w.key().orElse(null))));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_KEY
                        && "player_find_location_max_attempts".equals(w.key().orElse(null))));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_KEY
                        && "server_find_location_max_attempts".equals(w.key().orElse(null))));
    }

    @Test
    void unknownSummonTypeAndNumberUseTopSurface() {
        for (String type : new String[]{"nope", "1", "4"}) {
            ConfigSnapshot snapshot = ConfigLoader.load(baseLoot("""
                    summon_settings:
                      summon_type: %s
                    """.formatted(type)).build(), ACCEPTING, WarningKinds.all());
            assertEquals(PlacementRule.TOP_SURFACE, snapshot.summonRules("demo").orElseThrow().rule());
            assertTrue(snapshot.warnings().stream().anyMatch(w ->
                    w.kind() == ConfigWarning.Kind.UNUSABLE_SUMMON_TYPE));
        }
    }

    @Test
    void badOptionFallsBackAndSwappedMinMaxCorrected() {
        ConfigSnapshot plants = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: top_surface
                  top_surface:
                    plants: sideways
                  y_range: '20:10'
                """).build(), ACCEPTING, WarningKinds.all());
        assertEquals(PlantsMode.REPLACE, plants.summonRules("demo").orElseThrow().plantsMode());
        assertEquals(10, plants.summonRules("demo").orElseThrow().heightRange().min());
        assertEquals(20, plants.summonRules("demo").orElseThrow().heightRange().max());
        assertTrue(plants.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNUSABLE_SUMMON_OPTION));
        assertTrue(plants.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.CORRECTED_MIN_MAX));

        ConfigSnapshot buried = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: buried
                  buried:
                    depth:
                      min: 5
                      max: 2
                """).build(), ACCEPTING, WarningKinds.all());
        assertEquals(2, buried.summonRules("demo").orElseThrow().buriedDepthMin());
        assertEquals(5, buried.summonRules("demo").orElseThrow().buriedDepthMax());
        assertTrue(buried.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.CORRECTED_MIN_MAX));
    }

    @Test
    void ruleOptionSubsectionsLoad() {
        ConfigSnapshot any = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: any_surface
                  any_surface:
                    floor:
                      - minecraft:stone
                    headroom: 3
                """).build(), ACCEPTING, WarningKinds.all());
        assertEquals(Set.of("minecraft:stone"), any.summonRules("demo").orElseThrow().floorBlocks());
        assertEquals(3, any.summonRules("demo").orElseThrow().headroom());

        ConfigSnapshot deep = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: deep_buried
                  deep_buried:
                    enclosed: true
                """).build(), ACCEPTING, WarningKinds.all());
        assertTrue(deep.summonRules("demo").orElseThrow().deepBuriedEnclosed());

        ConfigSnapshot water = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: underwater
                  underwater:
                    position: floating
                    cave_water: true
                    min_depth: 4
                """).build(), ACCEPTING, WarningKinds.all());
        SummonRules wr = water.summonRules("demo").orElseThrow();
        assertEquals(UnderwaterPosition.FLOATING, wr.underwaterPosition());
        assertTrue(wr.caveWater());
        assertEquals(4, wr.minWaterDepth());
    }

    @Test
    void groupAndTagEntriesAreNotSentToTheNameCatalog() {
        NameCatalog rejectsGroupsAndTags = new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                if (writtenId.startsWith("@") || writtenId.startsWith("#") || "minecraft:not_a_block".equals(writtenId)) {
                    return Optional.empty();
                }
                return Optional.of(writtenId);
            }

            @Override
            public Set<String> tagMembers(String namespacedTag) {
                return Set.of("minecraft:dandelion");
            }
        };
        ConfigSnapshot snapshot = ConfigLoader.load(baseLoot("""
                summon_settings:
                  summon_type: top_surface
                  replaceable_blocks:
                    - '@plants'
                    - '#minecraft:flowers'
                    - minecraft:not_a_block
                  avoid_surfaces:
                    - '@solid'
                """).blocks("""
                groups:
                  plants:
                    - minecraft:grass
                  solid:
                    - minecraft:stone
                """).build(), rejectsGroupsAndTags, WarningKinds.all());

        assertTrue(snapshot.warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
                        && (w.writtenId().orElse("").startsWith("@") || w.writtenId().orElse("").startsWith("#"))));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
                        && "minecraft:not_a_block".equals(w.writtenId().orElse(null))));
        assertTrue(snapshot.summonRules("demo").orElseThrow().replaceableBlocks().contains("minecraft:grass"));
        assertTrue(snapshot.summonRules("demo").orElseThrow().replaceableBlocks().contains("minecraft:dandelion"));
    }

    private static ConfigSources.Builder baseLoot(String summonSettings) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", """
                        language: en
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        %s
                        """.formatted(summonSettings));
    }
}
