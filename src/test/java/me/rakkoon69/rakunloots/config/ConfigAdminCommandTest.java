package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAdminCommandTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void shippedConfigNamesTheAdminCommandAndItsAliasesWithoutWarning() throws Exception {
        String shipped;
        try (InputStream in = ConfigAdminCommandTest.class.getResourceAsStream("/config.yml")) {
            shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ConfigSnapshot snapshot = load(shipped);

        assertEquals("rkl", snapshot.adminCommand().name());
        assertEquals(List.of("rkloots"), snapshot.adminCommand().aliases());
        assertTrue(adminWarnings(snapshot).isEmpty());
    }

    @Test
    void missingBlockUsesTheDefaultsWithoutWarning() {
        ConfigSnapshot snapshot = load("language: en\n");

        assertEquals(AdminCommandSettings.defaults().name(), snapshot.adminCommand().name());
        assertEquals(AdminCommandSettings.defaults().aliases(), snapshot.adminCommand().aliases());
        assertTrue(adminWarnings(snapshot).isEmpty());
    }

    @Test
    void writtenNameAndAliasesAreRead() {
        ConfigSnapshot snapshot = load("""
                admin_command:
                  name: Takeitems
                  aliases:
                    - " Mine "
                    - owed
                """);

        assertEquals("takeitems", snapshot.adminCommand().name());
        assertEquals(List.of("mine", "owed"), snapshot.adminCommand().aliases());
        assertTrue(adminWarnings(snapshot).isEmpty());
    }

    @Test
    void emptyAliasesAreAllowed() {
        ConfigSnapshot snapshot = load("admin_command:\n  name: take\n  aliases: []\n");

        assertEquals("take", snapshot.adminCommand().name());
        assertEquals(List.of(), snapshot.adminCommand().aliases());
        assertTrue(adminWarnings(snapshot).isEmpty());
    }

    @Test
    void badNameWarnsHighAndUsesTheDefaultName() {
        for (String name : List.of("\"\"", "two words", "\"/slash\"", "3", "[a]")) {
            ConfigSnapshot snapshot = load("admin_command:\n  name: " + name + "\n  aliases: [owed]\n");

            assertEquals(AdminCommandSettings.defaults().name(), snapshot.adminCommand().name(), name);
            assertEquals(List.of("owed"), snapshot.adminCommand().aliases(), name);
            assertSingleUnusable(snapshot, "name");
        }
    }

    @Test
    void badAliasIsLeftOutWithAHighWarningAndAliasThatRepeatsTheNameOrAnotherIsDropped() {
        ConfigSnapshot snapshot = load("""
                admin_command:
                  name: take
                  aliases: [owed, "bad alias", take, owed, ok]
                """);

        assertEquals(List.of("owed", "ok"), snapshot.adminCommand().aliases());
        assertSingleUnusable(snapshot, "aliases");
    }

    @Test
    void aliasesThatAreNotAListWarnHighAndUseTheDefaultAliases() {
        ConfigSnapshot snapshot = load("admin_command:\n  name: take\n  aliases: owed\n");

        assertEquals("take", snapshot.adminCommand().name());
        assertEquals(AdminCommandSettings.defaults().aliases(), snapshot.adminCommand().aliases());
        assertSingleUnusable(snapshot, "aliases");
    }

    @Test
    void blockThatIsNotABlockWarnsHighAndUsesTheDefaults() {
        ConfigSnapshot snapshot = load("admin_command: take\n");

        assertEquals(AdminCommandSettings.defaults().name(), snapshot.adminCommand().name());
        assertSingleUnusable(snapshot, "admin_command");
    }

    private static void assertSingleUnusable(ConfigSnapshot snapshot, String key) {
        List<ConfigWarning> warnings = adminWarnings(snapshot);
        assertEquals(1, warnings.size(), key);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warnings.get(0).severity());
        assertEquals(Optional.of(key), warnings.get(0).key());
    }

    private static List<ConfigWarning> adminWarnings(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(warning -> warning.kind() == ConfigWarning.Kind.UNUSABLE_ADMIN_COMMAND_VALUE)
                .toList();
    }

    private static ConfigSnapshot load(String globals) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(globals)
                .language("en", "still_locked: locked\n")
                .build(), ACCEPTING, WarningKinds.all());
    }
}
