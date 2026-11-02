package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.LaserType;
import me.rakkoon69.rakunloots.server.BlockPosition;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A laser library that records each beam it started and stopped, and can be made to fail to start one, as GuardianBeam
 * does when it cannot reach the server, or to call a world peaceful.
 */
public final class FakeLaserLibrary implements LaserLibrary {

    public record Start(UUID worldKey, BlockPosition position, LaserType type, int viewDistance,
                        int startOffset) {
    }

    private final List<Start> starts = new CopyOnWriteArrayList<>();
    private final List<Start> stops = new CopyOnWriteArrayList<>();
    private final Set<UUID> peaceful = ConcurrentHashMap.newKeySet();
    private Exception failure;

    public List<Start> starts() {
        return starts;
    }

    public List<Start> stops() {
        return stops;
    }

    public void clear() {
        starts.clear();
        stops.clear();
        peaceful.clear();
    }

    /**
     * Every later start throws this.
     */
    public void failStarts(Exception failure) {
        this.failure = failure;
    }

    /**
     * The world is peaceful from now on.
     */
    public void setPeaceful(UUID worldKey) {
        peaceful.add(worldKey);
    }

    @Override
    public boolean peaceful(UUID worldKey) {
        return peaceful.contains(worldKey);
    }

    @Override
    public Beam start(UUID worldKey, BlockPosition position, LaserType type, int viewDistance, int startOffset)
            throws Exception {
        if (failure != null) {
            throw failure;
        }
        Start start = new Start(worldKey, position, type, viewDistance, startOffset);
        starts.add(start);
        return () -> stops.add(start);
    }
}
