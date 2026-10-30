package me.rakkoon69.rakunloots.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Copies the SQLite store into a backups folder and keeps the newest few copies.
 * <p>
 * A copy is taken with {@code VACUUM INTO} on the store's own connection, so it is one consistent snapshot of the
 * live file, never a half-written page. It lands under a temporary name and is renamed once complete, so a crash
 * mid-copy never leaves a partial file that counts as a backup. The next backup removes such a leftover.
 * <p>
 * Names carry a UTC timestamp to the millisecond, fixed width, so name order is age order. A new copy is always
 * named after the newest one already there, even when two copies land in the same millisecond or the clock steps
 * back. Pruning touches only files named this way whose timestamp is a real instant.
 */
final class SqliteBackups {

    /**
     * The folder inside the plugin data folder that holds the copies.
     */
    static final String FOLDER_NAME = "backups";

    private static final String NAME_PREFIX = "loot-boxes-";
    private static final String NAME_SUFFIX = ".db";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'")
            .withZone(ZoneOffset.UTC)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final String STAMP_REGEX = "(\\d{8}T\\d{6}\\.\\d{3}Z)";
    private static final Pattern NAME = Pattern.compile(
            Pattern.quote(NAME_PREFIX) + STAMP_REGEX + Pattern.quote(NAME_SUFFIX));
    private static final Pattern TEMP_NAME = Pattern.compile(
            Pattern.quote(NAME_PREFIX) + STAMP_REGEX + Pattern.quote(NAME_SUFFIX + TEMP_SUFFIX));

    private final Path folder;
    private final Clock clock;

    SqliteBackups(Path folder, Clock clock) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Writes one copy of the database behind the connection, then removes all but the newest {@code backupsKept}.
     * Zero writes none. The connection must not be inside a transaction.
     * <p>
     * When the copy is written but an old copy cannot be removed, the rest are still pruned and the failure is
     * reported; the new copy stays.
     */
    void backup(Connection connection, int backupsKept) throws LootBoxStoreException {
        Objects.requireNonNull(connection, "connection");
        if (backupsKept < 0) {
            throw new IllegalArgumentException("backupsKept must not be negative");
        }
        List<Path> copies;
        try {
            Files.createDirectories(folder);
            removeLeftoverTemps();
            copies = copies();
            if (backupsKept > 0) {
                copies.add(write(connection, nextStamp(copies)));
            }
        } catch (IOException ex) {
            throw new LootBoxStoreException("could not back up the loot box store into " + folder, ex);
        }
        prune(copies.subList(0, Math.max(0, copies.size() - backupsKept)));
    }

    private Path write(Connection connection, Instant stamp) throws IOException, LootBoxStoreException {
        Path target = folder.resolve(NAME_PREFIX + STAMP.format(stamp) + NAME_SUFFIX);
        Path temp = folder.resolve(target.getFileName() + TEMP_SUFFIX);
        try {
            try (PreparedStatement vacuum = connection.prepareStatement("VACUUM INTO ?")) {
                vacuum.setString(1, temp.toAbsolutePath().toString());
                vacuum.executeUpdate();
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (SQLException ex) {
            deleteQuietly(temp);
            throw new LootBoxStoreException("could not copy the loot box store to " + target, ex);
        } catch (IOException ex) {
            deleteQuietly(temp);
            throw ex;
        }
        return target;
    }

    /**
     * Now, or one millisecond after the newest copy when now would not sort after it.
     */
    private Instant nextStamp(List<Path> copies) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (copies.isEmpty()) {
            return now;
        }
        Instant newest = stampOf(copies.get(copies.size() - 1)).orElseThrow();
        return now.isAfter(newest) ? now : newest.plusMillis(1);
    }

    private void prune(List<Path> oldest) throws LootBoxStoreException {
        IOException failure = null;
        for (Path copy : oldest) {
            try {
                Files.deleteIfExists(copy);
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw new LootBoxStoreException("could not remove an old loot box backup from " + folder, failure);
        }
    }

    /**
     * The copies already in the folder, oldest first.
     */
    private List<Path> copies() throws IOException {
        List<Path> copies = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(file -> stampOf(file).isPresent())
                    .filter(Files::isRegularFile)
                    .forEach(copies::add);
        }
        Collections.sort(copies);
        return copies;
    }

    /**
     * Copies a crash left half written. They never count as backups.
     */
    private void removeLeftoverTemps() throws IOException {
        List<Path> temps = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(file -> TEMP_NAME.matcher(file.getFileName().toString()).matches())
                    .filter(Files::isRegularFile)
                    .forEach(temps::add);
        }
        for (Path temp : temps) {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * The instant a file's name carries, or empty when the name is not a backup name or its timestamp is not a
     * real instant.
     */
    private static Optional<Instant> stampOf(Path file) {
        Matcher matcher = NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(STAMP.parse(matcher.group(1), Instant::from));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The copy already failed; that failure is the one to report.
        }
    }
}
