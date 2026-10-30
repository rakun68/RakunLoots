package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Decides which players see which packet hologram, and sends them its packets through the packet adapter. It keeps
 * each hologram's world position and view range, and each player's world and position.
 *
 * <p>A player sees a hologram while they are in its world within its view range of its position. Coming within
 * range sends spawn; leaving range, changing world, or the hologram's removal sends destroy. Join, teleport, and
 * world change place the player at once, so every hologram in range spawns and every one out of range or in the old
 * world is destroyed. Quit forgets the player without packets, since the client is gone. A line update sends meta only
 * to current viewers, or destroy and spawn when the new lines need another number of entities.
 *
 * <p>Movement is checked once per second, from the first hologram on: one player position snapshot per world that
 * has a hologram, through the server adapter, which gathers it on each player's thread on Folia. The snapshot adds
 * players it lists and forgets players of that world it no longer lists. A snapshot never undoes an event that came
 * after it was asked for, so a teleport is not moved back by an older position. Remove all stops the check.
 *
 * <p>Every call is safe on any thread. Packets go out while the tracker's lock is held, so one player never gets a
 * hologram's destroy before its spawn.
 */
public final class ViewerTracker {

    /**
     * Movement is checked once per second.
     */
    static final long CHECK_PERIOD_TICKS = 20;

    private final PacketAdapter adapter;
    private final ServerAdapter server;

    private final Object lock = new Object();
    private final Map<UUID, Hologram> holograms = new LinkedHashMap<>();
    private final Map<UUID, Viewer> players = new HashMap<>();
    /**
     * Counts join, respawn, teleport, world change, and quit, so a snapshot asked for before one of them leaves that player
     * alone.
     */
    private long events;
    /**
     * When each player who quit while a snapshot was out did so, by event count, so that snapshot does not add them
     * back. A snapshot asked for after the quit no longer needs it.
     */
    private final Map<UUID, Long> quits = new HashMap<>();
    /**
     * The event count of each snapshot asked for and not yet applied, with how many of that count are out.
     */
    private final TreeMap<Long, Integer> pendingSnapshots = new TreeMap<>();
    private TaskHandle check;

