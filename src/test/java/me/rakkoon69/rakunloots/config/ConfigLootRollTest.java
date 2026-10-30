package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLootRollTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void weightBelowZeroIsAlwaysIncludedOutsideMinMax() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  min: 1
                  max: 1
                  entries:
                    always:
                      weight: -1
                      item:
                        id: diamond
                    pick:
                      weight: 2
                      item:
                        id: emerald
                """);

        Loot loaded = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow();
        assertEquals(1, loaded.roll().min());
        assertEquals(1, loaded.roll().max());
        assertEquals(List.of("always", "pick"), loaded.roll().entries().stream().map(ContentEntry::id).toList());
        assertEquals(-1, loaded.roll().entries().get(0).weight());
    }

    @Test
    void weightZeroSkipsEntryWithHighWarning() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    zero:
                      weight: 0
                      item:
                        id: diamond
                    keep:
                      item:
                        id: emerald
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        Loot loaded = snapshot.loot("demo").orElseThrow();
        assertEquals(List.of("keep"), loaded.roll().entries().stream().map(ContentEntry::id).toList());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && w.entryId().equals(Optional.of("zero"))
        ));
    }

    @Test
    void amountBelowOneSkipsEntryWithHighWarning() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    bad:
                      amount: 0
                      item:
                        id: diamond
                    keep:
                      item:
                        id: emerald
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertEquals(List.of("keep"), snapshot.loot("demo").orElseThrow().roll().entries().stream()
                .map(ContentEntry::id).toList());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY
                        && w.entryId().equals(Optional.of("bad"))
        ));
    }

    @Test
    void nonIntegerAmountOrWeightSkipsEntry() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    fraction:
                      amount: 10.5
                      item:
                        id: diamond
                    huge:
                      weight: 9999999999999999999
                      item:
                        id: diamond
                    keep:
                      item:
                        id: emerald
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertEquals(List.of("keep"), snapshot.loot("demo").orElseThrow().roll().entries().stream()
                .map(ContentEntry::id).toList());
        assertTrue(snapshot.warnings().stream().filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY)
                .count() >= 2);
    }

    @Test
    void missingOrBlankItemOrNonTextSourceSkipsEntry() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    missing_item:
                      amount: 1
                    not_map:
                      item: diamond
                    blank_id:
                      item:
                        id: "  "
                    bad_source:
                      item:
                        source: 1
                        id: diamond
                    keep:
                      item:
                        id: emerald
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertEquals(List.of("keep"), snapshot.loot("demo").orElseThrow().roll().entries().stream()
                .map(ContentEntry::id).toList());
        assertTrue(snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY).count() >= 4);
    }

    @Test
    void omittedMinIsZeroOmittedMaxIsWeightedCountAfterSkips() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    always:
                      weight: -2
                      item:
                        id: diamond
                    a:
                      item:
                        id: emerald
                    b:
                      weight: 3
                      item:
                        id: gold_ingot
                    skipped:
                      weight: 0
                      item:
                        id: coal
                """);

        Roll roll = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().roll();
        assertEquals(0, roll.min());
        assertEquals(2, roll.max());
        assertEquals(3, roll.entries().size());
    }

    @Test
    void negativeMinOrMaxBecomesZeroWithHighWarning() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  min: -3
                  max: -1
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        Roll roll = snapshot.loot("demo").orElseThrow().roll();
        assertEquals(0, roll.min());
        assertEquals(0, roll.max());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.CORRECTED_MIN_MAX
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
        ));
    }

    @Test
    void nonIntegerMinOrMaxUsesOmittedValueWithHighWarning() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  min: 1.5
                  max: nope
                  entries:
                    one:
                      item:
                        id: diamond
                    two:
                      item:
                        id: emerald
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        Roll roll = snapshot.loot("demo").orElseThrow().roll();
        assertEquals(0, roll.min());
        assertEquals(2, roll.max());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.CORRECTED_MIN_MAX));
    }

    @Test
    void minGreaterThanMaxBecomesMaxThenClampedToWeightedCount() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  min: 5
                  max: 3
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        Roll roll = snapshot.loot("demo").orElseThrow().roll();
        assertEquals(1, roll.min());
        assertEquals(1, roll.max());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.CORRECTED_MIN_MAX));
    }

    @Test
    void allAlwaysIncludedLoadsWithMinZeroMaxZero() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  min: 2
                  max: 4
                  entries:
                    a:
                      weight: -1
                      item:
                        id: diamond
                    b:
                      weight: -5
                      item:
                        id: emerald
                """);

        Roll roll = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().roll();
        assertEquals(0, roll.min());
        assertEquals(0, roll.max());
        assertEquals(2, roll.entries().size());
    }

    @Test
    void negativeOrNonIntegerSlotLeavesNoSlotKeepsEntry() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    neg:
                      slot: -1
                      item:
                        id: diamond
                    frac:
                      slot: 1.5
                      item:
                        id: emerald
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        List<ContentEntry> entries = snapshot.loot("demo").orElseThrow().roll().entries();
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).slot().isEmpty());
        assertTrue(entries.get(1).slot().isEmpty());
        assertTrue(snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.REJECTED_SLOT).count() >= 2);
    }

    @Test
    void firstDuplicateSlotWinsLaterLosesSlot() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    first:
                      slot: 3
                      item:
                        id: diamond
                    second:
                      slot: 3
                      item:
                        id: emerald
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        List<ContentEntry> entries = snapshot.loot("demo").orElseThrow().roll().entries();
        assertEquals(OptionalInt.of(3), entries.get(0).slot());
        assertTrue(entries.get(1).slot().isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.REJECTED_SLOT
                        && w.entryId().equals(Optional.of("second"))
        ));
    }

    @Test
    void missingOrWrongShapedRollSkipsLootOtherLootStillLoads() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("bad", """
                        language: en
                        open_mode: drop
                        block: barrel
                        """)
                .loot("good", """
                        language: en
                        open_mode: inventory
                        block: chest
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.loot("bad").isEmpty());
        assertTrue(snapshot.loot("good").isPresent());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNUSABLE_ROLL
                        && w.lootId().equals(Optional.of("bad"))
        ));
    }

    @Test
    void lootWithNoEntriesLeftIsSkipped() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    only:
                      weight: 0
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertTrue(snapshot.loot("demo").isEmpty());
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.NO_CONTENT_ENTRIES
        ));
    }

    @Test
    void wrongShapedVanillaAdditionIsKeptWithoutLootWarning() {
        ConfigSources sources = loot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                        custom_model_data: not-a-number
                        unbreakable: maybe
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        ContentEntry entry = snapshot.loot("demo").orElseThrow().roll().entries().get(0);
        assertEquals(Optional.of("not-a-number"), entry.item().additions().customModelData());
        assertEquals(Optional.of("maybe"), entry.item().additions().unbreakable());
        assertTrue(snapshot.warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY
        ));
    }

    private static ConfigSources loot(String body) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n" + body)
                .build();
    }
}
