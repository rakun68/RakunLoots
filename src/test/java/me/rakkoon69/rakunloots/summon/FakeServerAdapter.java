package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.MenuHandle;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.SharedInventory;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory server adapter for summon search and loot box lifecycle tests. Blocks come from the chunk views, with
 * every block write laid over them. Block work refuses what the real adapters refuse: an id that is not a block, and
 * head data on another block or without a skin. Drops, messages, and shared inventory opens and closes are
 * recorded. The async scheduler runs a single
 * task inline and steps repeats by hand; the other schedulers are one hand-stepped scheduler.
 */
public final class FakeServerAdapter implements ServerAdapter {

    /**
     * One chunk read through the adapter.
     */
    public record ReadRecord(int chunkX, int chunkZ, UnloadedChunkPolicy policy) {
    }

    /**
     * One block set through the adapter.
     */
    public record BlockWrite(BlockPosition position, String blockId, Optional<String> headData) {
    }

    /**
     * One stack dropped through the adapter.
     *
     * @param headData the head data of a block item this fake made, if any
     */
    public record Drop(BlockPosition position, String itemId, int amount, Optional<String> headData) {
    }

    /**
     * One chat message sent through the adapter.
     */
    public record Message(UUID playerId, String text) {
    }

    /**
     * One shared inventory shown to a player through the adapter.
     */
    public record InventoryOpen(UUID playerId, FakeSharedInventory inventory) {
    }

    /**
     * One close of a shared inventory asked for through the adapter, with the players it named.
     */
    public record InventoryClose(FakeSharedInventory inventory, Set<UUID> viewers) {
    }

    /**
     * One menu shown to a player through the adapter.
     */
    public record MenuOpen(UUID playerId, String title, int size, Map<Integer, ItemStack> contents) {
    }

    /**
     * One menu update asked for through the adapter.
     */
    public record MenuUpdate(UUID playerId, Map<Integer, ItemStack> contents) {
    }

    /**
     * One menu close asked for through the adapter.
     */
    public record MenuClose(UUID playerId, MenuHandle menu) {
    }

    /**
     * One block item asked for through the adapter, with the head data the caller named.
     */
    public record BlockItemCall(String blockId, Optional<String> headData) {
    }

    private record HeldGive(UUID playerId, ItemStack item, int amount, CompletableFuture<Integer> result) {
    }

    /**
     * One sound played through the adapter.
     */
    public record Sound(UUID playerId, String key, double volume, double pitch) {
    }

    private record HeldOpen(UUID playerId, FakeSharedInventory inventory, CompletableFuture<Boolean> opened) {
    }

    private record HeldMenuOpen(UUID playerId, FakeMenu menu, MenuHandle expectedOld,
                                CompletableFuture<Optional<MenuHandle>> opened) {
    }

    /**
     * A menu inventory made by this fake. It copies its contents and listens for close.
     */
    public static final class FakeMenu implements MenuHandle {

        private final UUID playerId;
        private final String title;
        private final int size;
        private final Map<Integer, ItemStack> contents = new HashMap<>();
        private boolean closed;

        private FakeMenu(UUID playerId, String title, int size, Map<Integer, ItemStack> contents) {
            this.playerId = playerId;
            this.title = title;
            this.size = size;
            replace(contents);
        }

        @Override
        public UUID playerId() {
            return playerId;
        }

        public String title() {
            return title;
        }

        public int size() {
            return size;
        }

        public Map<Integer, ItemStack> contents() {
            Map<Integer, ItemStack> copy = new HashMap<>();
            contents.forEach((slot, stack) -> copy.put(slot, stack.clone()));
            return copy;
        }

        public boolean closed() {
            return closed;
        }

        private void replace(Map<Integer, ItemStack> stacks) {
            contents.clear();
            stacks.forEach((slot, stack) -> {
                if (slot < 0 || slot >= size) {
                    throw new IllegalArgumentException("slot " + slot + " is outside a size of " + size);
                }
                contents.put(slot, stack.clone());
            });
        }
    }

