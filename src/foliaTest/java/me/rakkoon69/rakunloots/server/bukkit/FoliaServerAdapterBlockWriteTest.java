package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import me.rakkoon69.rakunloots.server.ParticleSpec;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.SharedInventory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block-write contract on a hand-faked Folia server, plus Folia's threading rule: block work runs on the region
 * that owns the block, player work on the player's entity scheduler, and the Bukkit scheduler is never called.
 */
class FoliaServerAdapterBlockWriteTest extends BlockWriteContract {

    FakeFoliaServer fakeServer;

    @Override
    ServerAdapter openAdapter() {
        fakeServer = new FakeFoliaServer();
        return new FoliaServerAdapter(fakeServer.plugin());
    }

    @AfterEach
    void bukkitSchedulerWasNeverCalled() {
        try {
            assertEquals(List.of(), fakeServer.bukkitSchedulerCalls(), "the Bukkit scheduler was called");
        } finally {
            fakeServer.shutdown();
        }
    }

    @Override
    UUID createWorld(int minHeight, int maxHeight) {
        return fakeServer.createWorld(minHeight, maxHeight);
    }

    @Override
    void setChunkState(UUID world, int chunkX, int chunkZ, ChunkState state) {
        fakeServer.world(world).setChunkState(chunkX, chunkZ, state);
    }

    @Override
    ChunkState chunkState(UUID world, int chunkX, int chunkZ) {
        return fakeServer.world(world).chunkState(chunkX, chunkZ);
    }

    @Override
    void setBlock(UUID world, BlockPosition position, String blockId) {
        fakeServer.setBlock(world, position.x(), position.y(), position.z(), blockId);
    }

    @Override
    String blockId(UUID world, BlockPosition position) {
        return fakeServer.world(world).blockType(position.x(), position.y(), position.z()).getKey().toString();
    }

    @Override
    Set<ChunkPosition> chunkTickets(UUID world) {
        return fakeServer.world(world).chunkTickets();
    }

    @Override
    Optional<URL> headSkin(UUID world, BlockPosition position) {
        return fakeServer.world(world).headProfile(position.x(), position.y(), position.z())
                .map(PlayerProfile::getTextures)
                .map(PlayerTextures::getSkin);
    }

    @Override
    List<Drop> drops(UUID world) {
        return fakeServer.world(world).drops().stream()
                .map(drop -> new Drop(block(drop.location()), drop.stack().getType().getKey().toString(),
                        drop.stack().getAmount()))
                .toList();
    }

    @Override
    List<SpawnedParticle> particles(UUID world) {
        return fakeServer.world(world).particles();
    }

    @Override
    UUID addPlayer(UUID world, double x, double y, double z) {
        return fakeServer.addPlayer(world, x, y, z);
    }

    @Override
    Optional<Inventory> topInventory(UUID player) {
        return Optional.ofNullable(fakeServer.player(player).topInventory());
    }

    @Override
    void fillStorage(UUID player, ItemStack filler, int emptySlots) {
        fakeServer.player(player).fillStorage(filler, emptySlots);
    }

    @Override
    int storedIn(UUID player, ItemStack item) {
        return fakeServer.player(player).stored(item);
    }

    @Override
    List<String> messages(UUID player) {
        return fakeServer.player(player).messages().stream().map(FakeFoliaPlayer.Message::text).toList();
    }

    @Override
    <T> T runUntilDone(CompletableFuture<T> future) throws Exception {
        return fakeServer.runUntilDone(future);
    }

    /**
     * Runs the work in a task on the region that owns the block, as an event at that block runs.
     */
    @Override
    <T> T onThreadThatOwns(UUID world, BlockPosition position, Supplier<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        fakeServer.regionScheduler().execute(fakeServer.plugin(), fakeServer.world(world).world(),
                position.x() >> 4, position.z() >> 4, () -> {
                    try {
                        result.complete(work.get());
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    }
                });
        return runUntilDone(result);
    }

