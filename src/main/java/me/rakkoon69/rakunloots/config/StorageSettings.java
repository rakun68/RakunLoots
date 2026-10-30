package me.rakkoon69.rakunloots.config;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The {@code storage} block of {@code config.yml}: which store keeps loot boxes, how to reach MySQL, and how many
 * SQLite backups to keep. The MySQL values are read for every type and used only by the MySQL store.
 */
public final class StorageSettings {

    public static final StorageType DEFAULT_TYPE = StorageType.SQLITE;
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 3306;
    public static final String DEFAULT_DATABASE = "rakunloots";
    public static final String DEFAULT_USER = "root";
    public static final String DEFAULT_PASSWORD = "";
    public static final String DEFAULT_TABLE_PREFIX = "rakunloots_";
    public static final int DEFAULT_BACKUPS_KEPT = 5;

    private static final Pattern TABLE_PREFIX = Pattern.compile("[A-Za-z0-9_]*");

    private final StorageType type;
    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final String tablePrefix;
    private final int backupsKept;

    StorageSettings(
            StorageType type,
            String host,
            int port,
            String database,
            String user,
            String password,
            String tablePrefix,
            int backupsKept
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.database = Objects.requireNonNull(database, "database");
        this.user = Objects.requireNonNull(user, "user");
        this.password = Objects.requireNonNull(password, "password");
        this.tablePrefix = Objects.requireNonNull(tablePrefix, "tablePrefix");
        this.backupsKept = backupsKept;
    }

    /**
     * Letters, digits, and underscores only, possibly empty. The prefix goes into SQL as written.
     */
    public static boolean isUsableTablePrefix(String prefix) {
        return TABLE_PREFIX.matcher(Objects.requireNonNull(prefix, "prefix")).matches();
    }

    public static StorageSettings defaults() {
        return new StorageSettings(DEFAULT_TYPE, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_DATABASE, DEFAULT_USER,
                DEFAULT_PASSWORD, DEFAULT_TABLE_PREFIX, DEFAULT_BACKUPS_KEPT);
    }

    public StorageType type() {
        return type;
    }

    public String host() {
        return host;
    }

    /**
     * Between 1 and 65535.
     */
    public int port() {
        return port;
    }

    public String database() {
        return database;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    /**
     * Put before each MySQL table name so each server's rows stay its own. Only letters, digits, and underscores;
     * may be empty.
     */
    public String tablePrefix() {
        return tablePrefix;
    }

    /**
     * How many SQLite backups stay. Never negative.
     */
    public int backupsKept() {
        return backupsKept;
    }
}
