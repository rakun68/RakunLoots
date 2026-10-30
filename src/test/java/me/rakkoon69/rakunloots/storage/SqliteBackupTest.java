package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.roll.RolledContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteBackupTest {

    @TempDir
    Path dataFolder;

    @TempDir
    Path restoreFolder;

    @Test
    void backupWritesACopyIntoBackupsThatOpensWithTheSameBoxes() throws Exception {
        UUID boxId = UUID.randomUUID();
        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            store.save(box(boxId));

            store.backup(5);
        }

        List<Path> copies = backups();
        assertEquals(1, copies.size());
        Files.copy(copies.get(0), restoreFolder.resolve(SqliteStores.FILE_NAME));
        try (LootBoxStore restored = SqliteStores.open(restoreFolder)) {
            List<LootBoxRecord> loaded = LootBoxStoreContract.records(
                    restored.loadAll(LootBoxStoreContract.unendingLoots(), world -> true));
            assertEquals(1, loaded.size());
            assertEquals(boxId, loaded.get(0).boxId());
            assertEquals("airdrops", loaded.get(0).lootId());
        }
    }

    @Test
    void onlyTheNewestCopiesStayAfterMoreBackupsThanKept() throws Exception {
        SteppingClock clock = new SteppingClock(Instant.parse("2026-09-25T10:00:00Z"));
        try (LootBoxStore store = SqliteStores.open(dataFolder, clock)) {
            for (int i = 0; i < 7; i++) {
                clock.advance(Duration.ofMinutes(1));
                store.backup(5);
            }
        }

        assertEquals(List.of(
                "loot-boxes-20260925T100300.000Z.db",
                "loot-boxes-20260925T100400.000Z.db",
                "loot-boxes-20260925T100500.000Z.db",
                "loot-boxes-20260925T100600.000Z.db",
                "loot-boxes-20260925T100700.000Z.db"
        ), backupNames());
    }

    @Test
    void eachBackupHoldsTheBoxesAtTheTimeItWasTaken() throws Exception {
        SteppingClock clock = new SteppingClock(Instant.parse("2026-09-25T10:00:00Z"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (LootBoxStore store = SqliteStores.open(dataFolder, clock)) {
            store.save(box(first));
            store.backup(5);
            clock.advance(Duration.ofSeconds(1));
            store.save(box(second));
            store.backup(5);
        }

        List<Path> copies = backups();
        assertEquals(List.of(first), boxIdsIn(copies.get(0)));
        assertEquals(Set.of(first, second), Set.copyOf(boxIdsIn(copies.get(1))));
    }

    @Test
    void backupsInTheSameInstantDoNotCollideAndKeepTheirOrder() throws Exception {
        SteppingClock clock = new SteppingClock(Instant.parse("2026-09-25T10:00:00Z"));
        UUID first = UUID.randomUUID();
        try (LootBoxStore store = SqliteStores.open(dataFolder, clock)) {
            store.save(box(first));
            store.backup(5);
            store.delete(first);
            store.backup(5);
            store.backup(2);
        }

        assertEquals(List.of(
                "loot-boxes-20260925T100000.001Z.db",
                "loot-boxes-20260925T100000.002Z.db"
        ), backupNames());
        assertTrue(boxIdsIn(backups().get(0)).isEmpty());
    }

    @Test
    void aClockSteppingBackStillNamesTheNewCopyNewest() throws Exception {
        SteppingClock clock = new SteppingClock(Instant.parse("2026-09-25T10:00:00Z"));
        UUID newest = UUID.randomUUID();
        try (LootBoxStore store = SqliteStores.open(dataFolder, clock)) {
            store.backup(1);
            clock.advance(Duration.ofHours(-1));
            store.save(box(newest));
            store.backup(1);
        }

        assertEquals(List.of("loot-boxes-20260925T100000.001Z.db"), backupNames());
        assertEquals(List.of(newest), boxIdsIn(backups().get(0)));
    }

    @Test
    void keepingZeroWritesNoCopyAndRemovesTheRest() throws Exception {
        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            store.backup(3);
            store.backup(3);

            store.backup(0);
        }

        assertTrue(backupNames().isEmpty());
    }

    @Test
    void pruningLeavesOtherFilesInBackupsAlone() throws Exception {
        Path backupsFolder = Files.createDirectories(dataFolder.resolve(SqliteBackups.FOLDER_NAME));
        Path ownerCopy = Files.writeString(backupsFolder.resolve("before-upgrade.db"), "kept");
        Path odd = Files.writeString(backupsFolder.resolve("loot-boxes-yesterday.db"), "kept");
        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            store.backup(0);
        }

        assertTrue(Files.exists(ownerCopy));
        assertTrue(Files.exists(odd));
    }

    @Test
    void aFileNamedLikeABackupWithAnImpossibleTimestampIsLeftAloneAndDoesNotStopBackups() throws Exception {
        Path backupsFolder = Files.createDirectories(dataFolder.resolve(SqliteBackups.FOLDER_NAME));
        Path impossible = Files.writeString(backupsFolder.resolve("loot-boxes-20261399T999999.999Z.db"), "kept");
        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            store.backup(1);
            store.backup(1);
        }

        assertTrue(Files.exists(impossible));
        assertEquals(2, backupNames().size());
    }

    @Test
    void aCopyLeftHalfWrittenByACrashIsRemovedOnTheNextBackup() throws Exception {
        Path backupsFolder = Files.createDirectories(dataFolder.resolve(SqliteBackups.FOLDER_NAME));
        Path halfWritten = Files.writeString(backupsFolder.resolve("loot-boxes-20200101T000000.000Z.db.tmp"), "x");
        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            store.backup(5);
        }

        assertTrue(Files.notExists(halfWritten));
        assertEquals(1, backupNames().size());
    }

    @Test
    void negativeBackupsKeptIsRejected() throws Exception {
        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            assertThrows(IllegalArgumentException.class, () -> store.backup(-1));
        }
    }

    @Test
    void aStoreWithNoFileDoesNothingOnBackup() throws Exception {
        try (LootBoxStore store = SqliteStores.openInMemory()) {
            store.save(box(UUID.randomUUID()));

            store.backup(5);
        }

        try (Stream<Path> files = Files.list(dataFolder)) {
            assertEquals(0, files.count());
        }
    }

    private List<UUID> boxIdsIn(Path copy) throws Exception {
        Path folder = Files.createTempDirectory(restoreFolder, "restore");
        Files.copy(copy, folder.resolve(SqliteStores.FILE_NAME));
        try (LootBoxStore restored = SqliteStores.open(folder)) {
            return restored.loadAll(LootBoxStoreContract.unendingLoots(), world -> true).boxes().stream()
                    .map(loaded -> loaded.box().boxId()).collect(Collectors.toList());
        }
    }

    private List<String> backupNames() throws Exception {
        return backups().stream().map(path -> path.getFileName().toString()).collect(Collectors.toList());
    }

    private List<Path> backups() throws Exception {
        try (Stream<Path> files = Files.list(dataFolder.resolve(SqliteBackups.FOLDER_NAME))) {
            return files.sorted().collect(Collectors.toList());
        }
    }

    private static LootBoxRecord box(UUID boxId) {
        return new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), UUID.randomUUID(), 1, 2, 3, "minecraft:barrel",
                OptionalInt.of(60), 0, new RolledContents(List.of()));
    }

    private static final class SteppingClock extends Clock {

        private Instant now;

        SteppingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
