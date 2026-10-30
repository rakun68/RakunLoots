package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.Loot;
import me.rakkoon69.rakunloots.config.MessageRenderer;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.summon.MissReason;
import me.rakkoon69.rakunloots.summon.Spot;
import me.rakkoon69.rakunloots.summon.SpotFinder;
import me.rakkoon69.rakunloots.summon.SummonResult;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

/**
 * Takes every summon through one path: search, hold the spot, wait out the loot's summon delay as a pending summon,
 * then check the block and write it. The summon item, the world timer, and an admin all call {@link #summon}.
 *
 * <p>The spot is held the moment the search returns it, before the delay starts, through the lifecycle's own hold, so
 * the occupied check counts it as taken and no other search picks it. Under {@code load} and {@code generate} the
 * search already holds the spot's chunk, and the reservation adopts that hold, so the chunk never unloads in between
 * (ADR 0015); a summon with no delay still waits for any hold of its own to land before it writes. The hold lasts
 * until the block is written or the summon is abandoned. When the delay ends the block is written only while it is still the block the search found.
 * An abandoned summon releases its spot and never searches again. Its summon item, if it came from one, goes to the
 * {@link SummonGiveBack}; a summon with no summon item is only refused.
 *
 * <p>Every block read and write, and the delayed step, runs through the server adapter at the block's location, so
 * each server keeps its own threading rule. Every call is safe on any thread.
 */
public final class SummonService {

    private final ServerAdapter adapter;
    private volatile ConfigSnapshot config;
    private final LootBoxLifecycle lifecycle;
    private final SpotFinder finder;
    private final SummonGiveBack giveBack;
    private final Warnings warnings;

    /**
     * Guards the pending summons and their states.
     */
    private final Object lock = new Object();
    private final Set<Pending> pending = new LinkedHashSet<>();

    public SummonService(
            ServerAdapter adapter,
            ConfigSnapshot config,
            LootBoxLifecycle lifecycle,
            SpotFinder finder,
            SummonGiveBack giveBack,
            Warnings warnings
    ) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.finder = Objects.requireNonNull(finder, "finder");
        this.giveBack = Objects.requireNonNull(giveBack, "giveBack");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * An admin summon: a loot, searched around a world or a player, with an optional owner the lock admits. It has no
     * summon item, so an abandon only refuses it.
     */
    public CompletableFuture<SummonReport> adminSummon(String lootId, SummonTarget target, Optional<UUID> owner) {
        return summon(lootId, target, owner, SummonSource.adminCommand());
    }

