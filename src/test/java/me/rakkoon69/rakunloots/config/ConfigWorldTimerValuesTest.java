package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world timer keys of {@code worlds.yml}: a bad value warns high with the world and the key and the default is
 * used, a missing key keeps its default quietly.
 */
class ConfigWorldTimerValuesTest {

    @Test
    void everyBadValueWarnsHighAndUsesItsDefault() {
        ConfigSnapshot snapshot = load("""
                worlds:
                  world:
                    minimum_player_to_spawn_loot: many
                    also_count_players_from: world_nether
                    public_location_horizontal_offset: -5
                    public_location_vertical_offset: far
                    broadcast_remove: maybe
                    loots:
                      demo: 1
                """);

        WorldSettings world = snapshot.world("world").orElseThrow();
        assertEquals(WorldSettings.DEFAULT_MINIMUM_PLAYER_TO_SPAWN_LOOT, world.minimumPlayerToSpawnLoot());
        assertEquals(List.of(), world.alsoCountPlayersFrom());
        assertEquals(WorldSettings.DEFAULT_PUBLIC_LOCATION_OFFSET, world.publicLocationHorizontalOffset());
        assertEquals(WorldSettings.DEFAULT_PUBLIC_LOCATION_OFFSET, world.publicLocationVerticalOffset());
        assertFalse(world.broadcastRemove());
        assertEquals(Set.of("minimum_player_to_spawn_loot", "also_count_players_from",
                        "public_location_horizontal_offset", "public_location_vertical_offset", "broadcast_remove"),
                unusable(snapshot));
    }

    @Test
    void aBadEntryInAlsoCountPlayersFromWarnsAndTheGoodOnesStay() {
        ConfigSnapshot snapshot = load("""
                worlds:
                  world:
                    also_count_players_from:
                      - world_nether
                      - [nested]
                    loots:
                      demo: 1
                """);

        assertEquals(List.of("world_nether"), snapshot.world("world").orElseThrow().alsoCountPlayersFrom());
        assertEquals(Set.of("also_count_players_from"), unusable(snapshot));
    }

    @Test
    void missingKeysKeepTheirDefaultsWithoutWarning() {
        ConfigSnapshot snapshot = load("""
                worlds:
                  world:
                    loots:
                      demo: 1
                """);

        WorldSettings world = snapshot.world("world").orElseThrow();
        assertEquals(WorldSettings.DEFAULT_MINIMUM_PLAYER_TO_SPAWN_LOOT, world.minimumPlayerToSpawnLoot());
        assertEquals(Set.of(), unusable(snapshot));
    }

    private static Set<String> unusable(ConfigSnapshot snapshot) {
        List<ConfigWarning> found = snapshot.warnings().stream()
                .filter(warning -> warning.kind() == ConfigWarning.Kind.UNUSABLE_WORLD_VALUE)
                .toList();
        for (ConfigWarning warning : found) {
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
            assertEquals(Optional.of("world"), warning.worldName());
        }
        return found.stream().map(warning -> warning.key().orElseThrow()).collect(Collectors.toSet());
    }

    private static ConfigSnapshot load(String worlds) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds(worlds)
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
                .build(), written -> Optional.of(written), WarningKinds.all());
    }
}
