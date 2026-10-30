package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code debug} key of config.yml: shipped off, true turns debug mode on, and a value that is not true or false
 * leaves it off with a high warning.
 */
class ConfigDebugTest {

    private static ConfigSnapshot load(String globals) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(globals)
                .language("en", "still_locked: locked\n")
                .build(), NameCatalog.empty(), WarningKinds.all());
    }

    @Test
    void theShippedConfigShipsDebugOffAndLoadsWithoutAWarningForIt() throws IOException {
        String shipped;
        try (InputStream in = ConfigDebugTest.class.getResourceAsStream("/config.yml")) {
            shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ConfigSnapshot snapshot = load(shipped);

        assertTrue(shipped.lines().anyMatch(line -> line.equals("debug: false")), "the key ships");
        assertFalse(snapshot.debug());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DEBUG));
    }

    @Test
    void aMissingKeyIsOff() {
        assertFalse(load("language: en\n").debug());
    }

    @Test
    void trueTurnsDebugOn() {
        assertTrue(load("language: en\ndebug: true\n").debug());
        assertTrue(load("language: en\ndebug: 'TRUE'\n").debug());
    }

    @Test
    void aValueThatIsNotTrueOrFalseStaysOffWithAHighWarning() {
        for (String bad : new String[]{"maybe", "5", "[true]"}) {
            ConfigSnapshot snapshot = load("language: en\ndebug: " + bad + "\n");

            assertFalse(snapshot.debug(), bad);
            assertEquals(1, snapshot.warnings().stream().filter(w ->
                    w.kind() == ConfigWarning.Kind.UNUSABLE_DEBUG
                            && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH).count(), bad);
        }
    }
}