    /**
     * Summons a loot: searches once, holds the spot found, waits out the loot's {@code summon_delay}, then writes the
     * block and places the box. Never blocks the calling thread.
     *
     * @param target where to search
     * @param owner  the player the lock admits, or empty for none
     * @param source where the summon came from: the box keeps its kind, and an abandon gives a summon item back
     * @return completes when the summon placed its box or was abandoned, which is after the delay when it has one
     */
    public CompletableFuture<SummonReport> summon(String lootId, SummonTarget target, Optional<UUID> owner,
                                                  SummonSource source) {
        Objects.requireNonNull(lootId, "lootId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(source, "source");
        return run(lootId, target, owner, source).whenComplete((report, failure) -> debug(lootId, source, report));
    }

    private CompletableFuture<SummonReport> run(String lootId, SummonTarget target, Optional<UUID> owner,
                                                SummonSource source) {
        ConfigSnapshot snapshot = config;
        if (snapshot.loot(lootId).isEmpty()) {
            return CompletableFuture.completedFuture(abandon(snapshot, lootId, source, AbandonReason.UNKNOWN_LOOT));
        }
        return search(lootId, target).handle((result, failure) -> {
            if (failure != null) {
                warnings.warn(LifecycleWarning.summonFailed(lootId, unwrap(failure)));
                return CompletableFuture.completedFuture(
                        abandon(config, lootId, source, AbandonReason.NO_SPOT_FOUND));
            }
            return found(lootId, owner, source, result);
        }).thenCompose(report -> report);
    }

    /**
     * Writes the debug line for how one summon ended, when debug mode is on: where it placed its box, or why it was
     * abandoned.
     */
    private void debug(String lootId, SummonSource source, SummonReport report) {
        if (report == null || !warnings.debugEnabled()) {
            return;
        }
        String from = DebugLines.source(source);
        if (report.box().isPresent()) {
            LootBox box = report.box().get();
            DebugLines.inWorld(adapter, warnings, box.worldKey(), world -> "summon placed loot=" + lootId
                    + " world=" + world + " " + DebugLines.at(box.position()) + " source=" + from);
        } else if (report.abandoned().isPresent()) {
            warnings.debug("summon abandoned loot=" + lootId + " reason=" + report.abandoned().get()
                    + " source=" + from);
        }
    }

    private CompletableFuture<SummonResult> search(String lootId, SummonTarget target) {
        if (target.playerId().isEmpty()) {
            return finder.serverSummon(target.worldKey(), lootId, lifecycle.occupiedCheck(), lifecycle.chunkHolds());
        }
        UUID playerId = target.playerId().get();
        return adapter.playerPositions(target.worldKey()).thenCompose(players -> {
            for (PlayerPosition player : players) {
                if (player.playerId().equals(playerId)) {
                    return finder.aroundCenter(target.worldKey(), lootId, (int) Math.floor(player.x()),
                            (int) Math.floor(player.y()), (int) Math.floor(player.z()), lifecycle.occupiedCheck(),
                            lifecycle.chunkHolds());
                }
            }
            return CompletableFuture.completedFuture(
                    SummonResult.miss(MissReason.NO_PLAYERS));
        });
    }

    private CompletableFuture<SummonReport> found(String lootId, Optional<UUID> owner, SummonSource source,
                                                  SummonResult result) {
        ConfigSnapshot snapshot = config;
        if (result.spot().isEmpty()) {
            return CompletableFuture.completedFuture(abandon(snapshot, lootId, source, AbandonReason.NO_SPOT_FOUND));
        }
        Spot spot = result.spot().get();
        Optional<Loot> loot = snapshot.loot(lootId);
        if (loot.isEmpty()) {
            if (result.chunkHeld()) {
                lifecycle.chunkHolds().give(spot.worldKey(), spot.x() >> 4, spot.z() >> 4);
            }
            return CompletableFuture.completedFuture(abandon(snapshot, lootId, source, AbandonReason.UNKNOWN_LOOT));
        }
        BlockPosition position = new BlockPosition(spot.x(), spot.y(), spot.z());
        // The search's hold on the spot's chunk goes to the reservation, which gives it back when the spot is held.
        if (!lifecycle.reserveSpot(spot.worldKey(), position, result.chunkHeld())) {
            return CompletableFuture.completedFuture(abandon(snapshot, lootId, source, AbandonReason.SPOT_HELD));
        }
        Pending summon = new Pending(lootId, spot, position, owner, source);
        int delay = loot.get().summonDelayTicks();
        // Told before the summon can be abandoned, so pending always comes before abandoned or shown.
        lifecycle.summonPending(spot, lootId, delay);
        synchronized (lock) {
            pending.add(summon);
        }
        if (delay <= 0) {
            // The chunk may unload until the ticket lands, so the block is read and written only after it has.
            lifecycle.holdLanded(spot.worldKey(), position).whenComplete((landed, failure) -> write(summon));
        } else {
            TaskHandle task = adapter.atLocation(spot.worldKey(), spot.x(), spot.z())
                    .runLater(() -> write(summon), delay);
            boolean abandoned;
            synchronized (lock) {
                summon.task = task;
                abandoned = summon.state == State.DONE;
            }
            if (abandoned) {
                task.cancel();
            }
        }
        return summon.report;
    }

    /**
     * The delay ended. Reads the block through the adapter, and writes the box only while the block is still the one
     * the search found.
     */
    private void write(Pending summon) {
        synchronized (lock) {
            if (summon.state != State.WAITING) {
                return;
            }
            summon.state = State.WRITING;
        }
        CompletableFuture<SummonReport> result;
        try {
            result = adapter.readBlock(summon.spot.worldKey(), summon.position).handle((found, failure) -> {
                if (failure != null) {
                    lifecycle.releaseSpot(summon.spot.worldKey(), summon.position);
                    return CompletableFuture.completedFuture(failed(summon, unwrap(failure)));
                }
                if (found.isEmpty()) {
                    lifecycle.releaseSpot(summon.spot.worldKey(), summon.position);
                    return CompletableFuture.completedFuture(abandonPending(summon, AbandonReason.CHUNK_UNLOADED));
                }
                if (!found.get().equals(LootBoxLifecycle.namespaced(summon.spot.blockId()))) {
                    lifecycle.releaseSpot(summon.spot.worldKey(), summon.position);
                    return CompletableFuture.completedFuture(abandonPending(summon, AbandonReason.BLOCK_CHANGED));
                }
                return placeBox(summon);
            }).thenCompose(report -> report);
        } catch (RuntimeException failure) {
            lifecycle.releaseSpot(summon.spot.worldKey(), summon.position);
            finish(summon, failed(summon, failure));
            return;
        }
        result.whenComplete((report, failure) -> finish(summon,
                failure != null ? failed(summon, unwrap(failure)) : report));
    }

    /**
     * Writes the block and places the box. The lifecycle gives up the hold whatever the outcome.
     */
    private CompletableFuture<SummonReport> placeBox(Pending summon) {
        try {
            return lifecycle.summonReserved(summon.lootId, summon.spot, summon.owner,
                    summon.source.kind()).handle((outcome, failure) -> {
                if (failure != null) {
                    return failed(summon, unwrap(failure));
                }
                if (outcome.isSummoned()) {
                    return SummonReport.placed(outcome.box().orElseThrow());
                }
                return abandonPending(summon, mapRefusal(outcome.refusal().orElseThrow()));
            });
        } catch (RuntimeException failure) {
            lifecycle.releaseSpot(summon.spot.worldKey(), summon.position);
            return CompletableFuture.completedFuture(failed(summon, failure));
        }
    }

    private SummonReport failed(Pending summon, Throwable failure) {
        warnings.warn(LifecycleWarning.summonFailed(summon.lootId, failure));
        return abandonPending(summon, AbandonReason.ROLL_FAILED);
    }

    private static AbandonReason mapRefusal(SummonRefusal refusal) {
        return switch (refusal) {
            case UNKNOWN_LOOT -> AbandonReason.UNKNOWN_LOOT;
            case SPOT_OCCUPIED -> AbandonReason.SPOT_HELD;
            case SPOT_CHANGED -> AbandonReason.BLOCK_CHANGED;
            case BLOCK_REFUSED -> AbandonReason.BLOCK_REFUSED;
        };
    }

    private void finish(Pending summon, SummonReport report) {
        synchronized (lock) {
            pending.remove(summon);
            summon.state = State.DONE;
        }
        summon.report.complete(report);
    }

    /**
     * A chunk unloaded. Each pending summon whose spot is in it is abandoned at once, so a summon never loads the
     * chunk. Call it from the chunk unload event.
     */
    public void chunkUnloaded(UUID worldKey, ChunkPosition chunk) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(chunk, "chunk");
        abandonWaiting(summon -> summon.spot.worldKey().equals(worldKey)
                && summon.position.x() >> 4 == chunk.chunkX() && summon.position.z() >> 4 == chunk.chunkZ(),
                AbandonReason.CHUNK_UNLOADED);
    }