    @Test
    void blockWorkRunsOnTheRegionThatOwnsTheBlock() throws Exception {
        UUID worldKey = loadedWorld();
        FakeFoliaWorld world = fakeServer.world(worldKey);
        FakeFoliaTicks.Region region = new FakeFoliaTicks.Region(worldKey, CHUNK_X, CHUNK_Z);
        record Case(String name, Supplier<CompletableFuture<?>> work) {
        }
        List<Case> cases = List.of(
                new Case("set", () -> adapter().setBlock(worldKey, BLOCK, "minecraft:player_head",
                        Optional.of(HEAD_DATA))),
                new Case("read", () -> adapter().readBlock(worldKey, BLOCK)),
                new Case("drop", () -> adapter().dropStacks(worldKey, BLOCK,
                        List.of(new ItemStack(Material.DIAMOND)))),
                new Case("replace", () -> adapter().replaceBlock(worldKey, BLOCK, "minecraft:player_head",
                        "minecraft:player_head", Optional.of(HEAD_DATA))),
                new Case("remove", () -> adapter().removeBlock(worldKey, BLOCK, "minecraft:player_head")),
                new Case("removeLoading", () -> adapter().removeBlockLoadingChunk(worldKey, BLOCK,
                        "minecraft:player_head")),
                new Case("hold", () -> adapter().holdChunk(worldKey, CHUNK_X, CHUNK_Z)),
                new Case("release", () -> adapter().releaseChunk(worldKey, CHUNK_X, CHUNK_Z)),
                new Case("particles", () -> adapter().drawParticles(worldKey, BLOCK, List.of(new ParticleDraw(
                        BLOCK.x(), BLOCK.y(), BLOCK.z(), new ParticleSpec("SMOKE_NORMAL", 1, Optional.empty()))))));

        for (Case each : cases) {
            int scheduledBefore = fakeServer.regionScheduler().scheduled().size();
            int callsBefore = world.calls().size();

            runUntilDone(each.work().get());

            List<FakeFoliaTicks.Region> scheduled = fakeServer.regionScheduler().scheduled()
                    .subList(scheduledBefore, fakeServer.regionScheduler().scheduled().size());
            // The async chunk load is the one call any thread may start, so it runs before the region task.
            List<FakeFoliaWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size()).stream()
                    .filter(call -> !call.method().equals("getChunkAtAsync")).toList();
            assertEquals(List.of(region), scheduled, each.name() + " ran one task on the region that owns the block");
            assertFalse(calls.isEmpty(), each.name() + " touched the block");
            for (FakeFoliaWorld.WorldCall call : calls) {
                assertEquals(Optional.of(region), call.owner(), each.name() + ": " + call);
            }
        }
        assertEquals("minecraft:air", blockId(worldKey, BLOCK), "every task ran");
    }

    /**
     * Folia's threading rule for chunk tickets: a ticket is added and removed in a task on the region that owns the
     * chunk, also for a chunk that was not loaded, which is loaded first by the async chunk call, and no task is
     * scheduled for a chunk that is not loaded.
     */
    @Test
    void aChunkTicketIsAddedAndRemovedOnTheRegionThatOwnsTheChunkAfterAnUnloadedChunkLoads() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeFoliaWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);
        FakeFoliaTicks.Region region = new FakeFoliaTicks.Region(worldKey, CHUNK_X, CHUNK_Z);
        int scheduledBefore = fakeServer.regionScheduler().scheduled().size();

        assertTrue(runUntilDone(adapter().holdChunk(worldKey, CHUNK_X, CHUNK_Z)));
        assertTrue(runUntilDone(adapter().releaseChunk(worldKey, CHUNK_X, CHUNK_Z)));

        List<FakeFoliaWorld.WorldCall> tickets = world.calls().stream()
                .filter(call -> call.method().endsWith("PluginChunkTicket")).toList();
        assertEquals(List.of("addPluginChunkTicket", "removePluginChunkTicket"),
                tickets.stream().map(FakeFoliaWorld.WorldCall::method).toList());
        for (FakeFoliaWorld.WorldCall call : tickets) {
            assertEquals(Optional.of(region), call.owner(), call.toString());
        }
        assertEquals(List.of(region, region), fakeServer.regionScheduler().scheduled()
                .subList(scheduledBefore, fakeServer.regionScheduler().scheduled().size()));
    }

    @Test
    void aRemoveThatLoadsTheChunkRunsItsBlockWorkOnTheRegionThatOwnsTheBlock() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeFoliaWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);
        setBlock(worldKey, BLOCK, "minecraft:barrel");
        FakeFoliaTicks.Region region = new FakeFoliaTicks.Region(worldKey, CHUNK_X, CHUNK_Z);
        int scheduledBefore = fakeServer.regionScheduler().scheduled().size();
        int callsBefore = world.calls().size();

        assertTrue(runUntilDone(adapter().removeBlockLoadingChunk(worldKey, BLOCK, "minecraft:barrel")));

        List<FakeFoliaWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size());
        assertEquals(List.of(region), fakeServer.regionScheduler().scheduled()
                .subList(scheduledBefore, fakeServer.regionScheduler().scheduled().size()));
        for (FakeFoliaWorld.WorldCall call : calls.stream().filter(each -> !each.method().equals("getChunkAtAsync"))
                .toList()) {
            assertEquals(Optional.of(region), call.owner(), call.toString());
        }
        assertEquals("minecraft:air", blockId(worldKey, BLOCK));
    }

    @Test
    void aStoppingPluginStillGivesItsChunkTicketBack() throws Exception {
        UUID worldKey = loadedWorld();
        assertTrue(runUntilDone(adapter().holdChunk(worldKey, CHUNK_X, CHUNK_Z)));
        fakeServer.disablePlugin();

        assertTrue(runUntilDone(adapter().releaseChunk(worldKey, CHUNK_X, CHUNK_Z)));
        assertEquals(Set.of(), chunkTickets(worldKey));
    }

    @Test
    void playerWorkRunsOnThePlayersEntityScheduler() throws Exception {
        UUID worldKey = loadedWorld();
        UUID first = addPlayer(worldKey, 0.5, 64.0, 0.5);
        UUID second = addPlayer(worldKey, 400.5, 64.0, 400.5);
        SharedInventory shared = adapter().createSharedInventory("Supply", 9, Map.of());

        runUntilDone(adapter().openSharedInventory(first, shared));
        runUntilDone(adapter().openSharedInventory(second, shared));
        runUntilDone(adapter().closeSharedInventory(shared, Set.of(first, second)));
        runUntilDone(adapter().sendMessage(first, "Still locked"));
        runUntilDone(adapter().sendMessage(second, "Still locked"));

        for (UUID player : List.of(first, second)) {
            assertEquals(List.of(new FakeFoliaPlayer.Message("Still locked",
                    Optional.of(new FakeFoliaTicks.Entity(player)))), fakeServer.player(player).messages());
            List<FakeFoliaPlayer.StateRead> calls = fakeServer.player(player).inventoryCalls();
            assertEquals(List.of("openInventory", "closeInventory"),
                    calls.stream().map(FakeFoliaPlayer.StateRead::method).toList());
            for (FakeFoliaPlayer.StateRead call : calls) {
                assertEquals(Optional.of(new FakeFoliaTicks.Entity(player)), call.owner(), call.toString());
            }
        }
    }

    @Test
    void aMultiLineMessageReachesThePlayerAsOneChatLinePerLineOnItsEntityScheduler() throws Exception {
        UUID worldKey = loadedWorld();
        UUID player = addPlayer(worldKey, 0.5, 64.0, 0.5);

        runUntilDone(adapter().sendMessage(player, "§aA loot spawned\nnear spawn"));

        Optional<FakeFoliaTicks.Owner> owner = Optional.of(new FakeFoliaTicks.Entity(player));
        assertEquals(List.of(new FakeFoliaPlayer.Message("§aA loot spawned", owner),
                new FakeFoliaPlayer.Message("§anear spawn", owner)), fakeServer.player(player).messages());
    }

    /**
     * Comparing two stacks reads the static Bukkit item factory, which this fake server never sets, so these two run
     * with a stand-in that only has one.
     */
    @Override
    @Test
    void inventoryRoomCountsEmptySlotsAndTheRoomLeftInStacksOfTheSameItem() throws Exception {
        try (ItemFactoryOnly factory = ItemFactoryOnly.install()) {
            super.inventoryRoomCountsEmptySlotsAndTheRoomLeftInStacksOfTheSameItem();
        }
    }

    @Override
    @Test
    void giveItemsGivesAsManyAsFitAndReportsHowMany() throws Exception {
        try (ItemFactoryOnly factory = ItemFactoryOnly.install()) {
            super.giveItemsGivesAsManyAsFitAndReportsHowMany();
        }
    }

    /**
     * The room and the give each read and change the player's inventory in a task on that player's entity scheduler.
     */
    @Test
    void inventoryWorkRunsOnThePlayersEntityScheduler() throws Exception {
        UUID worldKey = loadedWorld();
        UUID player = addPlayer(worldKey, 0.5, 64.0, 0.5);
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        runUntilDone(adapter().inventoryRoom(player, diamond));
        runUntilDone(adapter().giveItems(player, diamond, 3));

        List<Optional<FakeFoliaTicks.Owner>> owners = fakeServer.player(player).inventoryOwners();
        assertFalse(owners.isEmpty());
        for (Optional<FakeFoliaTicks.Owner> owner : owners) {
            assertEquals(Optional.of(new FakeFoliaTicks.Entity(player)), owner);
        }
    }

    /**
     * An open scheduled on the player's entity scheduler that has not run when the box's views close never runs its
     * open: the player never sees the inventory, even though they were not recorded as a viewer yet.
     */
    @Test
    void anOpenScheduledBeforeTheCloseNeverShowsTheInventory() throws Exception {
        UUID worldKey = loadedWorld();
        UUID player = addPlayer(worldKey, 0.5, 64.0, 0.5);
        SharedInventory shared = adapter().createSharedInventory("Supply", 9, Map.of());

        CompletableFuture<Boolean> open = adapter().openSharedInventory(player, shared);
        CompletableFuture<Void> close = adapter().closeSharedInventory(shared, Set.of(player));
        fakeServer.tick();
        fakeServer.tick();

        assertFalse(runUntilDone(open));
        runUntilDone(close);
        assertEquals(Optional.empty(), topInventory(player));
        assertEquals(List.of(), fakeServer.player(player).inventoryCalls().stream()
                .filter(call -> call.method().equals("openInventory")).toList());
    }

    private static BlockPosition block(Location location) {
        return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
