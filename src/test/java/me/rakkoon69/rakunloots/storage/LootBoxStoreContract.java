package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.config.StorageSettings;
import me.rakkoon69.rakunloots.roll.RolledContents;
import me.rakkoon69.rakunloots.roll.RolledStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * One contract every loot box store adapter passes. Each adapter's test extends it and opens a fresh, empty store.
 */
abstract class LootBoxStoreContract {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

    private LootBoxStore store;

    /**
     * A fresh store with no boxes in it.
     */
    abstract LootBoxStore openStore() throws Exception;

    /**
     * Makes the storage behind the store from {@link #openStore()} fail every read and write, or work again, as
     * when a database server goes down and comes back.
     */
    abstract void setReachable(boolean reachable);

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        store = openStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    @Test
    void savedBoxLoadsWithSameRolledContentsIncludingMetaAndSlots() throws Exception {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("Ruby Blade");
        meta.setLore(List.of("first line", "second line"));
        // MockBukkit cannot serialize enchantments, so this meta sticks to what the mock round-trips.
        meta.setCustomModelData(7);
        sword.setItemMeta(meta);
        RolledContents contents = new RolledContents(List.of(
                new RolledStack(OptionalInt.of(4), sword),
                new RolledStack(OptionalInt.empty(), new ItemStack(Material.DIAMOND, 64)),
                new RolledStack(OptionalInt.empty(), new ItemStack(Material.DIAMOND, 10))
        ));
        LootBoxRecord box = box(UUID.randomUUID(), contents);

        store.save(box);

        LootBoxRecord loaded = loadOnly();
        List<RolledStack> stacks = loaded.contents().stacks();
        assertEquals(3, stacks.size());
        assertEquals(OptionalInt.of(4), stacks.get(0).slot());
        assertEquals(sword, stacks.get(0).item());
        assertEquals("Ruby Blade", stacks.get(0).item().getItemMeta().getDisplayName());
        assertEquals(List.of("first line", "second line"), stacks.get(0).item().getItemMeta().getLore());
        assertEquals(7, stacks.get(0).item().getItemMeta().getCustomModelData());
        assertEquals(OptionalInt.empty(), stacks.get(1).slot());
        assertEquals(new ItemStack(Material.DIAMOND, 64), stacks.get(1).item());
        assertEquals(OptionalInt.empty(), stacks.get(2).slot());
        assertEquals(new ItemStack(Material.DIAMOND, 10), stacks.get(2).item());
    }

    @Test
    void boxWithNoRolledContentsLoadsEmpty() throws Exception {
        store.save(box(UUID.randomUUID(), new RolledContents(List.of())));

        assertTrue(loadOnly().contents().stacks().isEmpty());
    }

    @Test
    void ownerRoundTripsAndBoxWithNoOwnerLoadsWithNone() throws Exception {
        UUID player = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID owned = UUID.randomUUID();
        UUID unowned = UUID.randomUUID();
        store.save(new LootBoxRecord(owned, "airdrops", SummonKind.UNKNOWN, Optional.of(player), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 60, oneDiamond()));
        store.save(new LootBoxRecord(unowned, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 5, 70, 5, "minecraft:barrel",
                OptionalInt.of(300), 60, oneDiamond()));

        Map<UUID, LootBoxRecord> loaded = loadById();
        assertEquals(Optional.of(player), loaded.get(owned).owner());
        assertEquals(Optional.empty(), loaded.get(unowned).owner());
    }

    /**
     * Acceptance: how a box was summoned is stored and read back, for each kind a box can have.
     */
    @Test
    void theSummonKindRoundTripsForEachKind() throws Exception {
        Map<SummonKind, UUID> saved = new java.util.EnumMap<>(SummonKind.class);
        int x = 0;
        for (SummonKind kind : SummonKind.values()) {
            UUID boxId = UUID.randomUUID();
            saved.put(kind, boxId);
            store.save(new LootBoxRecord(boxId, "airdrops", kind, Optional.empty(), WORLD, x++, 70, 0,
                    "minecraft:barrel", OptionalInt.of(300), 60, oneDiamond()));
        }

        Map<UUID, LootBoxRecord> loaded = loadById();
        for (SummonKind kind : SummonKind.values()) {
            assertEquals(kind, loaded.get(saved.get(kind)).source(), kind.name());
        }
    }

