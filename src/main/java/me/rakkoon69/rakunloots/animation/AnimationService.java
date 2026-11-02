package me.rakkoon69.rakunloots.animation;

import me.rakkoon69.rakunloots.config.AnimationDefinition;
import me.rakkoon69.rakunloots.config.AnimationMoment;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.lootbox.BoxObserver;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.TaskScheduler;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Plays each loot's animations at the five moments of a loot box. It follows the lifecycle as its
 * {@link BoxObserver}, beside the hologram service, so the lifecycle knows nothing about animations.
 *
 * <ul>
 *   <li>Summon: starts when a summon holds its spot, at the spot, and stops when the summon is abandoned or when the
 *   box is placed there.</li>
 *   <li>Alive: starts when a box is shown and stops when it goes out of view or ends. A box shown again starts it
 *   again. An alive animation that has drawn its last frame starts again on the next tick, so a short animation
 *   repeats each second while the box is shown.</li>
 *   <li>Unlocked: plays once, when a shown box's lock reaches 0 on a tick. A box shown with no lock left never
 *   plays it.</li>
 *   <li>Open: plays each time a player opens the box.</li>
 *   <li>Remove: plays at the box's block when it ends, whatever ended it, and goes on after the box is gone.</li>
 * </ul>
 *
 * <p>An animation draws through the particle sink at the centre of its block, on a timer of the scheduler. It plays
 * only where its chunk is loaded, as the sink draws nothing in an unloaded chunk. An id that {@code animations.yml}
 * does not define, a type that is in no registry entry, or variables its type cannot use warn high and play nothing;
 * each distinct problem warns once per config. Stop ends every animation.
 *
 * <p>Every call is safe on any thread.
 */
public final class AnimationService implements BoxObserver {

    private static final double BLOCK_CENTRE = 0.5;

    private final AnimationRegistry registry;
    private final ParticleSink sink;
    private final TaskScheduler scheduler;
    private final Warnings warnings;

    private final Object lock = new Object();
    private ConfigSnapshot config;
    private boolean stopped;
    private final Set<String> warned = new HashSet<>();
    private final Map<SpotKey, List<RunningAnimation>> summons = new HashMap<>();
    private final Map<UUID, BoxState> boxes = new HashMap<>();
    /**
     * Everything started that may still draw, so stop can end it all. Finished ones are dropped as new ones start.
     */
    private final Set<RunningAnimation> running = new HashSet<>();

    /**
     * @param scheduler runs the animations' timers, such as the server adapter's global scheduler
     * @param warnings  told of each problem; called from any thread, so it must return quickly and never wait for
     *                  another thread
     */
    public AnimationService(ConfigSnapshot config, AnimationRegistry registry, ParticleSink sink,
                            TaskScheduler scheduler, Warnings warnings) {
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        checkTypes(config);
    }

