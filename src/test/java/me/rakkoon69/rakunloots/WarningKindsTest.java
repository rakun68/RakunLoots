package me.rakkoon69.rakunloots;

import me.rakkoon69.rakunloots.animation.AnimationWarning;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.hologram.HologramWarning;
import me.rakkoon69.rakunloots.lootbox.LifecycleWarning;
import me.rakkoon69.rakunloots.menu.MenuWarning;
import me.rakkoon69.rakunloots.roll.RollWarning;
import me.rakkoon69.rakunloots.storage.LootBoxWarning;
import me.rakkoon69.rakunloots.visuals.VisualsWarning;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.WarningKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every warning kind across the modules has a stable name and a default severity.
 */
class WarningKindsTest {

    private static final List<WarningKind[]> ENUMS = List.of(
            ConfigWarning.Kind.values(), LifecycleWarning.Kind.values(), LootBoxWarning.Kind.values(),
            VisualsWarning.Kind.values(), AnimationWarning.Kind.values(), HologramWarning.Kind.values(),
            RollWarning.Kind.values(), MenuWarning.Kind.values());

    /**
     * Acceptance: every kind of every module is registered, each under its own name.
     */
    @Test
    void everyKindOfEveryModuleHasItsOwnStableName() {
        Set<String> names = new HashSet<>();
        int constants = 0;
        for (WarningKind[] kinds : ENUMS) {
            for (WarningKind kind : kinds) {
                constants++;
                assertTrue(names.add(WarningKinds.name(kind)), "two kinds named " + WarningKinds.name(kind));
                assertEquals(kind, WarningKinds.byName(WarningKinds.name(kind)).orElseThrow());
            }
        }
        assertEquals(constants, WarningKinds.all().size());
    }

    /**
     * The name is the constant's name, except where two modules share one: those take their module's prefix.
     */
    @Test
    void aSharedConstantNameTakesItsModulesPrefix() {
        assertEquals("CONFIG_UNKNOWN_ANIMATION", WarningKinds.name(ConfigWarning.Kind.UNKNOWN_ANIMATION));
        assertEquals("ANIMATION_UNKNOWN_ANIMATION", WarningKinds.name(AnimationWarning.Kind.UNKNOWN_ANIMATION));
        assertEquals("LIFECYCLE_LOOT_MISSING", WarningKinds.name(LifecycleWarning.Kind.LOOT_MISSING));
        assertEquals("STORAGE_LOOT_MISSING", WarningKinds.name(LootBoxWarning.Kind.LOOT_MISSING));
        assertEquals("OLD_NAME", WarningKinds.name(ConfigWarning.Kind.OLD_NAME));
        assertEquals("NO_ROOM", WarningKinds.name(LifecycleWarning.Kind.NO_ROOM));
        assertEquals(2, Stream.of(WarningKinds.all()).flatMap(List::stream)
                .filter(kind -> kind.stableName().endsWith("_UNKNOWN_ANIMATION")).count());
    }

    /**
     * The defaults are what the modules raise today.
     */
    @Test
    void theDefaultsAreTheSeveritiesTheModulesRaise() {
        assertEquals(Severity.LOW, WarningKinds.of(LifecycleWarning.Kind.SLOT_MOVED).defaultSeverity());
        assertEquals(Severity.HIGH, WarningKinds.of(LifecycleWarning.Kind.NO_ROOM).defaultSeverity());
        assertEquals(Severity.SEVERE, WarningKinds.of(LootBoxWarning.Kind.FLUSH_FAILED).defaultSeverity());
        assertEquals(Severity.LOW, WarningKinds.of(VisualsWarning.Kind.GLOW_FAILED).defaultSeverity());
        assertEquals(Severity.HIGH, WarningKinds.of(HologramWarning.Kind.NO_PROVIDER).defaultSeverity());
        assertEquals(Severity.LOW, WarningKinds.of(ConfigWarning.Kind.UNKNOWN_WARNING_KIND).defaultSeverity());
        assertEquals(Severity.SEVERE, WarningKinds.of(ConfigWarning.Kind.GROUP_CYCLE).defaultSeverity());
        // A tag no supported version knows is a typo, raised high (#104).
        assertEquals(Severity.HIGH, WarningKinds.of(ConfigWarning.Kind.UNKNOWN_TAG).defaultSeverity());
        assertEquals(Severity.HIGH,
                WarningKinds.of(MenuWarning.Kind.MENU_LAYOUT_ITEM_MISSING).defaultSeverity());
    }
}