    @Test
    void remainingLifeRoundTripsAndUnendingLifeLoadsAsUnending() throws Exception {
        UUID ending = UUID.randomUUID();
        UUID unending = UUID.randomUUID();
        UUID ended = UUID.randomUUID();
        store.save(new LootBoxRecord(ending, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(1234), 0, oneDiamond()));
        store.save(new LootBoxRecord(unending, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 1, 70, 0, "minecraft:barrel",
                OptionalInt.empty(), 0, oneDiamond()));
        store.save(new LootBoxRecord(ended, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 2, 70, 0, "minecraft:barrel",
                OptionalInt.of(0), 0, oneDiamond()));

        Map<UUID, LootBoxRecord> loaded = loadById();
        assertEquals(OptionalInt.of(1234), loaded.get(ending).remainingLifeSeconds());
        assertEquals(OptionalInt.empty(), loaded.get(unending).remainingLifeSeconds());
        assertEquals(OptionalInt.of(0), loaded.get(ended).remainingLifeSeconds());
    }

    @Test
    void remainingLockRoundTripsAndZeroLoadsAsNoLock() throws Exception {
        UUID locked = UUID.randomUUID();
        UUID open = UUID.randomUUID();
        store.save(new LootBoxRecord(locked, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 45, oneDiamond()));
        store.save(new LootBoxRecord(open, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 1, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 0, oneDiamond()));

        Map<UUID, LootBoxRecord> loaded = loadById();
        assertEquals(45, loaded.get(locked).remainingLockSeconds());
        assertEquals(0, loaded.get(open).remainingLockSeconds());
    }

    @Test
    void locationAndLootIdRoundTrip() throws Exception {
        UUID boxId = UUID.randomUUID();
        UUID nether = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");
        store.save(new LootBoxRecord(boxId, "nether_treasure", SummonKind.UNKNOWN, Optional.empty(), nether, -30_000_000, -64, 29_999_999,
                "minecraft:barrel", OptionalInt.of(300), 0, oneDiamond()));

        LootBoxRecord loaded = loadOnly();
        assertEquals(boxId, loaded.boxId());
        assertEquals("nether_treasure", loaded.lootId());
        assertEquals(nether, loaded.worldId());
        assertEquals(-30_000_000, loaded.x());
        assertEquals(-64, loaded.y());
        assertEquals(29_999_999, loaded.z());
    }

    @Test
    void deletedBoxDoesNotLoadAgainAndOtherBoxesStay() throws Exception {
        UUID deleted = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        store.save(box(deleted, oneDiamond()));
        store.save(box(kept, oneDiamond()));

        store.delete(deleted);

        LootBoxRecord loaded = loadOnly();
        assertEquals(kept, loaded.boxId());
        assertEquals(1, loaded.contents().stacks().size());
    }

    @Test
    void deletingBoxThatIsNotStoredDoesNothing() throws Exception {
        store.save(box(UUID.randomUUID(), oneDiamond()));

        store.delete(UUID.randomUUID());

        assertEquals(1, loadRecords().size());
    }

    @Test
    void savingBoxAgainReplacesItsRowAndContents() throws Exception {
        UUID boxId = UUID.randomUUID();
        store.save(new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 60, new RolledContents(List.of(
                        new RolledStack(OptionalInt.of(0), new ItemStack(Material.DIAMOND, 5)),
                        new RolledStack(OptionalInt.empty(), new ItemStack(Material.EMERALD, 3))
                ))));