    private final UUID worldKey;
    private final FakeScheduler global = new FakeScheduler();
    private final FakeScheduler async = new FakeScheduler(true);
    private final Map<Long, ChunkView> views = new HashMap<>();
    private final Set<Long> loaded = new HashSet<>();
    private final Set<Long> generated = new HashSet<>();
    private final Set<Long> heldChunks = new HashSet<>();
    private final List<Runnable> deferredHolds = new ArrayList<>();
    private boolean holdsDeferred;
    private final List<ChunkPosition> holdCalls = new CopyOnWriteArrayList<>();
    private final List<ChunkPosition> releaseCalls = new CopyOnWriteArrayList<>();
    private final List<String> chunkOrder = new CopyOnWriteArrayList<>();
    private final List<ChunkPosition> chunksLoadedForRemoval = new CopyOnWriteArrayList<>();
    private final List<ReadRecord> reads = new CopyOnWriteArrayList<>();
    private final List<PlayerPosition> players = new ArrayList<>();
    private Optional<BlockPosition> spawn = Optional.empty();
    private final Map<BlockPosition, String> blocks = new HashMap<>();
    private final List<BlockWrite> blockWrites = new CopyOnWriteArrayList<>();
    private final List<Drop> drops = new CopyOnWriteArrayList<>();
    private final List<ParticleCall> particleCalls = new CopyOnWriteArrayList<>();
    private final Map<ItemStack, String> blockItemHeads = new IdentityHashMap<>();
    private final List<BlockItemCall> blockItemCalls = new CopyOnWriteArrayList<>();
    private final Set<UUID> onlinePlayers = new HashSet<>();
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final List<InventoryOpen> inventoryOpens = new CopyOnWriteArrayList<>();
    private final List<FakeSharedInventory> inventoryCloses = new CopyOnWriteArrayList<>();
    private final List<InventoryOpen> openCalls = new CopyOnWriteArrayList<>();
    private final List<InventoryClose> closeCalls = new CopyOnWriteArrayList<>();
    private final List<HeldOpen> heldOpens = new ArrayList<>();
    private boolean holdOpens;
    private boolean refuseOpens;
    private boolean failOpens;
    private final List<MenuOpen> menuOpens = new CopyOnWriteArrayList<>();
    private final List<MenuOpen> menuOpenCalls = new CopyOnWriteArrayList<>();
    private final List<MenuUpdate> menuUpdates = new CopyOnWriteArrayList<>();
    private final List<MenuClose> menuCloses = new CopyOnWriteArrayList<>();
    private final List<Sound> sounds = new CopyOnWriteArrayList<>();
    private final List<Runnable> nextTickActions = new CopyOnWriteArrayList<>();
    private final List<HeldMenuOpen> heldMenuOpens = new ArrayList<>();
    private final Map<UUID, FakeMenu> visibleMenus = new ConcurrentHashMap<>();
    private final Set<UUID> physicalOpenInProgress = ConcurrentHashMap.newKeySet();
    private boolean holdMenuOpens;
    private boolean refuseMenuOpens;
    private boolean failMenuOpens;
    private boolean failDrops;
    private final List<BlockPosition> blockReads = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<Void>> heldCloses = new ArrayList<>();
    private boolean holdCloses;
    private boolean worldLoaded = true;
    private final Map<UUID, FakePlayerInventory> playerInventories = new HashMap<>();
    private final List<Integer> giveCalls = new CopyOnWriteArrayList<>();
    private final List<HeldGive> heldGives = new ArrayList<>();
    private boolean holdGives;
    private Runnable onGive;

    public FakeServerAdapter(UUID worldKey) {
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
    }

    public UUID worldKey() {
        return worldKey;
    }

    public void setWorldLoaded(boolean worldLoaded) {
        this.worldLoaded = worldLoaded;
    }

    public void setSpawn(int x, int y, int z) {
        spawn = Optional.of(new BlockPosition(x, y, z));
    }

    void clearSpawn() {
        spawn = Optional.empty();
    }

    public void putChunk(ChunkView view, boolean loaded, boolean generated) {
        long key = chunkKey(view.chunkX(), view.chunkZ());
        views.put(key, view);
        if (loaded) {
            this.loaded.add(key);
        } else {
            this.loaded.remove(key);
        }
        if (generated) {
            this.generated.add(key);
        } else {
            this.generated.remove(key);
        }
    }

    void putChunk(FakeColumn column, boolean loaded, boolean generated) {
        putChunk(column.view(), loaded, generated);
    }

    public void setPlayers(List<PlayerPosition> players) {
        this.players.clear();
        this.players.addAll(players);
    }

    /**
     * Every chunk read through the adapter, in order.
     */
    public List<ReadRecord> reads() {
        return List.copyOf(reads);
    }

    /**
     * Every block read through the adapter, loaded or not, in order.
     */
    public List<BlockPosition> blockReads() {
        return List.copyOf(blockReads);
    }

    public FakeScheduler globalScheduler() {
        return global;
    }

    public FakeScheduler asyncScheduler() {
        return async;
    }

    /**
     * Sets a block as the world or another plugin would, without recording a write. The id may carry block data, as
     * in {@code minecraft:chest[facing=north,waterlogged=true]}; reads and comparisons see the id alone, as the real
     * adapters read the block type.
     */
    public void putBlock(BlockPosition position, String blockId) {
        blocks.put(position, blockId);
    }

    /**
     * The block at a position now, whether or not its chunk is loaded. Unset blocks come from the chunk view, or are
     * air.
     */
    public String blockAt(BlockPosition position) {
        String written = blocks.get(position);
        if (written != null) {
            return written;
        }
        ChunkView view = views.get(chunkKey(position.x() >> 4, position.z() >> 4));
        if (view == null || position.y() < view.minHeight() || position.y() >= view.maxHeight()) {
            return "minecraft:air";
        }
        return view.blockId(position.x() & 15, position.y(), position.z() & 15);
    }

    /**
     * Every block set through the adapter, in order.
     */
    public List<BlockWrite> blockWrites() {
        return List.copyOf(blockWrites);
    }

