package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import me.rakkoon69.rakunloots.server.ParticleSpec;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.SharedInventory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block-write contract on a recording MockBukkit server, for the adapters that run on the Bukkit main thread. It
 * adds their threading rule: block work and player work each run in one main-thread task.
 */
abstract class MockBukkitBlockWriteTest extends BlockWriteContract {

    /**
     * Calls that touch a block or drop at one.
     */
    private static final Set<String> BLOCK_CALLS = Set.of("isChunkLoaded", "getBlockAt", "getType", "setType",
            "getState", "update", "dropItemNaturally", "spawnParticle", "addPluginChunkTicket");

    MockBukkitWorlds fakeServer;

    /**
     * The adapter under test, bound to the mock server.
     */
    abstract ServerAdapter newAdapter(Plugin plugin);

    @Override
    final ServerAdapter openAdapter() {
        fakeServer = MockBukkitWorlds.start();
        return newAdapter(fakeServer.plugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
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
                .map(textures -> textures.getSkin());
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
        InventoryView view = fakeServer.player(player).getOpenInventory();
        return view.getType() == InventoryType.CRAFTING ? Optional.empty() : Optional.ofNullable(view.getTopInventory());
    }

    @Override
    void fillStorage(UUID player, ItemStack filler, int emptySlots) {
        PlayerInventory inventory = fakeServer.player(player).getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = filler.clone();
            stack.setAmount(stack.getMaxStackSize());
            inventory.setItem(slot, slot < emptySlots ? null : stack);
        }
    }

    @Override
    int storedIn(UUID player, ItemStack item) {
        int total = 0;
        for (ItemStack stack : fakeServer.player(player).getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(item)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Override
    List<String> messages(UUID player) {
        return fakeServer.player(player).messageCalls().stream().map(RecordingPlayer.MessageCall::message).toList();
    }

    @Override
    <T> T runUntilDone(CompletableFuture<T> future) throws Exception {
        return fakeServer.runUntilDone(future);
    }

    /**
     * The test thread started the mock server, so it is the main thread, which owns every block.
     */
    @Override
    <T> T onThreadThatOwns(UUID world, BlockPosition position, Supplier<T> work) {
        return work.get();
    }

    /**
     * A player head's block item keeps the skin its head data names, and is otherwise plain. The Folia test source
     * set has no item factory, so item meta is checked on the MockBukkit adapters only.
     */
    @Test
    void aPlayerHeadBlockItemKeepsItsHeadData() throws Exception {
        ItemStack head = adapter().blockItem("minecraft:player_head", Optional.of(HEAD_DATA)).orElseThrow();

        assertEquals(Material.PLAYER_HEAD, head.getType());
        assertEquals(1, head.getAmount());
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        assertEquals(new URL(SKIN), meta.getOwnerProfile().getTextures().getSkin());
        assertFalse(meta.hasDisplayName(), "the block item is plain");
        assertFalse(meta.hasLore(), "the block item is plain");
        assertEquals(null, ((SkullMeta) adapter().blockItem("minecraft:player_head", Optional.empty()).orElseThrow()
                .getItemMeta()).getOwnerProfile(), "no head data, no skin");
    }

    /**
     * The shipped loot's presentation block, as the config loader gives it, is set and removed. The Folia test
     * source set has no YAML parser, so this runs on the MockBukkit adapters only.
     */
    @Test
    void theShippedLootsBlockIsSetAndRemoved() throws Exception {
        UUID world = loadedWorld();
        Loot loot = ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                return writtenId.startsWith("@") || writtenId.startsWith("#") ? Optional.empty()
                        : Optional.of(writtenId);
            }

            @Override
            public Set<String> tagMembers(String namespacedTag) {
                return Set.of();
            }
        }, WarningKinds.all()).loot("airdrops").orElseThrow();

