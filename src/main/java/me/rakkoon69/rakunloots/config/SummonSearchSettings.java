package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import me.rakkoon69.rakunloots.summon.SummonRing;

import java.util.Objects;

/**
 * How a world picks columns for a server summon.
 */
public final class SummonSearchSettings {

    public enum Mode {
        SPAWN,
        SMART
    }

    public enum WhenAllAbsent {
        SPAWN,
        NONE
    }

    public static final Mode DEFAULT_MODE = Mode.SPAWN;
    public static final UnloadedChunkPolicy DEFAULT_UNLOADED = UnloadedChunkPolicy.LOAD;
    public static final SummonRing DEFAULT_SPAWN_RING = new SummonRing(0, 800);
    public static final SummonRing DEFAULT_SMART_RING = new SummonRing(32, 400);
    public static final int DEFAULT_PICK_BIAS = 20;
    public static final int DEFAULT_MEDIAN_PULL = 70;
    public static final int DEFAULT_IDLE_SECONDS = 300;
    public static final WhenAllAbsent DEFAULT_WHEN_ALL_ABSENT = WhenAllAbsent.SPAWN;

    private final Mode mode;
    private final UnloadedChunkPolicy unloadedChunks;
    private final SummonRing spawnRing;
    private final SummonRing smartRing;
    private final int pickBias;
    private final int medianPull;
    private final int idleSeconds;
    private final WhenAllAbsent whenAllAbsent;

    public SummonSearchSettings(
            Mode mode,
            UnloadedChunkPolicy unloadedChunks,
            SummonRing spawnRing,
            SummonRing smartRing,
            int pickBias,
            int medianPull,
            int idleSeconds,
            WhenAllAbsent whenAllAbsent
    ) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.unloadedChunks = Objects.requireNonNull(unloadedChunks, "unloadedChunks");
        this.spawnRing = Objects.requireNonNull(spawnRing, "spawnRing");
        this.smartRing = Objects.requireNonNull(smartRing, "smartRing");
        this.pickBias = pickBias;
        this.medianPull = medianPull;
        this.idleSeconds = idleSeconds;
        this.whenAllAbsent = Objects.requireNonNull(whenAllAbsent, "whenAllAbsent");
        if (this.mode == Mode.SMART && this.unloadedChunks == UnloadedChunkPolicy.SKIP) {
            throw new IllegalArgumentException("smart summon reads unloaded chunks as load, never skip");
        }
    }

    public static SummonSearchSettings defaults() {
        return new SummonSearchSettings(
                DEFAULT_MODE,
                DEFAULT_UNLOADED,
                DEFAULT_SPAWN_RING,
                DEFAULT_SMART_RING,
                DEFAULT_PICK_BIAS,
                DEFAULT_MEDIAN_PULL,
                DEFAULT_IDLE_SECONDS,
                DEFAULT_WHEN_ALL_ABSENT
        );
    }

    public Mode mode() {
        return mode;
    }

    public UnloadedChunkPolicy unloadedChunks() {
        return unloadedChunks;
    }

    public SummonRing spawnRing() {
        return spawnRing;
    }

    public SummonRing smartRing() {
        return smartRing;
    }

    public int pickBias() {
        return pickBias;
    }

    public int medianPull() {
        return medianPull;
    }

    public int idleSeconds() {
        return idleSeconds;
    }

    public WhenAllAbsent whenAllAbsent() {
        return whenAllAbsent;
    }
}
