package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigNamesTest {

    @Test
    void oldIdResolvesToCatalogCurrentIdAndReportsLowWarningNamingBoth() {
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
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.LOW
                        && w.writtenId().equals(Optional.of("GRASS"))
                        && w.currentId().equals(Optional.of("SHORT_GRASS"))
        ));
    }

    @Test
    void currentIdProducesNoNameWarning() {
        NameCatalog catalog = written -> Optional.of(written);

        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", """
                        language: en
                        loot_block_settings:
                          material: BARREL
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());

        assertEquals(Optional.of("BARREL"), snapshot.resolvedName("BARREL"));
        assertTrue(snapshot.warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME || w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
        ));
    }

    @Test
    void unknownIdIsLeftAsWrittenAndWarned() {
        NameCatalog catalog = written -> Optional.empty();

        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", """
                        language: en
                        loot_block_settings:
                          material: NOT_A_REAL_BLOCK
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());

        assertEquals(Optional.of("NOT_A_REAL_BLOCK"), snapshot.resolvedName("NOT_A_REAL_BLOCK"));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && w.writtenId().equals(Optional.of("NOT_A_REAL_BLOCK"))
        ));
    }

    @Test
    void vanillaItemTypeIdIsResolvedFromLootContent() {
        NameCatalog catalog = written -> {
            if ("grass".equals(written)) {
                return Optional.of("short_grass");
            }
            return Optional.of(written);
        };

        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", """
                        language: en
                        loot_content_settings:
                          loot_content:
                            gem:
                              item:
                                type: 'Vanilla:grass'
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());

        assertEquals(Optional.of("short_grass"), snapshot.resolvedName("grass"));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_NAME
                        && w.writtenId().equals(Optional.of("grass"))
                        && w.currentId().equals(Optional.of("short_grass"))
        ));
    }
}