    @Override
    public CompletableFuture<Optional<ChunkView>> readChunk(
            UUID worldKey,
            int chunkX,
            int chunkZ,
            UnloadedChunkPolicy policy
    ) {
        reads.add(new ReadRecord(chunkX, chunkZ, policy));
        if (!this.worldKey.equals(worldKey) || !worldLoaded) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long key = chunkKey(chunkX, chunkZ);
        return switch (policy) {
            case SKIP -> CompletableFuture.completedFuture(
                    loaded.contains(key) ? Optional.ofNullable(views.get(key)) : Optional.empty());
            case LOAD -> {
                if (!generated.contains(key)) {
                    yield CompletableFuture.completedFuture(Optional.empty());
                }
                // Like the real adapters, a read under LOAD leaves the chunk loaded.
                loaded.add(key);
                yield CompletableFuture.completedFuture(Optional.ofNullable(views.get(key)));
            }
            case GENERATE -> {
                if (!views.containsKey(key)) {
                    yield CompletableFuture.completedFuture(Optional.empty());
                }
                generated.add(key);
                loaded.add(key);
                yield CompletableFuture.completedFuture(Optional.of(views.get(key)));
            }
        };
    }

    @Override
    public CompletableFuture<Set<ChunkPosition>> loadedChunks(UUID worldKey) {
        if (!this.worldKey.equals(worldKey) || !worldLoaded) {
            return CompletableFuture.completedFuture(Set.of());
        }
        Set<ChunkPosition> chunks = new HashSet<>();
        for (long key : loaded) {
            chunks.add(new ChunkPosition(chunkX(key), chunkZ(key)));
        }
        return CompletableFuture.completedFuture(chunks);
    }

    @Override
    public CompletableFuture<List<PlayerPosition>> playerPositions(UUID worldKey) {
        if (!this.worldKey.equals(worldKey) || !worldLoaded) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(List.copyOf(players));
    }

    @Override
    public CompletableFuture<Optional<BlockPosition>> worldSpawn(UUID worldKey) {
        if (!this.worldKey.equals(worldKey) || !worldLoaded) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(spawn);
    }

    @Override
    public CompletableFuture<List<LoadedWorld>> loadedWorlds() {
        if (!worldLoaded) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(List.of(new LoadedWorld(worldKey, "world")));
    }

