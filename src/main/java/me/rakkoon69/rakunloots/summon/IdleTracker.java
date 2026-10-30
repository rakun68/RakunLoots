package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.SummonSearchSettings;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskHandle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player idle state from periodic position samples.
 */
public final class IdleTracker {

    private static final long SAMPLE_PERIOD_TICKS = 600L;

    private final ServerAdapter adapter;
    private final SearchClock clock;
    private volatile ConfigSnapshot config;
    private final Map<UUID, TrackedWorld> worlds = new ConcurrentHashMap<>();
    private TaskHandle sampleTask;

    public IdleTracker(ServerAdapter adapter, SearchClock clock, ConfigSnapshot config) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void start() {
        if (sampleTask != null) {
            return;
        }
        sampleAll();
        sampleTask = adapter.global().repeat(this::sampleAll, SAMPLE_PERIOD_TICKS, SAMPLE_PERIOD_TICKS);
    }

    public void stop() {
        if (sampleTask != null) {
            sampleTask.cancel();
            sampleTask = null;
        }
        worlds.clear();
    }

    public void reload(ConfigSnapshot config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void trackWorld(UUID worldKey, SummonSearchSettings settings) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(settings, "settings");
        worlds.compute(worldKey, (key, existing) -> {
            if (existing == null) {
                return new TrackedWorld(settings);
            }
            existing.replaceSettings(settings);
            return existing;
        });
    }

    public List<PlayerPosition> smartCandidates(UUID worldKey, List<PlayerPosition> players) {
        TrackedWorld world = worlds.get(worldKey);
        if (world == null || players.isEmpty()) {
            return players;
        }
        List<PlayerPosition> active = players.stream()
                .filter(player -> !world.isIdle(player.playerId()))
                .toList();
        if (active.isEmpty()) {
            return players;
        }
        return active;
    }

    public boolean isSampling() {
        return sampleTask != null;
    }

    private void sampleAll() {
        long nowMillis = clock.millis();
        adapter.loadedWorlds().whenComplete((loaded, failure) -> {
            if (loaded == null) {
                return;
            }
            ConfigSnapshot snapshot = config;
            for (LoadedWorld loadedWorld : loaded) {
                snapshot.world(loadedWorld.name()).ifPresent(worldSettings -> {
                    SummonSearchSettings settings = worldSettings.summonSearch();
                    if (settings.mode() != SummonSearchSettings.Mode.SMART) {
                        return;
                    }
                    TrackedWorld tracked = worlds.compute(loadedWorld.key(), (key, existing) -> {
                        if (existing == null) {
                            return new TrackedWorld(settings);
                        }
                        existing.replaceSettings(settings);
                        return existing;
                    });
                    adapter.playerPositions(loadedWorld.key()).whenComplete((positions, error) -> {
                        if (positions != null) {
                            tracked.applySample(positions, nowMillis);
                        }
                    });
                });
            }
        });
    }

    private static final class TrackedWorld {

        private SummonSearchSettings settings;
        private final Map<UUID, PlayerIdleState> players = new HashMap<>();

        private TrackedWorld(SummonSearchSettings settings) {
            this.settings = settings;
        }

        private void replaceSettings(SummonSearchSettings settings) {
            synchronized (this) {
                this.settings = settings;
            }
        }

        private void applySample(List<PlayerPosition> positions, long nowMillis) {
            synchronized (this) {
                Set<UUID> seen = new HashSet<>();
                for (PlayerPosition position : positions) {
                    seen.add(position.playerId());
                    players.compute(position.playerId(), (id, state) -> {
                        if (state == null) {
                            return PlayerIdleState.at(position, nowMillis);
                        }
                        return state.update(position, nowMillis, settings.idleSeconds());
                    });
                }
                players.keySet().retainAll(seen);
            }
        }

        private boolean isIdle(UUID playerId) {
            synchronized (this) {
                PlayerIdleState state = players.get(playerId);
                return state != null && state.idle;
            }
        }
    }

    private static final class PlayerIdleState {

        private final double anchorX;
        private final double anchorY;
        private final double anchorZ;
        private final long anchorMillis;
        private final boolean idle;

        private PlayerIdleState(double anchorX, double anchorY, double anchorZ, long anchorMillis, boolean idle) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.anchorMillis = anchorMillis;
            this.idle = idle;
        }

        private static PlayerIdleState at(PlayerPosition position, long nowMillis) {
            return new PlayerIdleState(position.x(), position.y(), position.z(), nowMillis, false);
        }

        private PlayerIdleState update(PlayerPosition position, long nowMillis, int idleSeconds) {
            if (withinOneBlock(position)) {
                long idleMillis = idleSeconds * 1000L;
                boolean nowIdle = nowMillis - anchorMillis >= idleMillis;
                return new PlayerIdleState(anchorX, anchorY, anchorZ, anchorMillis, nowIdle);
            }
            return at(position, nowMillis);
        }

        private boolean withinOneBlock(PlayerPosition position) {
            return Math.max(
                    Math.max(Math.abs(position.x() - anchorX), Math.abs(position.y() - anchorY)),
                    Math.abs(position.z() - anchorZ)
            ) <= 1.0;
        }
    }
}
