package me.rakkoon69.rakunloots.server;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block writes, drops, and shared inventories every server adapter passes. Each adapter's test extends it with
 * its own fake server and adds its threading rule.
 */
abstract class BlockWriteContract {

    static final int MIN_HEIGHT = -64;
    static final int MAX_HEIGHT = 320;
    static final int CHUNK_X = -2;
    static final int CHUNK_Z = 3;
    /**
     * A block below zero in chunk (-2, 3).
     */
    static final BlockPosition BLOCK = new BlockPosition(CHUNK_X * 16 + 5, -10, CHUNK_Z * 16 + 9);
    static final String SKIN = "http://textures.minecraft.net/texture/5a0b07e36d2b5b4a5d24d9b5f1b1b1c3f0f3c2d6";
    /**
     * Head data as the old loot files wrote it: the base64 textures value that names the skin.
     */
    static final String HEAD_DATA = Base64.getEncoder().encodeToString(
            ("{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN + "\"}}}").getBytes(StandardCharsets.UTF_8));

    /**
     * One stack dropped into the world.
     *
     * @param block  the block the drop landed in
     * @param itemId the stack's namespaced item id
     */
    record Drop(BlockPosition block, String itemId, int amount) {
    }

    private ServerAdapter adapter;

    /**
     * Starts a fake server with no worlds and no players and returns the adapter under test, bound to it.
     */
    abstract ServerAdapter openAdapter();

    /**
     * Adds a loaded world with every chunk ungenerated and every block air.
     *
     * @return the new world's key
     */
    abstract UUID createWorld(int minHeight, int maxHeight);

    abstract void setChunkState(UUID world, int chunkX, int chunkZ, ChunkState state);

    abstract ChunkState chunkState(UUID world, int chunkX, int chunkZ);

    /**
     * Sets one block, whatever state its chunk is in.
     */
    abstract void setBlock(UUID world, BlockPosition position, String blockId);

    /**
     * The namespaced id of one block, read without loading its chunk.
     */
    abstract String blockId(UUID world, BlockPosition position);

    /**
     * The chunks the plugin holds with a chunk ticket now, read without loading a chunk.
     */
    abstract Set<ChunkPosition> chunkTickets(UUID world);

    /**
     * The skin of the player head at a block, or empty when it has none.
     */
    abstract Optional<URL> headSkin(UUID world, BlockPosition position);

    /**
     * Every stack dropped into the world so far, in order.
     */
    abstract List<Drop> drops(UUID world);

    /**
     * Every particle spawned in the world so far, in order.
     */
    abstract List<SpawnedParticle> particles(UUID world);

    /**
     * Adds an online player standing at a position in a world.
     *
     * @return the player's UUID
     */
    abstract UUID addPlayer(UUID world, double x, double y, double z);

    /**
     * The top inventory of the view the player has open, or empty when they see only their own inventory.
     */
    abstract Optional<Inventory> topInventory(UUID player);

    /**
     * Every chat message the player got so far, in order.
     */
    abstract List<String> messages(UUID player);

    /**
     * Fills the player's storage slots with full stacks of the filler, leaving the given number of slots empty.
     */
    abstract void fillStorage(UUID player, ItemStack filler, int emptySlots);

    /**
     * How many of the item the player's storage slots hold.
     */
    abstract int storedIn(UUID player, ItemStack item);

    /**
     * Runs the fake server until the future completes and returns its value.
     */
    abstract <T> T runUntilDone(CompletableFuture<T> future) throws Exception;

    /**
     * Runs the work on the thread that owns a block, as an event at that block runs, and returns its value.
     */
    abstract <T> T onThreadThatOwns(UUID world, BlockPosition position, Supplier<T> work) throws Exception;

    @BeforeEach
    void openContract() {
        adapter = openAdapter();
    }

    /**
     * The adapter from {@link #openAdapter()}.
     */
    ServerAdapter adapter() {
        return adapter;
    }

