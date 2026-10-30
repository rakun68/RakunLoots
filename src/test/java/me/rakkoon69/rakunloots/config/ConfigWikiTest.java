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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWikiTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void aLootWithoutWikiShowsWithNoConfiguredIcon() {
        ConfigSnapshot snapshot = load("");

        WikiSettings wiki = snapshot.wiki("demo").orElseThrow();
        assertTrue(wiki.show());
        assertEquals(Optional.empty(), wiki.icon());
        assertTrue(snapshot.warnings().stream().noneMatch(warning ->
                        warning.kind() == ConfigWarning.Kind.UNUSABLE_WIKI
                                || warning.kind() == ConfigWarning.Kind.UNKNOWN_KEY),
                snapshot.warnings().toString());
    }

    @Test
    void falseHidesTheLootAndAShowFalseMapKeepsItsIcon() {
        ConfigSnapshot snapshot = load("wiki: false\n", """
                wiki:
                  show: false
                  icon:
                    id: emerald
                """);

        assertEquals(false, snapshot.wiki("off").orElseThrow().show());
        WikiSettings hidden = snapshot.wiki("map").orElseThrow();
        assertEquals(false, hidden.show());
        assertEquals("emerald", hidden.icon().orElseThrow().id());
    }

    @Test
    void anUnknownSubKeyWarnsHighAndDiscardsTheWholeMap() {
        ConfigSnapshot snapshot = load("""
                wiki:
                  mystery: false
                  show: false
                  icon:
                    id: emerald
                """);

        WikiSettings wiki = snapshot.wiki("demo").orElseThrow();
        assertTrue(wiki.show());
        assertEquals(Optional.empty(), wiki.icon());
        assertTrue(snapshot.warnings().stream().anyMatch(warning ->
                warning.kind() == ConfigWarning.Kind.UNKNOWN_KEY
                        && warning.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && warning.key().equals(Optional.of("wiki.mystery"))));
    }

    @Test
    void aWikiThatIsNotABooleanOrMapWarnsHighAndShows() {
        ConfigSnapshot snapshot = load("wiki: [false]\n");

        WikiSettings wiki = snapshot.wiki("demo").orElseThrow();
        assertTrue(wiki.show());
        assertEquals(Optional.empty(), wiki.icon());
        assertTrue(snapshot.warnings().stream().anyMatch(warning ->
                warning.kind() == ConfigWarning.Kind.UNUSABLE_WIKI
                        && warning.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    @Test
    void aWrongTypedIconResetsTheWholeMapAndShowsWithTheBlockIcon() {
        ConfigSnapshot snapshot = load("""
                wiki:
                  show: false
                  icon: emerald
                """);

        WikiSettings wiki = snapshot.wiki("demo").orElseThrow();
        assertTrue(wiki.show());
        assertEquals(Optional.empty(), wiki.icon());
        assertTrue(snapshot.warnings().stream().anyMatch(warning ->
                warning.kind() == ConfigWarning.Kind.UNUSABLE_WIKI
                        && warning.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && warning.key().equals(Optional.of("wiki.icon"))));
    }

    @Test
    void aWrongTypedShowResetsTheWholeMapAndShowsWithTheBlockIcon() {
        ConfigSnapshot snapshot = load("""
                wiki:
                  show: nope
                  icon:
                    id: emerald
                """);

        WikiSettings wiki = snapshot.wiki("demo").orElseThrow();
        assertTrue(wiki.show());
        assertEquals(Optional.empty(), wiki.icon());
        assertTrue(snapshot.warnings().stream().anyMatch(warning ->
                warning.kind() == ConfigWarning.Kind.UNUSABLE_WIKI
                        && warning.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && warning.key().equals(Optional.of("wiki.show"))));
    }

    @Test
    void shippedConfigNamesTheWikiCommandAndGivesItsPermissionToEveryone() throws IOException {
        assertTrue(resource("/config.yml").contains("wiki_command:"));
        ConfigSnapshot snapshot = loadShipped();

        assertEquals("loots", snapshot.wikiCommand().name());
        assertEquals(List.of("rklloots"), snapshot.wikiCommand().aliases());
        assertEquals(DefaultAccess.EVERYONE, snapshot.wikiCommand().permissionDefault());
        assertTrue(wikiCommandWarnings(snapshot).isEmpty(), wikiCommandWarnings(snapshot).toString());
    }

    @Test
    void missingWikiCommandUsesTheDefaultsWithoutWarning() {
        ConfigSnapshot snapshot = load("");

        assertEquals(WikiCommandSettings.defaults().name(), snapshot.wikiCommand().name());
        assertEquals(WikiCommandSettings.defaults().aliases(), snapshot.wikiCommand().aliases());
        assertEquals(DefaultAccess.EVERYONE, snapshot.wikiCommand().permissionDefault());
        assertTrue(wikiCommandWarnings(snapshot).isEmpty());
    }

    @Test
    void wikiCommandReadsNameAliasesAndPermission() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("""
                        wiki_command:
                          name: Loots
                          aliases: [guide, guide, loots]
                          permission_default: op
                        """)
                .language("en", "still_locked: locked\n")
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals("loots", snapshot.wikiCommand().name());
        assertEquals(List.of("guide"), snapshot.wikiCommand().aliases());
        assertEquals(DefaultAccess.OPERATORS, snapshot.wikiCommand().permissionDefault());
        assertTrue(wikiCommandWarnings(snapshot).isEmpty());
    }

    @Test
    void aBadWikiCommandValueWarnsHighAndUsesItsDefault() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals("""
                        wiki_command:
                          name: "two words"
                          aliases: [guide, "bad alias"]
                          permission_default: sometimes
                        """)
                .language("en", "still_locked: locked\n")
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals(WikiCommandSettings.defaults().name(), snapshot.wikiCommand().name());
        assertEquals(List.of("guide"), snapshot.wikiCommand().aliases());
        assertEquals(DefaultAccess.EVERYONE, snapshot.wikiCommand().permissionDefault());
        List<ConfigWarning> warnings = wikiCommandWarnings(snapshot);
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().allMatch(warning -> warning.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    private static ConfigSnapshot load(String wiki) {
        return load(wiki, "");
    }

    private static ConfigSnapshot load(String firstWiki, String secondWiki) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", "still_locked: locked\n");
        sources.loot("demo", loot(firstWiki));
        if (!secondWiki.isBlank()) {
            sources.loot("map", loot(secondWiki));
        }
        if (firstWiki.contains("false") && !secondWiki.isBlank()) {
            sources.loot("off", loot("wiki: false\n"));
        }
        return ConfigLoader.load(sources.build(), ACCEPTING, WarningKinds.all());
    }

    private static String loot(String wiki) {
        return "display_name: 'demo'\n"
                + "open_mode: drop\n"
                + "block: barrel\n"
                + wiki
                + "roll:\n"
                + "  entries:\n"
                + "    a:\n"
                + "      item:\n"
                + "        id: diamond\n";
    }

    private static ConfigSnapshot loadShipped() throws IOException {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds("{}\n")
                .language("en", resource("/languages/en.yml"))
                .build(), ACCEPTING, WarningKinds.all());
    }

    private static List<ConfigWarning> wikiCommandWarnings(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(warning -> warning.kind() == ConfigWarning.Kind.UNUSABLE_WIKI_COMMAND_VALUE)
                .toList();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigWikiTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
