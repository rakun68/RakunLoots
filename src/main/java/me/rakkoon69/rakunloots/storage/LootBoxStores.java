package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.StorageSettings;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Opens the loot box store that {@code storage.type} names. There is no fallback: a MySQL store that cannot reach
 * its database keeps retrying that database and never writes to SQLite, so rows are not split across two stores.
 */
public final class LootBoxStores {

    private LootBoxStores() {
    }

    /**
     * {@code mysql} opens {@link MySqlStores#open(StorageSettings)}; {@code sqlite}, which config loading also
     * uses for a bad or missing type, opens the SQLite file in the data folder through {@link SqliteStores#open(Path)}.
     * <p>
     * A MySQL store opens while MySQL is down. Its first load then throws; the caller warns at severe, keeps boxes
     * in memory, and marks and flushes them on this same store, which connects again on each flush until MySQL
     * answers. Failed flushes warn at severe through their {@link FlushResult}.
     *
     * @throws LootBoxStoreException when the SQLite file cannot be opened or a driver is not on the class path
     */
    public static LootBoxStore open(StorageSettings settings, Path dataFolder) throws LootBoxStoreException {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        return switch (settings.type()) {
            case MYSQL -> MySqlStores.open(settings);
            case SQLITE -> SqliteStores.open(dataFolder);
        };
    }
}
