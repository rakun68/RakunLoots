package me.rakkoon69.rakunloots.wiki;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.Loot;
import me.rakkoon69.rakunloots.config.Roll;
import me.rakkoon69.rakunloots.roll.LootChance;
import me.rakkoon69.rakunloots.server.TaskScheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Computes every loaded loot's exact content chances after each config load, on the async scheduler, and keeps the
 * result bound to the snapshot it came from. The expensive math never runs while the cache lock is held, and a job
 * whose load has been replaced is discarded at publication as well as at every read.
 */
public final class LootChanceCache {

    private final TaskScheduler async;
    private final Function<Roll, Map<String, LootChance.EntryChance>> calculator;
    private final CopyOnWriteArrayList<Consumer<ConfigSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private long generation;
    private ConfigSnapshot snapshot;
    private Map<String, Map<String, LootChance.EntryChance>> chances;

    public LootChanceCache(TaskScheduler async) {
        this(async, LootChance::calculate);
    }

    /**
     * The calculator seam is for tests that need to hold the expensive math while a reload lands.
     */
    LootChanceCache(TaskScheduler async, Function<Roll, Map<String, LootChance.EntryChance>> calculator) {
        this.async = Objects.requireNonNull(async, "async");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    /**
     * Called after the new result is published for this snapshot, on the worker thread that computed it.
     */
    public void addListener(Consumer<ConfigSnapshot> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Starts computing every loot of a newly loaded snapshot. An older job that is still running keeps its snapshot and
     * is discarded when it tries to publish.
     */
    public void load(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        long job;
        synchronized (lock) {
            this.snapshot = snapshot;
            this.chances = null;
            job = ++generation;
        }
        async.run(() -> compute(snapshot, job));
    }

    /**
     * The chances of one loot for the given snapshot, or empty while the snapshot is not the current one or its job
     * has not published. Reading never starts calculation.
     */
    public Optional<Map<String, LootChance.EntryChance>> chances(ConfigSnapshot snapshot, String lootId) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(lootId, "lootId");
        synchronized (lock) {
            if (this.snapshot != snapshot || chances == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(chances.get(lootId));
        }
    }

    private void compute(ConfigSnapshot source, long job) {
        if (!current(source, job)) {
            return;
        }
        Map<String, Map<String, LootChance.EntryChance>> computed = new LinkedHashMap<>();
        for (Map.Entry<String, Loot> loot : source.loots().entrySet()) {
            if (!current(source, job)) {
                return;
            }
            computed.put(loot.getKey(), calculator.apply(loot.getValue().roll()));
        }
        synchronized (lock) {
            if (generation != job || this.snapshot != source) {
                return;
            }
            chances = computed;
        }
        for (Consumer<ConfigSnapshot> listener : listeners) {
            try {
                listener.accept(source);
            } catch (RuntimeException ignored) {
                // A failed refresh must not turn a published calculation into a failed one.
            }
        }
    }

    private boolean current(ConfigSnapshot source, long job) {
        synchronized (lock) {
            return generation == job && snapshot == source;
        }
    }
}
