package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A loot's glow and laser, read from its file by the real config loader.
 */
class VisualsConfigTest {

    /**
     * Acceptance: the shipped loots/airdrops.yml gets glow and laser back. The old file glowed gold, with a guardian
     * laser seen from 50 blocks.
     */
    @Test
    void theShippedAirdropsGlowsGoldWithAGuardianLaserFromFiftyBlocks() {
        ConfigSnapshot snapshot = ShippedHologramConfig.load();

        assertEquals(new VisualsSettings(true, GlowColor.GOLD, Optional.of(LaserType.GUARDIAN), 50, 0),
                snapshot.visuals("airdrops").orElseThrow());
        assertEquals(List.of(), visualsWarnings(snapshot));
    }

    /**
     * Acceptance: the loot file gains glow (enabled, colour) and laser (type, view distance). A loot with neither
     * section has no glow and no laser, silently.
     */
    @Test
    void aLootThatSaysNothingHasNoGlowAndNoLaser() {
        ConfigSnapshot snapshot = load("");

        assertEquals(VisualsSettings.NONE, snapshot.visuals("plain").orElseThrow());
        assertEquals(List.of(), visualsWarnings(snapshot));
    }

    @Test
    void everyKeyIsReadAsWritten() {
        ConfigSnapshot snapshot = load("""
                glow:
                  enabled: true
                  color: dark_aqua
                laser:
                  type: crystal
                  view_distance: 120
                  start_offset: 9
                """);

        assertEquals(new VisualsSettings(true, GlowColor.DARK_AQUA, Optional.of(LaserType.CRYSTAL), 120, 9),
                snapshot.visuals("plain").orElseThrow());
        assertEquals(List.of(), visualsWarnings(snapshot));
    }

    @Test
    void aLaserTypeNoneIsNoLaserWithoutAWarning() {
        ConfigSnapshot snapshot = load("""
                laser:
                  type: NONE
                  view_distance: 30
                """);

        assertEquals(new VisualsSettings(false, GlowColor.WHITE, Optional.empty(), 30, 0),
                snapshot.visuals("plain").orElseThrow());
        assertEquals(List.of(), visualsWarnings(snapshot));
    }

    /**
     * Each value that cannot be used warns high on its own key and uses its default; the others stay.
     */
    @Test
    void eachUnusableValueWarnsHighOnItsKeyAndUsesItsDefault() {
        ConfigSnapshot snapshot = load("""
                glow:
                  enabled: maybe
                  color: PINK
                laser:
                  type: BEAM
                  view_distance: 0
                  start_offset: 65
                """);

        assertEquals(VisualsSettings.NONE, snapshot.visuals("plain").orElseThrow());
        assertEquals(List.of(
                        warning(ConfigWarning.Kind.UNUSABLE_GLOW, "enabled"),
                        warning(ConfigWarning.Kind.UNUSABLE_GLOW, "color"),
                        warning(ConfigWarning.Kind.UNUSABLE_LASER, "type"),
                        warning(ConfigWarning.Kind.UNUSABLE_LASER, "view_distance"),
                        warning(ConfigWarning.Kind.UNUSABLE_LASER, "start_offset")),
                visualsWarnings(snapshot));
        assertTrue(snapshot.warnings().stream().filter(VisualsConfigTest::isVisuals)
                .allMatch(w -> w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    /**
     * Acceptance: laser.start_offset is read as written from 0 to 64 and defaults to 0 without a warning; a value
     * outside that range, or not a whole number, warns high on its key and uses 0.
     */
    @Test
    void theLaserStartOffsetDefaultsToZeroAndTakesAnyWholeNumberFromZeroToSixtyFour() {
        assertEquals(0, startOffset("laser:\n  type: GUARDIAN\n"));
        assertEquals(5, startOffset("laser:\n  type: GUARDIAN\n  start_offset: 5\n"));
        assertEquals(64, startOffset("laser:\n  type: GUARDIAN\n  start_offset: 64\n"));
        assertEquals(List.of(), visualsWarnings(load("laser:\n  type: GUARDIAN\n  start_offset: 64\n")));
        for (String bad : List.of("-1", "65", "2.5", "high", "''")) {
            ConfigSnapshot snapshot = load("laser:\n  type: GUARDIAN\n  start_offset: " + bad + "\n");
            assertEquals(0, snapshot.visuals("plain").orElseThrow().laserStartOffset(), bad);
            assertEquals(List.of(warning(ConfigWarning.Kind.UNUSABLE_LASER, "start_offset")),
                    visualsWarnings(snapshot), bad);
        }
    }

    private static int startOffset(String extra) {
        return load(extra).visuals("plain").orElseThrow().laserStartOffset();
    }

    @Test
    void aSectionThatIsNotAMapWarnsHighAndUsesTheDefaults() {
        ConfigSnapshot snapshot = load("glow: true\nlaser: GUARDIAN\n");

        assertEquals(VisualsSettings.NONE, snapshot.visuals("plain").orElseThrow());
        assertEquals(List.of(
                        warning(ConfigWarning.Kind.UNUSABLE_GLOW, "glow"),
                        warning(ConfigWarning.Kind.UNUSABLE_LASER, "laser")),
                visualsWarnings(snapshot));
    }

    private static ConfigSnapshot load(String extra) {
        return ShippedHologramConfig.load("en", Map.of("plain", """
                language: en
                display_name: 'Plain'
                open_mode: drop
                lock: 5
                life: 60
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """ + extra));
    }

    /**
     * The warnings glow and laser can cause. The test catalog that lower cases every id adds old name warnings for the
     * shipped blocks, which are not this feature's.
     */
    private static List<String> visualsWarnings(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream().filter(VisualsConfigTest::isVisuals)
                .map(VisualsConfigTest::summary).toList();
    }

    private static boolean isVisuals(ConfigWarning warning) {
        return warning.kind() == ConfigWarning.Kind.UNUSABLE_GLOW || warning.kind() == ConfigWarning.Kind.UNUSABLE_LASER
                || warning.kind() == ConfigWarning.Kind.UNKNOWN_KEY;
    }

    private static String summary(ConfigWarning warning) {
        return warning.kind() + " " + warning.key().orElse("-");
    }

    private static String warning(ConfigWarning.Kind kind, String key) {
        return kind + " " + key;
    }
}