        assertTrue(runUntilDone(adapter().setBlock(world, BLOCK, loot.block(), loot.headData())));
        String placed = runUntilDone(adapter().readBlock(world, BLOCK)).orElseThrow();
        assertEquals("minecraft:barrel", placed);
        assertTrue(runUntilDone(adapter().removeBlock(world, BLOCK, placed)));
        assertEquals("minecraft:air", blockId(world, BLOCK));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = MockBukkitBlockWriteTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void blockWorkRunsOnTheMainThreadInOneTask() throws Exception {
        UUID worldKey = loadedWorld();
        FakeChunkWorld world = fakeServer.world(worldKey);
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
                new Case("particles", () -> adapter().drawParticles(worldKey, BLOCK, List.of(new ParticleDraw(
                        BLOCK.x(), BLOCK.y(), BLOCK.z(), new ParticleSpec("SMOKE_NORMAL", 1, Optional.empty()))))));

        for (Case each : cases) {
            int callsBefore = world.calls().size();

            OptionalInt task = runInOneMainThreadTask(each.work().get());

            List<FakeChunkWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size()).stream()
                    .filter(call -> BLOCK_CALLS.contains(call.method())).toList();
            assertFalse(calls.isEmpty(), each.name() + " touched the block");
            for (FakeChunkWorld.WorldCall call : calls) {
                assertTrue(call.mainThread(), each.name() + ": " + call);
                assertEquals(task, call.task(), each.name() + ": " + call);
            }
        }
        assertEquals("minecraft:air", blockId(worldKey, BLOCK), "every task ran");
    }

    /**
     * A plugin that is stopping has no scheduler, so a release on the main thread gives the ticket back before it
     * returns. An unloaded chunk is held and removed from in a main-thread task that finishes the load.
     */
    @Test
    void releasingAChunkOnTheMainThreadRunsBeforeItReturns() throws Exception {
        UUID worldKey = loadedWorld();
        assertTrue(runUntilDone(adapter().holdChunk(worldKey, CHUNK_X, CHUNK_Z)));

        CompletableFuture<Boolean> released = adapter().releaseChunk(worldKey, CHUNK_X, CHUNK_Z);

        assertTrue(released.isDone());
        assertTrue(released.get());
        assertEquals(Set.of(), chunkTickets(worldKey));
    }

    @Test
    void anUnloadedChunkIsHeldAndRemovedFromOnTheMainThread() throws Exception {
        UUID worldKey = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        FakeChunkWorld world = fakeServer.world(worldKey);
        setChunkState(worldKey, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);
        setBlock(worldKey, BLOCK, "minecraft:barrel");
        int callsBefore = world.calls().size();

        assertTrue(runUntilDone(adapter().holdChunk(worldKey, CHUNK_X, CHUNK_Z)));
        assertTrue(runUntilDone(adapter().removeBlockLoadingChunk(worldKey, BLOCK, "minecraft:barrel")));

        List<FakeChunkWorld.WorldCall> calls = world.calls().subList(callsBefore, world.calls().size());
        assertFalse(calls.isEmpty());
        for (FakeChunkWorld.WorldCall call : calls) {
            assertTrue(call.mainThread(), call.toString());
        }
        assertEquals(Set.of(new ChunkPosition(CHUNK_X, CHUNK_Z)), chunkTickets(worldKey));
        assertEquals("minecraft:air", blockId(worldKey, BLOCK));
    }

    /**
     * A head whose skin cannot be applied leaves no stray player head: the block is set back to what it was, and the
     * failure reaches the caller.
     */
    @Test
    void aHeadWhoseSkinFailsIsSetBackToWhatTheBlockWas() throws Exception {
        UUID world = loadedWorld();
        setBlock(world, BLOCK, "minecraft:barrel");
        fakeServer.server().failPlayerProfiles();

        ExecutionException failure = assertThrows(ExecutionException.class, () -> runUntilDone(
                adapter().setBlock(world, BLOCK, "minecraft:player_head", Optional.of(HEAD_DATA))));

        assertTrue(failure.getCause() instanceof IllegalStateException, failure.toString());
        assertEquals("minecraft:barrel", blockId(world, BLOCK));
    }

    /**
     * An open another plugin cancels completes false, so the lifecycle can let the viewer go.
     */
    @Test
    void anOpenAnotherPluginCancelsCompletesFalse() throws Exception {
        UUID world = loadedWorld();
        UUID player = addPlayer(world, 0.5, 64.0, 0.5);
        fakeServer.player(player).refuseInventoryOpens();
        SharedInventory shared = adapter().createSharedInventory("Supply", 9, Map.of());

        assertFalse(runUntilDone(adapter().openSharedInventory(player, shared)));
        assertEquals(Optional.empty(), topInventory(player));
    }

    @Test
    void playerWorkRunsOnTheMainThreadInOneTask() throws Exception {
        UUID worldKey = loadedWorld();
        UUID player = addPlayer(worldKey, 0.5, 64.0, 0.5);
        RecordingPlayer recording = fakeServer.player(player);
        SharedInventory shared = adapter().createSharedInventory("Supply", 9, Map.of());

        for (Supplier<CompletableFuture<?>> work : List.<Supplier<CompletableFuture<?>>>of(
                () -> adapter().openSharedInventory(player, shared),
                () -> adapter().closeSharedInventory(shared, Set.of(player)))) {
            int callsBefore = recording.inventoryCalls().size();

            OptionalInt task = runInOneMainThreadTask(work.get());

            List<RecordingPlayer.InventoryCall> calls =
                    recording.inventoryCalls().subList(callsBefore, recording.inventoryCalls().size());
            assertFalse(calls.isEmpty(), "opened or closed the inventory");
            for (RecordingPlayer.InventoryCall call : calls) {
                assertTrue(call.mainThread(), call.toString());
                assertEquals(task, call.task(), call.toString());
            }
        }
        assertEquals(Optional.empty(), topInventory(player), "every task ran");

        OptionalInt task = runInOneMainThreadTask(adapter().sendMessage(player, "Still locked"));
        List<RecordingPlayer.MessageCall> messages = recording.messageCalls();
        assertEquals(1, messages.size(), "sent one message");
        assertTrue(messages.get(0).mainThread(), messages.get(0).toString());
        assertEquals(task, messages.get(0).task(), messages.get(0).toString());
    }

    /**
     * A client shows a line break inside one chat message as a single line, so each line of a multi-line message
     * reaches the player as its own chat line, and a color code carries on to the next line as in one message.
     */
    @Test
    void aMultiLineMessageReachesThePlayerAsOneChatLinePerLine() throws Exception {
        UUID worldKey = loadedWorld();
        UUID player = addPlayer(worldKey, 0.5, 64.0, 0.5);
        RecordingPlayer recording = fakeServer.player(player);

        runUntilDone(adapter().sendMessage(player, "§cfirst\nsecond\n\n§lthird"));

        assertEquals(List.of("§cfirst", "§csecond", "§c", "§c§lthird"),
                recording.messageCalls().stream().map(RecordingPlayer.MessageCall::message).toList());
    }

    /**
     * Runs the work and checks it ran as one main-thread task.
     *
     * @return that task
     */
    private OptionalInt runInOneMainThreadTask(CompletableFuture<?> work) throws Exception {
        RecordingServerMock server = fakeServer.server();
        int runsBefore = server.getScheduler().runs().size();

        runUntilDone(work);

        List<RecordingScheduler.TaskRun> runs = server.getScheduler().runs();
        assertEquals(runsBefore + 1, runs.size(), "ran one task");
        RecordingScheduler.TaskRun run = runs.get(runs.size() - 1);
        assertTrue(run.mainThread(), "the task ran on the main thread");
        return OptionalInt.of(run.task());
    }

    private static BlockPosition block(Location location) {
        return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
