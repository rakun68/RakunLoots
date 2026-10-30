package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.WarningKinds;
import me.rakkoon69.rakunloots.server.NameCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageRendererTest {

    @Test
    void renderWithBothChanceAndChanceWithDecimals() {
        ConfigSnapshot config = ConfigLoader.load(
                ConfigSources.builder()
                        .language("en", "chances: 'Default {chance}% vs precise {chance:2}%'\n")
                        .loot("demo", "language: en\n")
                        .build(),
                NameCatalog.empty(),
                WarningKinds.all());

        String rendered = MessageRenderer.render(
                config,
                "demo",
                "chances",
                Map.of(),
                Map.of("chance", 0.125)
        );

        assertEquals("Default 12.5% vs precise 12.50%", rendered);
    }

    @Test
    void renderInvalidPrecisionPlaceholderFallsBackToOneDecimal() {
        ConfigSnapshot config = ConfigLoader.load(
                ConfigSources.builder()
                        .language("en", "invalid: '{chance:9} | {chance:x} | {chance:-1} | {chance:}'\n")
                        .loot("demo", "language: en\n")
                        .build(),
                NameCatalog.empty(),
                WarningKinds.all());

        String rendered = MessageRenderer.render(
                config,
                "demo",
                "invalid",
                Map.of(),
                Map.of("chance", 0.125)
        );

        assertEquals("12.5 | 12.5 | 12.5 | 12.5", rendered);
    }

    @Test
    void renderSubstitutesPrefixAndValues() {
        ConfigSnapshot config = ConfigLoader.load(
                ConfigSources.builder()
                        .language("en", "prefix: '[Box] '\ngreeting: '{prefix}Hello {player}!'\n")
                        .loot("demo", "language: en\n")
                        .build(),
                NameCatalog.empty(),
                WarningKinds.all());

        String rendered = MessageRenderer.render(config, "demo", "greeting", Map.of("player", "Steve"));
        assertEquals("[Box] Hello Steve!", rendered);
    }

    @Test
    void blankMessageRendersBlank() {
        ConfigSnapshot config = ConfigLoader.load(
                ConfigSources.builder()
                        .language("en", "prefix: '[Box] '\nblank: ''\n")
                        .loot("demo", "language: en\n")
                        .build(),
                NameCatalog.empty(),
                WarningKinds.all());

        String rendered = MessageRenderer.render(config, "demo", "blank");
        assertEquals("", rendered);
    }
}
