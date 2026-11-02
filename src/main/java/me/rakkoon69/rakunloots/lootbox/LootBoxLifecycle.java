package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.config.MessageRenderer;
import me.rakkoon69.rakunloots.config.loot.OpenMode;
import me.rakkoon69.rakunloots.config.summon.SummonSearchSettings;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.text.MessageText;
import me.rakkoon69.rakunloots.roll.LootRoller;
import me.rakkoon69.rakunloots.roll.RollResult;
import me.rakkoon69.rakunloots.roll.RolledContents;
import me.rakkoon69.rakunloots.roll.RolledStack;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.SharedInventory;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import me.rakkoon69.rakunloots.storage.FlushResult;
import me.rakkoon69.rakunloots.storage.LoadResult;
import me.rakkoon69.rakunloots.storage.LoadedBox;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.LootBoxStoreException;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.warning.Warnings;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The one place a loot box is summoned, taken from, broken, kept, and saved. It works in plugin types only; block
 * writes, drops, messages, and shared inventories go through the server adapter, so each server keeps its own
 * threading rule.
 *
 * <p>One registry maps a world and block position to a live box. It is filled from the store at start and from
 * summons, and it answers the summon search's occupied check. One global task counts every box's life and lock down
 * each second and hands a box whose life runs out to its location. One async task marks every live box and flushes
 * the store every 60 seconds; stop flushes once more.
 *
 * <p>A box whose block is no longer the block it was placed as, such as after another plugin replaced it, is a
 * changed block. The lifecycle looks for one when the chunk loads, and on click, break, end of life, and explosion,
 * comparing the block id only, so facing or waterlogging does not count. A changed block ends the box as a break,
 * without the block item, and warns high. A block is removed only while it is still the placed block.
 *
 * <p>Every {@link BoxObserver} added hears what happens to each box: shown when it is summoned or its chunk loads, ticked
 * after each 1 second countdown, out of view when its chunk unloads, and ended at every loot end. The lifecycle
 * reports it while holding its lock, so the events of one box never overtake each other.
 *
 * <p>Every call is safe on any thread.
 */
public final class LootBoxLifecycle {

    /**
     * 60 seconds.
     */
    static final long SAVE_PERIOD_TICKS = 20L * 60;

    /**
     * 1 second.
     */
    static final long TICK_PERIOD_TICKS = 20L;

    /**
     * Lets a player take from and break a locked box that is not theirs.
     */
    public static final String BYPASS_LOCK_PERMISSION = "rakunloots.bypass.lock";

    private static final String STILL_LOCKED_KEY = "still_locked";
    private static final Set<String> PLAYER_HEADS = Set.of("minecraft:player_head", "minecraft:player_wall_head");

    private final ServerAdapter adapter;
    private volatile ConfigSnapshot config;
    private final ItemSourceRegistry items;
    private final LootBoxStore store;
    private final Random random;
    private final Warnings warnings;

    /**
     * Guards the moves between reserved, live, and ended, and the save's marking, so the save never marks a box
     * whose delete is already queued.
     */
    private final Object lock = new Object();
    private final Map<Key, LootBox> boxes = new ConcurrentHashMap<>();
    private final Set<Key> reserved = ConcurrentHashMap.newKeySet();
    /**
     * The shared inventory of each inventory open box that someone has open, by box id and by inventory.
     */
    private final Map<UUID, Viewing> viewingByBox = new HashMap<>();
    private final Map<SharedInventory, Viewing> viewingByInventory = new IdentityHashMap<>();
    /**
     * Boxes whose life ran out and whose end is on its way to their location or waits for their chunk to load, so
     * the tick hands each one off once.
     */
    private final Set<UUID> endingOnTime = new HashSet<>();
    /**
     * Boxes the observer was last told are shown, so each shown and out of view is told once.
     */
    private final Set<UUID> shown = new HashSet<>();
    private final List<BoxObserver> observers = new CopyOnWriteArrayList<>();
    /**
     * Each loaded world's name by key, which the world's policy in the config is found by.
     */
    private final Map<UUID, String> worldNames = new ConcurrentHashMap<>();
    /**
     * The boxes that hold their chunk with a chunk ticket, by box id, and how many boxes hold each chunk. Both change
     * only under the lock. A box leaves {@link #holding} once its block is removed, not when it is unregistered.
     */
    private final Set<UUID> holding = new HashSet<>();
    private final Map<BoxChunk, Integer> chunkHolds = new HashMap<>();
    /**
     * The reserved spots that hold their chunk the same way while a summon waits out its delay. They count in
     * {@link #chunkHolds} beside the boxes, and change only under the lock.
     */
    private final Set<Key> reservedHolds = new HashSet<>();
    /**
     * Each reserved spot's own hold request, until the spot is released or becomes a box.
     */
    private final Map<Key, CompletableFuture<Void>> holdLanding = new ConcurrentHashMap<>();
    private TaskHandle saveTask;
    private TaskHandle tickTask;
    private boolean stopped;

    /**
     * @param random   picks free slots and drives the roll; shared across threads
     * @param warnings receives every warning the lifecycle, the roll, and the store find
     */
    public LootBoxLifecycle(
            ServerAdapter adapter,
            ConfigSnapshot config,
            ItemSourceRegistry items,
            LootBoxStore store,
            Random random,
            Warnings warnings
    ) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.items = Objects.requireNonNull(items, "items");
        this.store = Objects.requireNonNull(store, "store");
        this.random = Objects.requireNonNull(random, "random");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * Loads every stored box on the async scheduler and registers it, then starts the tick and the save task. A box
     * whose world is not loaded stays flagged in storage and is not registered, so it is never saved over or
     * deleted. A box whose loot is missing is registered and runs on, as ADR 0014 says. A stored box whose life
     * already ran out ends on the first tick that finds its
     * chunk loaded, or when its chunk loads. A store that cannot load warns severe and starts with no stored boxes.
     * Each stored box whose chunk is already loaded is shown to the observer once the server lists its world's loaded
     * chunks, since no chunk load will come for it. Never blocks the calling thread.
     *
     * @return completes once the stored boxes are registered and the tick and the save task run
     */
    public CompletableFuture<Void> start() {
        ConfigSnapshot snapshot = config;
        return adapter.loadedWorlds().thenCompose(worlds -> {
            Set<UUID> loaded = worlds.stream().map(LoadedWorld::key).collect(Collectors.toUnmodifiableSet());
            worlds.forEach(world -> worldNames.put(world.key(), world.name()));
            CompletableFuture<Void> done = new CompletableFuture<>();
            adapter.async().run(() -> {
                try {
                    load(snapshot, loaded);
                    startTasks();
                    showLoaded(loaded);
                    done.complete(null);
                } catch (RuntimeException failure) {
                    done.completeExceptionally(failure);
                }
            });
            return done;
        });
    }

