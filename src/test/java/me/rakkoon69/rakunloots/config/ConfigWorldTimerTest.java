package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWorldTimerTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    /**
     * Acceptance: one test loads the shipped worlds.yml and checks the timer, the player count, the spawn limit, and
     * the location broadcast keys it ships.
     */
    @Test
    void shippedWorldsFileGivesTheTimerItsSettings() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds(resource("/worlds.yml"))
                .build(), ACCEPTING, WarningKinds.all());

        WorldSettings world = snapshot.world("world").orElseThrow();
        assertEquals(420, world.timeBetweenLootSpawn());
        assertEquals(420, world.timerSeconds());
        assertEquals(5, world.lootSpawnLimit());
        assertEquals(1, world.minimumPlayerToSpawnLoot());
        assertEquals(List.of("world_nether", "world_the_end"), world.alsoCountPlayersFrom());
        assertEquals(40, world.publicLocationHorizontalOffset());
        assertEquals(15, world.publicLocationVerticalOffset());
        assertTrue(world.broadcastRemove());
        assertEquals(100, world.lootChance("airdrops").orElseThrow());
    }

    /**
     * Acceptance: the world timer is never under 5 seconds.
     */
    @Test
    void theTimerIsNeverUnderFiveSeconds() {
        for (String written : new String[]{"0", "1", "4", "-30"}) {
            WorldSettings world = world("time_between_loot_spawn: " + written + "\n");
            assertEquals(5, world.timerSeconds(), written);
        }
        assertEquals(5, world("time_between_loot_spawn: 5\n").timerSeconds());
        assertEquals(6, world("time_between_loot_spawn: 6\n").timerSeconds());
        assertEquals(5, world("").timerSeconds(), "left out");
    }

    @Test
    void leftOutKeysTakeTheModuleDefaults() {
        WorldSettings world = world("");

        assertEquals(WorldSettings.DEFAULT_MINIMUM_PLAYER_TO_SPAWN_LOOT, world.minimumPlayerToSpawnLoot());
        assertTrue(world.alsoCountPlayersFrom().isEmpty());
        assertEquals(0, world.publicLocationHorizontalOffset());
        assertEquals(0, world.publicLocationVerticalOffset());
        assertFalse(world.broadcastRemove());
    }

    @Test
    void anOffsetBelowZeroIsZero() {
        WorldSettings world = world("public_location_horizontal_offset: -4\npublic_location_vertical_offset: -1\n");

        assertEquals(0, world.publicLocationHorizontalOffset());
        assertEquals(0, world.publicLocationVerticalOffset());
    }

    /**
     * Acceptance: the owner remove message comes back to the loot file under the key the old file used.
     */
    @Test
    void theOwnerRemoveMessageFlagIsReadFromTheLootFileAndIsOffWhenLeftOut() {
        assertTrue(loot("message_owner_on_remove_enable: true\n").loot("demo").orElseThrow().messageOwnerOnRemove());
        assertFalse(loot("message_owner_on_remove_enable: false\n").loot("demo").orElseThrow().messageOwnerOnRemove());
        ConfigSnapshot left = loot("");
        assertFalse(left.loot("demo").orElseThrow().messageOwnerOnRemove());
        assertTrue(left.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_KEY));
    }

    @Test
    void anOwnerRemoveFlagThatIsNotABooleanIsOffWithAHighWarning() {
        ConfigSnapshot snapshot = loot("message_owner_on_remove_enable: maybe\n");

        assertFalse(snapshot.loot("demo").orElseThrow().messageOwnerOnRemove());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNUSABLE_MESSAGE_OWNER_ON_REMOVE
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && w.lootId().equals(Optional.of("demo"))));
    }

    private static WorldSettings world(String keys) {
        StringBuilder body = new StringBuilder("worlds:\n  world:\n");
        for (String line : keys.split("\n")) {
            if (!line.isEmpty()) {
                body.append("    ").append(line).append('\n');
            }
        }
        body.append("    loots:\n      demo: 1\n");
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .worlds(body.toString())
                .build(), ACCEPTING, WarningKinds.all()).world("world").orElseThrow();
    }

    private static ConfigSnapshot loot(String extra) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n" + extra + """
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

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigWorldTimerTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
