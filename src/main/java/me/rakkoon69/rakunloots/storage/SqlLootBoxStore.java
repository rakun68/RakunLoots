package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.config.StorageSettings;
import me.rakkoon69.rakunloots.roll.RolledContents;
import me.rakkoon69.rakunloots.roll.RolledStack;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The loot box store over JDBC. Every adapter uses this schema; only the table prefix differs. A SQLite store keeps
 * one connection for its whole life. A MySQL store opens its own, and opens a new one after a failure, so storage
 * that was down at start or dropped mid-run comes back on the next call.
 * <p>
 * Schema: a version table with one row, a boxes table keyed by box id, a stacks table keyed by box id and
 * position, and a table of unclaimed summon items keyed by player and loot id. A stack is its own row, so load can skip one stack that cannot be read and keep the rest of the box,
 * leaving its bytes in place until that box is saved again.
 * Migrations run forward only. A store written by a newer schema does not open. Each step tolerates a rerun, since
 * MySQL commits a table change at once and a step that fails partway leaves its tables without the version bump.
 */
class SqlLootBoxStore implements LootBoxStore {

    private static final String PREFIX_TOKEN = "{p}";
    private static final Pattern ADD_COLUMN = Pattern.compile("^ALTER TABLE (\\S+) ADD COLUMN (\\w+) ");
    /**
     * How long a reopening store waits for a connection it holds to answer before opening a new one.
     */
    private static final int VALID_TIMEOUT_SECONDS = 5;

    /**
     * Migration {@code i} takes the schema from version {@code i} to {@code i + 1}. Append only; never edit one
     * that has shipped. No server ran this store before phase 8, so phase 7 added the placed block to the first
     * migration instead of appending one (ADR 0009).
     */
    private static final List<List<String>> MIGRATIONS = List.of(
            List.of(
                    "CREATE TABLE IF NOT EXISTS {p}boxes ("
                            + "box_id CHAR(36) NOT NULL PRIMARY KEY, "
                            + "loot_id VARCHAR(255) NOT NULL, "
                            + "owner CHAR(36) NULL, "
                            + "world_id CHAR(36) NOT NULL, "
                            + "x INT NOT NULL, "
                            + "y INT NOT NULL, "
                            + "z INT NOT NULL, "
                            + "placed_block VARCHAR(255) NOT NULL, "
                            + "remaining_life INT NULL, "
                            + "remaining_lock INT NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS {p}stacks ("
                            + "box_id CHAR(36) NOT NULL, "
                            + "position INT NOT NULL, "
                            + "slot INT NULL, "
                            + "data MEDIUMBLOB NOT NULL, "
                            + "PRIMARY KEY (box_id, position))"
            ),
            List.of(
                    "CREATE TABLE IF NOT EXISTS {p}unclaimed_summon_items ("
                            + "player_id CHAR(36) NOT NULL, "
                            + "loot_id VARCHAR(255) NOT NULL, "
                            + "amount INT NOT NULL, "
                            + "PRIMARY KEY (player_id, loot_id))"
            ),
            // A box stored before summon kinds were kept reads as UNKNOWN through the column default.
            List.of(
                    "ALTER TABLE {p}boxes ADD COLUMN summon_source VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'"
            )
    );

    /**
     * The open connection, or null while a reopening store has none. Guarded by the store lock.
     */
    private Connection connection;
    /**
     * Present for a store that opens its own connections: it opens one on first use and a new one after a failure.
     * Empty for a store given one connection for its whole life.
     */
    private final Optional<ConnectionOpener> opener;
    private final String prefix;
    private final Optional<SqliteBackups> backups;
    /**
     * Set by close, so a reopening store does not open again afterwards. Guarded by the store lock.
     */
    private boolean closed;
    /**
     * Changed boxes not yet written, by box id. Not guarded by the store lock, so marking never waits on a write.
     */
    private final ConcurrentMap<UUID, LootBoxRecord> pending = new ConcurrentHashMap<>();
    /**
     * Boxes queued for delete and not yet deleted. Not guarded by the store lock, so queueing never waits on a
     * write.
     */
    private final Set<UUID> pendingDeletes = ConcurrentHashMap.newKeySet();

