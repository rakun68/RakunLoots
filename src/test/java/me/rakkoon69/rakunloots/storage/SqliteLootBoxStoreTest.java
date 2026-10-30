package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.roll.RolledContents;
import me.rakkoon69.rakunloots.roll.RolledStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteLootBoxStoreTest extends LootBoxStoreContract {

    @TempDir
    Path dataFolder;

    private volatile boolean reachable = true;

    /**
     * In-memory SQLite behind a connection that fails every call but close while unreachable.
     */
    @Override
    LootBoxStore openStore() throws Exception {
        Connection real = DriverManager.getConnection("jdbc:sqlite::memory:");
        Connection switchable = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (!reachable && !method.getName().equals("close")) {
                        throw new SQLException("storage unreachable");
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
        return new SqlLootBoxStore(switchable, "");
    }

    @Override
    void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    @Test
    void inMemoryStoreOpensEmpty() throws Exception {
        try (LootBoxStore store = SqliteStores.openInMemory()) {
            assertTrue(store.loadAll(unendingLoots(), world -> true).boxes().isEmpty());
        }
    }

    @Test
    void fileStoreKeepsBoxesAcrossReopening() throws Exception {
        UUID boxId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        LootBoxRecord box = new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.of(owner), UUID.randomUUID(), 1, 2, 3,
                "minecraft:chest", OptionalInt.empty(), 30, new RolledContents(List.of(
                        new RolledStack(OptionalInt.of(2), new ItemStack(Material.GOLD_INGOT, 12)))));
        try (LootBoxStore first = SqliteStores.open(dataFolder)) {
            first.save(box);
        }

        assertTrue(Files.exists(dataFolder.resolve(SqliteStores.FILE_NAME)));
        try (LootBoxStore second = SqliteStores.open(dataFolder)) {
            List<LootBoxRecord> loaded = records(second.loadAll(unendingLoots(), world -> true));
            assertEquals(1, loaded.size());
            assertEquals(boxId, loaded.get(0).boxId());
            assertEquals(Optional.of(owner), loaded.get(0).owner());
            assertEquals(OptionalInt.empty(), loaded.get(0).remainingLifeSeconds());
            assertEquals(30, loaded.get(0).remainingLockSeconds());
            assertEquals("minecraft:chest", loaded.get(0).placedBlockId());
            assertEquals(new ItemStack(Material.GOLD_INGOT, 12), loaded.get(0).contents().stacks().get(0).item());
        }
    }

    @Test
    void unclaimedSummonItemsAndAClaimSurviveARestart() throws Exception {
        UUID player = UUID.randomUUID();
        try (LootBoxStore first = SqliteStores.open(dataFolder)) {
            first.addUnclaimedSummonItems(player, "airdrops", 5);
        }
        try (LootBoxStore second = SqliteStores.open(dataFolder)) {
            assertEquals(List.of(new UnclaimedSummonItem("airdrops", 5, false)),
                    second.unclaimedSummonItems(player, unendingLoots()));
            // The claim commits before it returns, so the items given after it are never given twice.
            assertEquals(2, second.claimUnclaimedSummonItems(player, "airdrops", 2));
        }
        try (LootBoxStore third = SqliteStores.open(dataFolder)) {
            assertEquals(List.of(new UnclaimedSummonItem("airdrops", 3, false)),
                    third.unclaimedSummonItems(player, unendingLoots()));
        }
    }

    @Test
    void rowWhoseIdsAreNotUuidsIsLeftOutWithHighWarningAndStays() throws Exception {
        UUID damaged = UUID.randomUUID();
        UUID intact = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        try (LootBoxStore first = SqliteStores.open(dataFolder)) {
            first.save(new LootBoxRecord(damaged, "airdrops", SummonKind.UNKNOWN, Optional.empty(), world, 0, 70, 0, "minecraft:barrel",
                    OptionalInt.of(300), 0, new RolledContents(List.of())));
            first.save(new LootBoxRecord(intact, "airdrops", SummonKind.UNKNOWN, Optional.empty(), world, 1, 70, 0, "minecraft:barrel",
                    OptionalInt.of(300), 0, new RolledContents(List.of())));
        }
        // Stands in for a row an admin or another tool damaged by hand.
        setWorldId(damaged, "not-a-uuid");

        try (LootBoxStore second = SqliteStores.open(dataFolder)) {
            LoadResult result = second.loadAll(unendingLoots(), id -> true);

            assertEquals(List.of(intact), records(result).stream().map(LootBoxRecord::boxId).toList());
            assertEquals(1, result.warnings().size());
            assertEquals(LootBoxWarning.Kind.UNREADABLE_BOX, result.warnings().get(0).kind());
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, result.warnings().get(0).severity());
            assertEquals(damaged.toString(), result.warnings().get(0).boxId());
        }

        setWorldId(damaged, world.toString());
        try (LootBoxStore third = SqliteStores.open(dataFolder)) {
            assertEquals(2, third.loadAll(unendingLoots(), id -> true).boxes().size());
        }
    }

    /**
     * Acceptance: a database written before summon kinds were kept opens, keeps its boxes, and reads their kind as
     * unknown; a box saved after that keeps its kind. The old file is built from the schema as it shipped, version 2.
     */
    @Test
    void anExistingDatabaseMigratesAndItsBoxesReadAsUnknown() throws Exception {
        UUID old = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        createVersionTwoDatabase(old, world);

        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            Map<UUID, LootBoxRecord> loaded = byId(store.loadAll(unendingLoots(), id -> true));
            assertEquals(SummonKind.UNKNOWN, loaded.get(old).source());
            assertEquals("minecraft:barrel", loaded.get(old).placedBlockId());

            UUID fresh = UUID.randomUUID();
            store.save(new LootBoxRecord(fresh, "airdrops", SummonKind.WORLD_TIMER, Optional.empty(), world, 1, 70, 0,
                    "minecraft:barrel", OptionalInt.empty(), 0, new RolledContents(List.of())));
        }
        try (LootBoxStore reopened = SqliteStores.open(dataFolder)) {
            List<SummonKind> kinds = records(reopened.loadAll(unendingLoots(), id -> true)).stream()
                    .map(LootBoxRecord::source).sorted().toList();
            assertEquals(List.of(SummonKind.WORLD_TIMER, SummonKind.UNKNOWN), kinds);
        }
    }

    /**
     * A migration that failed after its column was added, before its version bump, runs again without failing.
     */
    @Test
    void aMigrationThatAlreadyAddedItsColumnRunsAgain() throws Exception {
        UUID old = UUID.randomUUID();
        createVersionTwoDatabase(old, UUID.randomUUID());
        String url = "jdbc:sqlite:" + dataFolder.resolve(SqliteStores.FILE_NAME).toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE boxes ADD COLUMN summon_source VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'");
        }

        try (LootBoxStore store = SqliteStores.open(dataFolder)) {
            assertEquals(1, store.loadAll(unendingLoots(), id -> true).boxes().size());
        }
    }

    private void createVersionTwoDatabase(UUID boxId, UUID world) throws Exception {
        Files.createDirectories(dataFolder);
        String url = "jdbc:sqlite:" + dataFolder.resolve(SqliteStores.FILE_NAME).toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE boxes (box_id CHAR(36) NOT NULL PRIMARY KEY, "
                    + "loot_id VARCHAR(255) NOT NULL, owner CHAR(36) NULL, world_id CHAR(36) NOT NULL, x INT NOT NULL, "
                    + "y INT NOT NULL, z INT NOT NULL, placed_block VARCHAR(255) NOT NULL, remaining_life INT NULL, "
                    + "remaining_lock INT NOT NULL)");
            statement.executeUpdate("CREATE TABLE stacks (box_id CHAR(36) NOT NULL, position INT NOT NULL, "
                    + "slot INT NULL, data MEDIUMBLOB NOT NULL, PRIMARY KEY (box_id, position))");
            statement.executeUpdate("CREATE TABLE unclaimed_summon_items (player_id CHAR(36) NOT NULL, "
                    + "loot_id VARCHAR(255) NOT NULL, amount INT NOT NULL, PRIMARY KEY (player_id, loot_id))");
            statement.executeUpdate("CREATE TABLE schema_version (version INT NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (2)");
            statement.executeUpdate("INSERT INTO boxes VALUES ('" + boxId + "', 'airdrops', NULL, '" + world
                    + "', 0, 70, 0, 'minecraft:barrel', NULL, 0)");
        }
    }

    private static Map<UUID, LootBoxRecord> byId(LoadResult result) {
        Map<UUID, LootBoxRecord> byId = new java.util.HashMap<>();
        records(result).forEach(record -> byId.put(record.boxId(), record));
        return byId;
    }

    @Test
    void fileStoreOpensInMissingDataFolder() throws Exception {
        Path missing = dataFolder.resolve("plugins").resolve("RakunLoots");

        try (LootBoxStore store = SqliteStores.open(missing)) {
            assertTrue(store.loadAll(unendingLoots(), world -> true).boxes().isEmpty());
        }
    }

    private void setWorldId(UUID boxId, String worldId) throws Exception {
        String url = "jdbc:sqlite:" + dataFolder.resolve(SqliteStores.FILE_NAME).toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement update = connection.prepareStatement("UPDATE boxes SET world_id = ? WHERE box_id = ?")) {
            update.setString(1, worldId);
            update.setString(2, boxId.toString());
            assertEquals(1, update.executeUpdate());
        }
    }
}