    public ViewerTracker(PacketAdapter adapter, ServerAdapter server) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Adds a hologram, spawns it to every known player in range, and starts the movement check if it is not running.
     *
     * @param lines     top line first; never empty
     * @param viewRange blocks, at least 1
     * @throws IllegalStateException when the id is already tracked
     */
    public void add(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
        Objects.requireNonNull(hologramId, "hologramId");
        Objects.requireNonNull(position, "position");
        List<String> copy = usable(lines);
        if (viewRange < 1) {
            throw new IllegalArgumentException("view range must be at least 1, got " + viewRange);
        }
        synchronized (lock) {
            if (holograms.containsKey(hologramId)) {
                throw new IllegalStateException("already tracked: " + hologramId);
            }
            Hologram hologram = new Hologram(position, viewRange, copy, entityIds(copy.size()));
            holograms.put(hologramId, hologram);
            for (Map.Entry<UUID, Viewer> player : players.entrySet()) {
                if (hologram.inRange(player.getValue())) {
                    hologram.viewers.add(player.getKey());
                    adapter.send(player.getKey(), hologram.spawn(adapter));
                }
            }
            if (check == null) {
                check = server.global().repeat(this::checkMovement, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
            }
        }
    }

    /**
     * Shows new lines to the hologram's current viewers. An unknown id does nothing.
     *
     * @param lines top line first; never empty
     */
    public void update(UUID hologramId, List<String> lines) {
        Objects.requireNonNull(hologramId, "hologramId");
        List<String> copy = usable(lines);
        synchronized (lock) {
            Hologram hologram = holograms.get(hologramId);
            if (hologram == null) {
                return;
            }
            if (adapter.entityCount(copy.size()) != hologram.entityIds.size()) {
                Object destroy = hologram.destroy(adapter);
                hologram.change(copy, entityIds(copy.size()));
                for (UUID viewer : hologram.viewers) {
                    adapter.send(viewer, destroy);
                    adapter.send(viewer, hologram.spawn(adapter));
                }
                return;
            }
            hologram.change(copy, hologram.entityIds);
            if (!hologram.viewers.isEmpty()) {
                Object meta = adapter.meta(hologram.entityIds, copy);
                for (UUID viewer : hologram.viewers) {
                    adapter.send(viewer, meta);
                }
            }
        }
    }

    /**
     * Destroys the hologram for every viewer and forgets it. An unknown id does nothing.
     */
    public void remove(UUID hologramId) {
        Objects.requireNonNull(hologramId, "hologramId");
        synchronized (lock) {
            Hologram hologram = holograms.remove(hologramId);
            if (hologram != null) {
                destroyForViewers(hologram);
            }
        }
    }

    /**
     * Destroys every hologram for its viewers, forgets them, and stops the movement check. Called when the plugin
     * stops.
     */
    public void removeAll() {
        synchronized (lock) {
            for (Hologram hologram : holograms.values()) {
                destroyForViewers(hologram);
            }
            holograms.clear();
            if (check != null) {
                check.cancel();
                check = null;
            }
        }
    }

    /**
     * A player joined, standing there.
     */
    public void joined(UUID worldKey, PlayerPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        synchronized (lock) {
            quits.remove(position.playerId());
            placedByEvent(worldKey, position);
        }
    }

    /**
     * A player teleported within a world, to there.
     */
    public void teleported(UUID worldKey, PlayerPosition position) {
        placedByEvent(worldKey, position);
    }

    /**
     * A player is now in another world, standing there.
     */
    public void changedWorld(UUID worldKey, PlayerPosition position) {
        placedByEvent(worldKey, position);
    }

    /**
     * A player respawned, standing there, in this world or another. The client dropped every hologram entity it had
     * when it respawned, so the tracker forgets that they saw any without sending destroy, then spawns every hologram
     * in range as a join does.
     */
    public void respawned(UUID worldKey, PlayerPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        synchronized (lock) {
            for (Hologram hologram : holograms.values()) {
                hologram.viewers.remove(position.playerId());
            }
            place(worldKey, position, ++events);
        }
    }

    /**
     * A player left the server. Nothing is sent to them.
     */
    public void quit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (lock) {
            events++;
            if (!pendingSnapshots.isEmpty()) {
                quits.put(playerId, events);
            }
            players.remove(playerId);
            for (Hologram hologram : holograms.values()) {
                hologram.viewers.remove(playerId);
            }
        }
    }

    /**
     * The players who see a hologram now; empty for an unknown id.
     */
    Set<UUID> viewers(UUID hologramId) {
        synchronized (lock) {
            Hologram hologram = holograms.get(hologramId);
            return hologram == null ? Set.of() : Set.copyOf(hologram.viewers);
        }
    }

    /**
     * Whether the tracker knows where a player is.
     */
    boolean knows(UUID playerId) {
        synchronized (lock) {
            return players.containsKey(playerId);
        }
    }

    /**
     * Asks one player position snapshot for each world that has a hologram, and applies each when it arrives.
     */
    private void checkMovement() {
        Set<UUID> worlds = new LinkedHashSet<>();
        long asked;
        synchronized (lock) {
            for (Hologram hologram : holograms.values()) {
                worlds.add(hologram.position.worldKey());
            }
            asked = events;
            if (!worlds.isEmpty()) {
                pendingSnapshots.merge(asked, worlds.size(), Integer::sum);
            }
        }
        for (UUID world : worlds) {
            server.playerPositions(world).whenComplete((positions, failure) -> {
                if (failure == null && positions != null) {
                    applySnapshot(world, positions, asked);
                }
                snapshotDone(asked);
            });
        }
    }

    /**
     * A snapshot asked for at that event count is applied or failed. Quits older than every snapshot still out are no
     * longer needed.
     */
    private void snapshotDone(long asked) {
        synchronized (lock) {
            pendingSnapshots.merge(asked, -1, (out, done) -> out + done == 0 ? null : out + done);
            if (pendingSnapshots.isEmpty()) {
                quits.clear();
            } else {
                long oldest = pendingSnapshots.firstKey();
                quits.values().removeIf(quit -> quit <= oldest);
            }
        }
    }

