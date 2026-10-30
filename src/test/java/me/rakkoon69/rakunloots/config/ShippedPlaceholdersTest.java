package me.rakkoon69.rakunloots.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every placeholder in a shipped file is written {@code {name}}. The test reads the files the way a new server gets
 * them: each resource in {@link ConfigFolder#DEFAULT_RESOURCES}.
 *
 * <p>It tells a placeholder from a legitimate angle bracket by shape, not by bracket alone. The old placeholder was
 * {@code <} then lower case letters and underscores then {@code >}. A hex color {@code <#fad778>}, the {@code <<} and
 * {@code >>} of a hologram frame, and a {@code <-----} rule never have that shape. Command usage such as
 * {@code timer <world> now [--force]} is not in any shipped file, since the command tree writes it, so it needs no
 * exception. The two shipped lines that do have the shape and are not placeholders are listed in
 * {@link #SYNTAX_LINES}.
 */
class ShippedPlaceholdersTest {

    private static final Pattern OLD_PLACEHOLDER = Pattern.compile("<[a-z_]+>");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-z_]+}");

    /**
     * Lines that describe a syntax, not a message: a permission node and a chance list entry.
     */
    private static final Set<String> SYNTAX_LINES = Set.of(
            "# The admin command. Each command under it has its own permission, rakunloots.command.<command>, and",
            "      # Syntax: <loot_id>:<chance>");

    private static String resource(String path) throws IOException {
        try (InputStream in = ShippedPlaceholdersTest.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void noShippedFileHoldsAnAngleBracketPlaceholder() throws IOException {
        List<String> found = new ArrayList<>();
        for (String path : ConfigFolder.DEFAULT_RESOURCES) {
            int number = 0;
            for (String line : resource(path).split("\r?\n", -1)) {
                number++;
                if (!SYNTAX_LINES.contains(line) && OLD_PLACEHOLDER.matcher(line).find()) {
                    found.add(path + ":" + number + " " + line);
                }
            }
        }
        assertEquals(List.of(), found);
    }

    @Test
    @SuppressWarnings("unchecked")
    void noShippedMessageKeyHoldsAnAngleBracketPlaceholder() throws IOException {
        List<String> found = new ArrayList<>();
        for (String language : List.of("en", "vi", "zh")) {
            Map<String, Object> messages = (Map<String, Object>) new Yaml().load(resource("languages/" + language + ".yml"));
            for (Map.Entry<String, Object> message : messages.entrySet()) {
                String text = message.getValue() instanceof List<?> list
                        ? String.join("\n", list.stream().map(String::valueOf).toList())
                        : String.valueOf(message.getValue());
                if (OLD_PLACEHOLDER.matcher(text).find()) {
                    found.add(language + " " + message.getKey() + ": " + text);
                }
            }
        }
        assertEquals(List.of(), found);
    }

    /**
     * A migrated key keeps its placeholders in every language: vi and zh name the same ones as en.
     */
    @Test
    @SuppressWarnings("unchecked")
    void everyLanguageNamesTheSamePlaceholdersAsEnglish() throws IOException {
        Map<String, Object> english = (Map<String, Object>) new Yaml().load(resource("languages/en.yml"));
        int withPlaceholders = 0;
        for (String language : List.of("vi", "zh")) {
            Map<String, Object> other = (Map<String, Object>) new Yaml().load(resource("languages/" + language + ".yml"));
            for (Map.Entry<String, Object> message : english.entrySet()) {
                Set<String> want = placeholders(message.getValue());
                withPlaceholders += want.isEmpty() ? 0 : 1;
                assertEquals(want, placeholders(other.get(message.getKey())), language + " " + message.getKey());
            }
        }
        assertTrue(withPlaceholders > 50, "the english file has placeholders to compare");
    }

    private static Set<String> placeholders(Object value) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value instanceof List<?> list ? list.toString() : String.valueOf(value));
        while (matcher.find()) {
            names.add(matcher.group());
        }
        return names;
    }

    /**
     * A value that holds a placeholder starts with a quote, since YAML reads one that starts with {@code {} as a map.
     * A block scalar ({@code |}) and a comment are not values in that sense.
     */
    @Test
    void everyShippedValueWithAPlaceholderIsQuoted() throws IOException {
        List<String> unquoted = new ArrayList<>();
        int quoted = 0;
        for (String path : ConfigFolder.DEFAULT_RESOURCES) {
            int blockIndent = -1;
            int number = 0;
            for (String line : resource(path).split("\r?\n", -1)) {
                number++;
                int indent = line.length() - line.stripLeading().length();
                if (blockIndent >= 0) {
                    if (line.isBlank() || indent > blockIndent) {
                        continue;
                    }
                    blockIndent = -1;
                }
                String text = line.strip();
                if (text.endsWith("|") || text.endsWith("|-") || text.endsWith(">") || text.endsWith(">-")) {
                    blockIndent = indent;
                    continue;
                }
                if (text.startsWith("#") || !PLACEHOLDER.matcher(text).find()) {
                    continue;
                }
                String value = text.startsWith("- ") ? text.substring(2) : text.replaceFirst("^[A-Za-z0-9_]+:\\s*", "");
                if (value.startsWith("\"") || value.startsWith("'")) {
                    quoted++;
                } else {
                    unquoted.add(path + ":" + number + " " + line);
                }
            }
        }
        assertEquals(List.of(), unquoted);
        assertTrue(quoted > 100, "values with placeholders were checked, " + quoted);
    }

    /**
     * The hologram lines, in the language file and in the shipped chest, read {@code {name}}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void everyShippedHologramLineUsesBraces() throws IOException {
        Map<String, Object> english = (Map<String, Object>) new Yaml().load(resource("languages/en.yml"));
        assertEquals(Arrays.asList("&f&l<< &r{name} &f&l>>", "&7Owner: &e{owner}", "&7Lock: &c{lock}",
                "&7Leaves in: &e{life}"), english.get("hologram_lines"));
        Map<String, Object> chest = (Map<String, Object>) new Yaml().load(resource("loots/treasure_chest.yml"));
        Map<String, Object> hologram = (Map<String, Object>) chest.get("hologram");
        assertEquals(Arrays.asList("&6&l{name}", "&7Owner: &e{owner}", "&7Unlocks in: &c{lock}", "&7Leaves in: &e{life}"),
                hologram.get("lines"));
    }
}
