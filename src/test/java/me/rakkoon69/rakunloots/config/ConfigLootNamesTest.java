package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLootNamesTest {

    @Test
    void oldBlockNameStoresCurrentIdWarnsAndLeavesFileUnchanged() {
        NameCatalog catalog = written -> {
            if ("GRASS".equals(written)) {
                return Optional.of("short_grass");
            }
            return Optional.of(written);
        };
        String yaml = """
                language: en
                open_mode: drop
                block: GRASS
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """;
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", yaml)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertEquals("short_grass", snapshot.loot("demo").orElseThrow().block());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME
                        && w.writtenId().equals(Optional.of("GRASS"))
                        && w.currentId().equals(Optional.of("short_grass"))
        ));
        assertEquals(yaml, sources.loots().get("demo"));
    }

    @Test
    void currentBlockIdLoadsWithNoNameWarning() {
        NameCatalog catalog = written -> Optional.of(written);
        ConfigSources sources = sources("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertEquals("barrel", snapshot.loot("demo").orElseThrow().block());
        assertTrue(snapshot.warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME || w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
        ));
    }

    @Test
    void unknownBlockSkipsLootWithUnknownNameWarning() {
        NameCatalog catalog = written -> Optional.empty();
        ConfigSources sources = sources("""
                open_mode: drop
                block: not_a_block
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertTrue(snapshot.loot("demo").isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
                        && w.writtenId().equals(Optional.of("not_a_block"))
        ));
    }

    @Test
    void missingOrNonTextBlockSkipsWithMissingBlockWarningNotUnknownName() {
        NameCatalog catalog = written -> Optional.of(written);
        ConfigSources missing = sources("""
                open_mode: drop
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources nonText = sources("""
                open_mode: drop
                block: 12
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot missingSnap = ConfigLoader.load(missing, catalog, WarningKinds.all());
        ConfigSnapshot nonTextSnap = ConfigLoader.load(nonText, catalog, WarningKinds.all());
        assertTrue(missingSnap.loot("demo").isEmpty());
        assertTrue(nonTextSnap.loot("demo").isEmpty());
        assertTrue(missingSnap.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.MISSING_BLOCK));
        assertTrue(nonTextSnap.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.MISSING_BLOCK));
        assertTrue(missingSnap.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME));
        assertTrue(nonTextSnap.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME));
    }

    @Test
    void oldVanillaItemIdStoresCurrentKeepsEntry() {
        NameCatalog catalog = written -> {
            if ("GRASS".equals(written)) {
                return Optional.of("short_grass");
            }
            return Optional.of(written);
        };
        ConfigSources sources = sources("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: GRASS
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertEquals("short_grass", snapshot.loot("demo").orElseThrow().roll().entries().get(0).item().id());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME
                        && w.writtenId().equals(Optional.of("GRASS"))
        ));
    }

    @Test
    void unknownVanillaItemIdStaysAsWrittenKeepsEntry() {
        NameCatalog catalog = written -> {
            if ("barrel".equals(written)) {
                return Optional.of("barrel");
            }
            return Optional.empty();
        };
        ConfigSources sources = sources("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: not_an_item
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertEquals("not_an_item", snapshot.loot("demo").orElseThrow().roll().entries().get(0).item().id());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
                        && w.writtenId().equals(Optional.of("not_an_item"))
        ));
    }

    @Test
    void nonVanillaItemIdIsNotPassedThroughCatalog() {
        NameCatalog catalog = written -> {
            if ("SWORD:CUTLASS".equals(written)) {
                return Optional.of("renamed");
            }
            return Optional.of(written);
        };
        ConfigSources sources = sources("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        source: mmoitems
                        id: SWORD:CUTLASS
                """);

        assertEquals("SWORD:CUTLASS",
                ConfigLoader.load(sources, catalog, WarningKinds.all()).loot("demo").orElseThrow().roll().entries().get(0).item().id());
        assertTrue(ConfigLoader.load(sources, catalog, WarningKinds.all()).warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME && w.writtenId().equals(Optional.of("SWORD:CUTLASS"))
        ));
    }

    @Test
    void catalogIdEqualToWrittenIdDoesNotWarn() {
        NameCatalog catalog = written -> Optional.of(written);
        ConfigSources sources = sources("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        assertTrue(ConfigLoader.load(sources, catalog, WarningKinds.all()).warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME || w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
        ));
    }

    @Test
    void headDataKeptOnlyForPlayerHeadIgnoringCase() {
        NameCatalog catalog = written -> Optional.of(written);
        ConfigSources head = sources("""
                open_mode: drop
                block: PLAYER_HEAD
                head_data: head-texture
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources wall = sources("""
                open_mode: drop
                block: player_wall_head
                head_data: head-texture
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources barrel = sources("""
                open_mode: drop
                block: barrel
                head_data: head-texture
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        assertEquals(Optional.of("head-texture"), ConfigLoader.load(head, catalog, WarningKinds.all()).loot("demo").orElseThrow().headData());
        assertTrue(ConfigLoader.load(wall, catalog, WarningKinds.all()).loot("demo").orElseThrow().headData().isEmpty());
        assertTrue(ConfigLoader.load(barrel, catalog, WarningKinds.all()).loot("demo").orElseThrow().headData().isEmpty());
        assertTrue(ConfigLoader.load(wall, catalog, WarningKinds.all()).warnings().stream()
                .noneMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_HEAD_DATA));
        assertTrue(ConfigLoader.load(barrel, catalog, WarningKinds.all()).warnings().stream()
                .noneMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_HEAD_DATA));
    }

    /**
     * The server catalog resolves {@code player_head} to {@code minecraft:player_head}, so the head data must survive
     * a namespaced id.
     */
    @Test
    void headDataKeptOnANamespacedPlayerHead() {
        NameCatalog namespaced = written -> Optional.of(written.contains(":") ? written : "minecraft:" + written);
        ConfigSources head = sources("""
                open_mode: drop
                block: player_head
                head_data: head-texture
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        Loot loot = ConfigLoader.load(head, namespaced, WarningKinds.all()).loot("demo").orElseThrow();

        assertEquals("minecraft:player_head", loot.block());
        assertEquals(Optional.of("head-texture"), loot.headData());
    }

    @Test
    void omittedHeadDataOnPlayerHeadLoadsWithNoExtraSkinNoWarning() {
        NameCatalog catalog = written -> Optional.of(written);
        ConfigSources sources = sources("""
                open_mode: drop
                block: player_head
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertTrue(snapshot.loot("demo").orElseThrow().headData().isEmpty());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_HEAD_DATA));
    }

    @Test
    void nonTextHeadDataIsIgnoredWithHighWarningLootStillLoads() {
        NameCatalog catalog = written -> Optional.of(written);
        ConfigSources sources = sources("""
                open_mode: drop
                block: player_head
                head_data: 12
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertTrue(snapshot.loot("demo").isPresent());
        assertTrue(snapshot.loot("demo").orElseThrow().headData().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_HEAD_DATA));
    }

    @Test
    void nameCollectionFromOldLootKeysStillWorks() {
        NameCatalog catalog = written -> {
            if ("GRASS".equals(written)) {
                return Optional.of("SHORT_GRASS");
            }
            return Optional.of(written);
        };
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", """
                        language: en
                        loot_block_settings:
                          material: GRASS
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());
        assertEquals(Optional.of("SHORT_GRASS"), snapshot.resolvedName("GRASS"));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME
                        && w.writtenId().equals(Optional.of("GRASS"))
        ));
    }

    private static ConfigSources sources(String body) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n" + body)
                .build();
    }
}