    private void applySnapshot(UUID worldKey, List<PlayerPosition> positions, long asked) {
        synchronized (lock) {
            Set<UUID> listed = new HashSet<>();
            for (PlayerPosition position : positions) {
                listed.add(position.playerId());
                Long quit = quits.get(position.playerId());
                if (quit != null && quit > asked) {
                    continue;
                }
                Viewer known = players.get(position.playerId());
                if (known == null || known.placedAt <= asked) {
                    place(worldKey, position, asked);
                }
            }
            for (Map.Entry<UUID, Viewer> player : new ArrayList<>(players.entrySet())) {
                Viewer viewer = player.getValue();
                if (viewer.worldKey.equals(worldKey) && viewer.placedAt <= asked
                        && !listed.contains(player.getKey())) {
                    forget(player.getKey());
                }
            }
        }
    }

    private void placedByEvent(UUID worldKey, PlayerPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        synchronized (lock) {
            place(worldKey, position, ++events);
        }
    }

    /**
     * Moves a player and spawns or destroys each hologram whose range they entered or left.
     */
    private void place(UUID worldKey, PlayerPosition position, long placedAt) {
        UUID playerId = position.playerId();
        Viewer viewer = new Viewer(worldKey, position.x(), position.y(), position.z(), placedAt);
        players.put(playerId, viewer);
        for (Hologram hologram : holograms.values()) {
            boolean inRange = hologram.inRange(viewer);
            boolean sees = hologram.viewers.contains(playerId);
            if (inRange && !sees) {
                hologram.viewers.add(playerId);
                adapter.send(playerId, hologram.spawn(adapter));
            } else if (!inRange && sees) {
                hologram.viewers.remove(playerId);
                adapter.send(playerId, hologram.destroy(adapter));
            }
        }
    }

    /**
     * Forgets a player a snapshot of their world no longer lists, destroying what they saw.
     */
    private void forget(UUID playerId) {
        players.remove(playerId);
        for (Hologram hologram : holograms.values()) {
            if (hologram.viewers.remove(playerId)) {
                adapter.send(playerId, hologram.destroy(adapter));
            }
        }
    }

    private void destroyForViewers(Hologram hologram) {
        if (hologram.viewers.isEmpty()) {
            return;
        }
        Object destroy = hologram.destroy(adapter);
        for (UUID viewer : hologram.viewers) {
            adapter.send(viewer, destroy);
        }
        hologram.viewers.clear();
    }

    private List<Integer> entityIds(int lineCount) {
        int count = adapter.entityCount(lineCount);
        List<Integer> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ids.add(adapter.nextEntityId());
        }
        return List.copyOf(ids);
    }

    private static List<String> usable(List<String> lines) {
        List<String> copy = List.copyOf(lines);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("a hologram needs a line");
        }
        return copy;
    }

    /**
     * Where a player stood last, and the event count when that was set.
     */
    private record Viewer(UUID worldKey, double x, double y, double z, long placedAt) {
    }

    /**
     * One tracked hologram. Its spawn and destroy packets are built when first needed and kept until its lines or
     * entities change.
     */
    private static final class Hologram {

        private final HologramPosition position;
        private final double rangeSquared;
        private final Set<UUID> viewers = new LinkedHashSet<>();
        private List<String> lines;
        private List<Integer> entityIds;
        private Object spawn;
        private Object destroy;

        private Hologram(HologramPosition position, int viewRange, List<String> lines, List<Integer> entityIds) {
            this.position = position;
            this.rangeSquared = (double) viewRange * viewRange;
            this.lines = lines;
            this.entityIds = entityIds;
        }

        private boolean inRange(Viewer viewer) {
            if (!viewer.worldKey.equals(position.worldKey())) {
                return false;
            }
            double dx = viewer.x - position.x();
            double dy = viewer.y - position.y();
            double dz = viewer.z - position.z();
            return dx * dx + dy * dy + dz * dz <= rangeSquared;
        }

        private void change(List<String> newLines, List<Integer> newEntityIds) {
            if (!newEntityIds.equals(entityIds)) {
                destroy = null;
            }
            lines = newLines;
            entityIds = newEntityIds;
            spawn = null;
        }

        private Object spawn(PacketAdapter adapter) {
            if (spawn == null) {
                spawn = adapter.spawn(entityIds, position, lines);
            }
            return spawn;
        }

        private Object destroy(PacketAdapter adapter) {
            if (destroy == null) {
                destroy = adapter.destroy(entityIds);
            }
            return destroy;
        }
    }
}