    /**
     * Takes ownership of the connection and brings its schema up to date. Backup does nothing, as for a store
     * whose database the host backs up.
     */
    SqlLootBoxStore(Connection connection, String tablePrefix) throws LootBoxStoreException {
        this(connection, tablePrefix, Optional.empty());
    }

    /**
     * Takes ownership of the connection and brings its schema up to date. Backup copies the database through the
     * given backups when present, and does nothing otherwise. The store keeps this one connection for its whole
     * life and never opens another.
     */
    SqlLootBoxStore(Connection connection, String tablePrefix, Optional<SqliteBackups> backups)
            throws LootBoxStoreException {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.opener = Optional.empty();
        this.prefix = Objects.requireNonNull(tablePrefix, "tablePrefix");
        this.backups = Objects.requireNonNull(backups, "backups");
        if (!StorageSettings.isUsableTablePrefix(tablePrefix)) {
            closeQuietly();
            throw new IllegalArgumentException("table prefix may hold only letters, digits, and underscores");
        }
        try {
            migrate();
        } catch (SQLException ex) {
            closeQuietly();
            throw new LootBoxStoreException("could not bring the loot box schema up to date", ex);
        } catch (LootBoxStoreException ex) {
            closeQuietly();
            throw ex;
        }
    }

    /**
     * A store that opens its own connections and does not connect yet, so it opens while the database is down.
     * Each call opens a connection when it has none and brings the schema up to date on it. A call that fails
     * against the database drops its connection, so the next call, such as the next flush, opens a new one. Backup
     * does nothing, as for a store whose database the host backs up.
     */
    SqlLootBoxStore(ConnectionOpener opener, String tablePrefix) {
        this.connection = null;
        this.opener = Optional.of(Objects.requireNonNull(opener, "opener"));
        this.prefix = Objects.requireNonNull(tablePrefix, "tablePrefix");
        this.backups = Optional.empty();
        if (!StorageSettings.isUsableTablePrefix(tablePrefix)) {
            throw new IllegalArgumentException("table prefix may hold only letters, digits, and underscores");
        }
    }

