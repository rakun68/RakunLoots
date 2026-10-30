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

class ConfigClaimCommandTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void shippedConfigNamesTheClaimCommandAndItsAliasesWithoutWarning() throws Exception {
        String shipped;
        try (InputStream in = ConfigClaimCommandTest.class.getResourceAsStream("/config.yml")) {
            shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ConfigSnapshot snapshot = load(shipped);

        assertEquals("claimsummon", snapshot.claimCommand().name());
        assertEquals(List.of("rklclaim"), snapshot.claimCommand().aliases());
        assertTrue(claimWarnings(snapshot).isEmpty());
    }

    /**
     * Acceptance: the shipped config.yml says {@code permission_default: true}, read without a warning.
     */
    @Test
    void shippedConfigGivesTheClaimPermissionToEveryoneWithoutWarning() throws Exception {
        String shipped;
        try (InputStream in = ConfigClaimCommandTest.class.getResourceAsStream("/config.yml")) {
            shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(shipped.contains("permission_default: true"), "the shipped file writes the key");

        ConfigSnapshot snapshot = load(shipped);

        assertEquals(DefaultAccess.EVERYONE, snapshot.claimCommand().permissionDefault());
        assertTrue(claimWarnings(snapshot).isEmpty());
    }

    @Test
    void permissionDefaultReadsTheValuesBukkitGivesAPermission() {
        assertAccess("true", DefaultAccess.EVERYONE);
        assertAccess("false", DefaultAccess.NOBODY);
        assertAccess("op", DefaultAccess.OPERATORS);
        assertAccess("not op", DefaultAccess.NON_OPERATORS);
        assertAccess("\"not op\"", DefaultAccess.NON_OPERATORS);
        assertAccess("\"Not_Op\"", DefaultAccess.NON_OPERATORS);
        assertAccess("\"!op\"", DefaultAccess.NON_OPERATORS);
        assertAccess("OP", DefaultAccess.OPERATORS);
    }

    @Test
    void missingPermissionDefaultIsEveryoneWithoutWarning() {
        ConfigSnapshot snapshot = load("claim_command:\n  name: take\n");

        assertEquals(DefaultAccess.EVERYONE, snapshot.claimCommand().permissionDefault());
        assertTrue(claimWarnings(snapshot).isEmpty());
    }

    @Test
    void permissionDefaultThatNamesNothingWarnsHighAndIsEveryone() {
        for (String value : List.of("sometimes", "7", "[op]", "\"\"")) {
            ConfigSnapshot snapshot = load("claim_command:\n  name: take\n  permission_default: " + value + "\n");

            assertEquals(DefaultAccess.EVERYONE, snapshot.claimCommand().permissionDefault(), value);
            assertSingleUnusable(snapshot, "permission_default");
        }
    }

    private static void assertAccess(String written, DefaultAccess expected) {
        ConfigSnapshot snapshot = load("claim_command:\n  name: take\n  permission_default: " + written + "\n");

        assertEquals(expected, snapshot.claimCommand().permissionDefault(), written);
        assertTrue(claimWarnings(snapshot).isEmpty(), written);
    }

    @Test
    void missingBlockUsesTheDefaultsWithoutWarning() {
        ConfigSnapshot snapshot = load("language: en\n");

        assertEquals(ClaimCommandSettings.defaults().name(), snapshot.claimCommand().name());
        assertEquals(ClaimCommandSettings.defaults().aliases(), snapshot.claimCommand().aliases());
        assertTrue(claimWarnings(snapshot).isEmpty());
    }

    @Test
    void writtenNameAndAliasesAreRead() {
        ConfigSnapshot snapshot = load("""
                claim_command:
                  name: Takeitems
                  aliases:
                    - " Mine "
                    - owed
                """);

        assertEquals("takeitems", snapshot.claimCommand().name());
        assertEquals(List.of("mine", "owed"), snapshot.claimCommand().aliases());
        assertTrue(claimWarnings(snapshot).isEmpty());
    }

    @Test
    void emptyAliasesAreAllowed() {
        ConfigSnapshot snapshot = load("claim_command:\n  name: take\n  aliases: []\n");

        assertEquals("take", snapshot.claimCommand().name());
        assertEquals(List.of(), snapshot.claimCommand().aliases());
        assertTrue(claimWarnings(snapshot).isEmpty());
    }

    @Test
    void badNameWarnsHighAndUsesTheDefaultName() {
        for (String name : List.of("\"\"", "two words", "\"/slash\"", "3", "[a]")) {
            ConfigSnapshot snapshot = load("claim_command:\n  name: " + name + "\n  aliases: [owed]\n");

            assertEquals(ClaimCommandSettings.defaults().name(), snapshot.claimCommand().name(), name);
            assertEquals(List.of("owed"), snapshot.claimCommand().aliases(), name);
            assertSingleUnusable(snapshot, "name");
        }
    }

    @Test
    void badAliasIsLeftOutWithAHighWarningAndAliasThatRepeatsTheNameOrAnotherIsDropped() {
        ConfigSnapshot snapshot = load("""
                claim_command:
                  name: take
                  aliases: [owed, "bad alias", take, owed, ok]
                """);

        assertEquals(List.of("owed", "ok"), snapshot.claimCommand().aliases());
        assertSingleUnusable(snapshot, "aliases");
    }

    @Test
    void aliasesThatAreNotAListWarnHighAndUseTheDefaultAliases() {
        ConfigSnapshot snapshot = load("claim_command:\n  name: take\n  aliases: owed\n");

        assertEquals("take", snapshot.claimCommand().name());
        assertEquals(ClaimCommandSettings.defaults().aliases(), snapshot.claimCommand().aliases());
        assertSingleUnusable(snapshot, "aliases");
    }

    @Test
    void blockThatIsNotABlockWarnsHighAndUsesTheDefaults() {
        ConfigSnapshot snapshot = load("claim_command: take\n");

        assertEquals(ClaimCommandSettings.defaults().name(), snapshot.claimCommand().name());
        assertSingleUnusable(snapshot, "claim_command");
    }

    private static void assertSingleUnusable(ConfigSnapshot snapshot, String key) {
        List<ConfigWarning> warnings = claimWarnings(snapshot);
        assertEquals(1, warnings.size(), key);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warnings.get(0).severity());
        assertEquals(Optional.of(key), warnings.get(0).key());
    }

    private static List<ConfigWarning> claimWarnings(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(warning -> warning.kind() == ConfigWarning.Kind.UNUSABLE_CLAIM_COMMAND_VALUE)
                .toList();
    }

    private static ConfigSnapshot load(String globals) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(globals)
                .language("en", "still_locked: locked\n")
                .build(), ACCEPTING, WarningKinds.all());
    }
}