    /**
     * Abandons every summon still in its delay, then takes the reloaded config. Call it before the lifecycle reloads.
     * A summon whose block write already began is let finish.
     */
    public void reload(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        abandonWaiting(summon -> true, AbandonReason.RELOAD_OR_STOP);
        config = snapshot;
    }

    /**
     * Abandons every summon still in its delay. Call it before the lifecycle stops. A summon whose block write
     * already began is let finish.
     */
    public void stop() {
        abandonWaiting(summon -> true, AbandonReason.RELOAD_OR_STOP);
    }

    /**
     * How many summons are pending in a world: spot held, block not written yet. The spawn limit counts them.
     */
    public int pendingCount(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        synchronized (lock) {
            return (int) pending.stream().filter(summon -> summon.spot.worldKey().equals(worldKey)).count();
        }
    }

    private void abandonWaiting(Predicate<Pending> which, AbandonReason reason) {
        List<Pending> taken = new ArrayList<>();
        synchronized (lock) {
            for (Pending summon : pending) {
                if (summon.state == State.WAITING && which.test(summon)) {
                    summon.state = State.DONE;
                    taken.add(summon);
                }
            }
            pending.removeAll(taken);
        }
        for (Pending summon : taken) {
            if (summon.task != null) {
                summon.task.cancel();
            }
            lifecycle.releaseSpot(summon.spot.worldKey(), summon.position);
            summon.report.complete(abandonPending(summon, reason));
        }
    }

    /**
     * Abandons a summon that was pending: tells the observers, then builds the report as {@link #abandon} does. Call
     * it after the spot is released.
     */
    private SummonReport abandonPending(Pending summon, AbandonReason reason) {
        lifecycle.summonAbandoned(summon.spot, summon.lootId, reason);
        return abandon(config, summon.lootId, summon.source, reason);
    }

    /**
     * Builds the abandon report, tells a summon item's player with the reason's message, and hands the summon item to
     * the give back. A summon with no summon item is neither messaged nor given anything.
     */
    private SummonReport abandon(ConfigSnapshot snapshot, String lootId, SummonSource source, AbandonReason reason) {
        String text = MessageRenderer.render(snapshot, lootId, reason.messageKey()).replace("{name}", lootId);
        source.summonItemPlayer().ifPresent(player -> {
            if (!text.isEmpty()) {
                adapter.sendMessage(player, text);
            }
            giveBack.giveBack(player, lootId, reason);
        });
        return SummonReport.abandoned(reason, text);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private enum State {
        WAITING, WRITING, DONE
    }

    private static final class Pending {

        private final String lootId;
        private final Spot spot;
        private final BlockPosition position;
        private final Optional<UUID> owner;
        private final SummonSource source;
        private final CompletableFuture<SummonReport> report = new CompletableFuture<>();
        private State state = State.WAITING;
        private TaskHandle task;

        private Pending(String lootId, Spot spot, BlockPosition position, Optional<UUID> owner, SummonSource source) {
            this.lootId = lootId;
            this.spot = spot;
            this.position = position;
            this.owner = owner;
            this.source = source;
        }
    }
}