    /**
     * A world whose chunk (-2, 3) is loaded.
     */
    UUID loadedWorld() {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.LOADED);
        return world;
    }

    @Test
    void setReadAndRemoveABlockByNamespacedId() throws Exception {
        UUID world = loadedWorld();

        assertTrue(runUntilDone(adapter.setBlock(world, BLOCK, "minecraft:barrel", Optional.empty())));
        assertEquals("minecraft:barrel", blockId(world, BLOCK));
        assertEquals(Optional.of("minecraft:barrel"), runUntilDone(adapter.readBlock(world, BLOCK)));

        assertTrue(runUntilDone(adapter.removeBlock(world, BLOCK, "minecraft:barrel")));
        assertEquals("minecraft:air", blockId(world, BLOCK));
        assertEquals(Optional.of("minecraft:air"), runUntilDone(adapter.readBlock(world, BLOCK)));
    }

    @Test
    void readGivesTheBlockAFixtureSet() throws Exception {
        UUID world = loadedWorld();
        setBlock(world, BLOCK, "minecraft:deepslate");

        assertEquals(Optional.of("minecraft:deepslate"), runUntilDone(adapter.readBlock(world, BLOCK)));
    }

    @Test
    void removeLeavesAnotherBlockInPlace() throws Exception {
        UUID world = loadedWorld();
        setBlock(world, BLOCK, "minecraft:stone");

        assertFalse(runUntilDone(adapter.removeBlock(world, BLOCK, "minecraft:barrel")));

        assertEquals("minecraft:stone", blockId(world, BLOCK));
    }

    @Test
    void replaceSetsTheBlockOnlyWhileItIsStillTheExpectedBlock() throws Exception {
        UUID world = loadedWorld();
        setBlock(world, BLOCK, "minecraft:stone");

        assertFalse(runUntilDone(adapter.replaceBlock(world, BLOCK, "minecraft:air", "minecraft:barrel",
                Optional.empty())));
        assertEquals("minecraft:stone", blockId(world, BLOCK));

        assertTrue(runUntilDone(adapter.replaceBlock(world, BLOCK, "minecraft:stone", "minecraft:barrel",
                Optional.empty())));
        assertEquals("minecraft:barrel", blockId(world, BLOCK));
    }

    @Test
    void replaceWithAPlayerHeadKeepsItsHeadData() throws Exception {
        UUID world = loadedWorld();

        assertTrue(runUntilDone(adapter.replaceBlock(world, BLOCK, "minecraft:air", "minecraft:player_head",
                Optional.of(HEAD_DATA))));

        assertEquals("minecraft:player_head", blockId(world, BLOCK));
        assertEquals(Optional.of(new URL(SKIN)), headSkin(world, BLOCK));
    }

    @Test
    void blockWorkInAnUnloadedChunkMissesAndLoadsNothing() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        for (ChunkState unloaded : new ChunkState[]{ChunkState.GENERATED, ChunkState.UNGENERATED}) {
            setChunkState(world, CHUNK_X, CHUNK_Z, unloaded);
            setBlock(world, BLOCK, "minecraft:barrel");

            assertFalse(runUntilDone(adapter.setBlock(world, BLOCK, "minecraft:chest", Optional.empty())), "set");
            assertEquals(Optional.empty(), runUntilDone(adapter.readBlock(world, BLOCK)), "read");
            assertFalse(runUntilDone(adapter.removeBlock(world, BLOCK, "minecraft:barrel")), "remove");
            assertFalse(runUntilDone(adapter.replaceBlock(world, BLOCK, "minecraft:barrel", "minecraft:chest",
                    Optional.empty())), "replace");
            assertFalse(runUntilDone(adapter.dropStacks(world, BLOCK, List.of(new ItemStack(Material.DIAMOND)))),
                    "drop");

            assertEquals(unloaded, chunkState(world, CHUNK_X, CHUNK_Z));
            assertEquals("minecraft:barrel", blockId(world, BLOCK));
            assertEquals(List.of(), drops(world));
        }
    }

    /**
     * Acceptance: a remove that loads the chunk removes the block in a chunk that is generated but not loaded, and
     * nothing holds the chunk afterwards, so it unloads as any chunk does.
     */
    @Test
    void removeLoadingTheChunkRemovesABlockInAnUnloadedChunkAndHoldsNothing() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);
        setBlock(world, BLOCK, "minecraft:barrel");

        assertTrue(runUntilDone(adapter.removeBlockLoadingChunk(world, BLOCK, "minecraft:barrel")));

        assertEquals("minecraft:air", blockId(world, BLOCK));
        assertEquals(Set.of(), chunkTickets(world));
    }

    @Test
    void removeLoadingTheChunkInALoadedChunkRemovesTheBlockToo() throws Exception {
        UUID world = loadedWorld();
        setBlock(world, BLOCK, "minecraft:barrel");

        assertTrue(runUntilDone(adapter.removeBlockLoadingChunk(world, BLOCK, "minecraft:barrel")));

        assertEquals("minecraft:air", blockId(world, BLOCK));
    }

    @Test
    void removeLoadingTheChunkLeavesAnotherBlockInPlaceAndNeverGeneratesAChunk() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);
        setBlock(world, BLOCK, "minecraft:stone");

        assertFalse(runUntilDone(adapter.removeBlockLoadingChunk(world, BLOCK, "minecraft:barrel")));
        assertEquals("minecraft:stone", blockId(world, BLOCK));

        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.UNGENERATED);
        assertFalse(runUntilDone(adapter.removeBlockLoadingChunk(world, BLOCK, "minecraft:air")));
        assertEquals(ChunkState.UNGENERATED, chunkState(world, CHUNK_X, CHUNK_Z));
        assertFalse(runUntilDone(adapter.removeBlockLoadingChunk(UUID.randomUUID(), BLOCK, "minecraft:barrel")));
    }

    @Test
    void removeLoadingTheChunkRefusesAnIdThatIsNotABlock() {
        UUID world = loadedWorld();

        for (String id : List.of("minecraft:diamond", "minecraft:not_a_thing")) {
            assertThrows(IllegalArgumentException.class, () -> adapter.removeBlockLoadingChunk(world, BLOCK, id), id);
        }
    }

    /**
     * Acceptance: a held chunk is loaded with a plugin chunk ticket, whether it was loaded or only generated, and
     * releasing the hold gives the ticket back.
     */
    @Test
    void holdingAChunkKeepsItLoadedWithATicketUntilItIsReleased() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        ChunkPosition chunk = new ChunkPosition(CHUNK_X, CHUNK_Z);
        for (ChunkState before : new ChunkState[]{ChunkState.LOADED, ChunkState.GENERATED}) {
            setChunkState(world, CHUNK_X, CHUNK_Z, before);

            assertTrue(runUntilDone(adapter.holdChunk(world, CHUNK_X, CHUNK_Z)), before.toString());
            assertEquals(ChunkState.LOADED, chunkState(world, CHUNK_X, CHUNK_Z), before.toString());
            assertEquals(Set.of(chunk), chunkTickets(world), before.toString());

            assertTrue(runUntilDone(adapter.releaseChunk(world, CHUNK_X, CHUNK_Z)), before.toString());
            assertEquals(Set.of(), chunkTickets(world), before.toString());
        }
    }

    @Test
    void holdingAChunkTwiceIsOneHoldAndReleasingAChunkThatWasNotHeldMisses() throws Exception {
        UUID world = loadedWorld();

        assertFalse(runUntilDone(adapter.releaseChunk(world, CHUNK_X, CHUNK_Z)));
        assertTrue(runUntilDone(adapter.holdChunk(world, CHUNK_X, CHUNK_Z)));
        assertTrue(runUntilDone(adapter.holdChunk(world, CHUNK_X, CHUNK_Z)));
        assertEquals(Set.of(new ChunkPosition(CHUNK_X, CHUNK_Z)), chunkTickets(world));

        assertTrue(runUntilDone(adapter.releaseChunk(world, CHUNK_X, CHUNK_Z)));
        assertEquals(Set.of(), chunkTickets(world));
    }

    @Test
    void holdingAChunkThatIsNotGeneratedOrInAWorldThatIsNotLoadedMissesAndGeneratesNothing() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.UNGENERATED);

        assertFalse(runUntilDone(adapter.holdChunk(world, CHUNK_X, CHUNK_Z)));
        assertEquals(ChunkState.UNGENERATED, chunkState(world, CHUNK_X, CHUNK_Z));
        assertEquals(Set.of(), chunkTickets(world));
        assertFalse(runUntilDone(adapter.holdChunk(UUID.randomUUID(), CHUNK_X, CHUNK_Z)));
        assertFalse(runUntilDone(adapter.releaseChunk(UUID.randomUUID(), CHUNK_X, CHUNK_Z)));
    }

    @Test
    void blockWorkInAWorldThatIsNotLoadedMisses() throws Exception {
        loadedWorld();
        UUID unloaded = UUID.randomUUID();

        assertFalse(runUntilDone(adapter.setBlock(unloaded, BLOCK, "minecraft:chest", Optional.empty())));
        assertEquals(Optional.empty(), runUntilDone(adapter.readBlock(unloaded, BLOCK)));
        assertFalse(runUntilDone(adapter.removeBlock(unloaded, BLOCK, "minecraft:chest")));
        assertFalse(runUntilDone(adapter.replaceBlock(unloaded, BLOCK, "minecraft:air", "minecraft:chest",
                Optional.empty())));
        assertFalse(runUntilDone(adapter.dropStacks(unloaded, BLOCK, List.of(new ItemStack(Material.DIAMOND)))));
    }

    /**
     * Acceptance: particles draw through the adapter at their points, a dust particle with its colour and size. The
     * written names {@code REDSTONE} and {@code DUST} are one particle, and the 1.18 API this runs on names it
     * {@code REDSTONE}.
     */
    @Test
    void drawParticlesSpawnsEachPointWithItsParticle() throws Exception {
        UUID world = loadedWorld();
        ParticleSpec dust = new ParticleSpec("redstone", 2, Optional.of(new ParticleSpec.Dust(210, 60, 40, 1.5f)));
        ParticleSpec dustByTheNewName = new ParticleSpec("DUST", 1, Optional.of(new ParticleSpec.Dust(1, 2, 3, 1f)));
        ParticleSpec smoke = new ParticleSpec("SMOKE_NORMAL", 3, Optional.empty());

        assertTrue(runUntilDone(adapter().drawParticles(world, BLOCK, List.of(
                new ParticleDraw(-27.5, -9.5, 57.5, dust),
                new ParticleDraw(-26.0, -8.0, 58.0, dustByTheNewName),
                new ParticleDraw(-25.0, -7.0, 59.0, smoke)))));

        assertEquals(List.of(
                new SpawnedParticle(org.bukkit.Particle.REDSTONE, -27.5, -9.5, 57.5, 2, "210:60:40:1.5"),
                new SpawnedParticle(org.bukkit.Particle.REDSTONE, -26.0, -8.0, 58.0, 1, "1:2:3:1.0"),
                new SpawnedParticle(org.bukkit.Particle.SMOKE_NORMAL, -25.0, -7.0, 59.0, 3, "")), particles(world));
    }

    /**
     * Acceptance: nothing loads a chunk. A draw whose owner chunk is not loaded, or whose world is not, draws
     * nothing and completes false.
     */
    @Test
    void drawParticlesInAnUnloadedChunkOrWorldDrawsNothingAndLoadsNothing() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        ParticleDraw draw = new ParticleDraw(-27.5, -9.5, 57.5,
                new ParticleSpec("SMOKE_NORMAL", 1, Optional.empty()));
        for (ChunkState unloaded : new ChunkState[]{ChunkState.GENERATED, ChunkState.UNGENERATED}) {
            setChunkState(world, CHUNK_X, CHUNK_Z, unloaded);

            assertFalse(runUntilDone(adapter().drawParticles(world, BLOCK, List.of(draw))));

            assertEquals(unloaded, chunkState(world, CHUNK_X, CHUNK_Z));
            assertEquals(List.of(), particles(world));
        }
        assertFalse(runUntilDone(adapter().drawParticles(UUID.randomUUID(), BLOCK, List.of(draw))));
    }

    @Test
    void aParticleNameTheServerLacksIsRefusedBeforeAnythingIsDrawn() {
        UUID world = loadedWorld();

        assertThrows(IllegalArgumentException.class, () -> adapter().drawParticles(world, BLOCK, List.of(
                new ParticleDraw(0, 0, 0, new ParticleSpec("SMOKE_NORMAL", 1, Optional.empty())),
                new ParticleDraw(0, 0, 0, new ParticleSpec("NOT_A_PARTICLE", 1, Optional.empty())))));

        assertEquals(List.of(), particles(world));
    }

    @Test
    void readHereGivesTheBlockOnTheThreadThatOwnsIt() throws Exception {
        UUID world = loadedWorld();
        setBlock(world, BLOCK, "minecraft:deepslate");

        assertEquals(Optional.of("minecraft:deepslate"),
                onThreadThatOwns(world, BLOCK, () -> adapter.readBlockHere(world, BLOCK)));
    }

    @Test
    void readHereInAnUnloadedChunkMissesAndLoadsNothing() throws Exception {
        UUID world = createWorld(MIN_HEIGHT, MAX_HEIGHT);
        setChunkState(world, CHUNK_X, CHUNK_Z, ChunkState.GENERATED);
        setBlock(world, BLOCK, "minecraft:barrel");

        assertEquals(Optional.empty(), onThreadThatOwns(world, BLOCK, () -> adapter.readBlockHere(world, BLOCK)));

        assertEquals(ChunkState.GENERATED, chunkState(world, CHUNK_X, CHUNK_Z));
    }

    @Test
    void readHereInAWorldThatIsNotLoadedMisses() {
        loadedWorld();

        assertEquals(Optional.empty(), adapter.readBlockHere(UUID.randomUUID(), BLOCK));
    }

    /**
     * A thread that does not own the block, such as an async worker, is refused before the block is touched.
     */
    @Test
    void readHereOffTheThreadThatOwnsTheBlockIsRefused() throws Exception {
        UUID world = loadedWorld();
        setBlock(world, BLOCK, "minecraft:deepslate");
        CompletableFuture<Optional<String>> read = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                read.complete(adapter.readBlockHere(world, BLOCK));
            } catch (Throwable failure) {
                read.completeExceptionally(failure);
            }
        }, "not the owner");

        worker.start();
        worker.join();

        Throwable refused = assertThrows(ExecutionException.class, read::get).getCause();
        assertEquals(IllegalStateException.class, refused.getClass());
    }

    @Test
    void anIdThatIsNotABlockIsRefused() {
        UUID world = loadedWorld();

        for (String id : List.of("minecraft:not_a_block", "minecraft:diamond", "not an id")) {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.setBlock(world, BLOCK, id, Optional.empty()), id);
            assertThrows(IllegalArgumentException.class, () -> adapter.removeBlock(world, BLOCK, id), id);
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.replaceBlock(world, BLOCK, id, "minecraft:barrel", Optional.empty()), id);
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.replaceBlock(world, BLOCK, "minecraft:air", id, Optional.empty()), id);
        }
    }

    @Test
    void aPlayerHeadSetWithHeadDataKeepsThatHeadData() throws Exception {
        UUID world = loadedWorld();

        assertTrue(runUntilDone(adapter.setBlock(world, BLOCK, "minecraft:player_head", Optional.of(HEAD_DATA))));

        assertEquals("minecraft:player_head", blockId(world, BLOCK));
        assertEquals(Optional.of(new URL(SKIN)), headSkin(world, BLOCK));
    }

    @Test
    void aPlayerHeadSetWithoutHeadDataHasNoSkin() throws Exception {
        UUID world = loadedWorld();

        assertTrue(runUntilDone(adapter.setBlock(world, BLOCK, "minecraft:player_head", Optional.empty())));

        assertEquals("minecraft:player_head", blockId(world, BLOCK));
        assertEquals(Optional.empty(), headSkin(world, BLOCK));
    }

    @Test
    void headDataOnAnotherBlockOrWithoutASkinIsRefusedBeforeAnyWrite() {
        UUID world = loadedWorld();
        String noSkin = Base64.getEncoder().encodeToString("{\"textures\":{}}".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> adapter.setBlock(world, BLOCK, "minecraft:barrel", Optional.of(HEAD_DATA)));
        assertThrows(IllegalArgumentException.class, () -> adapter.replaceBlock(world, BLOCK, "minecraft:air",
                "minecraft:barrel", Optional.of(HEAD_DATA)));
        for (String bad : List.of("not base64 !", noSkin, Base64.getEncoder().encodeToString(new byte[]{'{'}))) {
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.setBlock(world, BLOCK, "minecraft:player_head", Optional.of(bad)), bad);
            assertThrows(IllegalArgumentException.class, () -> adapter.replaceBlock(world, BLOCK, "minecraft:air",
                    "minecraft:player_head", Optional.of(bad)), bad);
        }
        assertEquals("minecraft:air", blockId(world, BLOCK));
    }

    @Test
    void droppingStacksPutsEveryStackIntoTheWorldAtThatBlock() throws Exception {
        UUID world = loadedWorld();
        ItemStack diamonds = new ItemStack(Material.DIAMOND, 3);
        List<ItemStack> stacks = new ArrayList<>(List.of(diamonds, new ItemStack(Material.STONE, 64),
                new ItemStack(Material.AIR), new ItemStack(Material.STICK, 1)));

        CompletableFuture<Boolean> drop = adapter.dropStacks(world, BLOCK, stacks);
        diamonds.setAmount(1);
        stacks.clear();

        assertTrue(runUntilDone(drop));
        assertEquals(List.of(new Drop(BLOCK, "minecraft:diamond", 3), new Drop(BLOCK, "minecraft:stone", 64),
                new Drop(BLOCK, "minecraft:stick", 1)), drops(world));
    }

    @Test
    void aBlockItemIsThePlainBlockAndABlockWithoutAnItemHasNone() {
        ItemStack barrel = adapter.blockItem("minecraft:barrel", Optional.empty()).orElseThrow();

        assertEquals(Material.BARREL, barrel.getType());
        assertEquals(1, barrel.getAmount());
        assertEquals(Material.PLAYER_HEAD,
                adapter.blockItem("minecraft:player_head", Optional.empty()).orElseThrow().getType());
        assertEquals(Optional.empty(), adapter.blockItem("minecraft:water", Optional.empty()));
    }

    @Test
    void aBlockItemRefusesWhatABlockWriteRefuses() {
        for (String id : List.of("minecraft:not_a_block", "minecraft:diamond", "not an id")) {
            assertThrows(IllegalArgumentException.class, () -> adapter.blockItem(id, Optional.empty()), id);
        }
        assertThrows(IllegalArgumentException.class,
                () -> adapter.blockItem("minecraft:barrel", Optional.of(HEAD_DATA)));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.blockItem("minecraft:player_head", Optional.of("not base64 !")));
    }

    @Test
    void aMessageReachesAnOnlinePlayerAndAPlayerWhoIsNotOnlineGetsNone() throws Exception {
        UUID world = loadedWorld();
        UUID player = addPlayer(world, 0.5, 64.0, 0.5);

        assertTrue(runUntilDone(adapter.sendMessage(player, "§cStill locked")));
        assertFalse(runUntilDone(adapter.sendMessage(UUID.randomUUID(), "§cStill locked")));

        assertEquals(List.of("§cStill locked"), messages(player));
    }

    @Test
    void inventoryRoomCountsEmptySlotsAndTheRoomLeftInStacksOfTheSameItem() throws Exception {
        UUID world = loadedWorld();
        UUID player = addPlayer(world, 0.5, 64.0, 0.5);
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        assertEquals(36 * 64, runUntilDone(adapter().inventoryRoom(player, diamond)));
        assertEquals(36, runUntilDone(adapter().inventoryRoom(player, new ItemStack(Material.DIAMOND_SWORD))));

        fillStorage(player, new ItemStack(Material.STONE), 2);

        assertEquals(2 * 64, runUntilDone(adapter().inventoryRoom(player, diamond)));
        assertEquals(2 * 64, runUntilDone(adapter().inventoryRoom(player, new ItemStack(Material.STONE, 5))),
                "full stacks of the same item have no room, and the amount of the item is ignored");
        assertEquals(0, runUntilDone(adapter().inventoryRoom(UUID.randomUUID(), diamond)), "not online");
    }

    @Test
    void giveItemsGivesAsManyAsFitAndReportsHowMany() throws Exception {
        UUID world = loadedWorld();
        UUID player = addPlayer(world, 0.5, 64.0, 0.5);
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        assertEquals(100, runUntilDone(adapter().giveItems(player, diamond, 100)));
        assertEquals(100, storedIn(player, diamond));

        fillStorage(player, new ItemStack(Material.STONE), 1);

        assertEquals(64, runUntilDone(adapter().giveItems(player, diamond, 100)));
        assertEquals(64, storedIn(player, diamond));
        assertEquals(0, runUntilDone(adapter().giveItems(player, diamond, 5)), "no room left");
        assertEquals(0, runUntilDone(adapter().giveItems(UUID.randomUUID(), diamond, 5)), "not online");
        assertThrows(IllegalArgumentException.class, () -> adapter().giveItems(player, diamond, 0));
    }

    @Test
    void aSharedInventoryShowsToThePlayerAndOneTakeIsGoneForEveryViewer() throws Exception {
        UUID world = loadedWorld();
        UUID first = addPlayer(world, 0.5, 64.0, 0.5);
        UUID second = addPlayer(world, 1.5, 64.0, 1.5);
        SharedInventory shared = adapter.createSharedInventory("Supply", 27,
                Map.of(0, new ItemStack(Material.DIAMOND, 3), 13, new ItemStack(Material.STONE, 64)));

        assertTrue(runUntilDone(adapter.openSharedInventory(first, shared)));
        assertTrue(runUntilDone(adapter.openSharedInventory(second, shared)));

        Inventory firstView = topInventory(first).orElseThrow();
        Inventory secondView = topInventory(second).orElseThrow();
        assertSame(firstView, secondView, "both players see one inventory");
        assertEquals(27, firstView.getSize());
        assertEquals(Map.of(0, "minecraft:diamond x3", 13, "minecraft:stone x64"), slots(firstView));

        firstView.setItem(0, null);

        assertEquals(Map.of(13, "minecraft:stone x64"), slots(secondView));
        assertEquals(Map.of(13, "minecraft:stone x64"), describe(shared.contents()));
        assertEquals("Supply", shared.title());
        assertEquals(27, shared.size());
    }

    @Test
    void closingASharedInventoryClosesEveryViewerAndLeavesAPlayerWhoMovedOn() throws Exception {
        UUID world = loadedWorld();
        UUID first = addPlayer(world, 0.5, 64.0, 0.5);
        UUID second = addPlayer(world, 1.5, 64.0, 1.5);
        UUID movedOn = addPlayer(world, 2.5, 64.0, 2.5);
        SharedInventory shared = adapter.createSharedInventory("Supply", 9, Map.of(4, new ItemStack(Material.DIAMOND)));
        SharedInventory other = adapter.createSharedInventory("Other", 9, Map.of());
        for (UUID player : List.of(first, second, movedOn)) {
            assertTrue(runUntilDone(adapter.openSharedInventory(player, shared)));
        }
        Inventory sharedView = topInventory(first).orElseThrow();
        assertTrue(runUntilDone(adapter.openSharedInventory(movedOn, other)));
        Inventory otherView = topInventory(movedOn).orElseThrow();

        runUntilDone(adapter.closeSharedInventory(shared, Set.of(first, second, movedOn)));

        assertEquals(Optional.empty(), topInventory(first));
        assertEquals(Optional.empty(), topInventory(second));
        assertSame(otherView, topInventory(movedOn).orElseThrow(), "the player who moved on keeps their view");
        assertNotSame(sharedView, otherView);
        assertEquals(Map.of(4, "minecraft:diamond x1"), describe(shared.contents()), "closing keeps the contents");
    }

    /**
     * An open that has not run when the inventory is closed does nothing, so a player never sees the inventory of a
     * box that already ended.
     */
    @Test
    void anOpenThatRunsAfterTheCloseDoesNothing() throws Exception {
        UUID world = loadedWorld();
        UUID player = addPlayer(world, 0.5, 64.0, 0.5);
        SharedInventory shared = adapter.createSharedInventory("Supply", 9, Map.of(4, new ItemStack(Material.DIAMOND)));

        CompletableFuture<Boolean> open = adapter.openSharedInventory(player, shared);
        CompletableFuture<Void> close = adapter.closeSharedInventory(shared, Set.of(player));

        assertFalse(runUntilDone(open));
        runUntilDone(close);
        assertEquals(Optional.empty(), topInventory(player));
    }

    @Test
    void openingForAPlayerWhoIsNotOnlineIsRefused() throws Exception {
        loadedWorld();
        SharedInventory shared = adapter.createSharedInventory("Supply", 9, Map.of());

        assertFalse(runUntilDone(adapter.openSharedInventory(UUID.randomUUID(), shared)));
    }

    @Test
    void aSharedInventoryThatIsNotAChestSizeOrHasASlotOutsideItIsRefused() {
        for (int size : new int[]{0, 8, 10, 63}) {
            assertThrows(IllegalArgumentException.class, () -> adapter.createSharedInventory("Supply", size, Map.of()),
                    "size " + size);
        }
        for (int slot : new int[]{-1, 9}) {
            assertThrows(IllegalArgumentException.class, () -> adapter.createSharedInventory("Supply", 9,
                    Map.of(slot, new ItemStack(Material.DIAMOND))), "slot " + slot);
        }
    }

    @Test
    void anInventoryAnotherAdapterMadeIsRefused() {
        SharedInventory foreign = new SharedInventory() {
            @Override
            public String title() {
                return "Foreign";
            }

            @Override
            public int size() {
                return 9;
            }

            @Override
            public Map<Integer, ItemStack> contents() {
                return Map.of();
            }
        };

        assertThrows(IllegalArgumentException.class, () -> adapter.openSharedInventory(UUID.randomUUID(), foreign));
        assertThrows(IllegalArgumentException.class, () -> adapter.closeSharedInventory(foreign, Set.of()));
    }

    private static Map<Integer, String> slots(Inventory inventory) {
        Map<Integer, ItemStack> contents = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                contents.put(slot, stack);
            }
        }
        return describe(contents);
    }

    /**
     * Each stack as its item id and amount. Item stacks compare by meta through the Bukkit item factory, which the
     * Folia fake has not got, so the contract compares id and amount.
     */
    private static Map<Integer, String> describe(Map<Integer, ItemStack> contents) {
        Map<Integer, String> described = new LinkedHashMap<>();
        contents.forEach((slot, stack) -> described.put(slot, stack.getType().getKey() + " x" + stack.getAmount()));
        return described;
    }
}
