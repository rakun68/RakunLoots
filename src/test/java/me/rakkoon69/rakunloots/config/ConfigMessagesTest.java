package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMessagesTest {

    @Test
    void activeLanguageLineWhenLootDoesNotOverride() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: vi\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("Vietnamese locked", snapshot.message("demo", "still_locked"));
    }

    @Test
    void shippedLanguagesResolveSharedKeyToDifferentTextWithMatchingKeySets() throws IOException {
        String en = shippedLanguage("en");
        String vi = shippedLanguage("vi");
        String zh = shippedLanguage("zh");

        ConfigSnapshot enSnap = loadShipped("en", en, vi, zh);
        ConfigSnapshot viSnap = loadShipped("vi", en, vi, zh);
        ConfigSnapshot zhSnap = loadShipped("zh", en, vi, zh);

        String enText = enSnap.message("demo", "still_locked");
        String viText = viSnap.message("demo", "still_locked");
        String zhText = zhSnap.message("demo", "still_locked");

        assertNotEquals(enText, viText);
        assertNotEquals(enText, zhText);
        assertNotEquals(viText, zhText);

        assertEquals(enSnap.languageKeys("en"), viSnap.languageKeys("vi"));
        assertEquals(enSnap.languageKeys("en"), zhSnap.languageKeys("zh"));
    }

    @Test
    void multiLineMessagesKeepEachLineWhetherBlockStringOrListOrLootOverride() throws IOException {
        String en = shippedLanguage("en");
        String vi = shippedLanguage("vi");
        String zh = shippedLanguage("zh");
        int lines = loadShipped("en", en, vi, zh).messageLines("demo", "lootbox_spawned_broadcast").size();
        assertTrue(lines > 1, "the shipped broadcast has several lines");
        assertEquals(lines, loadShipped("vi", en, vi, zh).messageLines("demo", "lootbox_spawned_broadcast").size());
        assertEquals(lines, loadShipped("zh", en, vi, zh).messageLines("demo", "lootbox_spawned_broadcast").size());

        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "block: |-\n  one\n  two\n  three\nlisted:\n  - a\n  - b\n")
                .loot("demo", "language: en\nmessages:\n  block: |-\n    x\n    y\n  listed:\n    - p\n    - q\n    - r\n")
                .loot("plain", "language: en\n")
                .build();
        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals(java.util.List.of("one", "two", "three"), snapshot.messageLines("plain", "block"));
        assertEquals(java.util.List.of("a", "b"), snapshot.messageLines("plain", "listed"));
        assertEquals(java.util.List.of("x", "y"), snapshot.messageLines("demo", "block"));
        assertEquals(java.util.List.of("p", "q", "r"), snapshot.messageLines("demo", "listed"));
    }

    @Test
    void addedLanguageIdResolvesWhenFileProvided() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: fr\n")
                .language("en", "still_locked: English locked\n")
                .language("fr", "still_locked: French locked\n")
                .loot("demo", "language: fr\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("French locked", snapshot.message("demo", "still_locked"));
    }

    @Test
    void missingLanguageFileUsesEnglishAndReportsHighWarningNamingId() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: de\n")
                .language("en", "still_locked: English locked\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("English locked", snapshot.message("demo", "still_locked"));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.MISSING_LANGUAGE
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && w.languageId().equals(Optional.of("de"))
        ));
    }

    @Test
    void keyMissingFromActiveLanguageUsesEnglishAndReportsHighWarningNamingKey() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "other: only other\n")
                .loot("demo", "language: vi\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("English locked", snapshot.message("demo", "still_locked"));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.MISSING_KEY
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && w.key().equals(Optional.of("still_locked"))
                        && w.languageId().equals(Optional.of("vi"))
        ));
    }

    @Test
    void keyMissingFromEnglishReturnsKeyIdAndReportsSevereWarning() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "other: only other\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("still_locked", snapshot.message("demo", "still_locked"));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.MISSING_ENGLISH_KEY
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.SEVERE
                        && w.key().equals(Optional.of("still_locked"))
        ));
    }

    @Test
    void presentOverrideIncludingBlankWinsForEveryActiveLanguage() {
        ConfigSources withText = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: en\nmessages:\n  still_locked: Custom override\n")
                .build();
        ConfigSources withBlank = ConfigSources.builder()
                .globals("language: zh\n")
                .language("en", "still_locked: English locked\n")
                .language("zh", "still_locked: Chinese locked\n")
                .loot("demo", "language: zh\nmessages:\n  still_locked: \"\"\n")
                .build();

        assertEquals(
                "Custom override",
                ConfigLoader.load(withText, NameCatalog.empty(), WarningKinds.all()).message("demo", "still_locked")
        );
        assertEquals(
                "",
                ConfigLoader.load(withBlank, NameCatalog.empty(), WarningKinds.all()).message("demo", "still_locked")
        );
    }

    @Test
    void missingOverrideUsesActiveLanguage() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", "still_locked: English locked\n")
                .language("vi", "still_locked: Vietnamese locked\n")
                .loot("demo", "language: vi\nmessages:\n  other: only other\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("Vietnamese locked", snapshot.message("demo", "still_locked"));
    }

    @Test
    void returnedTextIsStoredTextWithoutExpandingColorsOrPlaceholders() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: \"&cHello {name}\"\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("&cHello {name}", snapshot.message("demo", "still_locked"));
    }

    @Test
    void loadUsesLanguageFilesWithoutLegacyMessagesYml() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: From language folder\n")
                .loot("demo", "language: en\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, NameCatalog.empty(), WarningKinds.all());

        assertEquals("From language folder", snapshot.message("demo", "still_locked"));
    }

    private static ConfigSnapshot loadShipped(String active, String en, String vi, String zh) {
        return ConfigLoader.load(
                ConfigSources.builder()
                        .globals("language: " + active + "\n")
                        .language("en", en)
                        .language("vi", vi)
                        .language("zh", zh)
                        .loot("demo", "language: " + active + "\n")
                        .build(),
                NameCatalog.empty()
        , WarningKinds.all());
    }

    private static String shippedLanguage(String id) throws IOException {
        String path = "/languages/" + id + ".yml";
        try (InputStream in = ConfigMessagesTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