    @Override
    public synchronized void save(LootBoxRecord box) throws LootBoxStoreException {
        Objects.requireNonNull(box, "box");
        List<byte[]> encoded = new ArrayList<>();
        for (RolledStack stack : box.contents().stacks()) {
            try {
                encoded.add(StackCodec.encode(stack.item()));
            } catch (IOException ex) {
                throw new LootBoxStoreException("could not serialize a stack of box " + box.boxId(), ex);
            }
        }
        String boxId = box.boxId().toString();
        inTransaction(() -> {
            deleteRows(boxId);
            try (PreparedStatement insert = connection.prepareStatement(sql(
                    "INSERT INTO {p}boxes (box_id, loot_id, owner, world_id, x, y, z, placed_block, remaining_life, "
                            + "remaining_lock, summon_source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))) {
                insert.setString(1, boxId);
                insert.setString(2, box.lootId());
                insert.setString(3, box.owner().map(UUID::toString).orElse(null));
                insert.setString(4, box.worldId().toString());
                insert.setInt(5, box.x());
                insert.setInt(6, box.y());
                insert.setInt(7, box.z());
                insert.setString(8, box.placedBlockId());
                setNullableInt(insert, 9, box.remainingLifeSeconds());
                insert.setInt(10, box.remainingLockSeconds());
                insert.setString(11, box.source().name());
                insert.executeUpdate();
            }
            List<RolledStack> stacks = box.contents().stacks();
            if (stacks.isEmpty()) {
                return;
            }
            try (PreparedStatement insert = connection.prepareStatement(sql(
                    "INSERT INTO {p}stacks (box_id, position, slot, data) VALUES (?, ?, ?, ?)"))) {
                for (int position = 0; position < stacks.size(); position++) {
                    insert.setString(1, boxId);
                    insert.setInt(2, position);
                    setNullableInt(insert, 3, stacks.get(position).slot());
                    insert.setBytes(4, encoded.get(position));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }, "could not save box " + boxId);
    }

    @Override
    public synchronized void delete(UUID boxId) throws LootBoxStoreException {
        Objects.requireNonNull(boxId, "boxId");
        // Under the store lock, so a flush cannot write this change back after the row is gone.
        pending.remove(boxId);
        inTransaction(() -> deleteRows(boxId.toString()), "could not delete box " + boxId);
    }

    @Override
    public void markChanged(LootBoxRecord box) {
        Objects.requireNonNull(box, "box");
        pending.put(box.boxId(), box);
        // Checked after the put, so a delete queued at the same time either sees this change and drops it, or
        // is seen here.
        if (pendingDeletes.contains(box.boxId())) {
            pending.remove(box.boxId(), box);
        }
    }

    @Override
    public void queueDelete(UUID boxId) {
        Objects.requireNonNull(boxId, "boxId");
        pendingDeletes.add(boxId);
        pending.remove(boxId);
    }

    @Override
    public CompletableFuture<FlushResult> flush(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(this::writePending, executor);
    }

    /**
     * Writes each pending box in its newest state, then each queued delete. Each box is checked and written under
     * the store lock, so a delete either runs first and drops the change or runs after the write and removes the
     * row. A box marked again during its write keeps the newer state pending. A delete queued during a box's write
     * is written after it, in this flush or the next, so the row does not outlive it.
     * <p>
     * Once a reopening store loses its connection, the rest of this flush fails with the same cause instead of
     * connecting again for each box, so a database that is down costs one connect attempt per flush and does not
     * hold the store lock box after box.
     */
    private FlushResult writePending() {
        Set<UUID> written = new HashSet<>();
        Set<UUID> failed = new HashSet<>();
        List<LootBoxWarning> warnings = new ArrayList<>();
        Throwable unreachable = null;
        for (UUID boxId : List.copyOf(pending.keySet())) {
            LootBoxRecord box;
            synchronized (this) {
                box = pending.get(boxId);
                if (box == null || pendingDeletes.contains(boxId)) {
                    continue;
                }
                if (unreachable != null) {
                    failed.add(boxId);
                    warnings.add(LootBoxWarning.flushFailed(boxId.toString(), unreachable));
                    continue;
                }
                try {
                    save(box);
                } catch (LootBoxStoreException | RuntimeException ex) {
                    failed.add(boxId);
                    warnings.add(LootBoxWarning.flushFailed(boxId.toString(), ex));
                    if (opener.isPresent() && connection == null) {
                        unreachable = ex;
                    }
                    continue;
                }
            }
            pending.remove(boxId, box);
            written.add(boxId);
        }
        Set<UUID> deleted = new HashSet<>();
        for (UUID boxId : List.copyOf(pendingDeletes)) {
            synchronized (this) {
                if (unreachable != null) {
                    failed.add(boxId);
                    warnings.add(LootBoxWarning.flushFailed(boxId.toString(), unreachable));
                    continue;
                }
                try {
                    inTransaction(() -> deleteRows(boxId.toString()), "could not delete box " + boxId);
                } catch (LootBoxStoreException | RuntimeException ex) {
                    failed.add(boxId);
                    warnings.add(LootBoxWarning.flushFailed(boxId.toString(), ex));
                    if (opener.isPresent() && connection == null) {
                        unreachable = ex;
                    }
                    continue;
                }
            }
            pendingDeletes.remove(boxId);
            deleted.add(boxId);
        }
        return new FlushResult(written, deleted, failed, warnings);
    }

    @Override
    public synchronized void addUnclaimedSummonItems(UUID playerId, String lootId, int amount)
            throws LootBoxStoreException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lootId, "lootId");
        requirePositive(amount);
        inTransaction(() -> {
            try (PreparedStatement raise = connection.prepareStatement(sql(
                    "UPDATE {p}unclaimed_summon_items SET amount = amount + ? WHERE player_id = ? AND loot_id = ?"))) {
                raise.setInt(1, amount);
                raise.setString(2, playerId.toString());
                raise.setString(3, lootId);
                if (raise.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(sql(
                    "INSERT INTO {p}unclaimed_summon_items (player_id, loot_id, amount) VALUES (?, ?, ?)"))) {
                insert.setString(1, playerId.toString());
                insert.setString(2, lootId);
                insert.setInt(3, amount);
                insert.executeUpdate();
            }
        }, "could not add unclaimed summon items for " + playerId);
    }

    @Override
    public synchronized List<UnclaimedSummonItem> unclaimedSummonItems(UUID playerId, ConfigSnapshot config)
            throws LootBoxStoreException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(config, "config");
        connect();
        try (PreparedStatement query = connection.prepareStatement(sql(
                "SELECT loot_id, amount FROM {p}unclaimed_summon_items WHERE player_id = ? ORDER BY loot_id"))) {
            query.setString(1, playerId.toString());
            List<UnclaimedSummonItem> owed = new ArrayList<>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String lootId = rows.getString("loot_id");
                    owed.add(new UnclaimedSummonItem(lootId, rows.getInt("amount"), config.loot(lootId).isEmpty()));
                }
            }
            return owed;
        } catch (SQLException ex) {
            dropConnection();
            throw new LootBoxStoreException("could not read unclaimed summon items of " + playerId, ex);
        }
    }

