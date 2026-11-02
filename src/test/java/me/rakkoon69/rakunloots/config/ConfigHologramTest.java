package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigHologramTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);
    private static final List<String> HOLOGRAM_KEYS =
            List.of("hologram_lines", "hologram_unlocked", "hologram_no_owner", "hologram_unending");
    private static final String EN = """
            still_locked: locked
            hologram_lines:
              - 'EN {name}'
              - 'EN {owner} {lock} {life}'
            hologram_unlocked: EN unlocked
            hologram_no_owner: EN no owner
            hologram_unending: EN unending
            """;
    private static final String VI = """
            still_locked: khoa
            hologram_lines:
              - 'VI {name}'
              - 'VI {owner} {lock} {life}'
            hologram_unlocked: VI unlocked
            hologram_no_owner: VI no owner
            hologram_unending: VI unending
            """;

    @Test
    void theShippedAirdropsLoadsWithItsHologramSettings() throws IOException {
        ConfigSnapshot snapshot = loadShipped("en");

        HologramSettings hologram = snapshot.hologram("airdrops").orElseThrow();
        assertTrue(hologram.enabled());
        assertEquals(2.0, hologram.height());
        assertEquals(48, hologram.viewRange());
        assertEquals(Optional.empty(), hologram.lines());
        assertEquals(48, snapshot.hologramViewRange());
        assertEquals(HologramProviderChoice.AUTO, snapshot.hologramProvider());

        List<String> lines = snapshot.hologramLines("airdrops");
        assertEquals(snapshot.messageLines("airdrops", "hologram_lines"), lines);
        assertEquals(4, lines.size());
        for (String placeholder : List.of("{name}", "{owner}", "{lock}", "{life}")) {
            assertTrue(lines.stream().anyMatch(line -> line.contains(placeholder)), placeholder + " in " + lines);
        }
        assertTrue(snapshot.warnings().isEmpty(), snapshot.warnings().toString());
    }

    @Test
    void theFourNewMessagesExistInEveryShippedLanguage() throws IOException {
        ConfigSnapshot en = loadShipped("en");
        ConfigSnapshot vi = loadShipped("vi");
        ConfigSnapshot zh = loadShipped("zh");

        for (String language : List.of("en", "vi", "zh")) {
            assertTrue(en.languageKeys(language).containsAll(HOLOGRAM_KEYS), language + ": " + en.languageKeys(language));
        }
        for (String key : List.of("hologram_unlocked", "hologram_no_owner", "hologram_unending")) {
            assertFalse(en.message("airdrops", key).isBlank(), key);
            assertNotEquals(en.message("airdrops", key), vi.message("airdrops", key), key);
            assertNotEquals(en.message("airdrops", key), zh.message("airdrops", key), key);
            assertNotEquals(vi.message("airdrops", key), zh.message("airdrops", key), key);
        }
        for (ConfigSnapshot snapshot : List.of(en, vi, zh)) {
            List<String> lines = snapshot.hologramLines("airdrops");
            assertEquals(4, lines.size(), snapshot.activeLanguage() + ": " + lines);
            for (String placeholder : List.of("{name}", "{owner}", "{lock}", "{life}")) {
                assertTrue(lines.stream().anyMatch(line -> line.contains(placeholder)),
                        snapshot.activeLanguage() + " " + placeholder + " in " + lines);
            }
        }
        assertNotEquals(en.hologramLines("airdrops"), vi.hologramLines("airdrops"));
        assertNotEquals(en.hologramLines("airdrops"), zh.hologramLines("airdrops"));
        assertNotEquals(vi.hologramLines("airdrops"), zh.hologramLines("airdrops"));
        assertTrue(vi.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.MISSING_KEY), vi.warnings().toString());
        assertTrue(zh.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.MISSING_KEY), zh.warnings().toString());
    }

    @Test
    void aLootWithNoHologramGetsTheDefaultsAndTheHologramLinesMessage() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", EN)
                .language("vi", VI)
                .loot("demo", lootFile("vi", ""))
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals(new HologramSettings(true, 1.5, 48, Optional.empty()), snapshot.hologram("demo").orElseThrow());
        assertEquals(List.of("VI {name}", "VI {owner} {lock} {life}"), snapshot.hologramLines("demo"));
        assertEquals(48, snapshot.hologramViewRange());
        assertEquals(HologramProviderChoice.AUTO, snapshot.hologramProvider());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_HOLOGRAM),
                snapshot.warnings().toString());
    }

    @Test
    void aLootWritesEveryHologramKey() {
        ConfigSnapshot snapshot = load("", """
                hologram:
                  enabled: false
                  height: 2.25
                  view_range: 16
                  lines:
                    - 'Own {name}'
                    - 'Own {life}'
                """);

        assertEquals(new HologramSettings(false, 2.25, 16, Optional.of(List.of("Own {name}", "Own {life}"))),
                snapshot.hologram("demo").orElseThrow());
        assertEquals(List.of("Own {name}", "Own {life}"), snapshot.hologramLines("demo"));
        assertEquals(List.of(), hologramWarnings(snapshot));
    }

    @Test
    void aSkippedLootHasNoHologram() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .language("en", EN)
                .loot("demo", """
                        language: en
                        block: barrel
                        hologram:
                          height: 3
                        """)
                .build(), ACCEPTING, WarningKinds.all());

        assertTrue(snapshot.loot("demo").isEmpty());
        assertTrue(snapshot.hologram("demo").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "enabled: maybe          | enabled",
            "enabled: 1              | enabled",
            "height: tall            | height",
            "height: -0.5            | height",
            "height: .nan            | height",
            "height: [1]             | height",
            "view_range: far         | view_range",
            "view_range: 0           | view_range",
            "view_range: -4          | view_range",
            "view_range: 10.5        | view_range",
            "lines: just one string  | lines",
            "lines: []               | lines",
            "lines: [ok, 5]          | lines",
            "lines: [ok, {a: b}]     | lines",
    })
    void aBadLootHologramValueWarnsHighAndUsesItsDefault(String line, String key) {
        ConfigSnapshot snapshot = load("hologram:\n  view_range: 30\n", "hologram:\n  " + line + "\n");

        assertEquals(new HologramSettings(true, 1.5, 30, Optional.empty()), snapshot.hologram("demo").orElseThrow());
        assertEquals(List.of("EN {name}", "EN {owner} {lock} {life}"), snapshot.hologramLines("demo"));
        assertEquals(List.of("HIGH demo " + key), hologramWarnings(snapshot));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hologram: true", "hologram: [a]", "hologram: text"})
    void aLootHologramThatIsNotASectionWarnsHighAndUsesEveryDefault(String line) {
        ConfigSnapshot snapshot = load("", line + "\n");

        assertEquals(new HologramSettings(true, 1.5, 48, Optional.empty()), snapshot.hologram("demo").orElseThrow());
        assertEquals(List.of("HIGH demo hologram"), hologramWarnings(snapshot));
    }

    @Test
    void oneBadKeyKeepsTheOtherKeys() {
        ConfigSnapshot snapshot = load("", """
                hologram:
                  enabled: false
                  height: nope
                  view_range: 12
                  lines: [kept]
                """);

        assertEquals(new HologramSettings(false, 1.5, 12, Optional.of(List.of("kept"))),
                snapshot.hologram("demo").orElseThrow());
        assertEquals(List.of("HIGH demo height"), hologramWarnings(snapshot));
    }

    @Test
    void anUnknownKeyInALootHologramWarnsHigh() {
        ConfigSnapshot snapshot = load("", """
                hologram:
                  enable: false
                """);

        assertTrue(snapshot.hologram("demo").orElseThrow().enabled());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_KEY
                && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                && w.lootId().equals(Optional.of("demo"))
                && w.key().equals(Optional.of("enable"))), snapshot.warnings().toString());
    }

    @Test
    void aLootViewRangeOverridesTheGlobalOne() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("""
                        language: en
                        hologram:
                          view_range: 30
                        """)
                .language("en", EN)
                .loot("near", lootFile("en", "hologram:\n  view_range: 8\n"))
                .loot("far", lootFile("en", "hologram:\n  view_range: 120\n"))
                .loot("global", lootFile("en", "hologram:\n  height: 3\n"))
                .loot("bare", lootFile("en", ""))
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals(30, snapshot.hologramViewRange());
        assertEquals(8, snapshot.hologram("near").orElseThrow().viewRange());
        assertEquals(120, snapshot.hologram("far").orElseThrow().viewRange());
        assertEquals(30, snapshot.hologram("global").orElseThrow().viewRange());
        assertEquals(30, snapshot.hologram("bare").orElseThrow().viewRange());
    }

    @ParameterizedTest
    @ValueSource(strings = {"view_range: 0", "view_range: far", "view_range: 2.5", "view_range: -1"})
    void aBadGlobalViewRangeWarnsHighAndUses48(String line) {
        ConfigSnapshot snapshot = load("hologram:\n  " + line + "\n", "");

        assertEquals(48, snapshot.hologramViewRange());
        assertEquals(48, snapshot.hologram("demo").orElseThrow().viewRange());
        assertEquals(List.of("HIGH - view_range"), hologramWarnings(snapshot));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hologram: 5", "hologram: [a]", "hologram: far"})
    void aGlobalHologramThatIsNotASectionWarnsHighAndUses48(String line) {
        ConfigSnapshot snapshot = load(line + "\n", "");

        assertEquals(48, snapshot.hologramViewRange());
        assertEquals(List.of("HIGH - hologram"), hologramWarnings(snapshot));
    }

    @ParameterizedTest
    @CsvSource({
            "auto, AUTO",
            "packets, PACKETS",
            "decentholograms, DECENTHOLOGRAMS",
            "holographicdisplays, HOLOGRAPHICDISPLAYS",
            "fancyholograms, FANCYHOLOGRAMS",
            "none, NONE",
            "'  DecentHolograms ', DECENTHOLOGRAMS",
            "NONE, NONE",
    })
    void eachHologramProviderLoads(String written, HologramProviderChoice expected) {
        ConfigSnapshot snapshot = load("hologram_provider: '" + written + "'\n", "");

        assertEquals(expected, snapshot.hologramProvider());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_HOLOGRAM_PROVIDER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hologram_provider: holograms", "hologram_provider: ''", "hologram_provider: 3",
            "hologram_provider: [auto]", "hologram_provider: decent_holograms", "hologram_provider: false"})
    void anUnknownHologramProviderWarnsHighAndUsesAuto(String line) {
        ConfigSnapshot snapshot = load(line + "\n", "");

        assertEquals(HologramProviderChoice.AUTO, snapshot.hologramProvider());
        List<ConfigWarning> warnings = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_HOLOGRAM_PROVIDER)
                .toList();
        assertEquals(1, warnings.size(), snapshot.warnings().toString());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warnings.get(0).severity());
        assertEquals(Optional.of("hologram_provider"), warnings.get(0).key());
    }

    @Test
    void aMissingHologramProviderIsAutoWithNoWarning() {
        ConfigSnapshot snapshot = load("", "");

        assertEquals(HologramProviderChoice.AUTO, snapshot.hologramProvider());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_HOLOGRAM_PROVIDER
                || w.kind() == ConfigWarning.Kind.UNUSABLE_HOLOGRAM), snapshot.warnings().toString());
    }

    @Test
    void aLootOverridesEachHologramMessage() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", EN)
                .language("vi", VI)
                .loot("demo", lootFile("vi", """
                        messages:
                          hologram_lines:
                            - 'Loot {name}'
                            - 'Loot {lock}'
                          hologram_unlocked: Loot unlocked
                          hologram_no_owner: Loot no owner
                          hologram_unending: Loot unending
                        """))
                .loot("other", lootFile("vi", ""))
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals(List.of("Loot {name}", "Loot {lock}"), snapshot.hologramLines("demo"));
        assertEquals(List.of("Loot {name}", "Loot {lock}"), snapshot.messageLines("demo", "hologram_lines"));
        assertEquals("Loot unlocked", snapshot.message("demo", "hologram_unlocked"));
        assertEquals("Loot no owner", snapshot.message("demo", "hologram_no_owner"));
        assertEquals("Loot unending", snapshot.message("demo", "hologram_unending"));

        assertEquals(List.of("VI {name}", "VI {owner} {lock} {life}"), snapshot.hologramLines("other"));
        assertEquals("VI unlocked", snapshot.message("other", "hologram_unlocked"));
        assertEquals("VI no owner", snapshot.message("other", "hologram_no_owner"));
        assertEquals("VI unending", snapshot.message("other", "hologram_unending"));
    }

    @Test
    void aLootsOwnLinesWinOverItsLinesMessage() {
        ConfigSnapshot snapshot = load("", """
                hologram:
                  lines: ['Own {name}']
                messages:
                  hologram_lines: ['Message {name}']
                """);

        assertEquals(List.of("Own {name}"), snapshot.hologramLines("demo"));
        assertEquals(List.of("Message {name}"), snapshot.messageLines("demo", "hologram_lines"));
    }

    @Test
    void aLinesMessageWrittenAsOneStringIsOneLine() {
        ConfigSnapshot snapshot = load("", """
                messages:
                  hologram_lines: 'Only {name}'
                """);

        assertEquals(List.of("Only {name}"), snapshot.hologramLines("demo"));
    }

    @Test
    void aLinesMessageMissingInTheActiveLanguageUsesEnglishWithAHighWarning() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", EN)
                .language("vi", "still_locked: khoa\n")
                .loot("demo", lootFile("vi", ""))
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals(List.of("EN {name}", "EN {owner} {lock} {life}"), snapshot.hologramLines("demo"));
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.MISSING_KEY
                && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                && w.key().equals(Optional.of("hologram_lines"))), snapshot.warnings().toString());
    }

    @Test
    void aListOverrideRaisesTheLootLanguageWarningLikeAnyOverride() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", EN)
                .language("vi", VI)
                .loot("demo", lootFile("en", "messages:\n  hologram_lines: ['x']\n"))
                .build(), ACCEPTING, WarningKinds.all());

        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH), snapshot.warnings().toString());
    }

    /**
     * Each hologram warning as severity, loot id ("-" for the globals file), and key.
     */
    private static List<String> hologramWarnings(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_HOLOGRAM)
                .map(w -> w.severity() + " " + w.lootId().orElse("-") + " " + w.key().orElse("-"))
                .toList();
    }

    private static ConfigSnapshot load(String globals, String lootExtra) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n" + globals)
                .language("en", EN)
                .loot("demo", lootFile("en", lootExtra))
                .build(), ACCEPTING, WarningKinds.all());
    }

    private static String lootFile(String language, String extra) {
        return """
                language: %s
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                %s
                """.formatted(language, extra);
    }

    private static ConfigSnapshot loadShipped(String active) throws IOException {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml").replace("\nlanguage:", "\n#language:") + "\nlanguage: " + active + "\n")
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .language("vi", resource("/languages/vi.yml"))
                .language("zh", resource("/languages/zh.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), SHIPPED_CATALOG, WarningKinds.all());
    }

    private static final NameCatalog SHIPPED_CATALOG = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            if (writtenId.startsWith("@") || writtenId.startsWith("#")) {
                return Optional.empty();
            }
            return Optional.of(writtenId);
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            return Set.of("minecraft:" + namespacedTag.replace(':', '_'));
        }
    };

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigHologramTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
