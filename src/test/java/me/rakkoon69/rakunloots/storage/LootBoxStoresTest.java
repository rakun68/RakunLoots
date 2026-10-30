package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.config.StorageSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static me.rakkoon69.rakunloots.storage.LootBoxStoreContract.emptyBox;
import static me.rakkoon69.rakunloots.storage.LootBoxStoreContract.storageSettings;
import static me.rakkoon69.rakunloots.storage.LootBoxStoreContract.unendingLoots;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootBoxStoresTest {

    @TempDir
    Path dataFolder;

    @Test
    void sqliteTypeOpensTheFileInTheDataFolder() throws Exception {
        try (LootBoxStore store = LootBoxStores.open(storageSettings("type: sqlite"), dataFolder)) {
            store.save(emptyBox(UUID.randomUUID()));
        }

        assertTrue(Files.exists(dataFolder.resolve(SqliteStores.FILE_NAME)));
    }

    @Test
    void mysqlTypeOpensWhileMySqlIsDownAndNeverFallsBackToSqlite() throws Exception {
        UUID boxId = UUID.randomUUID();
        // Nothing listens on port 1, so the connection is refused at once.
        StorageSettings settings = storageSettings("""
                type: mysql
                host: 127.0.0.1
                port: 1
                """);
        assertEquals(1, settings.port());

        try (LootBoxStore store = LootBoxStores.open(settings, dataFolder)) {
            assertThrows(LootBoxStoreException.class, () -> store.loadAll(unendingLoots(), world -> true));
            store.markChanged(emptyBox(boxId));

            FlushResult result = store.flush(Runnable::run).get();

            assertEquals(Set.of(boxId), result.failed());
            assertEquals(1, result.warnings().size());
            assertEquals(LootBoxWarning.Kind.FLUSH_FAILED, result.warnings().get(0).kind());
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.SEVERE, result.warnings().get(0).severity());
            assertEquals(Set.of(boxId), store.flush(Runnable::run).get().failed());
            store.backup(5);
        }

        assertFalse(Files.exists(dataFolder.resolve(SqliteStores.FILE_NAME)));
        assertFalse(Files.exists(dataFolder.resolve(SqliteBackups.FOLDER_NAME)));
    }
}
