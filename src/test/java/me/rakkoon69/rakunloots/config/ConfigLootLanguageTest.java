package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.warning.WarningLevel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLootLanguageTest {

    @Test
    void clashWithPresentOverrideIncludingBlankReportsHighAndReturnsOverride() {
        ConfigSources withText = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: en\nmessages:\n  still_locked: Custom override\n")
                .build();
        ConfigSources withBlank = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: en\nmessages:\n  still_locked: \"\"\n")
                .build();

        ConfigSnapshot textSnap = ConfigLoader.load(withText, NameCatalog.empty(), WarningKinds.all());
        ConfigSnapshot blankSnap = ConfigLoader.load(withBlank, NameCatalog.empty(), WarningKinds.all());

        assertEquals("Custom override", textSnap.message("demo", "still_locked"));
        assertEquals("", blankSnap.message("demo", "still_locked"));
        assertTrue(textSnap.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && w.lootId().equals(Optional.of("demo"))
                        && w.lootLanguage().equals(Optional.of("en"))
                        && w.activeLanguage().equals(Optional.of("vi"))
        ));
        assertTrue(blankSnap.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
        ));
        assertEquals(Optional.of("en"), textSnap.lootLanguage("demo"));
        assertEquals("Custom override", textSnap.messageOverrides("demo").get("still_locked"));
        assertEquals("", blankSnap.messageOverrides("demo").get("still_locked"));
    }

    @Test
    void clashWithNoMessageOverrideReportsLowWarning() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.LOW
                        && w.lootId().equals(Optional.of("demo"))
                        && w.lootLanguage().equals(Optional.of("en"))
                        && w.activeLanguage().equals(Optional.of("vi"))
        ));
    }

    @Test
    void missingLootLanguageReportsLowWarning() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: English locked\n")
                .loot("demo", "display_name: Demo\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_MISSING
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.LOW
                        && w.lootId().equals(Optional.of("demo"))
                        && w.activeLanguage().equals(Optional.of("en"))
        ));
    }

    @Test
    void displayNameHologramOrOtherLootTextDoesNotRaiseClashToHigh() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", """
                        language: en
                        display_name: Demo Box
                        hologram_settings:
                          custom_texts:
                            - line one
                        message_owner_on_remove: some text
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.LOW
        ));
        assertTrue(snapshot.warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
        ));
    }

    @Test
    void matchingLootLanguageReportsNoLootLanguageWarningEvenWithOverride() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: vi\nmessages:\n  still_locked: Custom\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                || w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_MISSING));
    }

    @Test
    void warningLevelHidesProblemsBelowTheLevel() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: vi\nwarning-level: high\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals(WarningLevel.HIGH, snapshot.warningLevel());
        assertTrue(snapshot.warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
        ));
    }

    @Test
    void unknownWarningLevelUsesLowAndReportsHighWarning() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\nwarning-level: loud\n")
                .language("en", "still_locked: English locked\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals(WarningLevel.LOW, snapshot.warningLevel());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_WARNING_LEVEL
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
        ));
    }

    @Test
    void lootLanguageClashOffSilencesOnlyLootLanguageWarnings() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: de\n")
                .warnings("LOOT_LANGUAGE_CLASH: off\nLOOT_LANGUAGE_MISSING: off\n")
                .language("en", "still_locked: English locked\n")
                .loot("demo", "language: fr\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                || w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_MISSING));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.MISSING_LANGUAGE
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
        ));
    }

}
