package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.summon.PlantsMode;
import me.rakkoon69.rakunloots.config.summon.SummonRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBlockGroupsTest {

    private static final NameCatalog ACCEPTING = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            return Optional.of(writtenId);
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            if ("minecraft:flowers".equals(namespacedTag)) {
                return Set.of("minecraft:dandelion", "minecraft:poppy");
            }
            return Set.of();
        }
    };

    private static final NameCatalog WITH_OLD_NAMES = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            if ("GRASS".equals(writtenId)) {
                return Optional.of("minecraft:short_grass");
            }
            if ("UNKNOWN_BLOCK".equals(writtenId)) {
                return Optional.empty();
            }
            return Optional.of(writtenId.startsWith("minecraft:") ? writtenId : "minecraft:" + writtenId.toLowerCase());
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            return Set.of();
        }
    };

    @Test
    void shippedBlocksOnServer262SkipItsOptionalEntriesWithOneLowWarning() throws Exception {
        String blocks;
        try (java.io.InputStream in = ConfigBlockGroupsTest.class.getResourceAsStream("/blocks.yml")) {
            blocks = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        NameCatalog server262 = new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                return Optional.of("minecraft:grass".equals(writtenId) ? "minecraft:short_grass" : writtenId);
            }

            @Override
            public Set<String> tagMembers(String namespacedTag) {
                return "minecraft:tall_flowers".equals(namespacedTag) ? Set.of() : Set.of("minecraft:stone");
            }
        };

        ConfigSnapshot snapshot = ConfigLoader.load(base().blocks(blocks).build(), server262, WarningKinds.all());

        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_TAG
                || w.kind() == ConfigWarning.Kind.OLD_NAME));
        List<ConfigWarning> skipped = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES).toList();
        assertEquals(1, skipped.size());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, skipped.get(0).severity());
        assertEquals(List.of("?#minecraft:tall_flowers", "?minecraft:grass"), skipped.get(0).entries());
    }

    @Test
    void plainBlockIdsResolveAndNestedGroupsExpand() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          leaves:
                            - minecraft:oak_leaves
                            - minecraft:birch_leaves
                          canopy:
                            - '@leaves'
                            - minecraft:vine
                          forest:
                            - '@canopy'
                            - minecraft:oak_log
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertEquals(Set.of("minecraft:oak_leaves", "minecraft:birch_leaves"), snapshot.group("leaves").orElseThrow());
        assertEquals(
                Set.of("minecraft:oak_leaves", "minecraft:birch_leaves", "minecraft:vine"),
                snapshot.group("canopy").orElseThrow()
        );
        assertEquals(
                Set.of("minecraft:oak_leaves", "minecraft:birch_leaves", "minecraft:vine", "minecraft:oak_log"),
                snapshot.group("forest").orElseThrow()
        );
    }

    @Test
    void oldNameWarnsUnknownDropsAndRepeatsCountOnce() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          ground:
                            - GRASS
                            - UNKNOWN_BLOCK
                            - minecraft:dirt
                            - minecraft:dirt
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, WITH_OLD_NAMES, WarningKinds.all());

        assertEquals(Set.of("minecraft:short_grass", "minecraft:dirt"), snapshot.group("ground").orElseThrow());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME && "GRASS".equals(w.writtenId().orElse(null))));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_NAME && "UNKNOWN_BLOCK".equals(w.writtenId().orElse(null))));
    }

    @Test
    void missingBlocksFileExposesShippedGroups() {
        ConfigSnapshot snapshot = ConfigLoader.load(base().build(), ACCEPTING, WarningKinds.all());

        assertTrue(snapshot.group("solid").isPresent());
        assertTrue(snapshot.group("non_solid").isPresent());
        assertTrue(snapshot.group("plants").isPresent());
        assertTrue(snapshot.group("flowers").isPresent());
        assertTrue(snapshot.group("tall_plants").isPresent());
        assertTrue(snapshot.group("liquids").isPresent());
        assertTrue(snapshot.group("natural_ground").isPresent());
        assertTrue(snapshot.group("decorative").isPresent());
        assertTrue(snapshot.group("airdrop_blocks").isPresent());
    }

    @Test
    void ownerGroupReplacesShippedNameAndLeavesOthers() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          liquids:
                            - minecraft:water
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertEquals(Set.of("minecraft:water"), snapshot.group("liquids").orElseThrow());
        assertTrue(snapshot.group("solid").isPresent());
        assertFalse(snapshot.group("liquids").orElseThrow().contains("minecraft:lava"));
    }

    @Test
    void tagEntryExpandsAndUnknownTagIsDropped() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          pretty:
                            - '#minecraft:flowers'
                            - '#minecraft:no_such_tag'
                            - minecraft:stone
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertEquals(
                Set.of("minecraft:dandelion", "minecraft:poppy", "minecraft:stone"),
                snapshot.group("pretty").orElseThrow()
        );
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_TAG
                        && "#minecraft:no_such_tag".equals(w.writtenId().orElse(null))));
    }

    @Test
    void emptyCatalogSkipsNamesThatSomeVersionKnowsInTheLowLineNotAsUnknown() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          pretty:
                            - '#minecraft:flowers'
                            - minecraft:stone
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals(Set.of(), snapshot.group("pretty").orElseThrow());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_TAG
                        || w.kind() == ConfigWarning.Kind.UNKNOWN_NAME && w.writtenId().orElse("").startsWith("minecraft:")),
                snapshot.warnings().toString());
        List<String> skipped = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES)
                .flatMap(w -> w.entries().stream()).toList();
        assertTrue(skipped.containsAll(List.of("#minecraft:flowers", "minecraft:stone")), skipped.toString());
    }

    @Test
    void shippedGroupsUseTagsWhereTheyFit() {
        try (var in = ConfigBlockGroupsTest.class.getResourceAsStream("/blocks.yml")) {
            assert in != null;
            String text = new String(in.readAllBytes());
            assertTrue(text.contains("flowers:"), "shipped file names flowers");
            assertTrue(text.contains("#minecraft:"), "shipped groups use 1.18 tags where they fit");
            assertTrue(text.contains("minecraft:gravel"), "shipped groups list blocks by id otherwise");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void directSelfReferenceIsRefused() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          loop:
                            - '@loop'
                            - minecraft:stone
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertTrue(snapshot.group("loop").isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.GROUP_CYCLE
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.SEVERE
                        && "loop".equals(w.groupName().orElse(null))
                        && w.cycle().equals(List.of("loop"))));
    }

    @Test
    void threeGroupLoopRefusesEveryMemberRegardlessOfOrder() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          c:
                            - '@a'
                          a:
                            - '@b'
                          b:
                            - '@c'
                          safe:
                            - minecraft:stone
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertTrue(snapshot.group("a").isEmpty());
        assertTrue(snapshot.group("b").isEmpty());
        assertTrue(snapshot.group("c").isEmpty());
        assertEquals(Set.of("minecraft:stone"), snapshot.group("safe").orElseThrow());
        for (String name : List.of("a", "b", "c")) {
            assertTrue(snapshot.warnings().stream().anyMatch(w ->
                    w.kind() == ConfigWarning.Kind.GROUP_CYCLE
                            && name.equals(w.groupName().orElse(null))
                            && w.cycle().equals(List.of("a", "b", "c"))));
        }
    }

    @Test
    void refusedOrUnknownGroupEntryDropsAndKeepsRest() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          bad:
                            - '@bad'
                          mixed:
                            - '@bad'
                            - '@missing'
                            - minecraft:dirt
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertEquals(Set.of("minecraft:dirt"), snapshot.group("mixed").orElseThrow());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_GROUP
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && "@bad".equals(w.writtenId().orElse(null))));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_GROUP
                        && "@missing".equals(w.writtenId().orElse(null))));
    }

    @Test
    void emptyGroupLoadsEmptyWithLowWarning() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          nothing: []
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertEquals(Set.of(), snapshot.group("nothing").orElseThrow());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.EMPTY_GROUP
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.LOW
                        && "nothing".equals(w.groupName().orElse(null))));
    }

    @Test
    void oldNonSolidFillsWhenBlocksLacksItAndBlocksWinsWhenBothExist() {
        ConfigSources fill = base()
                .globals("""
                        language: en
                        non_solid_blocks:
                          - GRASS
                          - minecraft:air
                        """)
                .blocks("""
                        groups:
                          liquids:
                            - minecraft:water
                        """)
                .build();
        ConfigSnapshot filled = ConfigLoader.load(fill, WITH_OLD_NAMES, WarningKinds.all());
        assertEquals(Set.of("minecraft:short_grass", "minecraft:air"), filled.group("non_solid").orElseThrow());
        assertTrue(filled.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_KEY && "non_solid_blocks".equals(w.key().orElse(null))));

        ConfigSources both = base()
                .globals("""
                        language: en
                        non_solid_blocks:
                          - GRASS
                        """)
                .blocks("""
                        groups:
                          non_solid:
                            - minecraft:air
                        """)
                .build();
        ConfigSnapshot winner = ConfigLoader.load(both, WITH_OLD_NAMES, WarningKinds.all());
        assertEquals(Set.of("minecraft:air"), winner.group("non_solid").orElseThrow());
        assertTrue(winner.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_KEY && "non_solid_blocks".equals(w.key().orElse(null))));
    }

    @Test
    void illegalGroupNameIsDropped() {
        ConfigSources sources = base()
                .blocks("""
                        groups:
                          Airdrop-Blocks:
                            - minecraft:stone
                          fine:
                            - minecraft:dirt
                        """)
                .build();
        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertFalse(snapshot.group("Airdrop-Blocks").isPresent());
        assertEquals(Set.of("minecraft:dirt"), snapshot.group("fine").orElseThrow());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.ILLEGAL_GROUP_NAME
                        && "Airdrop-Blocks".equals(w.groupName().orElse(null))));
    }

    @Test
    void editedPlantsGroupReachesSummonRulesAndPlacement() {
        ConfigSnapshot snapshot = ConfigLoader.load(base()
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
                .build(), ACCEPTING, WarningKinds.all());

        SummonRules rules = snapshot.summonRules("demo").orElseThrow();
        assertEquals(Set.of("minecraft:dandelion"), rules.plantsBlocks());
        assertEquals(PlantsMode.ON_TOP, rules.plantsMode());
    }

    @Test
    void refusedSolidGroupWarnsAndLeavesEnclosedEmpty() {
        ConfigSnapshot snapshot = ConfigLoader.load(base()
                .blocks("""
                        groups:
                          solid:
                            - '@solid'
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
                          summon_type: deep_buried
                          deep_buried:
                            enclosed: true
                        """)
                .build(), ACCEPTING, WarningKinds.all());

        assertTrue(snapshot.summonRules("demo").orElseThrow().solidBlocks().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.GROUP_CYCLE && "solid".equals(w.groupName().orElse(null))));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_GROUP
                        && "solid".equals(w.groupName().orElse(null))
                        && "demo".equals(w.lootId().orElse(null))));
    }

    private static ConfigSources.Builder base() {
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
                        """);
    }
}
