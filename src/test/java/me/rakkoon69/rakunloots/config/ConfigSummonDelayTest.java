package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSummonDelayTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    /**
     * Acceptance: the loot file gains {@code summon_delay}, in ticks. Left out, it is 0: no wait.
     */
    @Test
    void summonDelayIsReadInTicksAndIsZeroWhenLeftOut() {
        ConfigSnapshot written = ConfigLoader.load(loot("summon_delay: 100\n"), ACCEPTING, WarningKinds.all());
        ConfigSnapshot omitted = ConfigLoader.load(loot(""), ACCEPTING, WarningKinds.all());

        assertEquals(100, written.loot("demo").orElseThrow().summonDelayTicks());
        assertEquals(0, omitted.loot("demo").orElseThrow().summonDelayTicks());
        assertTrue(written.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_KEY));
        assertTrue(omitted.warnings().stream().noneMatch(w -> w.lootId().equals(Optional.of("demo"))
                && w.kind() == ConfigWarning.Kind.UNUSABLE_SUMMON_DELAY));
    }

    @Test
    void aNegativeOrNonNumberSummonDelayIsZeroWithAHighWarning() {
        for (String bad : new String[]{"-5", "soon", "1.5"}) {
            ConfigSnapshot snapshot = ConfigLoader.load(loot("summon_delay: " + bad + "\n"), ACCEPTING, WarningKinds.all());

            assertEquals(0, snapshot.loot("demo").orElseThrow().summonDelayTicks(), bad);
            assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_SUMMON_DELAY
                    && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                    && w.lootId().equals(Optional.of("demo"))), bad);
        }
    }

    private static ConfigSources loot(String extra) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n" + extra + """
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();
    }
}
