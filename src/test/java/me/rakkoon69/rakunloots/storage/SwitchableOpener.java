package me.rakkoon69.rakunloots.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Opens connections through a real opener, and can go down like a database server: going unreachable closes every
 * connection it opened and fails every open until it is reachable again. A store must open a new connection to
 * recover, as after a MySQL restart.
 */
final class SwitchableOpener implements SqlLootBoxStore.ConnectionOpener {

    private final SqlLootBoxStore.ConnectionOpener real;
    private final List<Connection> opened = new ArrayList<>();
    private boolean reachable;
    private int attempts;

    SwitchableOpener(SqlLootBoxStore.ConnectionOpener real, boolean reachable) {
        this.real = Objects.requireNonNull(real, "real");
        this.reachable = reachable;
    }

    @Override
    public synchronized Connection open() throws SQLException {
        attempts++;
        if (!reachable) {
            throw new SQLException("storage unreachable");
        }
        Connection connection = real.open();
        opened.add(connection);
        return connection;
    }

    synchronized void setReachable(boolean reachable) {
        this.reachable = reachable;
        if (!reachable) {
            closeOpened();
        }
    }

    /**
     * Closes every connection opened so far and stays reachable, as when MySQL drops a connection left idle.
     */
    synchronized void closeIdleConnections() {
        closeOpened();
    }

    /**
     * How many times a store asked for a connection, reachable or not.
     */
    synchronized int attempts() {
        return attempts;
    }

    private void closeOpened() {
        for (Connection connection : opened) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Already gone is what going down means.
            }
        }
        opened.clear();
    }
}