    private void load(ConfigSnapshot snapshot, Set<UUID> loadedWorlds) {
        LoadResult result;
        try {
            result = store.loadAll(snapshot, loadedWorlds::contains);
        } catch (LootBoxStoreException failure) {
            warnings.warn(LifecycleWarning.loadFailed(failure));
            return;
        }
        result.warnings().forEach(warnings::warn);
        List<LootBox> registered = new ArrayList<>();
        synchronized (lock) {
            for (LoadedBox loaded : result.boxes()) {
                if (loaded.worldNotLoaded()) {
                    continue;
                }
                LootBox box = LootBox.of(loaded.box());
                if (boxes.putIfAbsent(Key.of(box.worldKey(), box.position()), box) == null) {
                    registered.add(box);
                }
            }
        }
        registered.forEach(this::syncHold);
    }

    /**
     * Shows each registered box whose chunk the server lists as loaded and that is not shown yet. Listing the loaded
     * chunks reads and loads no chunk.
     */
    private void showLoaded(Set<UUID> worlds) {
        for (UUID worldKey : worlds) {
            adapter.loadedChunks(worldKey).thenAccept(chunks -> {
                synchronized (lock) {
                    for (LootBox box : boxes.values()) {
                        if (box.worldKey().equals(worldKey) && chunks.contains(chunkOf(box.position()))) {
                            show(box);
                        }
                    }
                }
            });
        }
    }

    private void startTasks() {
        synchronized (lock) {
            if (saveTask == null && !stopped) {
                tickTask = adapter.global().repeat(this::tick, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
                saveTask = adapter.async().repeat(this::save, SAVE_PERIOD_TICKS, SAVE_PERIOD_TICKS);
            }
        }
    }

    /**
     * Stops the tick and the save task, then marks every live box and flushes the store once more, and waits for
     * that flush on the calling thread. A box whose shared inventory is open is saved with what is left in it now, so a stack
     * taken before the stop does not come back. The store stays open; its owner closes it.
     * The open inventories are read on the calling thread. Once saved, every open view is closed through the
     * adapter, so no player keeps taking from an inventory that is no longer saved.
     */
    public void stop() {
        List<TaskHandle> tasks = new ArrayList<>();
        Map<SharedInventory, Set<UUID>> open = new IdentityHashMap<>();
        synchronized (lock) {
            stopped = true;
            if (tickTask != null) {
                tasks.add(tickTask);
                tasks.add(saveTask);
            }
            tickTask = null;
            saveTask = null;
            viewingByBox.values().forEach(this::keepWhatIsLeft);
            viewingByBox.values().forEach(viewing -> open.put(viewing.inventory, Set.copyOf(viewing.viewers)));
        }
        tasks.forEach(TaskHandle::cancel);
        save();
        open.forEach(adapter::closeSharedInventory);
        releaseAllHolds();
    }

    /**
     * Gives back every chunk ticket, as the stop does. A failure to give one back is not reported: the plugin is
     * stopping, and the server drops what is left.
     */
    private void releaseAllHolds() {
        List<BoxChunk> chunks;
        synchronized (lock) {
            holding.clear();
            reservedHolds.clear();
            holdLanding.clear();
            chunks = List.copyOf(chunkHolds.keySet());
            chunkHolds.clear();
        }
        for (BoxChunk chunk : chunks) {
            try {
                adapter.releaseChunk(chunk.worldKey(), chunk.chunk().chunkX(), chunk.chunk().chunkZ())
                        .exceptionally(failure -> false);
            } catch (RuntimeException failure) {
                // The plugin is stopping.
            }
        }
    }

    /**
     * Marks every live box and flushes on the calling thread. Only live boxes are marked, under the lock, so a box
     * that ended since is never written back.
     */
    private void save() {
        synchronized (lock) {
            for (LootBox box : boxes.values()) {
                store.markChanged(box.toRecord());
            }
        }
        try {
            FlushResult result = store.flush(Runnable::run).join();
            result.warnings().forEach(warnings::warn);
        } catch (CompletionException failure) {
            warnings.warn(LifecycleWarning.saveFailed(failure.getCause() == null ? failure : failure.getCause()));
        }
    }

    /**
     * One second passes: every box's remaining life and remaining lock go down by 1, down to 0. Unending life stays
     * unending. The observer hears each box ticked. Each box whose life is now 0 is handed off once, to end at its
     * location. Runs on the global scheduler.
     */
    private void tick() {
        Map<UUID, List<LootBox>> due = new HashMap<>();
        synchronized (lock) {
            for (Map.Entry<Key, LootBox> entry : boxes.entrySet()) {
                LootBox box = entry.getValue().oneSecondLater();
                entry.setValue(box);
                tell(BoxObserver::ticked, box);
                if (box.remainingLifeSeconds().equals(OptionalInt.of(0)) && endingOnTime.add(box.boxId())) {
                    due.computeIfAbsent(box.worldKey(), world -> new ArrayList<>()).add(box);
                }
            }
        }
        due.forEach(this::endOnTime);
    }

    /**
     * Hands each box whose life ran out to its location, when its chunk is loaded. A box in an unloaded chunk keeps
     * remaining life 0 and ends when {@link #chunkLoaded} reports its chunk. Listing the loaded chunks reads and
     * loads no chunk, so no task is scheduled at an unloaded one.
     */
    private void endOnTime(UUID worldKey, List<LootBox> due) {
        reportFailure(adapter.loadedChunks(worldKey).thenAccept(loaded -> {
            for (LootBox box : due) {
                if (loaded.contains(chunkOf(box.position()))) {
                    timeOut(box);
                }
            }
        }), due, "loadedChunks");
    }

    /**
     * Ends a box whose life ran out, on the thread that owns its block, closing every open view first. What is left
     * drops only when its loot's {@code timeout_spill} is on; the block item never does. A changed block ends it as
     * a changed block instead. When its chunk unloaded since, it waits for the chunk to load. A box whose loot is
     * missing ends with every loot end rule off, so nothing spills.
     */
    private void timeOut(LootBox seen) {
        look(seen, true);
    }

    /**
     * Reads the box's block on the thread that owns it. A changed block ends the box. Otherwise a box whose life ran
     * out ends as a timeout. When the chunk is not loaded, nothing happens.
     */
    private void look(LootBox seen, boolean timedOut) {
        reportFailure(adapter.readBlock(seen.worldKey(), seen.position()).thenAccept(found -> {
            Key key = Key.of(seen.worldKey(), seen.position());
            if (found.isEmpty() || endIfChanged(key, seen, found.get()) || !timedOut) {
                return;
            }
            Optional<Loot> loot = config.loot(seen.lootId());
            end(key, seen, "life ran out", loot.isPresent() && loot.get().lootEnd().timeoutSpill(),
                    Optional.empty());
        }), List.of(seen), "readBlock");
    }

    /**
     * Warns for each box when a step chained on the server adapter fails, so the failure does not vanish with the
     * future. Nothing else changes: the step's result is not used.
     */
    private void reportFailure(CompletableFuture<?> step, List<LootBox> boxes, String name) {
        step.whenComplete((done, failure) -> {
            if (failure != null) {
                boxes.forEach(box -> warnFailed(box, name, failure));
            }
        });
    }

    private void warnFailed(LootBox box, String name, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        warnings.warn(LifecycleWarning.asyncFailed(box, name, cause));
    }

    private void reportFailure(CompletableFuture<?> step, LootBox box, String name) {
        reportFailure(step, List.of(box), name);
    }

    /**
     * A chunk loaded. Each box in it that is not shown yet is shown to the observer. Then each box in it whose block
     * changed while nobody looked ends as a changed block. Each box in it whose life ran out while the chunk was
     * unloaded, or before a restart, ends now, as a timeout. The blocks are read in a later task on the thread that
     * owns the chunk.
     *
     * <p>Call it on the thread that owns the chunk, from the chunk load event.
     */
    public void chunkLoaded(UUID worldKey, ChunkPosition chunk) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(chunk, "chunk");
        List<LootBox> inChunk = new ArrayList<>();
        synchronized (lock) {
            for (LootBox box : boxes.values()) {
                if (box.worldKey().equals(worldKey) && chunkOf(box.position()).equals(chunk)) {
                    if (timedOut(box)) {
                        endingOnTime.add(box.boxId());
                    }
                    show(box);
                    inChunk.add(box);
                }
            }
        }
        inChunk.forEach(box -> look(box, timedOut(box)));
    }

