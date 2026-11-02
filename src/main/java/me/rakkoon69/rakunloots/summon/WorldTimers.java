package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.WorldSettings;
import me.rakkoon69.rakunloots.lootbox.LifecycleWarning;
import me.rakkoon69.rakunloots.lootbox.DebugLines;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * One world timer for each world in {@code worlds.yml}. Every {@code time_between_loot_spawn} seconds, never under 5,
 * a timer summons a loot from its world's list through the {@link SummonService}, on the server adapter's global
 * scheduler. A timer starts full: its first summon comes one whole period after {@link #start} or {@link #reload}. It
 * is not stored.
 *
 * <p>A timer summons only when the world and the worlds in {@code also_count_players_from} hold at least
 * {@code minimum_player_to_spawn_loot} players, and while the world's live loot boxes plus its pending summons are
 * under {@code loot_spawn_limit}. That limit is the timer's alone: the summon item and an admin summon go through the
 * summon service without it. A timer summon whose spot is held is refused by the summon service, like any other.
 * A box a timer summon places is announced through {@link WorldBroadcasts}.
 *
 * <p>Every call is safe on any thread.
 */
public final class WorldTimers {

    private static final long TICKS_PER_SECOND = 20;

    private final ServerAdapter adapter;
    private volatile ConfigSnapshot config;
    private final LootBoxLifecycle lifecycle;
    private final SummonService service;
    private final WorldBroadcasts broadcasts;
    private final Warnings warnings;
    private final Random random;

    private final Object lock = new Object();
    private final Map<String, TaskHandle> tasks = new LinkedHashMap<>();

    public WorldTimers(
            ServerAdapter adapter,
            ConfigSnapshot config,
            LootBoxLifecycle lifecycle,
            SummonService service,
            WorldBroadcasts broadcasts,
            Warnings warnings,
            Random random
    ) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.service = Objects.requireNonNull(service, "service");
        this.broadcasts = Objects.requireNonNull(broadcasts, "broadcasts");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Starts one full timer for each world in the config. Timers already running are stopped first.
     */
    public void start() {
        schedule(config);
    }

    /**
     * Takes a reloaded config and starts every timer full again, so a reload never summons at once.
     */
    public void reload(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        schedule(snapshot);
    }

    /**
     * Stops every timer.
     */
    public void stop() {
        synchronized (lock) {
            tasks.values().forEach(TaskHandle::cancel);
            tasks.clear();
        }
    }

    private void schedule(ConfigSnapshot snapshot) {
        synchronized (lock) {
            tasks.values().forEach(TaskHandle::cancel);
            tasks.clear();
            config = snapshot;
            for (WorldSettings world : snapshot.worlds().values()) {
                tasks.put(world.worldName(), start(world));
            }
        }
    }

    private TaskHandle start(WorldSettings world) {
        long period = world.timerSeconds() * TICKS_PER_SECOND;
        String name = world.worldName();
        return adapter.global().repeat(() -> tick(name), period, period);
    }

    /**
     * Starts one world's timer full again, so its next summon comes one whole period from now. The other worlds'
     * timers are not touched.
     *
     * @return whether the world has a timer; false when {@code worlds.yml} lacks it
     */
    public boolean reset(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        synchronized (lock) {
            Optional<WorldSettings> found = config.world(worldName);
            if (found.isEmpty() || !tasks.containsKey(worldName)) {
                return false;
            }
            tasks.get(worldName).cancel();
            tasks.put(worldName, start(found.get()));
            return true;
        }
    }

    private void tick(String worldName) {
        try {
            summonNow(worldName, false).whenComplete((outcome, failure) -> {
                if (failure != null) {
                    warnings.warn(LifecycleWarning.timerFailed("timer summon in " + worldName, unwrap(failure)));
                }
            });
        } catch (RuntimeException failure) {
            warnings.warn(LifecycleWarning.timerFailed("timer summon in " + worldName, failure));
        }
    }

    /**
     * Runs one timer summon for a world now, with the timer's checks. Does not touch the timer's countdown.
     *
     * @param force whether to skip the minimum player count and the spawn limit
     * @return completes when the summon ended, which is after its summon delay, or at once when a check refused
     */
    public CompletableFuture<TimerOutcome> summonNow(String worldName, boolean force) {
        Objects.requireNonNull(worldName, "worldName");
        return run(worldName, force).whenComplete((outcome, failure) -> debug(worldName, outcome));
    }

    /**
     * Writes the debug line for how one timer run went, when debug mode is on: the loot it summoned and where, why
     * its summon was abandoned, or why it refused.
     */
    private void debug(String worldName, TimerOutcome outcome) {
        if (outcome == null || !warnings.debugEnabled()) {
            return;
        }
        String head = "timer world=" + worldName + " ";
        if (outcome.refusal().isPresent()) {
            warnings.debug(head + "refused reason=" + outcome.refusal().get());
        } else if (outcome.report().isPresent() && outcome.report().get().box().isPresent()) {
            LootBox box = outcome.report().get().box().get();
            warnings.debug(head + "summoned loot=" + box.lootId() + " " + DebugLines.at(box.position()));
        } else if (outcome.report().isPresent() && outcome.report().get().abandoned().isPresent()) {
            warnings.debug(head + "abandoned reason=" + outcome.report().get().abandoned().get());
        }
    }

    private CompletableFuture<TimerOutcome> run(String worldName, boolean force) {
        ConfigSnapshot snapshot = config;
        Optional<WorldSettings> found = snapshot.world(worldName);
        if (found.isEmpty()) {
            return CompletableFuture.completedFuture(TimerOutcome.refused(TimerRefusal.WORLD_UNKNOWN));
        }
        WorldSettings settings = found.get();
        return adapter.loadedWorlds().thenCompose(worlds -> {
            Optional<LoadedWorld> own = find(worlds, worldName);
            if (own.isEmpty()) {
                return CompletableFuture.completedFuture(TimerOutcome.refused(TimerRefusal.WORLD_NOT_LOADED));
            }
            UUID worldKey = own.get().key();
            if (force || settings.minimumPlayerToSpawnLoot() <= 0) {
                return afterPlayerCount(snapshot, settings, worldKey, force);
            }
            return playerCount(worlds, settings).thenCompose(count -> count < settings.minimumPlayerToSpawnLoot()
                    ? CompletableFuture.completedFuture(TimerOutcome.refused(TimerRefusal.BELOW_MINIMUM_PLAYERS))
                    : afterPlayerCount(snapshot, settings, worldKey, force));
        });
    }

    private CompletableFuture<TimerOutcome> afterPlayerCount(ConfigSnapshot snapshot, WorldSettings settings,
                                                             UUID worldKey, boolean force) {
        if (!force && lifecycle.liveBoxCount(worldKey) + service.pendingCount(worldKey) >= settings.lootSpawnLimit()) {
            return CompletableFuture.completedFuture(TimerOutcome.refused(TimerRefusal.AT_SPAWN_LIMIT));
        }
        Optional<String> lootId = pickLoot(snapshot, settings);
        if (lootId.isEmpty()) {
            return CompletableFuture.completedFuture(TimerOutcome.refused(TimerRefusal.NO_LOOT));
        }
        return service.summon(lootId.get(), SummonTarget.world(worldKey), Optional.empty(), SummonSource.worldTimer())
                .thenApply(report -> {
                    report.box().ifPresent(box -> announce(settings, box));
                    return TimerOutcome.summoned(report);
                });
    }

    private void announce(WorldSettings settings, LootBox box) {
        try {
            broadcasts.spawned(settings, box);
        } catch (RuntimeException failure) {
            warnings.warn(LifecycleWarning.timerFailed("spawned broadcast in " + settings.worldName(), failure));
        }
    }

    /**
     * Players in the world and in the worlds it also counts, each world once. A world that is not loaded counts none.
     */
    private CompletableFuture<Integer> playerCount(List<LoadedWorld> worlds, WorldSettings settings) {
        Set<String> names = new LinkedHashSet<>();
        names.add(settings.worldName());
        names.addAll(settings.alsoCountPlayersFrom());
        List<CompletableFuture<Integer>> counts = new ArrayList<>();
        for (String name : names) {
            find(worlds, name).ifPresent(world ->
                    counts.add(adapter.playerPositions(world.key()).thenApply(List::size)));
        }
        return CompletableFuture.allOf(counts.toArray(CompletableFuture<?>[]::new))
                .thenApply(done -> counts.stream().mapToInt(CompletableFuture::join).sum());
    }

    private static Optional<LoadedWorld> find(List<LoadedWorld> worlds, String name) {
        return worlds.stream().filter(world -> world.name().equals(name)).findFirst();
    }

    /**
     * A loot of the world's list chosen by chance. A loot the config lacks, or with a chance of 0 or less, is never
     * chosen.
     */
    private Optional<String> pickLoot(ConfigSnapshot snapshot, WorldSettings settings) {
        long total = 0;
        for (Map.Entry<String, Integer> entry : settings.loots().entrySet()) {
            if (entry.getValue() > 0 && snapshot.loot(entry.getKey()).isPresent()) {
                total += entry.getValue();
            }
        }
        if (total == 0) {
            return Optional.empty();
        }
        long roll = (long) (random.nextDouble() * total);
        for (Map.Entry<String, Integer> entry : settings.loots().entrySet()) {
            if (entry.getValue() > 0 && snapshot.loot(entry.getKey()).isPresent()) {
                if (roll < entry.getValue()) {
                    return Optional.of(entry.getKey());
                }
                roll -= entry.getValue();
            }
        }
        return Optional.empty();
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }
}