    @Override
    public synchronized int claimUnclaimedSummonItems(UUID playerId, String lootId, int amount)
            throws LootBoxStoreException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lootId, "lootId");
        requirePositive(amount);
        int[] lowered = new int[1];
        inTransaction(() -> {
            int stored = 0;
            try (PreparedStatement query = connection.prepareStatement(sql(
                    "SELECT amount FROM {p}unclaimed_summon_items WHERE player_id = ? AND loot_id = ?"))) {
                query.setString(1, playerId.toString());
                query.setString(2, lootId);
                try (ResultSet rows = query.executeQuery()) {
                    if (rows.next()) {
                        stored = rows.getInt("amount");
                    }
                }
            }
            if (stored == 0) {
                return;
            }
            int take = Math.min(stored, amount);
            String statement = take == stored
                    ? "DELETE FROM {p}unclaimed_summon_items WHERE player_id = ? AND loot_id = ?"
                    : "UPDATE {p}unclaimed_summon_items SET amount = amount - " + take
                    + " WHERE player_id = ? AND loot_id = ?";
            try (PreparedStatement change = connection.prepareStatement(sql(statement))) {
                change.setString(1, playerId.toString());
                change.setString(2, lootId);
                change.executeUpdate();
            }
            lowered[0] = take;
        }, "could not claim unclaimed summon items of " + playerId);
        return lowered[0];
    }

    private static void requirePositive(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
    }

    @Override
    public synchronized LoadResult loadAll(ConfigSnapshot config, Predicate<UUID> worldLoaded)
            throws LootBoxStoreException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(worldLoaded, "worldLoaded");
        List<LoadedBox> loaded = new ArrayList<>();
        List<LootBoxWarning> warnings = new ArrayList<>();
        for (LootBoxRecord stored : loadRecords(warnings)) {
            Optional<Loot> loot = config.loot(stored.lootId());
            boolean lootMissing = loot.isEmpty();
            if (lootMissing) {
                warnings.add(LootBoxWarning.lootMissing(stored.boxId().toString(), stored.lootId()));
            }
            LootBoxRecord box = loot.map(current -> capLife(stored, current.lifeSeconds())).orElse(stored);
            boolean worldNotLoaded = !worldLoaded.test(box.worldId());
            if (worldNotLoaded) {
                warnings.add(LootBoxWarning.worldNotLoaded(box.boxId().toString(), box.lootId()));
            }
            loaded.add(new LoadedBox(box, lootMissing, worldNotLoaded));
        }
        return new LoadResult(loaded, warnings);
    }

    /**
     * A shorter current life caps the remaining life, unending included. A longer or unending one leaves it.
     */
    private static LootBoxRecord capLife(LootBoxRecord box, OptionalInt currentLife) {
        if (currentLife.isEmpty()) {
            return box;
        }
        OptionalInt remaining = box.remainingLifeSeconds();
        if (remaining.isPresent() && remaining.getAsInt() <= currentLife.getAsInt()) {
            return box;
        }
        return box.withRemainingLife(currentLife);
    }

    /**
     * Every box row that can be read. A row that cannot, such as one whose ids are not UUIDs, is left out with a
     * warning and stays in storage.
     */
    private List<LootBoxRecord> loadRecords(List<LootBoxWarning> warnings) throws LootBoxStoreException {
        connect();
        try {
            Map<String, List<RolledStack>> stacksByBox = loadStacks(warnings);
            List<LootBoxRecord> boxes = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(sql(
                         "SELECT box_id, loot_id, owner, world_id, x, y, z, placed_block, remaining_life, "
                                 + "remaining_lock, summon_source FROM {p}boxes ORDER BY box_id"))) {
                while (rows.next()) {
                    String boxId = rows.getString("box_id");
                    try {
                        boxes.add(readBox(rows, boxId, stacksByBox.getOrDefault(boxId, List.of())));
                    } catch (IllegalArgumentException ex) {
                        warnings.add(LootBoxWarning.unreadableBox(boxId, ex));
                    }
                }
            }
            return boxes;
        } catch (SQLException ex) {
            dropConnection();
            throw new LootBoxStoreException("could not load boxes", ex);
        }
    }

    private static LootBoxRecord readBox(ResultSet rows, String boxId, List<RolledStack> stacks) throws SQLException {
        String owner = rows.getString("owner");
        return new LootBoxRecord(
                UUID.fromString(boxId),
                rows.getString("loot_id"),
                SummonKind.fromStored(rows.getString("summon_source")),
                owner == null ? Optional.empty() : Optional.of(UUID.fromString(owner)),
                UUID.fromString(rows.getString("world_id")),
                rows.getInt("x"),
                rows.getInt("y"),
                rows.getInt("z"),
                rows.getString("placed_block"),
                getNullableInt(rows, "remaining_life"),
                rows.getInt("remaining_lock"),
                new RolledContents(stacks)
        );
    }

    @Override
    public synchronized void backup(int backupsKept) throws LootBoxStoreException {
        if (backups.isPresent()) {
            connect();
            backups.get().backup(connection, backupsKept);
        } else {
            LootBoxStore.super.backup(backupsKept);
        }
    }

    @Override
    public synchronized void close() throws LootBoxStoreException {
        closed = true;
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new LootBoxStoreException("could not close the loot box store", ex);
        } finally {
            connection = null;
        }
    }

    /**
     * Makes sure the store has a connection. A reopening store with none opens one and brings its schema up to
     * date; a store given one connection already has it.
     */
    private void connect() throws LootBoxStoreException {
        if (closed) {
            throw new LootBoxStoreException("the loot box store is closed");
        }
        if (opener.isEmpty()) {
            return;
        }
        if (connection != null && isValid(connection)) {
            return;
        }
        dropConnection();
        try {
            connection = opener.get().open();
        } catch (SQLException | RuntimeException ex) {
            throw new LootBoxStoreException("could not reach the loot box database", ex);
        }
        try {
            migrate();
        } catch (SQLException ex) {
            dropConnection();
            throw new LootBoxStoreException("could not bring the loot box schema up to date", ex);
        } catch (LootBoxStoreException ex) {
            dropConnection();
            throw ex;
        }
    }

    /**
     * Whether the connection still answers. MySQL closes a connection left idle, and the first call after that
     * should open a new one instead of failing.
     */
    private static boolean isValid(Connection connection) {
        try {
            return connection.isValid(VALID_TIMEOUT_SECONDS);
        } catch (SQLException ex) {
            return false;
        }
    }

    /**
     * After a failure against the database, a reopening store closes its connection so the next call opens a new
     * one. A connection lost mid-run cannot tell a dropped link from a refused statement, so any failure drops it;
     * opening again costs one round trip. A store given one connection keeps it.
     */
    private void dropConnection() {
        if (opener.isEmpty() || connection == null) {
            return;
        }
        closeQuietly();
        connection = null;
    }

    /**
     * Stacks by box id, in position order. A stack that cannot be read is left out with a high warning. Its row is
     * not touched, so its bytes stay until that box is saved again.
     */
    private Map<String, List<RolledStack>> loadStacks(List<LootBoxWarning> warnings) throws SQLException {
        Map<String, List<RolledStack>> stacksByBox = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql(
                     "SELECT box_id, slot, data FROM {p}stacks ORDER BY box_id, position"))) {
            while (rows.next()) {
                String boxId = rows.getString("box_id");
                RolledStack stack;
                try {
                    stack = new RolledStack(getNullableInt(rows, "slot"), StackCodec.decode(rows.getBytes("data")));
                } catch (IOException | RuntimeException ex) {
                    // Stored bytes come from another server version or plugin set; any failure to read one
                    // stack must not cost the rest of the box.
                    warnings.add(LootBoxWarning.unreadableStack(boxId, ex));
                    continue;
                }
                stacksByBox.computeIfAbsent(boxId, id -> new ArrayList<>()).add(stack);
            }
        }
        return stacksByBox;
    }

    private void deleteRows(String boxId) throws SQLException {
        try (PreparedStatement stacks = connection.prepareStatement(sql("DELETE FROM {p}stacks WHERE box_id = ?"));
             PreparedStatement boxes = connection.prepareStatement(sql("DELETE FROM {p}boxes WHERE box_id = ?"))) {
            stacks.setString(1, boxId);
            stacks.executeUpdate();
            boxes.setString(1, boxId);
            boxes.executeUpdate();
        }
    }

    private void migrate() throws SQLException, LootBoxStoreException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql("CREATE TABLE IF NOT EXISTS {p}schema_version (version INT NOT NULL)"));
        }
        int version = readSchemaVersion();
        if (version > MIGRATIONS.size()) {
            throw new LootBoxStoreException("the loot box schema is version " + version
                    + ", newer than this plugin's version " + MIGRATIONS.size() + "; update the plugin");
        }
        for (int from = version; from < MIGRATIONS.size(); from++) {
            int to = from + 1;
            List<String> steps = MIGRATIONS.get(from);
            inTransaction(() -> {
                try (Statement statement = connection.createStatement()) {
                    for (String step : steps) {
                        runStep(statement, step);
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(sql(
                        "UPDATE {p}schema_version SET version = ?"))) {
                    update.setInt(1, to);
                    update.executeUpdate();
                }
            }, "could not migrate the loot box schema to version " + to);
        }
    }

    /**
     * Runs one migration step. A step that adds a column that is already there, left by an earlier run that failed
     * before its version bump, is skipped, as MySQL committed that column at once.
     */
    private void runStep(Statement statement, String step) throws SQLException {
        try {
            statement.executeUpdate(sql(step));
        } catch (SQLException failure) {
            Matcher added = ADD_COLUMN.matcher(step);
            if (!added.find() || !columnExists(statement, added.group(1), added.group(2))) {
                throw failure;
            }
        }
    }

    private boolean columnExists(Statement statement, String table, String column) {
        try (ResultSet ignored = statement.executeQuery(sql("SELECT " + column + " FROM " + table + " LIMIT 0"))) {
            return true;
        } catch (SQLException missing) {
            return false;
        }
    }

    private int readSchemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql("SELECT version FROM {p}schema_version"))) {
            if (rows.next()) {
                return rows.getInt("version");
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql("INSERT INTO {p}schema_version (version) VALUES (0)"));
        }
        return 0;
    }

    /**
     * Runs the work as one transaction. Any failure rolls it back before autocommit returns, so nothing partial
     * is committed. A failing rollback is attached to the original failure instead of hiding it.
     */
    private void inTransaction(SqlWork work, String failure) throws LootBoxStoreException {
        connect();
        Connection current = connection;
        boolean autoCommit;
        try {
            autoCommit = current.getAutoCommit();
            current.setAutoCommit(false);
        } catch (SQLException ex) {
            dropConnection();
            throw new LootBoxStoreException(failure, ex);
        }
        try {
            work.run();
            current.commit();
        } catch (SQLException | RuntimeException ex) {
            try {
                current.rollback();
            } catch (SQLException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            dropConnection();
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new LootBoxStoreException(failure, ex);
        } finally {
            try {
                current.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {
                // The next transaction sets autocommit again; the outcome above is the one to report.
            }
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, OptionalInt value) throws SQLException {
        if (value.isPresent()) {
            statement.setInt(index, value.getAsInt());
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private static OptionalInt getNullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private String sql(String template) {
        return template.replace(PREFIX_TOKEN, prefix);
    }

    private void closeQuietly() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // The open already failed; that failure is the one to report.
        }
    }

    /**
     * Opens a new connection to the database a reopening store keeps its boxes in.
     */
    @FunctionalInterface
    interface ConnectionOpener {
        Connection open() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }
}