    /**
     * A chunk unloaded. Each shown box in it is out of view for the observer. The boxes live on and keep counting
     * down; nothing is read or written.
     *
     * <p>Call it from the chunk unload event.
     */
    public void chunkUnloaded(UUID worldKey, ChunkPosition chunk) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(chunk, "chunk");
        synchronized (lock) {
            for (LootBox box : boxes.values()) {
                if (box.worldKey().equals(worldKey) && chunkOf(box.position()).equals(chunk)
                        && shown.remove(box.boxId())) {
                    tell(BoxObserver::outOfView, box);
                }
            }
        }
    }

    /**
     * Adds an observer that hears what happens to each box from now on, after the observers added before it. Boxes
     * already shown are not shown to it again. One observer that throws is warned about and does not stop the others.
     */
    public void addObserver(BoxObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * Stops telling the observer anything. Does nothing for an observer that was not added.
     */
    public void removeObserver(BoxObserver observer) {
        observers.remove(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * Tells every observer that a summon holds its spot and waits out its delay. The summon service calls it once the
     * spot is held, before the block is written or the summon is abandoned.
     */
    public void summonPending(Spot spot, String lootId, int delayTicks) {
        tellSummon(spot, lootId, observer -> observer.summonPending(spot, lootId, delayTicks));
    }

    /**
     * Tells every observer that a pending summon placed no box and released its spot.
     */
    public void summonAbandoned(Spot spot, String lootId, AbandonReason reason) {
        tellSummon(spot, lootId, observer -> observer.summonAbandoned(spot, lootId, reason));
    }

    private void tellSummon(Spot spot, String lootId, Consumer<BoxObserver> event) {
        BlockPosition position = new BlockPosition(spot.x(), spot.y(), spot.z());
        for (BoxObserver observer : observers) {
            try {
                event.accept(observer);
            } catch (RuntimeException | LinkageError failure) {
                warnings.warn(LifecycleWarning.observerFailed(lootId, spot.worldKey(), position, failure));
            }
        }
    }

    /**
     * Shows the box to the observer unless it is shown already. Call it under the lock.
     */
    private void show(LootBox box) {
        if (shown.add(box.boxId())) {
            tell(BoxObserver::shown, box);
        }
    }

    /**
     * Reports one event to each observer in turn. An observer that throws, or that was built against an API this
     * server lacks and so fails to link, is warned about and does not stop the caller or the other observers. Call it
     * under the lock.
     */
    private void tell(BiConsumer<BoxObserver, BoxEvent> event, LootBox box) {
        BoxEvent seen = BoxEvent.of(box);
        for (BoxObserver observer : observers) {
            try {
                event.accept(observer, seen);
            } catch (RuntimeException | LinkageError failure) {
                warnings.warn(LifecycleWarning.observerFailed(box, failure));
            }
        }
    }

    private static boolean timedOut(LootBox box) {
        return box.remainingLifeSeconds().equals(OptionalInt.of(0));
    }

    /**
     * Ends the box as a changed block when the block found is not the block it was placed as, and warns high when
     * this call ended it. Block data plays no part: the ids compared carry none.
     *
     * @return whether the block changed
     */
    private boolean endIfChanged(Key key, LootBox seen, String blockFound) {
        if (blockFound.equals(seen.placedBlockId())) {
            return false;
        }
        if (end(key, seen, "block changed", true, Optional.empty())) {
            warnings.warn(LifecycleWarning.changedBlock(seen, blockFound));
        }
        return true;
    }

    /**
     * Reads the box's block now, on the calling thread, which owns it, and ends the box as a changed block when it is
     * not the placed block.
     *
     * @return whether the block changed
     */
    private boolean endIfChangedHere(Key key, LootBox seen) {
        Optional<String> found = adapter.readBlockHere(seen.worldKey(), seen.position());
        return found.isPresent() && endIfChanged(key, seen, found.get());
    }

    private static ChunkPosition chunkOf(BlockPosition position) {
        return new ChunkPosition(position.x() >> 4, position.z() >> 4);
    }

    /**
     * Summons a loot onto a spot. The spot is reserved at once, so a second summon there is refused. The loot's
     * block replaces the block the search found, on the thread that owns the spot, only while it is still that
     * block. Then the loot rolls, every stack gets a slot, and the box is registered and marked for the next save.
     * Remaining life starts at the loot's life and remaining lock at its lock length. Never blocks the calling
     * thread.
     *
     * @param lootId the loot to summon
     * @param spot   where to place it, with the block the search found there
     * @param owner  the player the lock admits, or empty for none
     * @param source how the box was summoned, kept with the box
     * @return the box, or why none was placed; fails only when the roll throws, with the block removed again
     */
    public CompletableFuture<SummonOutcome> summon(String lootId, Spot spot, Optional<UUID> owner,
                                                   SummonKind source) {
        Objects.requireNonNull(lootId, "lootId");
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(source, "source");
        if (config.loot(lootId).isEmpty()) {
            return CompletableFuture.completedFuture(SummonOutcome.refused(SummonRefusal.UNKNOWN_LOOT));
        }
        BlockPosition position = new BlockPosition(spot.x(), spot.y(), spot.z());
        if (!reserveSpot(spot.worldKey(), position)) {
            return CompletableFuture.completedFuture(SummonOutcome.refused(SummonRefusal.SPOT_OCCUPIED));
        }
        return summonReserved(lootId, spot, owner, source);
    }

    /**
     * Holds a spot for a summon that has not written its block yet, such as one waiting out its summon delay. The
     * occupied check counts the held spot as taken. The hold ends in {@link #summonReserved}, which gives it up
     * whatever the outcome, or in {@link #releaseSpot}.
     *
     * @return whether the spot is now held; false when a loot box or another summon already holds it
     */
    public boolean reserveSpot(UUID worldKey, BlockPosition position) {
        return reserveSpot(worldKey, position, false);
    }

    /**
     * Holds a spot as {@link #reserveSpot(UUID, BlockPosition)} does, for a summon whose search already took a hold on
     * the spot's chunk through {@link #chunkHolds()}. The reservation adopts that hold instead of asking for its own,
     * so the chunk is never unheld between the search and the pending summon. When the spot cannot be held the hold
     * is given back.
     *
     * @param adoptHold whether the caller owns a hold on the spot's chunk from {@link #chunkHolds()}
     * @return whether the spot is now held; false when a loot box or another summon already holds it
     */
    public boolean reserveSpot(UUID worldKey, BlockPosition position, boolean adoptHold) {
        return reserve(Key.of(Objects.requireNonNull(worldKey, "worldKey"),
                Objects.requireNonNull(position, "position")), adoptHold);
    }

    /**
     * The chunk holds the summon search takes, through this lifecycle's per-chunk count. A box, a reserved spot, and
     * a search that reads the chunk share one ticket.
     */
    public ChunkHolds chunkHolds() {
        return searchHolds;
    }

    /**
     * Completes once the chunk hold of a reserved spot has landed or been refused. Completes at once for a spot that
     * adopted its hold, one under {@code skip}, and one that is not reserved. A summon with no delay waits for it
     * before it writes its block, since the chunk may unload until the ticket lands.
     */
    public CompletableFuture<Void> holdLanded(UUID worldKey, BlockPosition position) {
        return holdLanding.getOrDefault(Key.of(Objects.requireNonNull(worldKey, "worldKey"),
                Objects.requireNonNull(position, "position")), CompletableFuture.completedFuture(null));
    }

    /**
     * Gives up a hold made by {@link #reserveSpot} that no {@link #summonReserved} will end.
     */
    public void releaseSpot(UUID worldKey, BlockPosition position) {
        release(Key.of(Objects.requireNonNull(worldKey, "worldKey"), Objects.requireNonNull(position, "position")));
    }

    /**
     * Summons a loot onto a spot the caller already holds with {@link #reserveSpot}, as {@link #summon} does after
     * its own reservation. The hold ends with this call's result, whatever it is: the box registered, a refusal, or
     * a failure. A loot that left the config since the hold refuses as {@link SummonRefusal#UNKNOWN_LOOT}.
     */
    public CompletableFuture<SummonOutcome> summonReserved(String lootId, Spot spot, Optional<UUID> owner,
                                                           SummonKind source) {
        Objects.requireNonNull(lootId, "lootId");
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(source, "source");
        BlockPosition position = new BlockPosition(spot.x(), spot.y(), spot.z());
        Key key = Key.of(spot.worldKey(), position);
        Optional<Loot> found = config.loot(lootId);
        if (found.isEmpty()) {
            release(key);
            return CompletableFuture.completedFuture(SummonOutcome.refused(SummonRefusal.UNKNOWN_LOOT));
        }
        Loot loot = found.get();
        String blockId = namespaced(loot.block());
        CompletableFuture<Boolean> placed;
        try {
            placed = adapter.replaceBlock(spot.worldKey(), position, spot.blockId(), blockId, loot.headData());
        } catch (IllegalArgumentException refused) {
            release(key);
            warnings.warn(LifecycleWarning.blockRefused(lootId, refused));
            return CompletableFuture.completedFuture(SummonOutcome.refused(SummonRefusal.BLOCK_REFUSED));
        }
        return placed.handle((set, failure) -> {
            if (failure != null) {
                release(key);
                throw new CompletionException(failure);
            }
            if (!set) {
                release(key);
                return SummonOutcome.refused(SummonRefusal.SPOT_CHANGED);
            }
            try {
                return SummonOutcome.summoned(register(key, loot, spot.worldKey(), position, blockId, owner, source));
            } catch (RuntimeException rollFailed) {
                release(key);
                adapter.removeBlock(spot.worldKey(), position, blockId);
                throw rollFailed;
            }
        });
    }

    /**
     * Rolls and places the contents, then registers the box and shows it to the observer. Runs where the block was
     * set.
     */
    private LootBox register(Key key, Loot loot, UUID worldKey, BlockPosition position, String blockId,
                             Optional<UUID> owner, SummonKind source) {
        RollResult roll = LootRoller.roll(loot, items, random);
        SlotPlacement.Placed placed = SlotPlacement.place(loot.id(), roll.contents().stacks(), random);
        LootBox box = new LootBox(UUID.randomUUID(), loot.id(), owner, worldKey, position, blockId,
                loot.lifeSeconds(), loot.lockSeconds(), placed.contents(), source);
        synchronized (lock) {
            reserved.remove(key);
            holdLanding.remove(key);
            boxes.put(key, box);
            store.markChanged(box.toRecord());
            show(box);
            if (reservedHolds.contains(key)) {
                // The box takes its own hold before the spot gives up its, so the chunk never goes unheld between.
                applyHold(box, ticketed(worldKey, config));
                releaseReservedHold(key);
            }
        }
        syncHold(box);
        roll.warnings().forEach(warnings::warn);
        placed.warnings().forEach(warnings::warn);
        return box;
    }

    private boolean reserve(Key key, boolean adoptHold) {
        synchronized (lock) {
            if (boxes.containsKey(key) || !reserved.add(key)) {
                if (adoptHold) {
                    giveChunk(BoxChunk.of(key));
                }
                return false;
            }
            if (adoptHold) {
                if (!stopped) {
                    reservedHolds.add(key);
                }
                return true;
            }
        }
        holdLanding.put(key, syncReservedHold(key));
        return true;
    }

    private void release(Key key) {
        synchronized (lock) {
            reserved.remove(key);
            holdLanding.remove(key);
            releaseReservedHold(key);
        }
    }

    /**
     * The search's chunk holds: counted with the boxes and reserved spots, and a hold that fails is not counted.
     */
    private final ChunkHolds searchHolds = new ChunkHolds() {

        @Override
        public CompletableFuture<Boolean> take(UUID worldKey, int chunkX, int chunkZ) {
            BoxChunk chunk = new BoxChunk(worldKey, new ChunkPosition(chunkX, chunkZ));
            CompletableFuture<Boolean> asked;
            synchronized (lock) {
                if (stopped) {
                    return CompletableFuture.completedFuture(false);
                }
                asked = takeChunk(chunk);
            }
            return asked.handle((held, failure) -> {
                boolean holding = failure == null && held;
                if (!holding) {
                    synchronized (lock) {
                        if (chunkHolds.merge(chunk, -1, Integer::sum) <= 0) {
                            chunkHolds.remove(chunk);
                        }
                    }
                }
                return holding;
            });
        }

        @Override
        public void give(UUID worldKey, int chunkX, int chunkZ) {
            BoxChunk chunk = new BoxChunk(worldKey, new ChunkPosition(chunkX, chunkZ));
            CompletableFuture<Boolean> released;
            synchronized (lock) {
                released = giveChunk(chunk);
            }
            released.exceptionally(failure -> false);
        }
    };

    /**
     * Gives a reserved spot the chunk ticket its world's policy asks for, once the world's name is known, so a pending
     * summon's chunk cannot unload during its delay (ADR 0015). A spot released since, or a lifecycle that stopped,
     * gets none. A hold the server cannot make is not reported: there is no box to name, and the summon is abandoned
     * as an unloaded chunk when the chunk goes, as it was before.
     */
    private CompletableFuture<Void> syncReservedHold(Key key) {
        CompletableFuture<Void> named = worldNames.containsKey(key.worldKey())
                ? CompletableFuture.completedFuture(null) : learnWorlds();
        return named.thenCompose(learned -> {
            synchronized (lock) {
                if (reserved.contains(key) && !stopped && !reservedHolds.contains(key)
                        && ticketed(key.worldKey(), config)) {
                    reservedHolds.add(key);
                    return takeChunk(BoxChunk.of(key)).<Void>thenApply(held -> null);
                }
            }
            return CompletableFuture.<Void>completedFuture(null);
        }).handle((done, failure) -> null);
    }

    /**
     * Gives back what a reserved spot holds of its chunk, if anything. Call it under the lock.
     */
    private void releaseReservedHold(Key key) {
        if (reservedHolds.remove(key)) {
            giveChunk(BoxChunk.of(key));
        }
    }

    /**
     * Counts one more holder of the chunk and asks the server for the ticket when it is the first. Call it under the
     * lock.
     *
     * @return the server's answer, or an already completed one when another holder has the ticket
     */
    private CompletableFuture<Boolean> takeChunk(BoxChunk chunk) {
        if (chunkHolds.merge(chunk, 1, Integer::sum) == 1) {
            return adapter.holdChunk(chunk.worldKey(), chunk.chunk().chunkX(), chunk.chunk().chunkZ());
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Counts one holder less of the chunk and gives the ticket back when it was the last. Call it under the lock.
     */
    private CompletableFuture<Boolean> giveChunk(BoxChunk chunk) {
        if (chunkHolds.merge(chunk, -1, Integer::sum) <= 0) {
            chunkHolds.remove(chunk);
            return adapter.releaseChunk(chunk.worldKey(), chunk.chunk().chunkX(), chunk.chunk().chunkZ());
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * A player clicks or breaks a block. Where no loot box is, nothing happens and nothing is cancelled. Where the
     * box's block is no longer the placed block, the box ends as a changed block, whatever the lock, and nothing is
     * cancelled and no drops are touched, since the block is no longer a loot box.
     *
     * <p>While the box's lock runs, only its owner and a player with {@link #BYPASS_LOCK_PERMISSION} pass. A box with
     * no owner passes only bypass. Anyone else gets {@code still_locked} and the event is cancelled. Sneaking plays
     * no part.
     *
     * <p>A click is always cancelled, so the block's own use, such as a barrel's inventory, never runs on a loot box.
     * A click on a drop open box spills every stack at the box, with the block item when the loot's {@code drop_block}
     * is on as for a break, and ends it. A click on an inventory open box shows
     * the player the box's one shared inventory, even when the block is a container. A break drops what is left,
     * closing any open view of the shared inventory first, and the block
     * item when the loot's {@code drop_block} is on, and ends the box. A break is not cancelled, so the server breaks
     * the block and would drop its own items; the caller turns those off. An ended box's block is removed only while
     * it is still the placed block, and its row is deleted by the next save.
     *
     * <p>Call it on the thread that owns the block. The block work and drops run through the server adapter.
     *
     * @param bypassLock whether the player has {@link #BYPASS_LOCK_PERMISSION}
     * @return what the caller does with the event: cancel it for {@link InteractionResult#REFUSED} and for a click
     * that is {@link InteractionResult#ALLOWED}, turn the server's drops off for a break that is allowed, and leave
     * the event alone for {@link InteractionResult#ENDED_CHANGED} and {@link InteractionResult#NOT_A_BOX}
     */
    public InteractionResult interact(UUID playerId, UUID worldKey, BlockPosition position, boolean bypassLock,
                            Interaction interaction) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(interaction, "interaction");
        Key key = Key.of(Objects.requireNonNull(worldKey, "worldKey"), Objects.requireNonNull(position, "position"));
        LootBox box = boxes.get(key);
        if (box == null) {
            return InteractionResult.NOT_A_BOX;
        }
        if (endIfChangedHere(key, box)) {
            return InteractionResult.ENDED_CHANGED;
        }
        ConfigSnapshot snapshot = config;
        Optional<Loot> found = snapshot.loot(box.lootId());
        if (!admits(box, playerId, bypassLock)) {
            String message = MessageRenderer.render(snapshot, box.lootId(), STILL_LOCKED_KEY);
            if (!message.isEmpty()) {
                adapter.sendMessage(playerId, message);
            }
            return InteractionResult.REFUSED;
        }
        switch (interaction) {
            case CLICK -> {
                if (found.isPresent() && found.get().openMode() == OpenMode.DROP) {
                    synchronized (lock) {
                        if (boxes.get(key) == box) {
                            tell((observer, event) -> observer.opened(event, playerId), box);
                        }
                    }
                    end(key, box, "opened (drop open)", true,
                            found.get().lootEnd().dropBlock() ? blockItem(box, found.get()) : Optional.empty());
                } else {
                    // A box whose loot is missing opens as inventory open, titled with its loot id.
                    open(key, box, found.map(loot -> MessageText.color(loot.displayName())).orElse(box.lootId()),
                            playerId);
                }
                return InteractionResult.ALLOWED;
            }
            case BREAK -> {
                boolean dropBlock = found.isPresent() && found.get().lootEnd().dropBlock();
                end(key, box, "broken by a player", true,
                        dropBlock ? blockItem(box, found.get()) : Optional.empty());
                return InteractionResult.ALLOWED;
            }
            default -> throw new IllegalArgumentException("unknown interaction " + interaction);
        }
    }

    /**
     * Whether the lock lets the player take from or break the box. The owner and bypass always pass.
     */
    private static boolean admits(LootBox box, UUID playerId, boolean bypassLock) {
        return box.remainingLockSeconds() <= 0 || bypassLock || box.owner().equals(Optional.of(playerId));
    }

    /**
     * Shows the box's shared inventory to the player. The first viewer's click makes it from what is left in the
     * box, titled with the loot's display name, as large as the box's highest slot needs. Every later viewer gets
     * that same inventory until the last one closes it. A player who has it open already is not shown it again. An
     * open that fails, or that another plugin cancels, is closed for the player as if they had closed it. The placed
     * block's own inventory is never used.
     */
    private void open(Key key, LootBox seen, String title, UUID playerId) {
        SharedInventory inventory;
        synchronized (lock) {
            LootBox box = boxes.get(key);
            if (box == null || !box.boxId().equals(seen.boxId())) {
                return;
            }
            Viewing viewing = viewingByBox.get(box.boxId());
            if (viewing == null) {
                Map<Integer, ItemStack> slots = new TreeMap<>();
                for (RolledStack stack : box.contents().stacks()) {
                    stack.slot().ifPresent(slot -> slots.put(slot, stack.item()));
                }
                viewing = new Viewing(key, box.boxId(), adapter.createSharedInventory(
                        title, SlotPlacement.size(box.contents().stacks()), slots));
                viewingByBox.put(box.boxId(), viewing);
                viewingByInventory.put(viewing.inventory, viewing);
            }
            if (!viewing.viewers.add(playerId)) {
                return;
            }
            inventory = viewing.inventory;
        }
        adapter.openSharedInventory(playerId, inventory).whenComplete((opened, failure) -> {
            if (failure == null && opened) {
                synchronized (lock) {
                    LootBox current = boxes.get(key);
                    if (current != null && current.boxId().equals(seen.boxId())) {
                        tell((observer, event) -> observer.opened(event, playerId), current);
                    }
                }
            }
            if (failure != null || !opened) {
                inventoryClosed(inventory, playerId);
            }
            if (failure != null) {
                warnFailed(seen, "openSharedInventory", failure);
            }
        });
    }

    /**
     * A player closed a box's shared inventory. The box keeps what is left in it. When that was the last viewer,
     * the inventory is let go, and a box with nothing left ends when its loot is inventory open with
     * {@code empty_removal} on. An empty removal never drops the block item. An inventory whose box already ended is
     * ignored.
     *
     * <p>Call it on the thread that owns the viewer, from the inventory close event.
     *
     * @param inventory the shared inventory the player closed
     * @param viewerId  the player who closed it
     */
    public void inventoryClosed(SharedInventory inventory, UUID viewerId) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(viewerId, "viewerId");
        Viewing viewing;
        LootBox left;
        synchronized (lock) {
            viewing = viewingByInventory.get(inventory);
            if (viewing == null || !viewing.viewers.remove(viewerId)) {
                return;
            }
            left = keepWhatIsLeft(viewing);
            if (!viewing.viewers.isEmpty()) {
                return;
            }
            forget(viewing);
        }
        if (left == null || !left.contents().stacks().isEmpty()) {
            return;
        }
        Optional<Loot> found = config.loot(left.lootId());
        if (found.isPresent() && found.get().openMode() == OpenMode.INVENTORY
                && found.get().lootEnd().emptyRemoval()) {
            end(viewing.key, left, "emptied", true, Optional.empty());
        }
    }

    /**
     * Swaps in the box with what is left in its shared inventory now. Call it under the lock.
     *
     * @return the box as it is now, or null when it has ended
     */
    private LootBox keepWhatIsLeft(Viewing viewing) {
        LootBox box = boxes.get(viewing.key);
        if (box == null || !box.boxId().equals(viewing.boxId)) {
            return null;
        }
        List<RolledStack> left = new ArrayList<>();
        viewing.inventory.contents().forEach((slot, stack) -> left.add(new RolledStack(OptionalInt.of(slot), stack)));
        LootBox kept = box.withContents(new RolledContents(left));
        boxes.put(viewing.key, kept);
        return kept;
    }

    /**
     * Lets go of a shared inventory. Call it under the lock.
     */
    private void forget(Viewing viewing) {
        viewingByBox.remove(viewing.boxId);
        viewingByInventory.remove(viewing.inventory);
    }

    /**
     * Ends a box: unregisters it and queues its row's delete, then closes every view of its shared inventory, removes
     * its block while it is still the placed block, and drops what is left when asked, with the block item when
     * given. What is left is read from the shared inventory when one is open, after its views close. The observer
     * hears it ended, as it was last. A box another call ended first is left alone. A box's chunk ticket is given
     * back once its block is removed and its drops are dropped, so those still see a loaded chunk.
     *
     * @return whether this call ended the box
     */
    private boolean end(Key key, LootBox seen, String how, boolean dropWhatIsLeft, Optional<ItemStack> blockItem) {
        return end(key, seen, how, dropWhatIsLeft, blockItem, false);
    }

    /**
     * Ends a box as {@link #end(Key, LootBox, String, boolean, Optional)} does.
     *
     * @param loadChunk whether the block's removal loads its chunk when it is not loaded, as an admin removal does
     *                  (ADR 0015); every other loot end happens where its chunk is already loaded
     */
    private boolean end(Key key, LootBox seen, String how, boolean dropWhatIsLeft, Optional<ItemStack> blockItem,
                        boolean loadChunk) {
        LootBox box;
        Viewing viewing;
        Set<UUID> viewers = Set.of();
        synchronized (lock) {
            box = boxes.get(key);
            if (box == null || !box.boxId().equals(seen.boxId())) {
                return false;
            }
            boxes.remove(key);
            endingOnTime.remove(box.boxId());
            shown.remove(box.boxId());
            store.queueDelete(box.boxId());
            viewing = viewingByBox.get(box.boxId());
            if (viewing != null) {
                viewers = Set.copyOf(viewing.viewers);
                forget(viewing);
            }
            tell(BoxObserver::ended, box);
        }
        DebugLines.inWorld(adapter, warnings, box.worldKey(), world -> "loot end box=" + box.boxId() + " loot="
                + box.lootId() + " world=" + world + " " + DebugLines.at(box.position()) + " how=" + how);
        CompletableFuture<Void> closed = viewing == null
                ? CompletableFuture.completedFuture(null)
                : adapter.closeSharedInventory(viewing.inventory, viewers);
        reportFailure(closed.handle((done, failure) -> {
            if (failure != null) {
                warnFailed(box, "closeSharedInventory", failure);
            }
            List<ItemStack> drops = new ArrayList<>();
            if (dropWhatIsLeft && viewing == null) {
                box.contents().stacks().forEach(stack -> drops.add(stack.item()));
            } else if (dropWhatIsLeft) {
                drops.addAll(viewing.inventory.contents().values());
            }
            blockItem.ifPresent(drops::add);
            removeAndDrop(box, drops, loadChunk);
            return null;
        }), box, "end");
        return true;
    }

    /**
     * Removes the box's block while it is still the placed block, drops the stacks, and then gives back the box's
     * chunk ticket, whatever the outcome.
     */
    private void removeAndDrop(LootBox box, List<ItemStack> drops, boolean loadChunk) {
        CompletableFuture<Boolean> dropped;
        try {
            CompletableFuture<Boolean> removed;
            try {
                removed = loadChunk
                        ? adapter.removeBlockLoadingChunk(box.worldKey(), box.position(), box.placedBlockId())
                        : adapter.removeBlock(box.worldKey(), box.position(), box.placedBlockId());
            } catch (IllegalArgumentException notABlock) {
                removed = CompletableFuture.completedFuture(false);
            }
            dropped = removed.handle((done, failure) -> {
                if (failure != null) {
                    warnFailed(box, loadChunk ? "removeBlockLoadingChunk" : "removeBlock", failure);
                }
                return adapter.dropStacks(box.worldKey(), box.position(), drops);
            }).thenCompose(each -> each);
        } catch (RuntimeException failure) {
            releaseHold(box);
            throw failure;
        }
        reportFailure(dropped, box, "dropStacks");
        dropped.whenComplete((done, failure) -> releaseHold(box));
    }

    /**
     * The chunk a box sits in, with its world.
     */
    private record BoxChunk(UUID worldKey, ChunkPosition chunk) {

        static BoxChunk of(LootBox box) {
            return new BoxChunk(box.worldKey(), chunkOf(box.position()));
        }

        static BoxChunk of(Key key) {
            return new BoxChunk(key.worldKey(), new ChunkPosition(key.x() >> 4, key.z() >> 4));
        }
    }

    /**
     * Whether a world's boxes keep their chunk loaded with a chunk ticket: the world's {@code unloaded_chunks} is
     * {@code load} or {@code generate}, which is also the default for a world that worlds.yml does not name. The
     * world's name comes from the names learned from the server's loaded worlds.
     */
    private boolean ticketed(UUID worldKey, ConfigSnapshot snapshot) {
        UnloadedChunkPolicy policy = Optional.ofNullable(worldNames.get(worldKey))
                .flatMap(snapshot::world)
                .map(world -> world.summonSearch().unloadedChunks())
                .orElse(SummonSearchSettings.DEFAULT_UNLOADED);
        return policy != UnloadedChunkPolicy.SKIP;
    }

    /**
     * Remembers each loaded world's name, which the policy lookup needs, since a box knows its world by key.
     */
    private CompletableFuture<Void> learnWorlds() {
        return adapter.loadedWorlds().thenAccept(
                worlds -> worlds.forEach(world -> worldNames.put(world.key(), world.name())));
    }

    /**
     * Gives a registered box the chunk ticket its world's policy asks for, once the world's name is known. A box
     * that ended since, or a lifecycle that stopped, gets none.
     */
    private void syncHold(LootBox box) {
        CompletableFuture<Void> named = worldNames.containsKey(box.worldKey())
                ? CompletableFuture.completedFuture(null) : learnWorlds();
        reportFailure(named.thenRun(() -> {
            synchronized (lock) {
                LootBox live = boxes.get(Key.of(box.worldKey(), box.position()));
                if (live != null && live.boxId().equals(box.boxId())) {
                    applyHold(live, ticketed(live.worldKey(), config));
                }
            }
        }), box, "loadedWorlds");
    }

    /**
     * Makes a live box hold its chunk, or not, as wanted. A plugin holds one ticket per chunk, so the lifecycle
     * counts the boxes holding each chunk and asks the server only when the first starts and when the last gives up.
     * Call it under the lock.
     */
    private void applyHold(LootBox box, boolean wanted) {
        boolean holds = holding.contains(box.boxId());
        if (wanted && !holds && !stopped) {
            holding.add(box.boxId());
            reportFailure(takeChunk(BoxChunk.of(box)), box, "holdChunk");
        } else if (!wanted && holds) {
            releaseHold(box);
        }
    }

    /**
     * Gives back what a box holds of its chunk, if anything, and releases the chunk when no other box holds it.
     */
    private void releaseHold(LootBox box) {
        synchronized (lock) {
            if (!holding.remove(box.boxId())) {
                return;
            }
            reportFailure(giveChunk(BoxChunk.of(box)), box, "releaseChunk");
        }
    }

    /**
     * The placed block as a plain item, with the loot's head data on a player head, or empty when the block has no
     * item.
     */
    private Optional<ItemStack> blockItem(LootBox box, Loot loot) {
        boolean head = PLAYER_HEADS.contains(box.placedBlockId());
        try {
            return adapter.blockItem(box.placedBlockId(), head ? loot.headData() : Optional.empty());
        } catch (IllegalArgumentException notABlock) {
            return Optional.empty();
        }
    }

    /**
     * An explosion is about to break blocks. Every loot box among them is kept out of it, so the server never breaks
     * a box's block or drops it as its own item. A locked box stays, even when its loot allows explosions. An
     * unlocked box whose loot allows explosions ends: what is left drops, with the block item when the loot's
     * {@code drop_block} is on, and the lifecycle removes its block while it is still the placed block. Any other box
     * stays. A box whose block is no longer the placed block ends as a changed block and is not kept, since its
     * block is no longer a loot box.
     *
     * <p>Call it on the thread that owns the blocks, from the explosion event.
     *
     * @param blocks the blocks the explosion would break
     * @return the blocks to take out of the explosion
     */
    public Set<BlockPosition> explode(UUID worldKey, List<BlockPosition> blocks) {
        Objects.requireNonNull(worldKey, "worldKey");
        ConfigSnapshot snapshot = config;
        Set<BlockPosition> kept = new LinkedHashSet<>();
        for (BlockPosition position : blocks) {
            Key key = Key.of(worldKey, Objects.requireNonNull(position, "position"));
            LootBox box = boxes.get(key);
            if (box == null || endIfChangedHere(key, box)) {
                continue;
            }
            kept.add(position);
            Optional<Loot> loot = snapshot.loot(box.lootId());
            if (box.remainingLockSeconds() <= 0 && loot.isPresent() && loot.get().lootEnd().allowExplosion()) {
                end(key, box, "explosion", true,
                        loot.get().lootEnd().dropBlock() ? blockItem(box, loot.get()) : Optional.empty());
            }
        }
        return kept;
    }

    /**
     * Ends every live box the filter chooses as an admin removal, a loot end that drops nothing, gives no block item,
     * and removes the block only while it is still the placed block, so a block another plugin or a builder put there
     * stays, also when its chunk is not loaded: the removal loads the chunk for itself (ADR 0015). Every open view of
     * the box closes first, and the observer hears each box ended. The filter reads boxes
     * under the lifecycle's lock, so it must not call back into the lifecycle.
     *
     * @return how many boxes this call ended
     */
    public int remove(Predicate<LootBox> chosen) {
        Objects.requireNonNull(chosen, "chosen");
        List<LootBox> found = new ArrayList<>();
        synchronized (lock) {
            for (LootBox box : boxes.values()) {
                if (chosen.test(box)) {
                    found.add(box);
                }
            }
        }
        int ended = 0;
        for (LootBox box : found) {
            if (end(Key.of(box.worldKey(), box.position()), box, "admin removal", false, Optional.empty(), true)) {
                ended++;
            }
        }
        return ended;
    }

    /**
     * Takes a reloaded config. Open mode, lock length, loot end rules, and presentation come from it from now on.
     * Each live box keeps the block it was placed as, so a loot whose block changed leaves its live boxes alone. A
     * shorter life caps a box's remaining life; a longer one, or unending life, does not raise it. A box that waits
     * at remaining life 0 for its chunk keeps waiting.
     *
     * <p>A box whose loot the new config lacks stays registered and runs on, as ADR 0014 says: it counts down its
     * stored life and lock, opens as inventory open with its stored stacks, and ends with every loot end rule off.
     * Such boxes are warned about high, once for this reload, naming how many and which loot ids.
     */
    public void reload(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int withoutLoot = 0;
        Set<String> missing = new TreeSet<>();
        synchronized (lock) {
            config = snapshot;
            for (Map.Entry<Key, LootBox> entry : boxes.entrySet()) {
                Optional<Loot> loot = snapshot.loot(entry.getValue().lootId());
                if (loot.isPresent()) {
                    entry.setValue(entry.getValue().lifeCappedAt(loot.get().lifeSeconds()));
                } else {
                    withoutLoot++;
                    missing.add(entry.getValue().lootId());
                }
            }
        }
        if (withoutLoot > 0) {
            warnings.warn(LifecycleWarning.lootMissing(withoutLoot, List.copyOf(missing)));
        }
        learnWorlds().handle((done, failure) -> null).thenRun(this::syncHolds);
    }

    /**
     * Applies every world's policy to its live boxes: a world that now holds chunks loaded gives its boxes tickets, and
     * one that now skips gives them back.
     */
    private void syncHolds() {
        synchronized (lock) {
            ConfigSnapshot snapshot = config;
            for (LootBox box : boxes.values()) {
                applyHold(box, ticketed(box.worldKey(), snapshot));
            }
        }
    }

    /**
     * Whether a block holds a loot box, or one a summon is placing there.
     */
    public boolean isLootBox(UUID worldKey, int x, int y, int z) {
        Key key = new Key(Objects.requireNonNull(worldKey, "worldKey"), x, y, z);
        return boxes.containsKey(key) || reserved.contains(key);
    }

    /**
     * The occupied check for the summon search: a block that holds a loot box, or one being summoned there.
     */
    public OccupiedCheck occupiedCheck() {
        return this::isLootBox;
    }

    /**
     * The live box at a block, as it is now.
     */
    public Optional<LootBox> boxAt(UUID worldKey, BlockPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        return Optional.ofNullable(boxes.get(Key.of(worldKey, position)));
    }

    /**
     * How many live boxes a world holds now, in loaded chunks or not. A summon that has only reserved its spot is not
     * a box yet; the summon service counts those. The world timer's spawn limit adds the two.
     */
    public int liveBoxCount(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        int count = 0;
        for (Key key : boxes.keySet()) {
            if (key.worldKey().equals(worldKey)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The id the server reports for a block, so a later changed-block check compares like with like.
     */
    public static String namespaced(String blockId) {
        String lower = blockId.trim().toLowerCase(Locale.ROOT);
        return lower.contains(":") ? lower : "minecraft:" + lower;
    }

    private record Key(UUID worldKey, int x, int y, int z) {

        static Key of(UUID worldKey, BlockPosition position) {
            return new Key(worldKey, position.x(), position.y(), position.z());
        }
    }

    /**
     * One box's shared inventory and the players who have it open. The viewers change only under the lock.
     */
    private static final class Viewing {

        private final Key key;
        private final UUID boxId;
        private final SharedInventory inventory;
        private final Set<UUID> viewers = new HashSet<>();

        private Viewing(Key key, UUID boxId, SharedInventory inventory) {
            this.key = key;
            this.boxId = boxId;
            this.inventory = inventory;
        }
    }
}