    /**
     * Takes a reloaded config and warns again for each animation whose type is unknown. Animations already playing
     * go on; each box's alive animations follow the new config from the next tick.
     */
    public void reload(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            config = snapshot;
            warned.clear();
        }
        checkTypes(snapshot);
    }

    /**
     * Stops every animation. Later events play nothing.
     */
    public void stop() {
        synchronized (lock) {
            stopped = true;
            summons.clear();
            boxes.clear();
            running.forEach(RunningAnimation::stop);
            running.clear();
        }
    }

    private void checkTypes(ConfigSnapshot snapshot) {
        for (AnimationDefinition definition : snapshot.animations().values()) {
            if (registry.find(definition.type()).isEmpty()) {
                warnOnce(AnimationWarning.unknownType(definition.id(), definition.type()));
            }
        }
    }

    @Override
    public void summonPending(Spot spot, String lootId, int delayTicks) {
        List<RunningAnimation> started;
        synchronized (lock) {
            if (stopped) {
                return;
            }
            SpotKey key = new SpotKey(spot.worldKey(), spot.x(), spot.y(), spot.z());
            stopAll(summons.remove(key));
            started = play(AnimationMoment.SUMMON, lootId, spot.worldKey(), spot.x(), spot.y(), spot.z());
            summons.put(key, started);
        }
    }

    @Override
    public void summonAbandoned(Spot spot, String lootId, AbandonReason reason) {
        synchronized (lock) {
            stopAll(summons.remove(new SpotKey(spot.worldKey(), spot.x(), spot.y(), spot.z())));
        }
    }

    @Override
    public void shown(BoxEvent box) {
        synchronized (lock) {
            if (stopped) {
                return;
            }
            BlockPosition at = box.position();
            stopAll(summons.remove(new SpotKey(box.worldKey(), at.x(), at.y(), at.z())));
            BoxState state = boxes.computeIfAbsent(box.boxId(), id -> new BoxState());
            state.shown = true;
            state.locked = box.remainingLockSeconds() > 0;
            stopAlive(state);
            startAlive(box, state);
        }
    }

    @Override
    public void ticked(BoxEvent box) {
        synchronized (lock) {
            BoxState state = boxes.get(box.boxId());
            if (stopped || state == null || !state.shown) {
                return;
            }
            if (state.locked && box.remainingLockSeconds() <= 0) {
                state.locked = false;
                play(AnimationMoment.UNLOCKED, box);
            }
            startAlive(box, state);
        }
    }

    @Override
    public void outOfView(BoxEvent box) {
        synchronized (lock) {
            BoxState state = boxes.get(box.boxId());
            if (state != null) {
                state.shown = false;
                stopAlive(state);
            }
        }
    }

    @Override
    public void ended(BoxEvent box) {
        synchronized (lock) {
            BoxState state = boxes.remove(box.boxId());
            if (state != null) {
                stopAlive(state);
            }
            BlockPosition at = box.position();
            stopAll(summons.remove(new SpotKey(box.worldKey(), at.x(), at.y(), at.z())));
            if (!stopped) {
                play(AnimationMoment.REMOVE, box);
            }
        }
    }

    @Override
    public void opened(BoxEvent box, UUID playerId) {
        synchronized (lock) {
            if (!stopped) {
                play(AnimationMoment.OPEN, box);
            }
        }
    }

    /**
     * Starts the box's alive animations that are not running: all of them when it was just shown or its loot's list
     * changed, and each one that finished since.
     */
    private void startAlive(BoxEvent box, BoxState state) {
        List<String> ids = ids(box.lootId(), AnimationMoment.ALIVE);
        if (!ids.equals(state.aliveIds)) {
            stopAlive(state);
            state.aliveIds = ids;
            state.alive = new ArrayList<>();
            ids.forEach(id -> state.alive.add(null));
        }
        for (int i = 0; i < ids.size(); i++) {
            RunningAnimation current = state.alive.get(i);
            if (current == null || current.finished()) {
                state.alive.set(i, start(AnimationMoment.ALIVE, box.lootId(), ids.get(i), box.worldKey(),
                        box.position().x(), box.position().y(), box.position().z()).orElse(null));
            }
        }
    }

    private void stopAlive(BoxState state) {
        for (RunningAnimation animation : state.alive) {
            if (animation != null) {
                animation.stop();
                running.remove(animation);
            }
        }
        state.alive = new ArrayList<>();
        state.aliveIds = List.of();
    }

    private void stopAll(List<RunningAnimation> animations) {
        if (animations != null) {
            for (RunningAnimation animation : animations) {
                animation.stop();
                running.remove(animation);
            }
        }
    }

    private List<RunningAnimation> play(AnimationMoment moment, BoxEvent box) {
        return play(moment, box.lootId(), box.worldKey(), box.position().x(), box.position().y(),
                box.position().z());
    }

    private List<RunningAnimation> play(AnimationMoment moment, String lootId, UUID worldKey, int blockX, int blockY,
                                        int blockZ) {
        List<RunningAnimation> started = new ArrayList<>();
        for (String id : ids(lootId, moment)) {
            start(moment, lootId, id, worldKey, blockX, blockY, blockZ).ifPresent(started::add);
        }
        return started;
    }

    private List<String> ids(String lootId, AnimationMoment moment) {
        return config.lootAnimations(lootId).map(animations -> animations.ids(moment)).orElse(List.of());
    }

    /**
     * Starts one animation at the centre of a block, or warns and starts nothing. Call it under the lock.
     */
    private Optional<RunningAnimation> start(AnimationMoment moment, String lootId, String animationId, UUID worldKey,
                                             int blockX, int blockY, int blockZ) {
        Optional<AnimationDefinition> definition = config.animation(animationId);
        if (definition.isEmpty()) {
            warnOnce(AnimationWarning.unknownAnimation(lootId, moment, animationId));
            return Optional.empty();
        }
        Optional<AnimationType> type = registry.find(definition.get().type());
        if (type.isEmpty()) {
            warnOnce(AnimationWarning.unknownType(animationId, definition.get().type()));
            return Optional.empty();
        }
        AnimationContext context = new AnimationContext(worldKey, blockX + BLOCK_CENTRE, blockY + BLOCK_CENTRE,
                blockZ + BLOCK_CENTRE, sink, scheduler, failure -> warnOnce(AnimationWarning.drawFailed(animationId,
                failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure)));
        try {
            RunningAnimation animation = type.get().start(definition.get().variables(), context);
            running.removeIf(RunningAnimation::finished);
            running.add(animation);
            return Optional.of(animation);
        } catch (IllegalArgumentException unusable) {
            warnOnce(AnimationWarning.unusableVariables(animationId, String.valueOf(unusable.getMessage())));
            return Optional.empty();
        }
    }

    /**
     * Reports a warning the first time it is seen since the config was taken. It runs under the service's lock for
     * a problem found while starting, and on a draw's thread for a failed draw.
     */
    private void warnOnce(AnimationWarning warning) {
        boolean first;
        synchronized (lock) {
            first = warned.add(warning.identity());
        }
        if (first) {
            warnings.warn(warning);
        }
    }

    private record SpotKey(UUID worldKey, int x, int y, int z) {
    }

    private static final class BoxState {
        boolean shown;
        boolean locked;
        List<String> aliveIds = List.of();
        List<RunningAnimation> alive = new ArrayList<>();
    }
}