        store.save(new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(120), 0, new RolledContents(List.of(
                        new RolledStack(OptionalInt.of(9), new ItemStack(Material.EMERALD, 3))
                ))));

        LootBoxRecord loaded = loadOnly();
        assertEquals(OptionalInt.of(120), loaded.remainingLifeSeconds());
        assertEquals(0, loaded.remainingLockSeconds());
        assertEquals(1, loaded.contents().stacks().size());
        assertEquals(OptionalInt.of(9), loaded.contents().stacks().get(0).slot());
        assertEquals(new ItemStack(Material.EMERALD, 3), loaded.contents().stacks().get(0).item());
    }

    @Test
    void boxWithMissingLootLoadsFlaggedWithHighWarningAndItsRowStays() throws Exception {
        UUID stale = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        store.save(new LootBoxRecord(stale, "removed_loot", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 0, oneDiamond()));
        store.save(box(current, oneDiamond()));

        LoadResult result = store.loadAll(unendingLoots(), world -> true);

        Map<UUID, LoadedBox> loaded = byId(result);
        assertTrue(loaded.get(stale).lootMissing());
        assertFalse(loaded.get(stale).worldNotLoaded());
        assertFalse(loaded.get(current).lootMissing());
        assertEquals(1, result.warnings().size());
        LootBoxWarning warning = result.warnings().get(0);
        assertEquals(LootBoxWarning.Kind.LOOT_MISSING, warning.kind());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        assertEquals(stale.toString(), warning.boxId());
        assertEquals(Optional.of("removed_loot"), warning.lootId());

        LoadResult again = store.loadAll(unendingLoots(), world -> true);
        assertTrue(byId(again).get(stale).lootMissing());
        assertEquals(1, again.warnings().size());
    }

    @Test
    void boxInUnloadedWorldLoadsFlaggedWithWarningAndItsRowStays() throws Exception {
        UUID unloadedWorld = UUID.fromString("00000000-0000-0000-0000-00000000cccc");
        UUID away = UUID.randomUUID();
        UUID here = UUID.randomUUID();
        store.save(new LootBoxRecord(away, "airdrops", SummonKind.UNKNOWN, Optional.empty(), unloadedWorld, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 0, oneDiamond()));
        store.save(box(here, oneDiamond()));

        LoadResult result = store.loadAll(unendingLoots(), WORLD::equals);

        Map<UUID, LoadedBox> loaded = byId(result);
        assertTrue(loaded.get(away).worldNotLoaded());
        assertFalse(loaded.get(away).lootMissing());
        assertFalse(loaded.get(here).worldNotLoaded());
        assertEquals(1, result.warnings().size());
        LootBoxWarning warning = result.warnings().get(0);
        assertEquals(LootBoxWarning.Kind.WORLD_NOT_LOADED, warning.kind());
        assertEquals(away.toString(), warning.boxId());

        LoadResult worldBack = store.loadAll(unendingLoots(), world -> true);
        assertFalse(byId(worldBack).get(away).worldNotLoaded());
        assertTrue(worldBack.warnings().isEmpty());
    }

    @Test
    void shorterCurrentLifeCapsRemainingLifeAndLongerOrUnendingLeavesIt() throws Exception {
        UUID shortened = UUID.randomUUID();
        UUID unendingShortened = UUID.randomUUID();
        UUID lengthened = UUID.randomUUID();
        UUID nowUnending = UUID.randomUUID();
        store.save(new LootBoxRecord(shortened, "short", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(500), 0, oneDiamond()));
        store.save(new LootBoxRecord(unendingShortened, "short", SummonKind.UNKNOWN, Optional.empty(), WORLD, 1, 70, 0, "minecraft:barrel",
                OptionalInt.empty(), 0, oneDiamond()));
        store.save(new LootBoxRecord(lengthened, "long", SummonKind.UNKNOWN, Optional.empty(), WORLD, 2, 70, 0, "minecraft:barrel",
                OptionalInt.of(500), 0, oneDiamond()));
        store.save(new LootBoxRecord(nowUnending, "endless", SummonKind.UNKNOWN, Optional.empty(), WORLD, 3, 70, 0, "minecraft:barrel",
                OptionalInt.of(500), 0, oneDiamond()));

        LoadResult result = store.loadAll(lootsWithLife(Map.of("short", 120, "long", 9000, "endless", -1)),
                world -> true);

        Map<UUID, LoadedBox> loaded = byId(result);
        assertEquals(OptionalInt.of(120), loaded.get(shortened).box().remainingLifeSeconds());
        assertEquals(OptionalInt.of(120), loaded.get(unendingShortened).box().remainingLifeSeconds());
        assertEquals(OptionalInt.of(500), loaded.get(lengthened).box().remainingLifeSeconds());
        assertEquals(OptionalInt.of(500), loaded.get(nowUnending).box().remainingLifeSeconds());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void boxWithMissingLootKeepsItsRemainingLife() throws Exception {
        UUID boxId = UUID.randomUUID();
        store.save(new LootBoxRecord(boxId, "removed_loot", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(4000), 0, oneDiamond()));

        LoadResult result = store.loadAll(lootsWithLife(Map.of("airdrops", 10)), world -> true);

        assertEquals(OptionalInt.of(4000), byId(result).get(boxId).box().remainingLifeSeconds());
    }

    @Test
    void stackThatCannotBeReadIsSkippedWithHighWarningAndTheRestOfTheBoxLoads() throws Exception {
        UUID boxId = UUID.randomUUID();
        store.save(new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 0, new RolledContents(List.of(
                        new RolledStack(OptionalInt.of(1), new ItemStack(Material.DIAMOND, 5)),
                        new RolledStack(OptionalInt.of(2), namedSword()),
                        new RolledStack(OptionalInt.empty(), new ItemStack(Material.EMERALD, 3))
                ))));
        StreamableServerMock.forgetItemMeta();

        LoadResult result = store.loadAll(unendingLoots(), world -> true);

        assertEquals(1, result.boxes().size());
        List<RolledStack> stacks = result.boxes().get(0).box().contents().stacks();
        assertEquals(2, stacks.size());
        assertEquals(OptionalInt.of(1), stacks.get(0).slot());
        assertEquals(new ItemStack(Material.DIAMOND, 5), stacks.get(0).item());
        assertEquals(OptionalInt.empty(), stacks.get(1).slot());
        assertEquals(new ItemStack(Material.EMERALD, 3), stacks.get(1).item());
        assertEquals(1, result.warnings().size());
        LootBoxWarning warning = result.warnings().get(0);
        assertEquals(LootBoxWarning.Kind.UNREADABLE_STACK, warning.kind());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        assertEquals(boxId.toString(), warning.boxId());
    }

    @Test
    void unreadableStackKeepsItsBytesUntilTheBoxIsSavedAgain() throws Exception {
        UUID kept = UUID.randomUUID();
        UUID resaved = UUID.randomUUID();
        RolledContents contents = new RolledContents(List.of(
                new RolledStack(OptionalInt.empty(), new ItemStack(Material.DIAMOND, 5)),
                new RolledStack(OptionalInt.of(2), namedSword())
        ));
        store.save(box(kept, contents));
        store.save(box(resaved, contents));
        StreamableServerMock.forgetItemMeta();
        Map<UUID, LoadedBox> whileUnreadable = byId(store.loadAll(unendingLoots(), world -> true));
        store.save(whileUnreadable.get(resaved).box());

        StreamableServerMock.rememberItemMeta();
        LoadResult result = store.loadAll(unendingLoots(), world -> true);

        Map<UUID, LoadedBox> loaded = byId(result);
        List<RolledStack> keptStacks = loaded.get(kept).box().contents().stacks();
        assertEquals(2, keptStacks.size());
        assertEquals(namedSword(), keptStacks.get(1).item());
        assertEquals(OptionalInt.of(2), keptStacks.get(1).slot());
        assertEquals(1, loaded.get(resaved).box().contents().stacks().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void changedBoxIsWrittenOnFlushAndUnchangedBoxIsNotRewritten() throws Exception {
        UUID unchanged = UUID.randomUUID();
        UUID changed = UUID.randomUUID();
        store.save(box(unchanged, oneDiamond()));
        store.save(box(changed, oneDiamond()));

        store.markChanged(box(changed, new RolledContents(List.of())));

        assertEquals(1, loadById().get(changed).contents().stacks().size());
        FlushResult result = store.flush(Runnable::run).get();
        assertEquals(Set.of(changed), result.written());
        assertTrue(result.failed().isEmpty());
        assertTrue(result.warnings().isEmpty());
        Map<UUID, LootBoxRecord> loaded = loadById();
        assertTrue(loaded.get(changed).contents().stacks().isEmpty());
        assertEquals(1, loaded.get(unchanged).contents().stacks().size());

        assertTrue(store.flush(Runnable::run).get().written().isEmpty());
    }

    @Test
    void flushWritesOnTheGivenExecutorNotTheCallingThread() throws Exception {
        UUID boxId = UUID.randomUUID();
        List<Runnable> queued = new ArrayList<>();
        store.markChanged(box(boxId, oneDiamond()));

        CompletableFuture<FlushResult> flushing = store.flush(queued::add);

        assertFalse(flushing.isDone());
        assertTrue(loadRecords().isEmpty());
        queued.forEach(Runnable::run);
        assertEquals(Set.of(boxId), flushing.get().written());
        assertEquals(boxId, loadOnly().boxId());
    }

    @Test
    void flushWritesTheLatestMarkedStateOfABox() throws Exception {
        UUID boxId = UUID.randomUUID();
        List<Runnable> queued = new ArrayList<>();
        store.markChanged(new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 60, oneDiamond()));
        CompletableFuture<FlushResult> flushing = store.flush(queued::add);
        store.markChanged(new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(250), 10, new RolledContents(List.of())));

        queued.forEach(Runnable::run);

        assertEquals(Set.of(boxId), flushing.get().written());
        LootBoxRecord loaded = loadOnly();
        assertEquals(OptionalInt.of(250), loaded.remainingLifeSeconds());
        assertEquals(10, loaded.remainingLockSeconds());
        assertTrue(loaded.contents().stacks().isEmpty());
        assertTrue(store.flush(Runnable::run).get().written().isEmpty());
    }

    @Test
    void deletedBoxIsNotWrittenBackByAPendingChange() throws Exception {
        UUID boxId = UUID.randomUUID();
        store.save(box(boxId, oneDiamond()));
        store.markChanged(box(boxId, new RolledContents(List.of())));

        store.delete(boxId);

        assertTrue(store.flush(Runnable::run).get().written().isEmpty());
        assertTrue(loadRecords().isEmpty());
    }

    @Test
    void failingStoreWarnsSevereKeepsTheChangePendingAndTheNextFlushRetriesIt() throws Exception {
        UUID boxId = UUID.randomUUID();
        store.markChanged(box(boxId, oneDiamond()));
        setReachable(false);

        FlushResult failed = store.flush(Runnable::run).get();

        assertTrue(failed.written().isEmpty());
        assertEquals(Set.of(boxId), failed.failed());
        assertEquals(1, failed.warnings().size());
        LootBoxWarning warning = failed.warnings().get(0);
        assertEquals(LootBoxWarning.Kind.FLUSH_FAILED, warning.kind());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.SEVERE, warning.severity());
        assertEquals(boxId.toString(), warning.boxId());
        assertTrue(warning.cause().isPresent());

        setReachable(true);
        FlushResult retried = store.flush(Runnable::run).get();

        assertEquals(Set.of(boxId), retried.written());
        assertTrue(retried.failed().isEmpty());
        assertEquals(boxId, loadOnly().boxId());
    }

    @Test
    void placedBlockIdRoundTrips() throws Exception {
        UUID barrel = UUID.randomUUID();
        UUID head = UUID.randomUUID();
        store.save(new LootBoxRecord(barrel, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 0, oneDiamond()));
        store.markChanged(new LootBoxRecord(head, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 1, 70, 0,
                "minecraft:player_head", OptionalInt.of(300), 0, oneDiamond()));
        store.flush(Runnable::run).get();

        Map<UUID, LootBoxRecord> loaded = loadById();
        assertEquals("minecraft:barrel", loaded.get(barrel).placedBlockId());
        assertEquals("minecraft:player_head", loaded.get(head).placedBlockId());
    }

    @Test
    void cappedLifeKeepsThePlacedBlockId() throws Exception {
        store.save(new LootBoxRecord(UUID.randomUUID(), "short", SummonKind.UNKNOWN, Optional.empty(), WORLD, 0, 70, 0,
                "minecraft:beacon", OptionalInt.of(500), 0, oneDiamond()));

        LoadResult result = store.loadAll(lootsWithLife(Map.of("short", 120)), world -> true);

        LootBoxRecord loaded = records(result).get(0);
        assertEquals(OptionalInt.of(120), loaded.remainingLifeSeconds());
        assertEquals("minecraft:beacon", loaded.placedBlockId());
    }

    @Test
    void queuedDeleteDoesNotWaitForTheStore() throws Exception {
        UUID boxId = UUID.randomUUID();
        CompletableFuture<Void> queued;
        // Every adapter is the JDBC store, whose writes hold its own lock. Holding that lock stands in for a flush
        // or save in progress; a queued delete must return without waiting for it.
        synchronized (store) {
            queued = CompletableFuture.runAsync(() -> store.queueDelete(boxId));
            try {
                queued.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                fail("queueDelete waited for the store lock");
            }
        }
        assertTrue(queued.isDone());
    }

    @Test
    void queuedDeleteWritesNothingUntilTheNextFlushAndThenTheBoxNoLongerLoads() throws Exception {
        UUID deleted = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        store.save(box(deleted, oneDiamond()));
        store.save(box(kept, oneDiamond()));

        store.queueDelete(deleted);

        assertEquals(Set.of(deleted, kept), loadById().keySet());

        FlushResult result = store.flush(Runnable::run).get();

        assertEquals(Set.of(deleted), result.deleted());
        assertTrue(result.written().isEmpty());
        assertTrue(result.failed().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertEquals(Set.of(kept), loadById().keySet());
        assertTrue(store.flush(Runnable::run).get().deleted().isEmpty());
    }

    @Test
    void changePendingForBoxQueuedForDeleteIsDroppedAndNeverWritten() throws Exception {
        UUID markedBefore = UUID.randomUUID();
        UUID markedAfter = UUID.randomUUID();
        store.markChanged(box(markedBefore, oneDiamond()));
        store.queueDelete(markedBefore);
        store.queueDelete(markedAfter);
        store.markChanged(box(markedAfter, oneDiamond()));

        FlushResult result = store.flush(Runnable::run).get();

        assertTrue(result.written().isEmpty());
        assertTrue(result.failed().isEmpty());
        assertTrue(loadRecords().isEmpty());
        assertTrue(store.flush(Runnable::run).get().written().isEmpty());
        assertTrue(loadRecords().isEmpty());
    }

    @Test
    void queuedDeleteThatFailsWarnsSevereAndStaysPendingForTheNextFlush() throws Exception {
        UUID boxId = UUID.randomUUID();
        store.save(box(boxId, oneDiamond()));
        store.queueDelete(boxId);
        setReachable(false);

        FlushResult failed = store.flush(Runnable::run).get();

        assertTrue(failed.deleted().isEmpty());
        assertEquals(Set.of(boxId), failed.failed());
        assertEquals(1, failed.warnings().size());
        LootBoxWarning warning = failed.warnings().get(0);
        assertEquals(LootBoxWarning.Kind.FLUSH_FAILED, warning.kind());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.SEVERE, warning.severity());
        assertEquals(boxId.toString(), warning.boxId());
        assertTrue(warning.cause().isPresent());

        setReachable(true);
        assertEquals(boxId, loadOnly().boxId());
        FlushResult retried = store.flush(Runnable::run).get();

        assertEquals(Set.of(boxId), retried.deleted());
        assertTrue(retried.failed().isEmpty());
        assertTrue(loadRecords().isEmpty());
    }

    /**
     * A config whose loots named in this suite all have unending life, so no remaining life is capped.
     */
    static ConfigSnapshot unendingLoots() {
        return lootsWithLife(Map.of("airdrops", -1, "nether_treasure", -1));
    }

    /**
     * A config holding these loots, each with the given life in seconds. A negative life is unending.
     */
    static ConfigSnapshot lootsWithLife(Map<String, Integer> lifeByLoot) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n");
        lifeByLoot.forEach((lootId, life) -> sources.loot(lootId, """
                language: en
                open_mode: drop
                block: barrel
                life: %d
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """.formatted(life)));
        return ConfigLoader.load(sources.build(), written -> Optional.of(written), WarningKinds.all());
    }

    /**
     * The storage settings a {@code storage} block of these lines loads to.
     */
    static StorageSettings storageSettings(String block) {
        StringBuilder globals = new StringBuilder("language: en\nstorage:\n");
        block.lines().forEach(line -> globals.append("  ").append(line).append('\n'));
        return ConfigLoader.load(ConfigSources.builder()
                .globals(globals.toString())
                .language("en", "still_locked: locked\n")
                .build(), written -> Optional.of(written), WarningKinds.all()).storage();
    }

    /**
     * The text as a YAML single-quoted scalar, so it loads as written.
     */
    static String quoted(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /**
     * A box of the loot {@code airdrops} with no rolled contents, in a world of its own.
     */
    static LootBoxRecord emptyBox(UUID boxId) {
        return new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), UUID.randomUUID(), 0, 70, 0, "minecraft:barrel",
                OptionalInt.of(300), 0, new RolledContents(List.of()));
    }

    static List<LootBoxRecord> records(LoadResult result) {
        List<LootBoxRecord> records = new ArrayList<>();
        for (LoadedBox loaded : result.boxes()) {
            records.add(loaded.box());
        }
        return records;
    }

    private static Map<UUID, LoadedBox> byId(LoadResult result) {
        Map<UUID, LoadedBox> byId = new HashMap<>();
        for (LoadedBox loaded : result.boxes()) {
            byId.put(loaded.box().boxId(), loaded);
        }
        return byId;
    }

    @Test
    void addingUnclaimedSummonItemsOfTheSameLootRaisesTheAmountAndAnotherLootOrPlayerKeepsItsOwnRow()
            throws Exception {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        store.addUnclaimedSummonItems(player, "airdrops", 2);
        store.addUnclaimedSummonItems(player, "airdrops", 3);
        store.addUnclaimedSummonItems(player, "nether_treasure", 1);
        store.addUnclaimedSummonItems(other, "airdrops", 7);

        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 5, false),
                        new UnclaimedSummonItem("nether_treasure", 1, false)),
                store.unclaimedSummonItems(player, unendingLoots()));
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 7, false)),
                store.unclaimedSummonItems(other, unendingLoots()));
        assertTrue(store.unclaimedSummonItems(UUID.randomUUID(), unendingLoots()).isEmpty());
    }

    @Test
    void claimingLowersTheStoredAmountAndDeletesTheRowAtZero() throws Exception {
        UUID player = UUID.randomUUID();
        store.addUnclaimedSummonItems(player, "airdrops", 5);

        assertEquals(2, store.claimUnclaimedSummonItems(player, "airdrops", 2));
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 3, false)),
                store.unclaimedSummonItems(player, unendingLoots()));

        assertEquals(3, store.claimUnclaimedSummonItems(player, "airdrops", 3));
        assertTrue(store.unclaimedSummonItems(player, unendingLoots()).isEmpty());
    }

    @Test
    void claimingMoreThanIsStoredLowersToZeroAndClaimingWhatIsNotOwedLowersNothing() throws Exception {
        UUID player = UUID.randomUUID();
        store.addUnclaimedSummonItems(player, "airdrops", 4);

        assertEquals(4, store.claimUnclaimedSummonItems(player, "airdrops", 10));
        assertEquals(0, store.claimUnclaimedSummonItems(player, "airdrops", 1));
        assertEquals(0, store.claimUnclaimedSummonItems(UUID.randomUUID(), "airdrops", 1));
        assertTrue(store.unclaimedSummonItems(player, unendingLoots()).isEmpty());
    }

    @Test
    void anAmountBelowOneIsRefused() {
        UUID player = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> store.addUnclaimedSummonItems(player, "airdrops", 0));
        assertThrows(IllegalArgumentException.class, () -> store.claimUnclaimedSummonItems(player, "airdrops", 0));
    }

    @Test
    void anUnclaimedRowWhoseLootIsMissingIsFlaggedAndKept() throws Exception {
        UUID player = UUID.randomUUID();
        store.addUnclaimedSummonItems(player, "deleted_loot", 3);
        store.addUnclaimedSummonItems(player, "airdrops", 1);

        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 1, false),
                        new UnclaimedSummonItem("deleted_loot", 3, true)),
                store.unclaimedSummonItems(player, unendingLoots()));
        // Reading again changes nothing, and the loot coming back clears the flag.
        assertEquals(List.of(new UnclaimedSummonItem("deleted_loot", 3, false)),
                store.unclaimedSummonItems(player, lootsWithLife(Map.of("deleted_loot", -1))).stream()
                        .filter(item -> item.lootId().equals("deleted_loot")).toList());
    }

    @Test
    void unclaimedSummonItemsOfAnUnreachableStoreFailAndStayIntactOnceReachable() throws Exception {
        UUID player = UUID.randomUUID();
        store.addUnclaimedSummonItems(player, "airdrops", 2);

        setReachable(false);
        assertThrows(LootBoxStoreException.class, () -> store.claimUnclaimedSummonItems(player, "airdrops", 1));
        assertThrows(LootBoxStoreException.class, () -> store.addUnclaimedSummonItems(player, "airdrops", 1));
        assertThrows(LootBoxStoreException.class, () -> store.unclaimedSummonItems(player, unendingLoots()));
        setReachable(true);

        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 2, false)),
                store.unclaimedSummonItems(player, unendingLoots()));
    }

    private List<LootBoxRecord> loadRecords() throws Exception {
        return records(store.loadAll(unendingLoots(), world -> true));
    }

    private Map<UUID, LootBoxRecord> loadById() throws Exception {
        Map<UUID, LootBoxRecord> byId = new HashMap<>();
        for (LootBoxRecord box : loadRecords()) {
            byId.put(box.boxId(), box);
        }
        return byId;
    }

    private static ItemStack namedSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("Ruby Blade");
        sword.setItemMeta(meta);
        return sword;
    }

    private static RolledContents oneDiamond() {
        return new RolledContents(List.of(new RolledStack(OptionalInt.empty(), new ItemStack(Material.DIAMOND))));
    }

    private LootBoxRecord loadOnly() throws Exception {
        List<LootBoxRecord> boxes = loadRecords();
        assertEquals(1, boxes.size());
        return boxes.get(0);
    }

    private static LootBoxRecord box(UUID boxId, RolledContents contents) {
        return new LootBoxRecord(boxId, "airdrops", SummonKind.UNKNOWN, Optional.empty(), WORLD, 10, 64, -20, "minecraft:barrel",
                OptionalInt.of(300), 0, contents);
    }
}
