package me.rakkoon69.rakunloots.server;

import org.bukkit.block.data.BlockData;
import be.seeseemelk.mockbukkit.ChunkMock;
import be.seeseemelk.mockbukkit.Coordinate;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.BlockMock;
import be.seeseemelk.mockbukkit.entity.ItemEntityMock;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A MockBukkit world whose chunks are ungenerated, generated but unloaded, or loaded, as the test sets them, and
 * whose spawn block the test may move. It records each chunk call, block call, player listing, spawn read, and
 * drop made on it with the thread and recorded task it ran in. Loading a chunk the way a real server does, including
 * {@link #getChunkAt(int, int)} and {@link #getBlockAt(int, int, int)}, changes its state. Blocks the test did not
 * set are air. A player head keeps the profile its block state was updated with, until the block changes.
 */
final class FakeChunkWorld extends WorldMock {

    /**
     * One call made on this world.
     */
    static final class WorldCall {

        private final String method;
        private final Optional<Boolean> generate;
        private final boolean mainThread;
        private final OptionalInt task;

        private WorldCall(String method, Optional<Boolean> generate, boolean mainThread, OptionalInt task) {
            this.method = method;
            this.generate = generate;
            this.mainThread = mainThread;
            this.task = task;
        }

        String method() {
            return method;
        }

        /**
         * The generate flag a load call was given. Empty for other calls.
         */
        Optional<Boolean> generate() {
            return generate;
        }

        boolean mainThread() {
            return mainThread;
        }

        /**
         * The recording scheduler's task this call ran inside, if any.
         */
        OptionalInt task() {
            return task;
        }

        @Override
        public String toString() {
            return method + generate.map(flag -> "(generate=" + flag + ")").orElse("")
                    + (mainThread ? " on main" : " off main") + " in task " + task;
        }
    }

    private final Map<Long, ChunkState> chunks = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Material>> blocks = new ConcurrentHashMap<>();
    private final Map<BlockPosition, PlayerProfile> heads = new ConcurrentHashMap<>();
    private final List<DropCall> drops = new CopyOnWriteArrayList<>();
    private final List<SpawnedParticle> particles = new CopyOnWriteArrayList<>();
    private final List<WorldCall> calls = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> asyncLoads = new ConcurrentLinkedQueue<>();
    private final Set<Long> tickets = ConcurrentHashMap.newKeySet();
    private volatile int spawnX;
    private volatile int spawnY = 64;
    private volatile int spawnZ;

    FakeChunkWorld(int minHeight, int maxHeight) {
        super(Material.AIR, minHeight, maxHeight, minHeight);
    }

    void setChunkState(int chunkX, int chunkZ, ChunkState state) {
        chunks.put(chunkKey(chunkX, chunkZ), state);
    }

    ChunkState chunkState(int chunkX, int chunkZ) {
        return chunks.getOrDefault(chunkKey(chunkX, chunkZ), ChunkState.UNGENERATED);
    }

    void setBlock(int x, int y, int z, Material material) {
        blocks.computeIfAbsent(chunkKey(x >> 4, z >> 4), key -> new ConcurrentHashMap<>())
                .put(blockKey(x & 15, y, z & 15), material);
        heads.remove(new BlockPosition(x, y, z));
    }

    /**
     * The block's material, read without loading its chunk or recording a call.
     */
    Material blockType(int x, int y, int z) {
        return blocks.getOrDefault(chunkKey(x >> 4, z >> 4), Map.of())
                .getOrDefault(blockKey(x & 15, y, z & 15), Material.AIR);
    }

    /**
     * The profile a player head at the block was last updated with, if any.
     */
    Optional<PlayerProfile> headProfile(int x, int y, int z) {
        return Optional.ofNullable(heads.get(new BlockPosition(x, y, z)));
    }

    List<DropCall> drops() {
        return List.copyOf(drops);
    }

    void setSpawn(int x, int y, int z) {
        spawnX = x;
        spawnY = y;
        spawnZ = z;
    }

    List<WorldCall> calls() {
        return List.copyOf(calls);
    }

    /**
     * Finishes every async chunk load started so far, on the calling thread, as Paper does on a later tick of the
     * main thread.
     */
    void finishAsyncLoads() {
        for (Runnable load = asyncLoads.poll(); load != null; load = asyncLoads.poll()) {
            load.run();
        }
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        record("isChunkLoaded");
        return chunkState(x, z) == ChunkState.LOADED;
    }

    @Override
    public boolean isChunkLoaded(@NotNull Chunk chunk) {
        return isChunkLoaded(chunk.getX(), chunk.getZ());
    }

    @Override
    public boolean isChunkGenerated(int x, int z) {
        record("isChunkGenerated");
        return chunkState(x, z) != ChunkState.UNGENERATED;
    }

    @Override
    public void loadChunk(int x, int z) {
        loadChunk(x, z, true);
    }

    @Override
    public void loadChunk(@NotNull Chunk chunk) {
        loadChunk(chunk.getX(), chunk.getZ(), true);
    }

    @Override
    public boolean loadChunk(int x, int z, boolean generate) {
        record("loadChunk", Optional.of(generate));
        if (!generate && chunkState(x, z) == ChunkState.UNGENERATED) {
            return false;
        }
        setChunkState(x, z, ChunkState.LOADED);
        return true;
    }

    /**
     * Loads, and generates when missing, as a real server does.
     */
    @Override
    public @NotNull ChunkMock getChunkAt(int x, int z) {
        record("getChunkAt");
        setChunkState(x, z, ChunkState.LOADED);
        return new FakeChunk(x, z);
    }

    @Override
    public Chunk getChunkAt(Location location) {
        return getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public Chunk getChunkAt(Block block) {
        return getChunkAt(block.getX() >> 4, block.getZ() >> 4);
    }

    /**
     * Paper only. The overload every other {@code getChunkAtAsync} goes through. The load finishes when the test
     * calls {@link #finishAsyncLoads()}. Without generate, an ungenerated chunk completes with null, as on Paper.
     */
    @Override
    public @NotNull CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean gen, boolean urgent) {
        record("getChunkAtAsync", Optional.of(gen));
        CompletableFuture<Chunk> load = new CompletableFuture<>();
        asyncLoads.add(() -> {
            if (!gen && chunkState(x, z) == ChunkState.UNGENERATED) {
                load.complete(null);
                return;
            }
            setChunkState(x, z, ChunkState.LOADED);
            load.complete(new FakeChunk(x, z));
        });
        return load;
    }

    /**
     * Adds the plugin's ticket, which loads the chunk, generating it when missing, as a real server does. A plugin
     * holds one ticket per chunk, so a second add returns false.
     */
    @Override
    public boolean addPluginChunkTicket(int x, int z, @NotNull Plugin plugin) {
        record("addPluginChunkTicket");
        setChunkState(x, z, ChunkState.LOADED);
        return tickets.add(chunkKey(x, z));
    }

    @Override
    public boolean removePluginChunkTicket(int x, int z, @NotNull Plugin plugin) {
        record("removePluginChunkTicket");
        return tickets.remove(chunkKey(x, z));
    }

    /**
     * The chunks a plugin holds with a ticket now, read without recording a call.
     */
    Set<ChunkPosition> chunkTickets() {
        return tickets.stream().map(key -> new ChunkPosition(chunkX(key), chunkZ(key)))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Chunk @NotNull [] getLoadedChunks() {
        record("getLoadedChunks");
        return chunks.entrySet().stream()
                .filter(chunk -> chunk.getValue() == ChunkState.LOADED)
                .map(chunk -> new FakeChunk(chunkX(chunk.getKey()), chunkZ(chunk.getKey())))
                .toArray(Chunk[]::new);
    }

    /**
     * Loads the chunk, and generates it when missing, as a real server does.
     */
    @Override
    public @NotNull BlockMock getBlockAt(int x, int y, int z) {
        record("getBlockAt");
        setChunkState(x >> 4, z >> 4, ChunkState.LOADED);
        return new FakeBlock(x, y, z);
    }

    @Override
    public @NotNull BlockMock getBlockAt(@NotNull Coordinate coordinate) {
        return getBlockAt(coordinate.x, coordinate.y, coordinate.z);
    }

    /**
     * Records the drop at the location it was asked for, before the random spread, then drops as MockBukkit does.
     */
    @Override
    public @NotNull ItemEntityMock dropItemNaturally(@NotNull Location location, @NotNull ItemStack item) {
        record("dropItemNaturally");
        drops.add(new DropCall(location.clone(), item.clone()));
        return super.dropItemNaturally(location, item);
    }

    /**
     * Records the particle spawn with the thread and task it ran in. Nothing is drawn.
     */
    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, T data) {
        record("spawnParticle");
        particles.add(SpawnedParticle.of(particle, x, y, z, count, data));
    }

    List<SpawnedParticle> particles() {
        return List.copyOf(particles);
    }

    @Override
    public @NotNull List<Player> getPlayers() {
        record("getPlayers");
        return super.getPlayers();
    }

    @Override
    public @NotNull Location getSpawnLocation() {
        record("getSpawnLocation");
        return new Location(this, spawnX, spawnY, spawnZ);
    }

    @Override
    public int getMinHeight() {
        record("getMinHeight");
        return super.getMinHeight();
    }

    @Override
    public int getMaxHeight() {
        record("getMaxHeight");
        return super.getMaxHeight();
    }

    private void record(String method) {
        record(method, Optional.empty());
    }

    private void record(String method, Optional<Boolean> generate) {
        calls.add(new WorldCall(method, generate, Bukkit.isPrimaryThread(), RecordingScheduler.runningTask()));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
    }

    private static int chunkX(long chunkKey) {
        return (int) chunkKey;
    }

    private static int chunkZ(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) y << 8) | ((long) x << 4) | z;
    }

    /**
     * One stack dropped into this world, at the location the drop was asked for.
     */
    record DropCall(Location location, ItemStack stack) {
    }

    /**
     * A block of this world, backed by the world's block map. Only the calls a block write needs work.
     */
    private final class FakeBlock extends BlockMock {

        private final int x;
        private final int y;
        private final int z;

        private FakeBlock(int x, int y, int z) {
            super(Material.AIR, new Location(FakeChunkWorld.this, x, y, z));
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public @NotNull Material getType() {
            record("getType");
            return blockType(x, y, z);
        }

        @Override
        public void setType(@NotNull Material type) {
            setType(type, true);
        }

        /**
         * Refuses an item that is not a block, as CraftBlock does.
         */
        @Override
        public void setType(@NotNull Material type, boolean applyPhysics) {
            record("setType");
            if (!type.isBlock()) {
                throw new IllegalArgumentException("not a block: " + type);
            }
            setBlock(x, y, z, type);
        }

        /**
         * The block's data, made from its type in the world map, which holds no more than the type.
         */
        @Override
        public @NotNull BlockData getBlockData() {
            record("getBlockData");
            return Bukkit.createBlockData(blockType(x, y, z));
        }

        @Override
        public void setBlockData(@NotNull BlockData data, boolean applyPhysics) {
            record("setBlockData");
            setBlock(x, y, z, data.getMaterial());
        }

        /**
         * A player head's state. Other states are not faked.
         */
        @Override
        public BlockState getState() {
            record("getState");
            Material type = blockType(x, y, z);
            if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) {
                throw new UnsupportedOperationException("only a player head's state is faked, not " + type);
            }
            return skull(type);
        }

        /**
         * A skull state that refuses a profile with neither a name nor textures, as CraftSkull does, and writes its
         * profile to the block on update while the block is still that head.
         */
        private Skull skull(Material type) {
            BlockPosition position = new BlockPosition(x, y, z);
            PlayerProfile[] profile = {heads.get(position)};
            return (Skull) Proxy.newProxyInstance(Skull.class.getClassLoader(), new Class<?>[]{Skull.class},
                    (proxy, method, args) -> switch (method.getName() + "/" + (args == null ? 0 : args.length)) {
                        case "getType/0" -> type;
                        case "getOwnerProfile/0" -> profile[0];
                        case "setOwnerProfile/1" -> {
                            PlayerProfile set = (PlayerProfile) args[0];
                            if (set != null && set.getName() == null && set.getTextures().isEmpty()) {
                                throw new IllegalArgumentException("The skull profile is missing a name or textures!");
                            }
                            profile[0] = set;
                            yield null;
                        }
                        case "update/0", "update/1", "update/2" -> {
                            record("update");
                            if (blockType(x, y, z) != type) {
                                yield false;
                            }
                            if (profile[0] == null) {
                                heads.remove(position);
                            } else {
                                heads.put(position, profile[0]);
                            }
                            yield true;
                        }
                        case "hashCode/0" -> System.identityHashCode(proxy);
                        case "equals/1" -> proxy == args[0];
                        case "toString/0" -> "FakeSkull[" + x + ", " + y + ", " + z + "]";
                        default -> throw new UnsupportedOperationException(method.toString());
                    });
        }
    }

    private final class FakeChunk extends ChunkMock {

        private FakeChunk(int x, int z) {
            super(FakeChunkWorld.this, x, z);
        }

        @Override
        public @NotNull ChunkSnapshot getChunkSnapshot() {
            return getChunkSnapshot(true, false, false);
        }

        /**
         * Copies this chunk's blocks now, as a real snapshot does. Only the calls a block read needs work.
         */
        @Override
        public @NotNull ChunkSnapshot getChunkSnapshot(boolean includeMaxblocky, boolean includeBiome,
                                                       boolean includeBiomeTempRain) {
            record("getChunkSnapshot");
            int chunkX = getX();
            int chunkZ = getZ();
            Map<Long, Material> copy = Map.copyOf(blocks.getOrDefault(chunkKey(chunkX, chunkZ), Map.of()));
            int minY = FakeChunkWorld.super.getMinHeight();
            int maxY = FakeChunkWorld.super.getMaxHeight();
            String worldName = getName();
            return (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                    new Class<?>[]{ChunkSnapshot.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> chunkX;
                        case "getZ" -> chunkZ;
                        case "getWorldName" -> worldName;
                        case "getBlockType" -> {
                            int x = (int) args[0];
                            int y = (int) args[1];
                            int z = (int) args[2];
                            if (x < 0 || x > 15 || z < 0 || z > 15 || y < minY || y >= maxY) {
                                throw new IllegalArgumentException("outside the chunk: " + x + ", " + y + ", " + z);
                            }
                            yield copy.getOrDefault(blockKey(x, y, z), Material.AIR);
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "FakeChunkSnapshot[" + chunkX + ", " + chunkZ + "]";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public boolean isLoaded() {
            return chunkState(getX(), getZ()) == ChunkState.LOADED;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getX(), getZ());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FakeChunk other && other.getX() == getX() && other.getZ() == getZ();
        }
    }
}
