package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.StorageSettings;

import java.sql.DriverManager;
import java.util.Objects;
import java.util.Properties;

/**
 * Opens the MySQL loot box store, for hosts who already run MySQL. The server fetches the driver from the plugin's
 * {@code libraries}; it is not shaded. Tables take the configured prefix so each server's rows stay its own.
 * Backup does nothing: the host backs up its own database.
 */
public final class MySqlStores {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    /**
     * Bounds how long a call waits on a database that does not answer, so a flush fails and retries later instead
     * of hanging.
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int SOCKET_TIMEOUT_MILLIS = 30_000;

    private MySqlStores() {
    }

    /**
     * A store for the MySQL named in the settings. It does not connect yet, so it opens while MySQL is down: each
     * call connects when it has no connection, and a call that fails drops its connection so the next one, such as
     * the next flush, connects again. The schema is brought up to date on each new connection.
     *
     * @throws LootBoxStoreException when the MySQL driver is not on the class path
     */
    public static LootBoxStore open(StorageSettings settings) throws LootBoxStoreException {
        return new SqlLootBoxStore(opener(settings), settings.tablePrefix());
    }

    /**
     * Opens connections to the MySQL named in the settings. For tests that switch the database off and on.
     */
    static SqlLootBoxStore.ConnectionOpener opener(StorageSettings settings) throws LootBoxStoreException {
        Objects.requireNonNull(settings, "settings");
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException ex) {
            throw new LootBoxStoreException("the MySQL driver did not load; the server fetches it from the plugin's "
                    + "libraries at start", ex);
        }
        String url = "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database();
        Properties properties = new Properties();
        properties.setProperty("user", settings.user());
        properties.setProperty("password", settings.password());
        properties.setProperty("connectTimeout", Integer.toString(CONNECT_TIMEOUT_MILLIS));
        properties.setProperty("socketTimeout", Integer.toString(SOCKET_TIMEOUT_MILLIS));
        return () -> DriverManager.getConnection(url, properties);
    }
}
