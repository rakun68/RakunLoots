package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.StorageSettings;
import me.rakkoon69.rakunloots.config.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The store contract against a real MySQL. Runs only when {@code RAKUNLOOTS_MYSQL_HOST} names one, and is skipped
 * otherwise. {@code RAKUNLOOTS_MYSQL_PORT} (default 3306), {@code RAKUNLOOTS_MYSQL_DATABASE} (default
 * {@code rakunloots_test}, which must exist), {@code RAKUNLOOTS_MYSQL_USER} (default {@code root}), and
 * {@code RAKUNLOOTS_MYSQL_PASSWORD} (default empty) say how to reach it.
 * <p>
 * Skipped too when that MySQL does not answer. Each test uses its own table prefix and drops every table with that
 * prefix afterwards, so runs never see each other's rows.
 */
class MySqlLootBoxStoreTest extends LootBoxStoreContract {

    private static final String HOST = "RAKUNLOOTS_MYSQL_HOST";

    @TempDir
    Path dataFolder;

    private final List<String> prefixes = new ArrayList<>();
    private SwitchableOpener opener;

    @BeforeAll
    static void requireMySql() throws Exception {
        assumeTrue(System.getenv(HOST) != null && !System.getenv(HOST).isBlank(),
                HOST + " is not set, so no MySQL is reachable");
        try (Connection ignored = MySqlStores.opener(settings("rlt_")).open()) {
            // Reached it.
        } catch (SQLException ex) {
            assumeTrue(false, "no MySQL is reachable at " + System.getenv(HOST) + ": " + ex.getMessage());
        }
    }

    @Override
    LootBoxStore openStore() throws Exception {
        String prefix = newPrefix();
        opener = new SwitchableOpener(MySqlStores.opener(settings(prefix)), true);
        return new SqlLootBoxStore(opener, prefix);
    }

    @Override
    void setReachable(boolean reachable) {
        opener.setReachable(reachable);
    }

    @AfterEach
    void dropTables() throws Exception {
        if (prefixes.isEmpty()) {
            return;
        }
        try (Connection connection = MySqlStores.opener(settings(prefixes.get(0))).open()) {
            List<String> tables = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("SELECT table_name FROM "
                    + "information_schema.tables WHERE table_schema = DATABASE() AND LEFT(table_name, ?) = ?")) {
                for (String prefix : prefixes) {
                    query.setInt(1, prefix.length());
                    query.setString(2, prefix);
                    try (ResultSet rows = query.executeQuery()) {
                        while (rows.next()) {
                            tables.add(rows.getString(1));
                        }
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                for (String table : tables) {
                    statement.executeUpdate("DROP TABLE IF EXISTS `" + table + "`");
                }
            }
        }
    }

    @Test
    void storageTypeMysqlOpensAStoreWhoseTablesTakeTheConfiguredPrefix() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        StorageSettings firstServer = settings(newPrefix());
        StorageSettings secondServer = settings(newPrefix());
        assertEquals(StorageType.MYSQL, firstServer.type());

        try (LootBoxStore one = LootBoxStores.open(firstServer, dataFolder);
             LootBoxStore two = LootBoxStores.open(secondServer, dataFolder)) {
            one.save(emptyBox(first));
            two.save(emptyBox(second));

            assertEquals(List.of(first), boxIds(one));
            assertEquals(List.of(second), boxIds(two));
        }
        try (LootBoxStore reopened = LootBoxStores.open(firstServer, dataFolder)) {
            assertEquals(List.of(first), boxIds(reopened));
        }
        assertTrue(Files.notExists(dataFolder.resolve(SqliteStores.FILE_NAME)));
    }

    private String newPrefix() {
        String prefix = "rlt" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_";
        prefixes.add(prefix);
        return prefix;
    }

    private static StorageSettings settings(String tablePrefix) {
        StorageSettings settings = storageSettings("""
                type: mysql
                host: %s
                port: %s
                database: %s
                user: %s
                password: %s
                table_prefix: %s
                """.formatted(
                quoted(System.getenv(HOST)),
                env("RAKUNLOOTS_MYSQL_PORT", "3306"),
                quoted(env("RAKUNLOOTS_MYSQL_DATABASE", "rakunloots_test")),
                quoted(env("RAKUNLOOTS_MYSQL_USER", "root")),
                quoted(env("RAKUNLOOTS_MYSQL_PASSWORD", "")),
                quoted(tablePrefix)));
        assertEquals(tablePrefix, settings.tablePrefix());
        return settings;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private static List<UUID> boxIds(LootBoxStore store) throws Exception {
        return records(store.loadAll(unendingLoots(), world -> true)).stream().map(LootBoxRecord::boxId).toList();
    }
}
