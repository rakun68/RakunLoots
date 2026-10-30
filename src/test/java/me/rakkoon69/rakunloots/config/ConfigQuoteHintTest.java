package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.warning.WarningLevel;
import me.rakkoon69.rakunloots.warning.WarningLog;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A YAML error on a line that holds {@code {} tells the owner to quote the value. The hint is the shipped
 * {@code yaml_quote_hint} message of the active language.
 */
class ConfigQuoteHintTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    private static final String UNQUOTED = """
            language: en
            open_mode: drop
            display_name: {name} Chest
            block: chest
            """;

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigQuoteHintTest.class.getResourceAsStream("/" + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ConfigSnapshot load(String language, String lootFile) throws IOException {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: " + language + "\n")
                .language("en", resource("languages/en.yml"))
                .language("vi", resource("languages/vi.yml"))
                .language("zh", resource("languages/zh.yml"))
                .loot("chest", lootFile)
                .build(), ACCEPTING, WarningKinds.all());
    }

    private static ConfigWarning rejected(ConfigSnapshot snapshot) {
        List<ConfigWarning> rejected = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.YAML_PARSE_REJECTED).toList();
        assertEquals(1, rejected.size(), snapshot.warnings().toString());
        return rejected.get(0);
    }

    @Test
    void anUnquotedPlaceholderValueGivesTheQuoteHintOnItsLine() throws IOException {
        ConfigSnapshot snapshot = load("en", UNQUOTED);

        ConfigWarning warning = rejected(snapshot);
        assertEquals(Optional.of("chest"), warning.lootId());
        String hint = warning.hint().orElseThrow();
        assertTrue(hint.contains("quotes"), hint);
        assertTrue(hint.contains("Line 3"), hint);
        assertTrue(snapshot.loots().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theHintIsTheShippedMessageOfTheActiveLanguage() throws IOException {
        for (String language : List.of("en", "vi", "zh")) {
            Map<String, String> shipped = (Map<String, String>) new Yaml().load(resource("languages/" + language + ".yml"));
            String expected = shipped.get("yaml_quote_hint").replace("{line}", "3");

            assertEquals(Optional.of(expected), rejected(load(language, UNQUOTED)).hint(), language);
        }
    }

    @Test
    void aYamlErrorOnALineWithoutABraceGivesNoHint() throws IOException {
        ConfigSnapshot snapshot = load("en", "language: en\nlines: [unclosed\n");

        assertEquals(Optional.empty(), rejected(snapshot).hint());
    }

    @Test
    void theHintReachesTheLog() throws IOException {
        ConfigSnapshot snapshot = load("en", UNQUOTED);
        List<String> lines = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        new WarningLog(logger, () -> WarningLevel.LOW).config(snapshot.rawWarnings());

        String line = lines.stream().filter(l -> l.contains("YAML_PARSE_REJECTED")).findFirst().orElseThrow();
        assertTrue(line.contains("hint=" + rejected(snapshot).hint().orElseThrow()), line);
    }

    /**
     * The files an owner edits placeholders in cannot be loaded when they are not YAML, and the error says which file
     * and line, and carries the hint.
     */
    private static String failureMessage(String config, String worlds, String en) throws IOException {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(config)
                .worlds(worlds)
                .language("en", en)
                .language("vi", resource("languages/vi.yml"))
                .language("zh", resource("languages/zh.yml"));
        try {
            ConfigLoader.load(sources.build(), ACCEPTING, WarningKinds.all());
        } catch (org.yaml.snakeyaml.error.YAMLException expected) {
            return expected.getMessage();
        }
        throw new AssertionError("the load did not fail");
    }

    @Test
    void anUnquotedPlaceholderInAnEnglishFileNamesTheFileAndLineAndCarriesTheHint() throws IOException {
        String en = "prefix: \"[R]\"\nstill_locked: {prefix} locked\n";

        String message = failureMessage("language: en\n", resource("worlds.yml"), en);

        assertTrue(message.contains("languages/en.yml"), message);
        assertTrue(message.contains("line 2"), message);
        assertTrue(message.contains("Line 2 holds a {. Put the whole value in quotes"), message);
    }

    @Test
    void whenTheActiveLanguageFileCannotBeReadTheEnglishHintShows() throws IOException {
        String vi = "prefix: \"[R]\"\nstill_locked: {prefix} locked\n";
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: vi\n")
                .language("en", resource("languages/en.yml"))
                .language("vi", vi);

        try {
            ConfigLoader.load(sources.build(), ACCEPTING, WarningKinds.all());
            throw new AssertionError("the load did not fail");
        } catch (org.yaml.snakeyaml.error.YAMLException expected) {
            assertTrue(expected.getMessage().contains("languages/vi.yml"), expected.getMessage());
            assertTrue(expected.getMessage().contains("Line 2 holds a {. Put the whole value in quotes"),
                    expected.getMessage());
        }
    }

    @Test
    void anUnquotedPlaceholderInConfigYmlNamesTheFileAndLineAndCarriesTheHint() throws IOException {
        String message = failureMessage("language: en\nnote: {name} here\n", resource("worlds.yml"),
                resource("languages/en.yml"));

        assertTrue(message.contains("config.yml"), message);
        assertTrue(message.contains("line 2"), message);
        assertTrue(message.contains("Line 2 holds a {. Put the whole value in quotes"), message);
    }

    @Test
    void anUnquotedPlaceholderInWorldsYmlNamesTheFileAndLineAndCarriesTheHint() throws IOException {
        String message = failureMessage("language: en\n", "worlds:\n  world:\n    note: {name} here\n",
                resource("languages/en.yml"));

        assertTrue(message.contains("worlds.yml"), message);
        assertTrue(message.contains("line 3"), message);
        assertTrue(message.contains("Line 3 holds a {. Put the whole value in quotes"), message);
    }
}
