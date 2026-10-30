package me.rakkoon69.rakunloots.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Opens the default loot box store: one SQLite file in the plugin data folder, through the driver the server
 * bundles.
 * Tables take no prefix; the file holds only this plugin's rows.
 */
public final class SqliteStores {

    /**
     * The file name inside the plugin data folder.
     */
    public static final String FILE_NAME = "loot-boxes.db";

    private static final String DRIVER = "org.sqlite.JDBC";

    private SqliteStores() {
    }

    /**
     * Opens, or creates, the store file in the plugin data folder and brings its schema up to date.
     * Backup copies the file into the {@code backups} folder beside it.
     */
    public static LootBoxStore open(Path dataFolder) throws LootBoxStoreException {
        return open(dataFolder, Clock.systemUTC());
    }

    /**
     * As {@link #open(Path)}, with backups named by the given clock. For tests.
     */
    static LootBoxStore open(Path dataFolder, Clock clock) throws LootBoxStoreException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(clock, "clock");
        Path file = dataFolder.resolve(FILE_NAME);
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException ex) {
            throw new LootBoxStoreException("could not create " + dataFolder, ex);
        }
        return connect("jdbc:sqlite:" + file.toAbsolutePath(),
                Optional.of(new SqliteBackups(dataFolder.resolve(SqliteBackups.FOLDER_NAME), clock)));
    }

    /**
     * A store that lives only as long as it stays open. It has no file, so backup does nothing. For tests.
     */
    public static LootBoxStore openInMemory() throws LootBoxStoreException {
        return connect("jdbc:sqlite::memory:", Optional.empty());
    }

    private static LootBoxStore connect(String url, Optional<SqliteBackups> backups) throws LootBoxStoreException {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException ex) {
            throw new LootBoxStoreException("the server does not bundle the SQLite driver", ex);
        }
        Connection connection;
        try {
            connection = DriverManager.getConnection(url);
        } catch (SQLException ex) {
            throw new LootBoxStoreException("could not open " + url, ex);
        }
        return new SqlLootBoxStore(connection, "", backups);
    }
}
