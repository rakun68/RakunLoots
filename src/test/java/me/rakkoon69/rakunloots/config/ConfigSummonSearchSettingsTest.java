package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.config.summon.SummonSearchSettings;
import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import me.rakkoon69.rakunloots.config.summon.SummonRing;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSummonSearchSettingsTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    /**
     * Phase 8d: the shipped worlds.yml says generate, because load finds no spot on a fresh world, and its comment
     * names the recommended policies and what skip needs.
     */
    @Test
    void shippedWorldsFileSaysGenerateAndItsCommentExplainsSkip() throws java.io.IOException {
        String shipped;
        try (java.io.InputStream in = ConfigSummonSearchSettingsTest.class.getResourceAsStream("/worlds.yml")) {
            shipped = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds(shipped)
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals(UnloadedChunkPolicy.GENERATE,
                snapshot.world("world").orElseThrow().summonSearch().unloadedChunks());
        assertTrue(shipped.contains("load or generate is recommended"), shipped);
        assertTrue(shipped.contains("skip works only with mode: spawn and a spawn ring where chunks stay loaded, "
                + "such as spawn chunks or busy areas"), shipped);
        assertTrue(shipped.contains("Under skip a box in an unloaded chunk waits for a player to load it before its "
                + "life can end"), shipped);
    }

    @Test
    void newSectionLoadsWithDefaultsForMissingKeys() {
        ConfigSnapshot snapshot = loadWorlds("""
                worlds:
                  world:
                    summon_search:
                      mode: smart
                    loots:
                      airdrops: 100
                """);

        SummonSearchSettings search = snapshot.world("world").orElseThrow().summonSearch();
        assertEquals(SummonSearchSettings.Mode.SMART, search.mode());
        assertEquals(UnloadedChunkPolicy.LOAD, search.unloadedChunks());
        assertEquals(SummonSearchSettings.DEFAULT_SPAWN_RING, search.spawnRing());
        assertEquals(SummonSearchSettings.DEFAULT_SMART_RING, search.smartRing());
        assertEquals(20, search.pickBias());
        assertEquals(70, search.medianPull());
        assertEquals(300, search.idleSeconds());
        assertEquals(SummonSearchSettings.WhenAllAbsent.SPAWN, search.whenAllAbsent());
    }

    @Test
    void outOfRangeValuesWarnHighAndUseDefaults() {
        ConfigSnapshot snapshot = loadWorlds("""
                worlds:
                  world:
                    summon_search:
                      smart:
                        pick_bias: 200
                        median_pull: 10
                        idle_seconds: -1
                        when_all_absent: maybe
                    loots:
                      airdrops: 1
                """);

        SummonSearchSettings search = snapshot.world("world").orElseThrow().summonSearch();
        assertEquals(20, search.pickBias());
        assertEquals(70, search.medianPull());
        assertEquals(300, search.idleSeconds());
        assertEquals(SummonSearchSettings.WhenAllAbsent.SPAWN, search.whenAllAbsent());
        assertTrue(snapshot.warnings().stream().filter(w ->
                w.kind() == ConfigWarning.Kind.UNUSABLE_SUMMON_SEARCH).count() >= 4);
    }

    @Test
    void oldKeysMapAndWarnAndNewSectionWins() {
        ConfigSnapshot oldOnly = loadWorlds("""
                worlds:
                  world:
                    smart_summon: true
                    smart_summon_distance: 250
                    only_loaded_chunks: true
                    max_distance_to_world_spawn_point_x: 100
                    max_distance_to_world_spawn_point_z: 500
                    loots:
                      airdrops: 1
                """);
        SummonSearchSettings fromOld = oldOnly.world("world").orElseThrow().summonSearch();
        assertEquals(SummonSearchSettings.Mode.SMART, fromOld.mode());
        // smart+skip warns and becomes load
        assertEquals(UnloadedChunkPolicy.LOAD, fromOld.unloadedChunks());
        assertEquals(new SummonRing(32, 250), fromOld.smartRing());
        assertEquals(new SummonRing(0, 500), fromOld.spawnRing());
        assertTrue(oldOnly.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.OLD_KEY && "smart_summon".equals(w.key().orElse(null))));
        assertTrue(oldOnly.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.SMART_WITH_SKIP));

        ConfigSnapshot both = loadWorlds("""
                worlds:
                  world:
                    smart_summon: true
                    only_loaded_chunks: true
                    max_distance_to_world_spawn_point_x: 100
                    summon_search:
                      mode: spawn
                      unloaded_chunks: generate
                      spawn_ring:
                        min: 10
                        max: 50
                    loots:
                      airdrops: 1
                """);
        SummonSearchSettings winner = both.world("world").orElseThrow().summonSearch();
        assertEquals(SummonSearchSettings.Mode.SPAWN, winner.mode());
        assertEquals(UnloadedChunkPolicy.GENERATE, winner.unloadedChunks());
        assertEquals(new SummonRing(10, 50), winner.spawnRing());
        assertTrue(both.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.OLD_KEY));
    }

    @Test
    void partialNewSectionKeepsOldRings() {
        ConfigSnapshot snapshot = loadWorlds("""
                worlds:
                  world:
                    max_distance_to_world_spawn_point_x: 3000
                    smart_summon_distance: 180
                    summon_search:
                      mode: smart
                    loots:
                      airdrops: 1
                """);
        SummonSearchSettings search = snapshot.world("world").orElseThrow().summonSearch();
        assertEquals(new SummonRing(0, 3000), search.spawnRing());
        assertEquals(new SummonRing(32, 180), search.smartRing());
        assertEquals(SummonSearchSettings.Mode.SMART, search.mode());
    }

    @Test
    void smartWithSkipWarnsAndReadsAsLoad() {
        ConfigSnapshot snapshot = loadWorlds("""
                worlds:
                  world:
                    summon_search:
                      mode: smart
                      unloaded_chunks: skip
                    loots:
                      airdrops: 1
                """);
        assertEquals(UnloadedChunkPolicy.LOAD, snapshot.world("world").orElseThrow().summonSearch().unloadedChunks());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.SMART_WITH_SKIP));
    }

    @Test
    void smartWithSkipIsRejectedByTheConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new SummonSearchSettings(
                SummonSearchSettings.Mode.SMART,
                UnloadedChunkPolicy.SKIP,
                SummonSearchSettings.DEFAULT_SPAWN_RING,
                SummonSearchSettings.DEFAULT_SMART_RING,
                SummonSearchSettings.DEFAULT_PICK_BIAS,
                SummonSearchSettings.DEFAULT_MEDIAN_PULL,
                SummonSearchSettings.DEFAULT_IDLE_SECONDS,
                SummonSearchSettings.DEFAULT_WHEN_ALL_ABSENT
        ));
    }

    private static ConfigSnapshot loadWorlds(String worldsYaml) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds(worldsYaml)
                .loot("demo", """
                        language: en
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build(), ACCEPTING, WarningKinds.all());
    }
}