    @Override
    public CompletableFuture<Boolean> setBlock(UUID worldKey, BlockPosition position, String blockId,
                                               Optional<String> headData) {
        String id = placement(blockId, headData);
        if (!blockLoaded(worldKey, position)) {
            return CompletableFuture.completedFuture(false);
        }
        write(position, id, headData);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> replaceBlock(UUID worldKey, BlockPosition position, String expectedBlockId,
                                                   String blockId, Optional<String> headData) {
        String expected = blockType(expectedBlockId);
        String id = placement(blockId, headData);
        if (!blockLoaded(worldKey, position) || !typeAt(position).equals(expected)) {
            return CompletableFuture.completedFuture(false);
        }
        write(position, id, headData);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Optional<String>> readBlock(UUID worldKey, BlockPosition position) {
        return CompletableFuture.completedFuture(readBlockHere(worldKey, position));
    }

    /**
     * Reads the block on the calling thread; the fake world has no owning thread.
     */
    @Override
    public Optional<String> readBlockHere(UUID worldKey, BlockPosition position) {
        Objects.requireNonNull(position, "position");
        blockReads.add(position);
        if (!blockLoaded(worldKey, position)) {
            return Optional.empty();
        }
        return Optional.of(typeAt(position));
    }

    /**
     * The block id at a position without its block data.
     */
    private String typeAt(BlockPosition position) {
        String block = blockAt(position);
        int data = block.indexOf('[');
        return data < 0 ? block : block.substring(0, data);
    }

    @Override
    public CompletableFuture<Boolean> removeBlock(UUID worldKey, BlockPosition position, String blockId) {
        String expected = blockType(blockId);
        if (!blockLoaded(worldKey, position) || !typeAt(position).equals(expected)) {
            return CompletableFuture.completedFuture(false);
        }
        write(position, "minecraft:air", Optional.empty());
        chunkOrder.add("remove");
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Removes the block as the real adapters do: the world must be loaded and the chunk generated, an unloaded chunk
     * is loaded only for the removal and is unloaded again after, and the block is removed only while it is still
     * the given block. A chunk loaded for a removal is recorded in {@link #chunksLoadedForRemoval()}.
     */
    @Override
    public CompletableFuture<Boolean> removeBlockLoadingChunk(UUID worldKey, BlockPosition position, String blockId) {
        String expected = blockType(blockId);
        long chunk = chunkKey(position.x() >> 4, position.z() >> 4);
        if (!this.worldKey.equals(worldKey) || !worldLoaded || !generated.contains(chunk)) {
            return CompletableFuture.completedFuture(false);
        }
        if (!loaded.contains(chunk)) {
            chunksLoadedForRemoval.add(new ChunkPosition(chunkX(chunk), chunkZ(chunk)));
        }
        if (!typeAt(position).equals(expected)) {
            return CompletableFuture.completedFuture(false);
        }
        write(position, "minecraft:air", Optional.empty());
        chunkOrder.add("remove");
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Holds the chunk as the real adapters do: the world must be loaded and the chunk generated, and a chunk that is
     * not loaded is loaded and stays loaded. A plugin holds one ticket per chunk, so a second hold changes nothing.
     */
    @Override
    public CompletableFuture<Boolean> holdChunk(UUID worldKey, int chunkX, int chunkZ) {
        holdCalls.add(new ChunkPosition(chunkX, chunkZ));
        if (holdsDeferred) {
            CompletableFuture<Boolean> landing = new CompletableFuture<>();
            deferredHolds.add(() -> landing.complete(landHold(worldKey, chunkX, chunkZ)));
            return landing;
        }
        return CompletableFuture.completedFuture(landHold(worldKey, chunkX, chunkZ));
    }

    private boolean landHold(UUID worldKey, int chunkX, int chunkZ) {
        long chunk = chunkKey(chunkX, chunkZ);
        if (!this.worldKey.equals(worldKey) || !worldLoaded || !generated.contains(chunk)) {
            return false;
        }
        loaded.add(chunk);
        heldChunks.add(chunk);
        return true;
    }

    /**
     * From now on a hold asked for does not land until {@link #landHolds}, as on a real server, where the ticket is
     * added on the main thread some time after the plugin asks. Until it lands the chunk is not held, so it can be
     * unloaded, and a block in it cannot be read.
     */
    public void deferHolds() {
        holdsDeferred = true;
    }

    /**
     * Lands every hold asked for since {@link #deferHolds}, and holds land at once again.
     */
    public void landHolds() {
        holdsDeferred = false;
        List<Runnable> landing = List.copyOf(deferredHolds);
        deferredHolds.clear();
        landing.forEach(Runnable::run);
    }

    /**
     * Releases the chunk's ticket. The chunk stays loaded here, since the real server unloads it later.
     */
    @Override
    public CompletableFuture<Boolean> releaseChunk(UUID worldKey, int chunkX, int chunkZ) {
        releaseCalls.add(new ChunkPosition(chunkX, chunkZ));
        chunkOrder.add("release");
        if (!this.worldKey.equals(worldKey) || !worldLoaded) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(heldChunks.remove(chunkKey(chunkX, chunkZ)));
    }

    /**
     * The chunks held with a chunk ticket now.
     */
    public Set<ChunkPosition> heldChunks() {
        Set<ChunkPosition> held = new HashSet<>();
        for (long key : heldChunks) {
            held.add(new ChunkPosition(chunkX(key), chunkZ(key)));
        }
        return held;
    }

    /**
     * Every hold asked for, in order, whether it was granted or not.
     */
    public List<ChunkPosition> holdCalls() {
        return List.copyOf(holdCalls);
    }

    /**
     * Every release asked for, in order.
     */
    public List<ChunkPosition> releaseCalls() {
        return List.copyOf(releaseCalls);
    }

    /**
     * Each block removal that removed its block ({@code remove}) and each chunk release ({@code release}), in the
     * order they happened.
     */
    public List<String> removalAndReleaseOrder() {
        return List.copyOf(chunkOrder);
    }

    /**
     * The chunks a removal had to load, in order.
     */
    public List<ChunkPosition> chunksLoadedForRemoval() {
        return List.copyOf(chunksLoadedForRemoval);
    }

    /**
     * Unloads a chunk the way a server does when nothing keeps it: a held chunk stays loaded.
     *
     * @return whether the chunk is unloaded now
     */
    public boolean unloadChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (heldChunks.contains(key)) {
            return false;
        }
        loaded.remove(key);
        return true;
    }

    private boolean blockLoaded(UUID worldKey, BlockPosition position) {
        return this.worldKey.equals(worldKey) && worldLoaded
                && loaded.contains(chunkKey(position.x() >> 4, position.z() >> 4));
    }

    private void write(BlockPosition position, String blockId, Optional<String> headData) {
        blocks.put(position, blockId);
        blockWrites.add(new BlockWrite(position, blockId, headData));
    }

    /**
     * The namespaced id of a block, as the real adapters resolve it.
     *
     * @throws IllegalArgumentException when the id is not a block
     */
    private static String blockType(String blockId) {
        Objects.requireNonNull(blockId, "blockId");
        NamespacedKey key = NamespacedKey.fromString(blockId);
        Material type = key == null ? null : Material.matchMaterial(key.toString());
        if (type == null || !type.isBlock()) {
            throw new IllegalArgumentException("not a block id: " + blockId);
        }
        return type.getKey().toString();
    }

    /**
     * Checks a block to set and its head data as the real adapters do.
     *
     * @throws IllegalArgumentException when the id is not a block, or the head data is set on another block or is
     *                                  not base64 JSON with {@code textures.SKIN.url}
     */
    private static String placement(String blockId, Optional<String> headData) {
        Objects.requireNonNull(headData, "headData");
        String id = blockType(blockId);
        if (headData.isEmpty()) {
            return id;
        }
        if (!id.equals("minecraft:player_head") && !id.equals("minecraft:player_wall_head")) {
            throw new IllegalArgumentException("head data is only for a player head, not " + blockId);
        }
        try {
            String json = new String(Base64.getDecoder().decode(headData.get().trim()), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            JsonElement url = skin == null ? null : skin.get("url");
            if (url == null || !url.isJsonPrimitive()) {
                throw new IllegalArgumentException("head data names no skin");
            }
        } catch (JsonParseException | IllegalStateException | ClassCastException failure) {
            throw new IllegalArgumentException("head data is not a textures value", failure);
        }
        return id;
    }

    /**
     * Drops the stacks, as the real adapters do: nothing in an unloaded chunk, and air is skipped.
     */
    @Override
    public CompletableFuture<Boolean> dropStacks(UUID worldKey, BlockPosition position, List<ItemStack> stacks) {
        Objects.requireNonNull(position, "position");
        List<ItemStack> given = List.copyOf(stacks);
        if (failDrops) {
            return CompletableFuture.failedFuture(new IllegalStateException("drop failed"));
        }
        if (!blockLoaded(worldKey, position)) {
            return CompletableFuture.completedFuture(false);
        }
        for (ItemStack stack : given) {
            if (!stack.getType().isAir()) {
                drops.add(new Drop(position, stack.getType().getKey().toString(), stack.getAmount(),
                        headDataOf(stack)));
            }
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * One particle draw asked for through the adapter, with the thread-owner block it was asked to run at.
     */
    public record ParticleCall(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws) {
    }

    /**
     * Draws as the real adapters do: nothing when the owner's chunk is not loaded, and a particle name the server
     * does not have is refused before anything is drawn. Every call is recorded, drawn or not.
     */
    @Override
    public CompletableFuture<Boolean> drawParticles(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws) {
        Objects.requireNonNull(owner, "owner");
        List<ParticleDraw> asked = List.copyOf(draws);
        for (ParticleDraw draw : asked) {
            particleExists(draw.spec().particle());
        }
        particleCalls.add(new ParticleCall(worldKey, owner, asked));
        return CompletableFuture.completedFuture(blockLoaded(worldKey, owner));
    }

    /**
     * The particle draws asked for, in order.
     */
    public List<ParticleCall> particleCalls() {
        return List.copyOf(particleCalls);
    }

    /**
     * Resolves a particle name as the real adapters do: through Bukkit's particle enum, where {@code REDSTONE} and
     * {@code DUST} are one particle named by whichever the server has.
     */
    private static void particleExists(String name) {
        String upper = name.trim().toUpperCase(java.util.Locale.ROOT);
        for (String candidate : List.of(upper, "REDSTONE".equals(upper) ? "DUST" : upper,
                "DUST".equals(upper) ? "REDSTONE" : upper)) {
            try {
                Particle.valueOf(candidate);
                return;
            } catch (IllegalArgumentException unknown) {
                // try the next name
            }
        }
        throw new IllegalArgumentException("not a particle on this server: " + name);
    }

    /**
     * Makes the plain block item, refusing what the real adapters refuse. MockBukkit's skull meta cannot hold a
     * skin, so this fake remembers which head data each block item it made carries, and a drop of that stack reports
     * it. The adapter tests check the real item's skin.
     */
    @Override
    public Optional<ItemStack> blockItem(String blockId, Optional<String> headData) {
        String id = placement(blockId, headData);
        blockItemCalls.add(new BlockItemCall(id, headData));
        Material type = Material.matchMaterial(id);
        if (type == null || !type.isItem()) {
            return Optional.empty();
        }
        ItemStack item = new ItemStack(type);
        headData.ifPresent(data -> blockItemHeads.put(item, data));
        return Optional.of(item);
    }

    /**
     * The block items asked for through the adapter.
     */
    public List<BlockItemCall> blockItemCalls() {
        return List.copyOf(blockItemCalls);
    }

    private Optional<String> headDataOf(ItemStack stack) {
        return Optional.ofNullable(blockItemHeads.get(stack));
    }

    /**
     * Sends the message to an online player.
     */
    @Override
    public CompletableFuture<Boolean> sendMessage(UUID playerId, String message) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(message, "message");
        if (!onlinePlayers.contains(playerId)) {
            return CompletableFuture.completedFuture(false);
        }
        messages.add(new Message(playerId, message));
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Puts a player online, so messages reach them.
     */
    public void addOnlinePlayer(UUID playerId) {
        onlinePlayers.add(playerId);
    }

    /**
     * Every stack dropped through the adapter, in order.
     */
    public List<Drop> drops() {
        return List.copyOf(drops);
    }

    /**
     * Every message a player got, in order.
     */
    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /**
     * A player's own inventory, made on first use. Only online players have one to read or fill.
     */
    public FakePlayerInventory inventoryOf(UUID playerId) {
        return playerInventories.computeIfAbsent(playerId, id -> new FakePlayerInventory());
    }

    /**
     * Runs the action when a give is asked for, before any item goes in, as a crash between a claim and its give
     * would. It sees the world as the claim left it.
     */
    public void onGive(Runnable action) {
        this.onGive = action;
    }

    /**
     * Every give asked for through the adapter, as the amount asked, in order.
     */
    public List<Integer> giveCalls() {
        return List.copyOf(giveCalls);
    }

    /**
     * Counts how many of the item fit, refusing what the real adapters refuse. A player who is not online has no room.
     */
    @Override
    public CompletableFuture<Integer> inventoryRoom(UUID playerId, ItemStack item) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        if (!onlinePlayers.contains(playerId)) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.completedFuture(inventoryOf(playerId).room(item));
    }

    /**
     * Gives as many as fit and reports how many, refusing what the real adapters refuse. Nothing goes to a player who
     * is not online.
     */
    @Override
    public CompletableFuture<Integer> giveItems(UUID playerId, ItemStack item, int amount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
        giveCalls.add(amount);
        if (onGive != null) {
            onGive.run();
        }
        if (!onlinePlayers.contains(playerId)) {
            return CompletableFuture.completedFuture(0);
        }
        if (holdGives) {
            CompletableFuture<Integer> held = new CompletableFuture<>();
            heldGives.add(new HeldGive(playerId, item, amount, held));
            return held;
        }
        return CompletableFuture.completedFuture(inventoryOf(playerId).give(item, amount));
    }

    /**
     * Holds every give after the claim lowered storage, as a give waiting for the player's thread would.
     */
    public void holdGives() {
        holdGives = true;
    }

    /**
     * Lets every held give put its items in and completes it.
     */
    public void releaseGives() {
        holdGives = false;
        List<HeldGive> held = List.copyOf(heldGives);
        heldGives.clear();
        held.forEach(give -> give.result.complete(inventoryOf(give.playerId).give(give.item, give.amount)));
    }

    /**
     * Makes a shared inventory, refusing what the real adapters refuse.
     */
    @Override
    public SharedInventory createSharedInventory(String title, int size, Map<Integer, ItemStack> contents) {
        return new FakeSharedInventory(title, size, contents);
    }

    /**
     * Shows the inventory to an online player and records it. While opens are held, the open waits for
     * {@link #releaseInventoryOpens()}, as an open on another player's thread would; one that runs after the
     * inventory was closed does nothing and completes false. While opens are refused, every open completes false,
     * as when another plugin cancels the open event; while they fail, every open completes exceptionally.
     */
    @Override
    public CompletableFuture<Boolean> openSharedInventory(UUID playerId, SharedInventory inventory) {
        Objects.requireNonNull(playerId, "playerId");
        FakeSharedInventory shared = made(inventory);
        openCalls.add(new InventoryOpen(playerId, shared));
        if (failOpens) {
            return CompletableFuture.failedFuture(new IllegalStateException("open failed"));
        }
        if (holdOpens) {
            CompletableFuture<Boolean> opened = new CompletableFuture<>();
            heldOpens.add(new HeldOpen(playerId, shared, opened));
            return opened;
        }
        return CompletableFuture.completedFuture(show(playerId, shared));
    }

    private boolean show(UUID playerId, FakeSharedInventory shared) {
        if (refuseOpens || !onlinePlayers.contains(playerId) || shared.closed()) {
            return false;
        }
        visibleMenus.remove(playerId);
        shared.open(playerId);
        inventoryOpens.add(new InventoryOpen(playerId, shared));
        return true;
    }

    /**
     * Makes every later shared inventory open wait until {@link #releaseInventoryOpens()}.
     */
    public void holdInventoryOpens() {
        holdOpens = true;
    }

    /**
     * Runs every held open, in order, and stops holding them.
     */
    public void releaseInventoryOpens() {
        holdOpens = false;
        List<HeldOpen> held = List.copyOf(heldOpens);
        heldOpens.clear();
        held.forEach(open -> open.opened.complete(show(open.playerId, open.inventory)));
    }

    /**
     * Makes every later drop complete exceptionally.
     */
    public void failDrops() {
        failDrops = true;
    }

    /**
     * Makes every later shared inventory open complete false.
     */
    public void refuseInventoryOpens() {
        refuseOpens = true;
    }

    /**
     * Makes every later shared inventory open complete exceptionally.
     */
    public void failInventoryOpens() {
        failOpens = true;
    }

    /**
     * Every shared inventory open asked for, whether or not it showed, in order.
     */
    public List<InventoryOpen> inventoryOpenCalls() {
        return List.copyOf(openCalls);
    }

    /**
     * Closes the inventory for good and the given players' views of it, and records it. No close event follows:
     * without a listener, none reaches the lifecycle. While closes are held, the views close and the future waits
     * for {@link #releaseInventoryCloses()}, as a close on another player's thread would.
     */
    @Override
    public CompletableFuture<Void> closeSharedInventory(SharedInventory inventory, Set<UUID> viewers) {
        FakeSharedInventory shared = made(inventory);
        shared.closeFor(viewers);
        inventoryCloses.add(shared);
        closeCalls.add(new InventoryClose(shared, Set.copyOf(viewers)));
        if (!holdCloses) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> closed = new CompletableFuture<>();
        heldCloses.add(closed);
        return closed;
    }

    /**
     * Makes every later shared inventory close wait until {@link #releaseInventoryCloses()}.
     */
    public void holdInventoryCloses() {
        holdCloses = true;
    }

    /**
     * Completes every held close, in order, and stops holding them.
     */
    public void releaseInventoryCloses() {
        holdCloses = false;
        List<CompletableFuture<Void>> held = List.copyOf(heldCloses);
        heldCloses.clear();
        held.forEach(closed -> closed.complete(null));
    }

    /**
     * Makes a menu, refusing what the real adapters refuse.
     */
    @Override
    public MenuHandle createMenu(UUID playerId, String title, int size, Map<Integer, ItemStack> contents) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(contents, "contents");
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("size must be a multiple of 9 from 9 to 54, not " + size);
        }
        contents.forEach((slot, stack) -> {
            Objects.requireNonNull(stack, "stack");
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("slot " + slot + " is outside a size of " + size);
            }
        });
        return new FakeMenu(playerId, title, size, contents);
    }

    /**
     * Shows a menu to an online player and records it. While opens are held, the open waits for
     * {@link #releaseMenuOpens()}. While opens are refused or fail, no menu opens.
     */
    @Override
    public CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu) {
        return openMenu(playerId, menu, null);
    }

    @Override
    public CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu, MenuHandle expectedOld) {
        Objects.requireNonNull(playerId, "playerId");
        FakeMenu made = madeMenu(menu);
        menuOpenCalls.add(new MenuOpen(playerId, made.title, made.size, made.contents()));
        if (failMenuOpens) {
            return CompletableFuture.failedFuture(new IllegalStateException("menu open failed"));
        }
        if (holdMenuOpens) {
            CompletableFuture<Optional<MenuHandle>> opened = new CompletableFuture<>();
            heldMenuOpens.add(new HeldMenuOpen(playerId, made, expectedOld, opened));
            return opened;
        }
        return CompletableFuture.completedFuture(showMenu(playerId, made, expectedOld));
    }

    private Optional<MenuHandle> showMenu(UUID playerId, FakeMenu menu, MenuHandle expectedOld) {
        if (refuseMenuOpens || !onlinePlayers.contains(playerId) || menu.closed()) {
            return Optional.empty();
        }
        if (!playerId.equals(menu.playerId())) {
            throw new IllegalArgumentException("menu belongs to " + menu.playerId() + ", not " + playerId);
        }
        if (expectedOld != null) {
            FakeMenu oldMenu = madeMenu(expectedOld);
            if (oldMenu.closed() || visibleMenus.get(playerId) != oldMenu) {
                return Optional.empty();
            }
        }
        physicalOpenInProgress.add(playerId);
        try {
            visibleMenus.put(playerId, menu);
            menuOpens.add(new MenuOpen(playerId, menu.title, menu.size, menu.contents()));
            return Optional.of(menu);
        } finally {
            physicalOpenInProgress.remove(playerId);
        }
    }

    @Override
    public boolean isPhysicalOpenInProgress(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return physicalOpenInProgress.contains(playerId);
    }

    public void holdMenuOpens() {
        holdMenuOpens = true;
    }

    public void releaseMenuOpens() {
        holdMenuOpens = false;
        List<HeldMenuOpen> held = List.copyOf(heldMenuOpens);
        heldMenuOpens.clear();
        held.forEach(open -> open.opened.complete(showMenu(open.playerId, open.menu, open.expectedOld)));
    }

    public void refuseMenuOpens() {
        refuseMenuOpens = true;
    }

    public void failMenuOpens() {
        failMenuOpens = true;
    }

    public List<MenuOpen> menuOpenCalls() {
        return List.copyOf(menuOpenCalls);
    }

    public List<MenuOpen> menuOpens() {
        return List.copyOf(menuOpens);
    }

    @Override
    public CompletableFuture<Void> updateMenu(MenuHandle menu, Map<Integer, ItemStack> contents) {
        FakeMenu made = madeMenu(menu);
        made.replace(contents);
        if (!made.closed) {
            menuUpdates.add(new MenuUpdate(made.playerId, made.contents()));
        }
        return CompletableFuture.completedFuture(null);
    }

    public List<MenuUpdate> menuUpdates() {
        return List.copyOf(menuUpdates);
    }

    @Override
    public CompletableFuture<Void> closeMenu(MenuHandle menu) {
        FakeMenu made = madeMenu(menu);
        made.closed = true;
        visibleMenus.remove(made.playerId, made);
        menuCloses.add(new MenuClose(made.playerId, made));
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Simulates the player externally closing their current visible menu view or opening an unrelated inventory.
     */
    public void closePlayerView(UUID playerId) {
        visibleMenus.remove(playerId);
    }

    public Optional<FakeMenu> visibleMenu(UUID playerId) {
        return Optional.ofNullable(visibleMenus.get(playerId));
    }

    public List<MenuClose> menuCloses() {
        return List.copyOf(menuCloses);
    }

    @Override
    public CompletableFuture<Void> runOnPlayerNextTick(UUID playerId, Runnable action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        if (onlinePlayers.contains(playerId)) {
            nextTickActions.add(action);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Runs every deferred player action, as the next tick does.
     */
    public void runNextTickActions() {
        List<Runnable> actions = List.copyOf(nextTickActions);
        nextTickActions.clear();
        actions.forEach(Runnable::run);
    }

    @Override
    public CompletableFuture<Boolean> playSound(UUID playerId, String key, double volume, double pitch) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(key, "key");
        if (!onlinePlayers.contains(playerId)) {
            return CompletableFuture.completedFuture(false);
        }
        sounds.add(new Sound(playerId, key, volume, pitch));
        return CompletableFuture.completedFuture(true);
    }

    public List<Sound> sounds() {
        return List.copyOf(sounds);
    }

    private static FakeMenu madeMenu(MenuHandle menu) {
        Objects.requireNonNull(menu, "menu");
        if (!(menu instanceof FakeMenu made)) {
            throw new IllegalArgumentException("not made by a server adapter: " + menu);
        }
        return made;
    }

    private static FakeSharedInventory made(SharedInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        if (!(inventory instanceof FakeSharedInventory shared)) {
            throw new IllegalArgumentException("not made by a server adapter: " + inventory);
        }
        return shared;
    }

    /**
     * Every shared inventory shown to a player, in order.
     */
    public List<InventoryOpen> inventoryOpens() {
        return List.copyOf(inventoryOpens);
    }

    /**
     * Every shared inventory whose views were all closed through the adapter, in order.
     */
    public List<FakeSharedInventory> inventoryCloses() {
        return List.copyOf(inventoryCloses);
    }

    /**
     * Every close asked for through the adapter, with the players it named, in order.
     */
    public List<InventoryClose> inventoryCloseCalls() {
        return List.copyOf(closeCalls);
    }

    @Override
    public TaskScheduler atLocation(UUID worldKey, int blockX, int blockZ) {
        return global;
    }

    @Override
    public TaskScheduler forPlayer(UUID playerId) {
        return global;
    }

    @Override
    public TaskScheduler global() {
        return global;
    }

    @Override
    public TaskScheduler async() {
        return async;
    }

    /**
     * Holds every async task, as a read still waiting for the async scheduler would.
     */
    public void holdAsync() {
        async.hold();
    }

    /**
     * Runs every held async task at once and lets later ones run inline again.
     */
    public void releaseAsync() {
        async.release();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
    }

    private static int chunkX(long key) {
        return (int) key;
    }

    private static int chunkZ(long key) {
        return (int) (key >> 32);
    }

    public static final class FakeScheduler implements TaskScheduler {

        private final boolean inline;
        private long tick;
        private final List<Scheduled> pending = new ArrayList<>();
        private boolean holdInline;
        private final List<Runnable> heldInline = new ArrayList<>();

        private FakeScheduler() {
            this(false);
        }

        private FakeScheduler(boolean inline) {
            this.inline = inline;
        }

        private int runCalls;

        /**
         * How many tasks were handed to {@link #run}, run at once or not.
         */
        public int runCalls() {
            return runCalls;
        }

        @Override
        public TaskHandle run(Runnable task) {
            runCalls++;
            if (inline) {
                if (holdInline) {
                    heldInline.add(task);
                    return () -> heldInline.remove(task);
                }
                task.run();
                return () -> {
                };
            }
            pending.add(new Scheduled(task, tick, 0));
            return () -> {
            };
        }

        @Override
        public TaskHandle runLater(Runnable task, long delayTicks) {
            pending.add(new Scheduled(task, tick + delayTicks, 0));
            return () -> pending.removeIf(scheduled -> scheduled.task == task);
        }

        @Override
        public TaskHandle repeat(Runnable task, long delayTicks, long periodTicks) {
            Scheduled scheduled = new Scheduled(task, tick + delayTicks, periodTicks);
            pending.add(scheduled);
            return () -> scheduled.cancelled = true;
        }

        private void hold() {
            holdInline = true;
        }

        private void release() {
            holdInline = false;
            List<Runnable> tasks = List.copyOf(heldInline);
            heldInline.clear();
            tasks.forEach(Runnable::run);
        }

        public void stepTicks(long ticks) {
            for (long step = 0; step < ticks; step++) {
                tick++;
                List<Scheduled> due = new ArrayList<>();
                for (Scheduled scheduled : List.copyOf(pending)) {
                    if (!scheduled.cancelled && scheduled.nextDue <= tick) {
                        due.add(scheduled);
                    }
                }
                for (Scheduled scheduled : due) {
                    scheduled.task.run();
                    if (scheduled.period > 0 && !scheduled.cancelled) {
                        scheduled.nextDue = tick + scheduled.period;
                    } else {
                        scheduled.cancelled = true;
                    }
                }
                pending.removeIf(scheduled -> scheduled.cancelled && scheduled.period == 0);
            }
        }

        private static final class Scheduled {
            private final Runnable task;
            private long nextDue;
            private final long period;
            private boolean cancelled;

            private Scheduled(Runnable task, long nextDue, long period) {
                this.task = task;
                this.nextDue = nextDue;
                this.period = period;
            }
        }
    }
}
