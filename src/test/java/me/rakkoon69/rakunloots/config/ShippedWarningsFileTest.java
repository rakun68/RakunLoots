package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.WarningKinds;
import me.rakkoon69.rakunloots.warning.WarningKind;
import me.rakkoon69.rakunloots.warning.WarningSettings;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code warnings.yml} a new server gets, read from the jar the way the config load reads it.
 */
class ShippedWarningsFileTest {

    private static String shipped() throws IOException {
        try (InputStream in = ShippedWarningsFileTest.class.getResourceAsStream("/warnings.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Acceptance: a fresh file lists every kind at its default, and only {@code LOOT_LANGUAGE_CLASH} is off, since it
     * replaces the old switch that silenced loot language warnings.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aFreshFileListsEveryKindAtItsDefault() throws IOException {
        Map<String, Object> file = (Map<String, Object>) new Yaml().load(shipped());

        List<String> expected = new ArrayList<>();
        for (WarningKind kind : WarningKinds.all()) {
            String name = WarningKinds.name(kind);
            expected.add(name);
            Object written = file.get(name);
            String want = name.equals("LOOT_LANGUAGE_CLASH")
                    ? "off" : kind.defaultSeverity().name().toLowerCase(Locale.ROOT);
            // SnakeYAML reads an unquoted off as false.
            String got = Boolean.FALSE.equals(written) ? "off" : String.valueOf(written);
            assertEquals(want, got, name);
        }
        assertEquals(expected, new ArrayList<>(file.keySet()), "every kind, once, in the registry's order");
    }

    /**
     * Acceptance: the header says changing the severities is at the owner's own risk, and why.
     */
    @Test
    void theHeaderSaysChangingSeveritiesIsAtTheOwnersOwnRisk() throws IOException {
        String header = shipped().lines().takeWhile(line -> line.startsWith("#")).reduce("", (a, b) -> a + b + "\n");

        assertTrue(header.contains("own risk"), header);
        assertTrue(header.contains("broken loot") && header.contains("lost box"), header);
        assertTrue(header.contains("low") && header.contains("high") && header.contains("severe")
                && header.contains("off"), header);
    }

    /**
     * Acceptance: the shipped file loads with no warning, and it is one of the files saved on first start.
     */
    @Test
    void theShippedFileLoadsQuietAndIsSavedOnFirstStart() throws IOException {
        List<ConfigWarning> warnings = new ArrayList<>();

        WarningSettings settings = WarningsConfig.parse(shipped(), WarningKinds.all(), warnings);

        assertEquals(List.of(), warnings);
        assertTrue(WarningKinds.all().stream().allMatch(kind -> settings.setting(WarningKinds.name(kind)).isPresent()));
        assertEquals(WarningSettings.Setting.OFF, settings.setting("LOOT_LANGUAGE_CLASH").orElseThrow());
        assertTrue(ConfigFolder.DEFAULT_RESOURCES.contains("warnings.yml"));
    }

    /**
     * Acceptance: {@code warn-loot-language} is gone from the shipped config.
     */
    @Test
    void theShippedConfigHasNoWarnLootLanguageKey() throws IOException {
        try (InputStream in = ShippedWarningsFileTest.class.getResourceAsStream("/config.yml")) {
            assertFalse(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("warn-loot-language"));
        }
    }
}
