package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.ConfigWarning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store that opens its connection on demand and opens a new one after a failure, as the MySQL store does.
 * Runs over a SQLite file so it needs no database server; the file outlives each connection, as a MySQL database
 * outlives a restart.
 */
class ReopeningLootBoxStoreTest extends LootBoxStoreContract {

    @TempDir
    Path dataFolder;

    private SwitchableOpener opener;

    @Override
    LootBoxStore openStore() {
        opener = new SwitchableOpener(fileOpener(), true);
        return new SqlLootBoxStore(opener, "test_");
    }

    @Override
    void setReachable(boolean reachable) {
        opener.setReachable(reachable);
    }

    @Test
    void storeOpenedWhileUnreachableFailsToLoadThenLoadsOnceReachable() throws Exception {
        SwitchableOpener down = new SwitchableOpener(fileOpener(), false);
        try (LootBoxStore store = new SqlLootBoxStore(down, "test_")) {
            assertThrows(LootBoxStoreException.class, () -> store.loadAll(unendingLoots(), world -> true));

            down.setReachable(true);

            assertTrue(store.loadAll(unendingLoots(), world -> true).boxes().isEmpty());
        }
    }

    @Test
    void storeOpenedWhileUnreachableKeepsChangesPendingAndWritesThemOnceReachable() throws Exception {
        SwitchableOpener down = new SwitchableOpener(fileOpener(), false);
        UUID boxId = UUID.randomUUID();
        try (LootBoxStore store = new SqlLootBoxStore(down, "test_")) {
            store.markChanged(emptyBox(boxId));

            FlushResult failed = store.flush(Runnable::run).get();

            assertEquals(Set.of(boxId), failed.failed());
            assertEquals(1, failed.warnings().size());
            assertEquals(LootBoxWarning.Kind.FLUSH_FAILED, failed.warnings().get(0).kind());
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.SEVERE, failed.warnings().get(0).severity());

            down.setReachable(true);

            assertEquals(Set.of(boxId), store.flush(Runnable::run).get().written());
            assertEquals(List.of(boxId), records(store.loadAll(unendingLoots(), world -> true)).stream()
                    .map(LootBoxRecord::boxId).toList());
        }
    }

    @Test
    void lostConnectionIsReopenedForLoad() throws Exception {
        UUID boxId = UUID.randomUUID();
        try (LootBoxStore store = openStore()) {
            store.save(emptyBox(boxId));
            setReachable(false);
            assertThrows(LootBoxStoreException.class, () -> store.loadAll(unendingLoots(), world -> true));

            setReachable(true);

            assertEquals(1, store.loadAll(unendingLoots(), world -> true).boxes().size());
        }
    }

    @Test
    void flushWhileUnreachableTriesToConnectOnceAndKeepsEveryBoxPending() throws Exception {
        SwitchableOpener down = new SwitchableOpener(fileOpener(), false);
        Set<UUID> boxIds = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        try (LootBoxStore store = new SqlLootBoxStore(down, "test_")) {
            boxIds.forEach(boxId -> store.markChanged(emptyBox(boxId)));

            FlushResult failed = store.flush(Runnable::run).get();

            assertEquals(1, down.attempts());
            assertEquals(boxIds, failed.failed());
            assertEquals(3, failed.warnings().size());
            assertTrue(failed.warnings().stream()
                    .allMatch(warning -> warning.severity() == me.rakkoon69.rakunloots.warning.Severity.SEVERE));

            down.setReachable(true);

            assertEquals(boxIds, store.flush(Runnable::run).get().written());
        }
    }

    @Test
    void connectionClosedWhileIdleIsReplacedWithoutAFailure() throws Exception {
        UUID changed = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        try (LootBoxStore store = openStore()) {
            store.save(emptyBox(deleted));
            opener.closeIdleConnections();
            store.delete(deleted);
            opener.closeIdleConnections();
            store.markChanged(emptyBox(changed));

            FlushResult result = store.flush(Runnable::run).get();

            assertEquals(Set.of(changed), result.written());
            assertTrue(result.warnings().isEmpty());
            opener.closeIdleConnections();
            assertEquals(List.of(changed), records(store.loadAll(unendingLoots(), world -> true)).stream()
                    .map(LootBoxRecord::boxId).toList());
        }
    }

    @Test
    void closedStoreDoesNotReopen() throws Exception {
        LootBoxStore store = openStore();
        store.close();

        assertThrows(LootBoxStoreException.class, () -> store.loadAll(unendingLoots(), world -> true));
        assertThrows(LootBoxStoreException.class, () -> store.save(emptyBox(UUID.randomUUID())));
    }

    @Test
    void unusableTablePrefixIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SqlLootBoxStore(fileOpener(), "bad-prefix"));
    }

    private SqlLootBoxStore.ConnectionOpener fileOpener() {
        String url = "jdbc:sqlite:" + dataFolder.resolve("reopening.db").toAbsolutePath();
        return () -> DriverManager.getConnection(url);
    }
}
