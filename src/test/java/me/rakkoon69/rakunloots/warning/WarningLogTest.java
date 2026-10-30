package me.rakkoon69.rakunloots.warning;

import me.rakkoon69.rakunloots.animation.AnimationWarning;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.hologram.HologramWarning;
import me.rakkoon69.rakunloots.items.ItemWarning;
import me.rakkoon69.rakunloots.lootbox.LifecycleWarning;
import me.rakkoon69.rakunloots.roll.RollWarning;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxWarning;
import me.rakkoon69.rakunloots.visuals.VisualsWarning;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every warning type the modules raise reaches the plugin's logger, with its kind and its fields, at the level its
 * severity maps to, and the config's warning level filters them. The warnings are made through the factories their own
 * modules use, since no module hands one out.
 */
class WarningLogTest {

    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();
    private WarningLevel level = WarningLevel.LOW;
    private boolean debug;
    private WarningSettings settings = WarningSettings.none();
    private final WarningLog log = new WarningLog(logger, () -> level, () -> debug, () -> settings);

    WarningLogTest() {
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T make(Class<T> type, String factory, Object... args) {
        try {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(factory) && method.getParameterCount() == args.length) {
                    method.setAccessible(true);
                    return (T) method.invoke(null, args);
                }
            }
            throw new IllegalArgumentException("no " + type + "." + factory);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String message(int index) {
        return records.get(index).getMessage();
    }

    @Test
    void lifecycleWarningsLogAtTheLevelOfTheirSeverityWithTheirFields() {
        Throwable failure = new IllegalStateException("boom");
        UUID world = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        log.warn(make(LifecycleWarning.class, "slotMoved", "crate", 4));
        log.warn(make(LifecycleWarning.class, "summonFailed", "crate", failure));
        log.warn(make(LifecycleWarning.class, "saveFailed", failure));
        log.warn(make(LifecycleWarning.class, "lootMissing", 2, List.of("a", "b")));
        log.warn(make(LifecycleWarning.class, "timerFailed", "world broadcast", failure));
        log.warn(make(LifecycleWarning.class, "observerFailed", "crate", world, new BlockPosition(1, 2, 3), failure));

        assertEquals(List.of(Level.INFO, Level.WARNING, Level.SEVERE, Level.WARNING, Level.WARNING, Level.WARNING),
                records.stream().map(LogRecord::getLevel).toList());
        assertTrue(message(0).contains("SLOT_MOVED") && message(0).contains("crate") && message(0).contains("4"),
                message(0));
        assertTrue(message(1).contains("SUMMON_FAILED"), message(1));
        assertSame(failure, records.get(1).getThrown());
        assertTrue(message(2).contains("SAVE_FAILED"), message(2));
        assertTrue(message(3).contains("LOOT_MISSING") && message(3).contains("2") && message(3).contains("a, b"),
                message(3));
        assertTrue(message(4).contains("TIMER_FAILED") && message(4).contains("world broadcast"), message(4));
        assertTrue(message(5).contains("OBSERVER_FAILED") && message(5).contains(world.toString())
                && message(5).contains("1, 2, 3"), message(5));
    }

    /**
     * A low warning, such as glow off on Spigot, names its cause in one line. Only a high or severe one carries the
     * stack trace.
     */
    @Test
    void aLowWarningNamesItsCauseWithoutAStackTrace() {
        Throwable failure = new UnsupportedOperationException("only on Paper");
        log.warn(make(VisualsWarning.class, "unavailable", VisualsWarning.Kind.GLOW_UNAVAILABLE, failure));
        log.warn(make(LifecycleWarning.class, "summonFailed", "crate", failure));

        assertEquals(Level.INFO, records.get(0).getLevel());
        assertTrue(message(0).contains("only on Paper"), message(0));
        assertEquals(null, records.get(0).getThrown());
        assertSame(failure, records.get(1).getThrown());
    }

    /**
     * Acceptance: the warning log records every warning before the warning level check, from the config load and from
     * runtime, one entry per kind with its severity, count, first 3 subjects, and whether it passed the level.
     */
    @Test
    void everyWarningIsRecordedBeforeTheWarningLevelCheckOnePerKind() {
        level = WarningLevel.SEVERE;
        Throwable failure = new IllegalStateException("boom");
        log.config(List.of(ConfigWarning.unknownName("a"), ConfigWarning.unknownName("b"),
                ConfigWarning.unknownName("a"), ConfigWarning.unknownName("c"), ConfigWarning.unknownName("d"),
                ConfigWarning.oldName("grass", "short_grass")));
        log.warn(make(LifecycleWarning.class, "summonFailed", "crate", failure));
        log.warn(make(LifecycleWarning.class, "saveFailed", failure));
        log.warn(make(VisualsWarning.class, "unavailable", VisualsWarning.Kind.GLOW_UNAVAILABLE, failure));

        assertEquals(1, records.size(), "only the severe one reaches the logger");
        assertEquals(List.of(
                new WarningRecord.Entry("UNKNOWN_NAME", Severity.HIGH, 5, List.of("a", "b", "c"), false),
                new WarningRecord.Entry("OLD_NAME", Severity.LOW, 1, List.of("grass"), false),
                new WarningRecord.Entry("SUMMON_FAILED", Severity.HIGH, 1, List.of("crate"), false),
                new WarningRecord.Entry("SAVE_FAILED", Severity.SEVERE, 1, List.of(), true),
                new WarningRecord.Entry("GLOW_UNAVAILABLE", Severity.LOW, 1, List.of(), false)
        ), log.record().entries());
    }

    @Test
    void skippedOptionalEntriesAreListedAsTheSubjectOfTheirWarning() {
        log.config(List.of(ConfigWarning.skippedOptionalEntries(List.of("?minecraft:grass", "?#minecraft:tall_flowers"))));

        assertEquals(List.of(new WarningRecord.Entry("SKIPPED_OPTIONAL_ENTRIES", Severity.LOW, 1,
                List.of("?minecraft:grass, ?#minecraft:tall_flowers"), true)), log.record().entries());
    }

    @Test
    void theSharedTypoLineListsItsTyposAndHowManyMore() {
        List<String> typos = new ArrayList<>();
        for (int i = 0; i < 79; i++) {
            typos.add("n" + i);
        }
        log.config(List.of(ConfigWarning.moreTypos(typos)));

        assertEquals(1, records.size());
        String line = records.get(0).getMessage();
        assertTrue(line.startsWith("MORE_TYPOS"), line);
        assertTrue(line.contains("entries=[n0, n1, n2, n3, n4, n5, n6, n7, n8, n9] (69 more)"), line);
        assertFalse(line.contains("n10"), line);
        assertEquals(List.of(new WarningRecord.Entry("MORE_TYPOS", Severity.HIGH, 1,
                List.of("n0, n1, n2, n3, n4, n5, n6, n7, n8, n9 (69 more)"), true)), log.record().entries());
    }

    /**
     * Acceptance: the record is cleared, and holds one entry per kind however many warnings come.
     */
    @Test
    void theRecordIsClearedAndHoldsOneEntryPerKind() {
        for (int i = 0; i < 1000; i++) {
            log.config(List.of(ConfigWarning.unknownName("n" + i)));
        }
        assertEquals(1, log.record().entries().size());
        assertEquals(1000, log.record().entries().get(0).count());

        log.record().clear();

        assertEquals(List.of(), log.record().entries());
    }

    @Test
    void rollStoreAnimationVisualsAndHologramWarningsAllReachTheLog() {
        Throwable failure = new IllegalStateException("boom");
        log.warn(make(RollWarning.class, "itemWarning", "crate", "entry1", ItemWarning.unknownId("nope")));
        log.warn(make(LootBoxWarning.class, "flushFailed", "box-1", failure));
        log.warn(make(AnimationWarning.class, "drawFailed", "RED_SPHERE", failure));
        log.warn(make(VisualsWarning.class, "unavailable", VisualsWarning.Kind.GLOW_UNAVAILABLE, failure));
        log.warn(make(HologramWarning.class, "noProvider", Map.of()));

        assertEquals(List.of(Level.WARNING, Level.SEVERE, Level.WARNING, Level.INFO, Level.WARNING),
                records.stream().map(LogRecord::getLevel).toList());
        assertTrue(message(0).contains("crate") && message(0).contains("entry1") && message(0).contains("UNKNOWN_ID")
                && message(0).contains("nope"), message(0));
        assertTrue(message(1).contains("FLUSH_FAILED") && message(1).contains("box-1"), message(1));
        assertTrue(message(2).contains("DRAW_FAILED") && message(2).contains("RED_SPHERE"), message(2));
        assertTrue(message(3).contains("GLOW_UNAVAILABLE"), message(3));
        assertTrue(message(4).contains("NO_PROVIDER"), message(4));
    }

    @Test
    void aDebugLineIsInfoWithItsPrefixWhileDebugIsOnAndNothingWhileOff() {
        log.debug("off line");
        debug = true;
        log.debug("summon placed airdrops");
        debug = false;
        log.debug("off again");

        assertEquals(1, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertEquals("[debug] summon placed airdrops", message(0));
    }

    @Test
    void aWarningLevelOfSevereDoesNotHideDebugLines() {
        level = WarningLevel.SEVERE;
        debug = true;

        log.debug("a line");

        assertEquals(1, records.size());
    }

    @Test
    void configWarningsLogWithTheirFields() {
        log.config(List.of(ConfigWarning.oldName("GRASS", "SHORT_GRASS"), ConfigWarning.unknownName("zzz"),
                ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, "crate", "entry1")));

        assertEquals(3, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertEquals(Level.WARNING, records.get(1).getLevel());
        assertTrue(message(0).contains("OLD_NAME") && message(0).contains("GRASS") && message(0).contains("SHORT_GRASS"),
                message(0));
        assertTrue(message(2).contains("crate") && message(2).contains("entry1"), message(2));
    }

    @Test
    void theWarningLevelDropsWhatIsBelowIt() {
        level = WarningLevel.HIGH;

        log.warn(make(LifecycleWarning.class, "slotMoved", "crate", 4));
        log.warn(make(LifecycleWarning.class, "noRoom", "crate"));

        assertEquals(1, records.size());
        assertTrue(message(0).contains("NO_ROOM"), message(0));
    }

    @Test
    void aFailureSinkLogsWhatFailedAtSevereWithItsCause() {
        Throwable failure = new IllegalStateException("boom");

        log.failure("claim failed for a player", failure);

        assertEquals(Level.SEVERE, records.get(0).getLevel());
        assertEquals("claim failed for a player", message(0));
        assertSame(failure, records.get(0).getThrown());
    }

    /**
     * Acceptance: the warning log applies the file's severity before the warning level and before the record. A slot
     * moved is low, so at level high it is hidden; raised to severe it reaches the log as severe and the record holds
     * it as severe, passed.
     */
    @Test
    void theFilesSeverityIsAppliedBeforeTheLevelAndTheRecord() {
        level = WarningLevel.HIGH;
        log.warn(make(LifecycleWarning.class, "slotMoved", "crate", 4));
        assertTrue(records.isEmpty());
        assertEquals(Severity.LOW, log.record().entries().get(0).severity());
        assertFalse(log.record().entries().get(0).passedLevel());

        log.record().clear();
        settings = WarningSettings.of(Map.of("SLOT_MOVED", WarningSettings.Setting.SEVERE));
        log.warn(make(LifecycleWarning.class, "slotMoved", "crate", 4));

        assertEquals(List.of(Level.SEVERE), records.stream().map(LogRecord::getLevel).toList());
        assertEquals(Severity.SEVERE, log.record().entries().get(0).severity());
        assertTrue(log.record().entries().get(0).passedLevel());
    }

    /**
     * Acceptance: an off kind is neither logged nor recorded, for a kind of every module, and the other kinds of the
     * module still are.
     */
    @Test
    void anOffKindIsNeitherLoggedNorRecordedForEveryModule() {
        Throwable failure = new IllegalStateException("boom");
        settings = WarningSettings.of(Map.of(
                "SLOT_MOVED", WarningSettings.Setting.OFF,
                "ITEM_WARNING", WarningSettings.Setting.OFF,
                "FLUSH_FAILED", WarningSettings.Setting.OFF,
                "DRAW_FAILED", WarningSettings.Setting.OFF,
                "GLOW_UNAVAILABLE", WarningSettings.Setting.OFF,
                "NO_PROVIDER", WarningSettings.Setting.OFF,
                "OLD_NAME", WarningSettings.Setting.OFF));

        log.warn(make(LifecycleWarning.class, "slotMoved", "crate", 4));
        log.warn(make(RollWarning.class, "itemWarning", "crate", "entry1", ItemWarning.unknownId("nope")));
        log.warn(make(LootBoxWarning.class, "flushFailed", "box-1", failure));
        log.warn(make(AnimationWarning.class, "drawFailed", "RED_SPHERE", failure));
        log.warn(make(VisualsWarning.class, "unavailable", VisualsWarning.Kind.GLOW_UNAVAILABLE, failure));
        log.warn(make(HologramWarning.class, "noProvider", Map.of()));
        log.config(List.of(ConfigWarning.oldName("grass", "short_grass")));
        log.warn(make(LifecycleWarning.class, "noRoom", "crate"));

        assertEquals(1, records.size());
        assertTrue(message(0).contains("NO_ROOM"), message(0));
        assertEquals(List.of("NO_ROOM"), log.record().entries().stream().map(WarningRecord.Entry::kind).toList());
    }

    /**
     * A kind two modules both name is told apart by its stable name in the log and in the record.
     */
    @Test
    void twoKindsWithOneConstantNameKeepTheirOwnStableNames() {
        log.warn(make(LifecycleWarning.class, "lootMissing", 2, List.of("a", "b")));
        log.warn(make(LootBoxWarning.class, "lootMissing", "box-1", "crate"));
        settings = WarningSettings.of(Map.of("STORAGE_LOOT_MISSING", WarningSettings.Setting.OFF));
        log.warn(make(LifecycleWarning.class, "lootMissing", 2, List.of("a", "b")));
        log.warn(make(LootBoxWarning.class, "lootMissing", "box-1", "crate"));

        assertEquals(3, records.size());
        assertTrue(message(0).startsWith("LIFECYCLE_LOOT_MISSING"), message(0));
        assertTrue(message(1).startsWith("STORAGE_LOOT_MISSING"), message(1));
        assertEquals(List.of("LIFECYCLE_LOOT_MISSING", "STORAGE_LOOT_MISSING"),
                log.record().entries().stream().map(WarningRecord.Entry::kind).toList());
    }
}
