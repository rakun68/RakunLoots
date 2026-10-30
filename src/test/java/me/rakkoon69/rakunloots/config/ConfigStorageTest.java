package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStorageTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void missingStorageBlockUsesDefaultsWithoutWarning() {
        ConfigSnapshot snapshot = load("language: en\n");

        StorageSettings storage = snapshot.storage();
        assertEquals(StorageType.SQLITE, storage.type());
        assertEquals("localhost", storage.host());
        assertEquals(3306, storage.port());
        assertEquals("rakunloots", storage.database());
        assertEquals("root", storage.user());
        assertEquals("", storage.password());
        assertEquals("rakunloots_", storage.tablePrefix());
        assertEquals(5, storage.backupsKept());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    @Test
    void shippedConfigStorageBlockLoadsAsTheDefaultsWithoutWarning() throws Exception {
        String shipped;
        try (InputStream in = ConfigStorageTest.class.getResourceAsStream("/config.yml")) {
            shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ConfigSnapshot snapshot = load(shipped);

        StorageSettings storage = snapshot.storage();
        StorageSettings defaults = StorageSettings.defaults();
        assertEquals(defaults.type(), storage.type());
        assertEquals(defaults.host(), storage.host());
        assertEquals(defaults.port(), storage.port());
        assertEquals(defaults.database(), storage.database());
        assertEquals(defaults.user(), storage.user());
        assertEquals(defaults.password(), storage.password());
        assertEquals(defaults.tablePrefix(), storage.tablePrefix());
        assertEquals(defaults.backupsKept(), storage.backupsKept());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    @Test
    void missingGlobalsUsesStorageDefaults() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .language("en", "still_locked: locked\n")
                .build(), ACCEPTING, WarningKinds.all());

        assertEquals(StorageType.SQLITE, snapshot.storage().type());
        assertEquals(5, snapshot.storage().backupsKept());
    }

    @Test
    void writtenMysqlBlockIsRead() {
        ConfigSnapshot snapshot = load("""
                language: en
                storage:
                  type: mysql
                  host: db.example.net
                  port: 3307
                  database: survival
                  user: loots
                  password: hunter2
                  table_prefix: s1_
                  backups_kept: 12
                """);

        StorageSettings storage = snapshot.storage();
        assertEquals(StorageType.MYSQL, storage.type());
        assertEquals("db.example.net", storage.host());
        assertEquals(3307, storage.port());
        assertEquals("survival", storage.database());
        assertEquals("loots", storage.user());
        assertEquals("hunter2", storage.password());
        assertEquals("s1_", storage.tablePrefix());
        assertEquals(12, storage.backupsKept());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    @Test
    void typeIgnoresCaseAndSurroundingSpace() {
        assertEquals(StorageType.MYSQL, load("storage:\n  type: \" MySQL \"\n").storage().type());
        assertEquals(StorageType.SQLITE, load("storage:\n  type: SQLite\n").storage().type());
    }

    @Test
    void missingTypeUsesSqliteWithoutWarning() {
        ConfigSnapshot snapshot = load("storage:\n  host: db.example.net\n");

        assertEquals(StorageType.SQLITE, snapshot.storage().type());
        assertEquals("db.example.net", snapshot.storage().host());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    @Test
    void badTypeWarnsHighAndUsesSqlite() {
        for (String type : List.of("postgres", "\"\"", "3", "[mysql]")) {
            ConfigSnapshot snapshot = load("storage:\n  type: " + type + "\n");

            assertEquals(StorageType.SQLITE, snapshot.storage().type(), type);
            List<ConfigWarning> warnings = storageWarnings(snapshot);
            assertEquals(1, warnings.size(), type);
            assertEquals(ConfigWarning.Kind.UNKNOWN_STORAGE_TYPE, warnings.get(0).kind());
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warnings.get(0).severity());
            assertEquals(Optional.of("type"), warnings.get(0).key());
        }
    }

    @Test
    void badPortWarnsHighAndUsesDefaultWhileOtherValuesStay() {
        for (String port : List.of("0", "65536", "-1", "abc", "33.5")) {
            ConfigSnapshot snapshot = load("storage:\n  type: mysql\n  port: " + port + "\n  database: survival\n");

            assertEquals(3306, snapshot.storage().port(), port);
            assertEquals(StorageType.MYSQL, snapshot.storage().type());
            assertEquals("survival", snapshot.storage().database());
            assertSingleUnusableValue(snapshot, "port");
        }
    }

    @Test
    void badBackupsKeptWarnsHighAndUsesFive() {
        for (String kept : List.of("-1", "many", "2.5")) {
            ConfigSnapshot snapshot = load("storage:\n  backups_kept: " + kept + "\n");

            assertEquals(5, snapshot.storage().backupsKept(), kept);
            assertSingleUnusableValue(snapshot, "backups_kept");
        }
    }

    @Test
    void zeroBackupsKeptIsAllowed() {
        ConfigSnapshot snapshot = load("storage:\n  backups_kept: 0\n");

        assertEquals(0, snapshot.storage().backupsKept());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    @Test
    void tablePrefixOutsideLettersDigitsAndUnderscoreWarnsHighAndUsesDefault() {
        for (String prefix : List.of("\"s1-\"", "\"s1 \"", "\"a;drop\"", "[s1_]")) {
            ConfigSnapshot snapshot = load("storage:\n  table_prefix: " + prefix + "\n");

            assertEquals("rakunloots_", snapshot.storage().tablePrefix(), prefix);
            assertSingleUnusableValue(snapshot, "table_prefix");
        }
    }

    @Test
    void emptyTablePrefixIsAllowed() {
        ConfigSnapshot snapshot = load("storage:\n  table_prefix: \"\"\n");

        assertEquals("", snapshot.storage().tablePrefix());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    @Test
    void quotedNumericPasswordIsReadAsWritten() {
        ConfigSnapshot snapshot = load("storage:\n  user: \"1001\"\n  password: \"0123\"\n");

        assertEquals("1001", snapshot.storage().user());
        assertEquals("0123", snapshot.storage().password());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    @Test
    void unquotedNumericPasswordWarnsHighBecauseYamlMayChangeIt() {
        ConfigSnapshot snapshot = load("storage:\n  password: 0123\n");

        assertEquals("", snapshot.storage().password());
        assertSingleUnusableValue(snapshot, "password");
    }

    @Test
    void blankOrNonTextConnectionValuesWarnHighAndUseDefaults() {
        ConfigSnapshot snapshot = load("""
                storage:
                  host: "  "
                  database: [survival]
                  user: {name: loots}
                  password: [secret]
                """);

        StorageSettings storage = snapshot.storage();
        assertEquals("localhost", storage.host());
        assertEquals("rakunloots", storage.database());
        assertEquals("root", storage.user());
        assertEquals("", storage.password());
        assertEquals(
                List.of("host", "database", "user", "password"),
                storageWarnings(snapshot).stream().map(warning -> warning.key().orElseThrow()).toList()
        );
        for (ConfigWarning warning : storageWarnings(snapshot)) {
            assertEquals(ConfigWarning.Kind.UNUSABLE_STORAGE_VALUE, warning.kind());
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        }
    }

    @Test
    void storageThatIsNotABlockWarnsHighAndUsesDefaults() {
        ConfigSnapshot snapshot = load("storage: mysql\n");

        assertEquals(StorageType.SQLITE, snapshot.storage().type());
        assertEquals(5, snapshot.storage().backupsKept());
        assertSingleUnusableValue(snapshot, "storage");
    }

    @Test
    void storageWarningsFollowWarningLevel() {
        ConfigSnapshot snapshot = load("warning-level: severe\nstorage:\n  type: postgres\n  port: abc\n");

        assertEquals(StorageType.SQLITE, snapshot.storage().type());
        assertEquals(3306, snapshot.storage().port());
        assertTrue(storageWarnings(snapshot).isEmpty());
    }

    private static void assertSingleUnusableValue(ConfigSnapshot snapshot, String key) {
        List<ConfigWarning> warnings = storageWarnings(snapshot);
        assertEquals(1, warnings.size(), key);
        assertEquals(ConfigWarning.Kind.UNUSABLE_STORAGE_VALUE, warnings.get(0).kind());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warnings.get(0).severity());
        assertEquals(Optional.of(key), warnings.get(0).key());
    }

    private static List<ConfigWarning> storageWarnings(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(warning -> warning.kind() == ConfigWarning.Kind.UNKNOWN_STORAGE_TYPE
                        || warning.kind() == ConfigWarning.Kind.UNUSABLE_STORAGE_VALUE)
                .toList();
    }

    private static ConfigSnapshot load(String globals) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(globals)
                .language("en", "still_locked: locked\n")
                .build(), ACCEPTING, WarningKinds.all());
    }
}
