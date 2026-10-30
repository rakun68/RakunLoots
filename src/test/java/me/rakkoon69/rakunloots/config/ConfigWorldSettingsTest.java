package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWorldSettingsTest {

    @Test
    void writtenTimingLimitAndLootEntryAreReadableForThatWorld() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds("""
                        worlds:
                          world:
                            time_between_loot_spawn: 420
                            loot_spawn_limit: 5
                            loots:
                              airdrops: 100
                        """)
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        WorldSettings world = snapshot.world("world").orElseThrow();
        assertEquals(420, world.timeBetweenLootSpawn());
        assertEquals(5, world.lootSpawnLimit());
        assertEquals(100, world.lootChance("airdrops").orElseThrow());
    }

    @Test
    void missingKeyInsideWorldReturnsModuleDefault() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds("""
                        worlds:
                          world:
                            loots:
                              airdrops: 50
                        """)
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        WorldSettings world = snapshot.world("world").orElseThrow();
        assertEquals(WorldSettings.DEFAULT_TIME_BETWEEN_LOOT_SPAWN, world.timeBetweenLootSpawn());
        assertEquals(WorldSettings.DEFAULT_LOOT_SPAWN_LIMIT, world.lootSpawnLimit());
        assertEquals(50, world.lootChance("airdrops").orElseThrow());
    }

    @Test
    void missingWorldSettingsFileReturnsNoWorldsAndReportsHighWarning() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertTrue(snapshot.worlds().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.MISSING_WORLDS
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
        ));
    }
}
