package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code warnings.yml} through the config load. {@code warning-level: loud} raises UNKNOWN_WARNING_LEVEL at high, which
 * is the kind each test changes, hides, or leaves alone.
 */
class ConfigWarningsFileTest {

    private static ConfigSnapshot load(String warningsFile) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\nwarning-level: loud\n")
                .language("en", "still_locked: locked\n");
        if (warningsFile != null) {
            sources.warnings(warningsFile);
        }
        return ConfigLoader.load(sources.build(), NameCatalog.empty(), WarningKinds.all());
    }

    private static Optional<ConfigWarning> of(List<ConfigWarning> warnings, ConfigWarning.Kind kind) {
        return warnings.stream().filter(w -> w.kind() == kind).findFirst();
    }

    /**
     * Acceptance: {@code warnings.yml} maps a kind name to low, high, severe, or off. A changed severity is the one the
     * config's warnings carry.
     */
    @Test
    void aChangedSeverityIsTheSeverityTheWarningCarries() {
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                of(load(null).warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).orElseThrow().severity());

        for (String word : List.of("low", "severe")) {
            ConfigSnapshot snapshot = load("UNKNOWN_WARNING_LEVEL: " + word + "\n");

            assertEquals(me.rakkoon69.rakunloots.warning.Severity.valueOf(word.toUpperCase()),
                    of(snapshot.warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).orElseThrow().severity(), word);
        }
    }

    /**
     * Acceptance: off, written the way a YAML file shows it, with no quotes, drops the kind from the config's warnings.
     */
    @Test
    void anOffKindIsNotInTheWarnings() {
        ConfigSnapshot snapshot = load("UNKNOWN_WARNING_LEVEL: off\n");

        assertTrue(of(snapshot.warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).isEmpty());
        assertTrue(of(snapshot.rawWarnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).isPresent(),
                "the raw list is what the log judges");
        assertTrue(load("UNKNOWN_WARNING_LEVEL: 'off'\n").warnings().stream()
                .noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL), "quoted off");
    }

    /**
     * Acceptance: an unknown kind warns low, and the line is left out.
     */
    @Test
    void anUnknownKindWarnsLow() {
        ConfigSnapshot snapshot = load("UNKNOWN_WARNING_LEVELL: off\n");

        ConfigWarning warning = of(snapshot.warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_KIND).orElseThrow();
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, warning.severity());
        assertEquals(Optional.of("UNKNOWN_WARNING_LEVELL"), warning.key());
        assertTrue(of(snapshot.warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).isPresent(),
                "the misspelled line changed nothing");
    }

    /**
     * Acceptance: a kind missing from the file keeps its default, and so does every kind when there is no file.
     */
    @Test
    void aMissingKindKeepsItsDefault() {
        for (String file : java.util.Arrays.asList("OLD_NAME: severe\n", "", "# nothing\n", null)) {
            ConfigSnapshot snapshot = load(file);

            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                    of(snapshot.warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).orElseThrow().severity(),
                    String.valueOf(file));
        }
    }

    /**
     * A value that is not low, high, severe, or off warns high and leaves the kind at its default. A file that is not
     * YAML warns high and changes nothing.
     */
    @Test
    void anUnusableValueOrFileWarnsHighAndChangesNothing() {
        ConfigSnapshot value = load("UNKNOWN_WARNING_LEVEL: loud\n");
        ConfigSnapshot broken = load("UNKNOWN_WARNING_LEVEL: [low\n");

        ConfigWarning unusable = of(value.warnings(), ConfigWarning.Kind.UNUSABLE_WARNING_SEVERITY).orElseThrow();
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, unusable.severity());
        assertEquals(Optional.of("UNKNOWN_WARNING_LEVEL"), unusable.key());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                of(value.warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).orElseThrow().severity());
        assertEquals(Optional.of("warnings"), of(broken.warnings(), ConfigWarning.Kind.YAML_PARSE_REJECTED)
                .orElseThrow().key());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                of(broken.warnings(), ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL).orElseThrow().severity());
    }

    /**
     * The warning level stays the log's threshold, applied after the file: at level high, a high kind lowered to low is
     * hidden, and one raised to severe still shows, as severe.
     */
    @Test
    void theWarningLevelStillFiltersAfterTheFile() {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\nwarning-level: high\ndebug: maybe\n")
                .language("en", "still_locked: locked\n");

        ConfigSnapshot plain = ConfigLoader.load(sources.build(), NameCatalog.empty(), WarningKinds.all());
        ConfigSnapshot lowered = ConfigLoader.load(sources.warnings("UNUSABLE_DEBUG: low\n").build(),
                NameCatalog.empty(), WarningKinds.all());
        ConfigSnapshot raised = ConfigLoader.load(sources.warnings("UNUSABLE_DEBUG: severe\n").build(),
                NameCatalog.empty(), WarningKinds.all());

        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                of(plain.warnings(), ConfigWarning.Kind.UNUSABLE_DEBUG).orElseThrow().severity());
        assertTrue(of(lowered.warnings(), ConfigWarning.Kind.UNUSABLE_DEBUG).isEmpty());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.SEVERE,
                of(raised.warnings(), ConfigWarning.Kind.UNUSABLE_DEBUG).orElseThrow().severity());
    }
}
