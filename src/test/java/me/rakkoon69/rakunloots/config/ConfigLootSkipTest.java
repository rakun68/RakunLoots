package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLootSkipTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void missingBlankUnknownOrNonTextOpenModeSkipsLoot() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("missing", """
                        language: en
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .loot("blank", """
                        language: en
                        open_mode: "  "
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .loot("unknown", """
                        language: en
                        open_mode: crate
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .loot("number", """
                        language: en
                        open_mode: 1
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .loot("good", """
                        language: en
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.loot("missing").isEmpty());
        assertTrue(snapshot.loot("blank").isEmpty());
        assertTrue(snapshot.loot("unknown").isEmpty());
        assertTrue(snapshot.loot("number").isEmpty());
        assertTrue(snapshot.loot("good").isPresent());
        assertEquals(4, snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_OPEN_MODE).count());
    }

    @Test
    void openModeIsTrimmedAndMatchedIgnoringCase() {
        ConfigSources sources = loot("""
                open_mode: "  DrOp  "
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        assertEquals(OpenMode.DROP, ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().openMode());
    }

    @Test
    void negativeOrNonIntegerLockMeansNoLockWithHighWarning() {
        ConfigSources neg = loot("""
                open_mode: drop
                lock: -5
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources bad = loot("""
                open_mode: drop
                lock: 1.5
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot negSnap = ConfigLoader.load(neg, ACCEPTING, WarningKinds.all());
        ConfigSnapshot badSnap = ConfigLoader.load(bad, ACCEPTING, WarningKinds.all());
        assertEquals(0, negSnap.loot("demo").orElseThrow().lockSeconds());
        assertEquals(0, badSnap.loot("demo").orElseThrow().lockSeconds());
        assertTrue(negSnap.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_LOCK));
        assertTrue(badSnap.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_LOCK));
    }

    @Test
    void negativeLifeIsUnendingNonIntegerLifeIs360() {
        ConfigSources neg = loot("""
                open_mode: drop
                life: -10
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources bad = loot("""
                open_mode: drop
                life: forever
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot negSnap = ConfigLoader.load(neg, ACCEPTING, WarningKinds.all());
        ConfigSnapshot badSnap = ConfigLoader.load(bad, ACCEPTING, WarningKinds.all());
        assertEquals(OptionalInt.empty(), negSnap.loot("demo").orElseThrow().lifeSeconds());
        assertEquals(OptionalInt.of(360), badSnap.loot("demo").orElseThrow().lifeSeconds());
        assertTrue(negSnap.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.NEGATIVE_LIFE
                && w.severity() == me.rakkoon69.rakunloots.warning.Severity.LOW));
        assertTrue(badSnap.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_LIFE));
    }

    @Test
    void nonBooleanLootEndValueIsOffAndNonMapLeavesAllOff() {
        ConfigSources flag = loot("""
                open_mode: drop
                loot_end:
                  allow_explosion: 1
                  drop_block: yes
                  timeout_spill: true
                  empty_removal: false
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources notMap = loot("""
                open_mode: drop
                loot_end: true
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        LootEndRules flags = ConfigLoader.load(flag, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().lootEnd();
        assertFalse(flags.allowExplosion());
        assertFalse(flags.dropBlock());
        assertTrue(flags.timeoutSpill());
        assertFalse(flags.emptyRemoval());

        LootEndRules allOff = ConfigLoader.load(notMap, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().lootEnd();
        assertFalse(allOff.allowExplosion());
        assertFalse(allOff.dropBlock());
        assertFalse(allOff.timeoutSpill());
        assertFalse(allOff.emptyRemoval());
    }

    @Test
    void nonTextDisplayNameBecomesLootIdNonListDescriptionBecomesEmpty() {
        ConfigSources sources = loot("""
                open_mode: drop
                display_name: 12
                description: not-a-list
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        Loot loaded = snapshot.loot("demo").orElseThrow();
        assertEquals("demo", loaded.displayName());
        assertEquals(List.of(), loaded.description());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DISPLAY_NAME));
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DESCRIPTION));
    }

    @Test
    void nonTextDescriptionLineIsDroppedOtherLinesRemain() {
        ConfigSources sources = loot("""
                open_mode: drop
                description:
                  - keep
                  - 42
                  - also
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertEquals(List.of("keep", "also"), snapshot.loot("demo").orElseThrow().description());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_DESCRIPTION));
    }

    @Test
    void unknownKeysWarnAndAreIgnoredWithoutSkippingLoot() {
        ConfigSources sources = loot("""
                open_mode: drop
                hologram_settings: {}
                block: barrel
                loot_end:
                  allow_explosion: false
                  weird_flag: true
                roll:
                  shuffle: 3
                  entries:
                    one:
                      chance: 10
                      item:
                        id: diamond
                        texture: ignore
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.loot("demo").isPresent());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_KEY && w.key().equals(Optional.of("hologram_settings"))
        ));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_KEY && w.key().equals(Optional.of("weird_flag"))
        ));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_KEY && w.key().equals(Optional.of("shuffle"))
        ));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_KEY && w.key().equals(Optional.of("chance"))
        ));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_KEY && w.key().equals(Optional.of("texture"))
        ));
    }

    @Test
    void skippedLootKeepsLanguageAndMessageOverrides() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("bad", """
                        language: en
                        open_mode: nope
                        messages:
                          still_locked: Override
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.loot("bad").isEmpty());
        assertEquals(Optional.of("en"), snapshot.lootLanguage("bad"));
        assertEquals("Override", snapshot.message("bad", "still_locked"));
    }

    @Test
    void yamlParserRejectionContributesNothingOtherLootLoads() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("broken", "language: en\nopen_mode: [\n")
                .loot("good", """
                        language: en
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.loot("broken").isEmpty());
        assertTrue(snapshot.lootLanguage("broken").isEmpty());
        assertTrue(snapshot.messageOverrides("broken").isEmpty());
        assertTrue(snapshot.loot("good").isPresent());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.YAML_PARSE_REJECTED
                        && w.lootId().equals(Optional.of("broken"))
        ));
    }

    @Test
    void blankFileSkipsLootAndReportsMissingLootLanguage() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("empty", "")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.loot("empty").isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_MISSING
                        && w.lootId().equals(Optional.of("empty"))
        ));
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNUSABLE_OPEN_MODE
                        && w.lootId().equals(Optional.of("empty"))
        ));
    }

    @Test
    void warningLevelSevereHidesHighWarningsButKeepsCorrectedLoot() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\nwarning-level: severe\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", """
                        language: en
                        open_mode: drop
                        lock: -1
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertEquals(0, snapshot.loot("demo").orElseThrow().lockSeconds());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_LOCK));
    }

    @Test
    void silencingLootLanguageWarningsDoesNotHideLootWarnings() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .warnings("LOOT_LANGUAGE_CLASH: off\nLOOT_LANGUAGE_MISSING: off\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", """
                        language: vi
                        open_mode: drop
                        lock: -1
                        messages:
                          still_locked: X
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                || w.kind() == ConfigWarning.Kind.LOOT_LANGUAGE_MISSING));
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_LOCK));
    }

    private static ConfigSources loot(String body) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n" + body)
                .build();
    }
}
