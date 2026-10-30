package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigChancePrecisionWarningTest {

    @Test
    void unusableDecimalsWarnsHighOncePerLanguageString() {
        ConfigSnapshot snapshot = ConfigLoader.load(
                ConfigSources.builder()
                        .language("en",
                                "line_nine: 'Chance is {chance:9}'\n" +
                                "line_x: 'Chance is {chance:x}'\n" +
                                "line_multi: 'Two bad {chance:9} and {chance:x} in one line'\n" +
                                "line_valid: 'Valid {chance:0} and {chance:6} and {chance}'\n")
                        .loot("demo", "language: en\n")
                        .build(),
                NameCatalog.empty()
        , WarningKinds.all());

        List<ConfigWarning> decimalWarnings = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DECIMALS)
                .toList();

        // 3 strings with unusable decimals: line_nine, line_x, line_multi
        assertEquals(3, decimalWarnings.size(), decimalWarnings.toString());

        for (ConfigWarning w : decimalWarnings) {
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, w.severity());
            assertEquals(Optional.of("en"), w.languageId());
        }

        List<String> keys = decimalWarnings.stream().map(w -> w.key().orElse("")).sorted().toList();
        assertEquals(List.of("line_multi", "line_nine", "line_x"), keys);
    }

    @Test
    void unusableDecimalsWarnsHighOncePerLootOverride() {
        ConfigSnapshot snapshot = ConfigLoader.load(
                ConfigSources.builder()
                        .loot("demo",
                                "messages:\n" +
                                "  broadcast: 'Bad loot chance {chance:9}'\n" +
                                "  title: 'Bad loot title {chance:x}'\n" +
                                "  subtitle: 'Good {chance:2}'\n")
                        .build(),
                NameCatalog.empty()
        , WarningKinds.all());

        List<ConfigWarning> decimalWarnings = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DECIMALS)
                .toList();

        assertEquals(2, decimalWarnings.size(), decimalWarnings.toString());

        for (ConfigWarning w : decimalWarnings) {
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, w.severity());
            assertEquals(Optional.of("demo"), w.lootId());
        }

        List<String> keys = decimalWarnings.stream().map(w -> w.key().orElse("")).sorted().toList();
        assertEquals(List.of("broadcast", "title"), keys);
    }

    @Test
    void emptyDecimalsWarnsHighForLanguageAndLootOverride() {
        ConfigSnapshot snapshot = ConfigLoader.load(
                ConfigSources.builder()
                        .language("en", "empty_language: 'Chance {chance:}'\n")
                        .loot("demo",
                                "language: en\n" +
                                "messages:\n" +
                                "  empty_loot: 'Chance {chance:}'\n")
                        .build(),
                NameCatalog.empty()
        , WarningKinds.all());

        List<ConfigWarning> decimalWarnings = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DECIMALS)
                .toList();

        assertEquals(2, decimalWarnings.size(), decimalWarnings.toString());

        ConfigWarning language = decimalWarnings.stream()
                .filter(w -> w.languageId().isPresent()).findFirst().orElseThrow();
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, language.severity());
        assertEquals(Optional.of("empty_language"), language.key());

        ConfigWarning loot = decimalWarnings.stream()
                .filter(w -> w.lootId().isPresent()).findFirst().orElseThrow();
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, loot.severity());
        assertEquals(Optional.of("empty_loot"), loot.key());
    }

    @Test
    void validDecimalsProduceNoWarnings() {
        ConfigSnapshot snapshot = ConfigLoader.load(
                ConfigSources.builder()
                        .language("en",
                                "all_valid: '{chance:0} {chance:1} {chance:2} {chance:3} {chance:4} {chance:5} {chance:6} {chance}'\n")
                        .loot("demo", "language: en\n")
                        .build(),
                NameCatalog.empty()
        , WarningKinds.all());

        List<ConfigWarning> decimalWarnings = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DECIMALS)
                .toList();

        assertTrue(decimalWarnings.isEmpty(), decimalWarnings.toString());
    }
}
