package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;

import org.bukkit.block.data.BlockData;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A hand fake of a Folia world whose chunks are ungenerated, generated but unloaded, or loaded, as the test sets
 * them, whose spawn block the test may move, and whose players the fake server moves in and out. It records each
 * call made on it, and on its chunks and blocks, with the owner whose task it ran in. Loading a chunk the way a real
 * server does, including {@code getChunkAt} and {@code getBlockAt}, changes its state. An async chunk load completes
 * on a chunk loader thread of its own, which is not a region thread, since folia-api does not say which thread
 * completes it. Blocks the test did not set are air. A player head keeps the profile its block state was updated
 * with, until the block changes. Drops are recorded, not spawned.
 */
final class FakeFoliaWorld {

    /**
     * One call made on this world or one of its chunks.
     *
     * @param generate the generate flag a load call was given; empty for other calls
     * @param owner    the owner whose task the call ran in, if any
     */
    record WorldCall(String method, Optional<Boolean> generate, Optional<FakeFoliaTicks.Owner> owner,
                     String thread) {

        @Override
        public String toString() {
            return method + generate.map(flag -> "(generate=" + flag + ")").orElse("")
                    + " on " + thread + owner.map(each -> " in " + each).orElse(" in no task");
        }
    }

    /**
     * One stack dropped into this world, at the location the drop was asked for.
     */
    record DropCall(Location location, ItemStack stack) {
    }

    private final UUID key = UUID.randomUUID();
    private final int minHeight;
    private final int maxHeight;
    private final Map<Long, ChunkState> chunks = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Material>> blocks = new ConcurrentHashMap<>();
    private final List<WorldCall> calls = new CopyOnWriteArrayList<>();
    private final Map<BlockPosition, PlayerProfile> heads = new ConcurrentHashMap<>();
    private final List<DropCall> drops = new CopyOnWriteArrayList<>();
    private final List<SpawnedParticle> particles = new CopyOnWriteArrayList<>();
    private final List<Player> players = new CopyOnWriteArrayList<>();
    private final Set<Long> tickets = ConcurrentHashMap.newKeySet();
    private final ExecutorService chunkLoader;
    private final World world;
    private volatile int spawnX;
    private volatile int spawnY = 64;
    private volatile int spawnZ;

    FakeFoliaWorld(int minHeight, int maxHeight, ExecutorService chunkLoader) {
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.chunkLoader = chunkLoader;
        this.world = FoliaFakes.fake(World.class, "FakeFoliaWorld[" + key + "]", (proxy, method, args) ->
                switch (method.getName() + "/" + args.length) {
                    case "getUID/0" -> key;
                    case "getName/0" -> "world-" + key;
                    case "getMinHeight/0" -> {
                        record("getMinHeight");
                        yield minHeight;
                    }
                    case "getMaxHeight/0" -> {
                        record("getMaxHeight");
                        yield maxHeight;
                    }
                    case "isChunkLoaded/2" -> {
                        record("isChunkLoaded");
                        yield chunkState((int) args[0], (int) args[1]) == ChunkState.LOADED;
                    }
                    case "getChunkAt/2" -> {
                        record("getChunkAt");
                        setChunkState((int) args[0], (int) args[1], ChunkState.LOADED);
                        yield chunk((int) args[0], (int) args[1]);
                    }
                    case "addPluginChunkTicket/3" -> {
                        record("addPluginChunkTicket");
                        setChunkState((int) args[0], (int) args[1], ChunkState.LOADED);
                        yield tickets.add(chunkKey((int) args[0], (int) args[1]));
                    }
                    case "removePluginChunkTicket/3" -> {
                        record("removePluginChunkTicket");
                        yield tickets.remove(chunkKey((int) args[0], (int) args[1]));
                    }
                    case "getChunkAtAsync/4" -> getChunkAtAsync((int) args[0], (int) args[1], (boolean) args[2]);
                    case "getLoadedChunks/0" -> {
                        record("getLoadedChunks");
                        yield chunks.entrySet().stream()
                                .filter(each -> each.getValue() == ChunkState.LOADED)
                                .map(each -> chunk((int) (long) each.getKey(), (int) (each.getKey() >> 32)))
                                .toArray(Chunk[]::new);
                    }
                    case "getPlayers/0" -> {
                        record("getPlayers");
                        yield List.copyOf(players);
                    }
                    case "getSpawnLocation/0" -> {
                        record("getSpawnLocation");
                        yield new Location((World) proxy, spawnX, spawnY, spawnZ);
                    }
                    case "getBlockAt/3" -> {
                        record("getBlockAt");
                        int x = (int) args[0];
                        int z = (int) args[2];
                        setChunkState(x >> 4, z >> 4, ChunkState.LOADED);
                        yield block(x, (int) args[1], z);
                    }
                    case "dropItemNaturally/2" -> {
                        record("dropItemNaturally");
                        drops.add(new DropCall(((Location) args[0]).clone(), ((ItemStack) args[1]).clone()));
                        yield null;
                    }
                    case "spawnParticle/6" -> {
                        record("spawnParticle");
                        particles.add(SpawnedParticle.of((Particle) args[0], (double) args[1], (double) args[2],
                                (double) args[3], (int) args[4], args[5]));
                        yield null;
                    }
                    default -> FoliaFakes.UNHANDLED;
                });
    }

    List<SpawnedParticle> particles() {
        return List.copyOf(particles);
    }

    UUID key() {
        return key;
    }

    World world() {
        return world;
    }

    void setChunkState(int chunkX, int chunkZ, ChunkState state) {
        chunks.put(chunkKey(chunkX, chunkZ), state);
    }

    ChunkState chunkState(int chunkX, int chunkZ) {
        return chunks.getOrDefault(chunkKey(chunkX, chunkZ), ChunkState.UNGENERATED);
    }

    void setBlock(int x, int y, int z, Material material) {
        blocks.computeIfAbsent(chunkKey(x >> 4, z >> 4), each -> new ConcurrentHashMap<>())
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

    /**
     * The chunks a plugin holds with a ticket now, read without recording a call.
     */
    Set<ChunkPosition> chunkTickets() {
        return tickets.stream().map(key -> new ChunkPosition((int) (long) key, (int) (key >> 32)))
                .collect(Collectors.toUnmodifiableSet());
    }

    List<DropCall> drops() {
        return List.copyOf(drops);
    }

    void setSpawn(int x, int y, int z) {
        spawnX = x;
        spawnY = y;
        spawnZ = z;
    }

    void addPlayer(Player player) {
        players.add(player);
    }

    void removePlayer(Player player) {
        players.remove(player);
    }

    List<WorldCall> calls() {
        return List.copyOf(calls);
    }

    /**
     * The overload every other {@code getChunkAtAsync} goes through. Without generate, an ungenerated chunk
     * completes with null, as on Paper.
     */
    private CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean generate) {
        record("getChunkAtAsync", Optional.of(generate));
        return CompletableFuture.supplyAsync(() -> {
            if (!generate && chunkState(x, z) == ChunkState.UNGENERATED) {
                return null;
            }
            setChunkState(x, z, ChunkState.LOADED);
            return chunk(x, z);
        }, chunkLoader);
    }

    private Chunk chunk(int chunkX, int chunkZ) {
        return FoliaFakes.fake(Chunk.class, "FakeFoliaChunk[" + chunkX + ", " + chunkZ + "]",
                (proxy, method, args) -> switch (method.getName() + "/" + args.length) {
                    case "getX/0" -> chunkX;
                    case "getZ/0" -> chunkZ;
                    case "getWorld/0" -> world;
                    case "isLoaded/0" -> chunkState(chunkX, chunkZ) == ChunkState.LOADED;
                    case "getChunkSnapshot/0", "getChunkSnapshot/3" -> snapshot(chunkX, chunkZ);
                    default -> FoliaFakes.UNHANDLED;
                });
    }

    /**
     * Copies the chunk's blocks now, as a real snapshot does. Only the calls a block read needs work.
     */
    private ChunkSnapshot snapshot(int chunkX, int chunkZ) {
        record("getChunkSnapshot");
        Map<Long, Material> copy = Map.copyOf(blocks.getOrDefault(chunkKey(chunkX, chunkZ), Map.of()));
        return FoliaFakes.fake(ChunkSnapshot.class, "FakeFoliaChunkSnapshot[" + chunkX + ", " + chunkZ + "]",
                (proxy, method, args) -> switch (method.getName() + "/" + args.length) {
                    case "getX/0" -> chunkX;
                    case "getZ/0" -> chunkZ;
                    case "getBlockType/3" -> {
                        int x = (int) args[0];
                        int y = (int) args[1];
                        int z = (int) args[2];
                        if (x < 0 || x > 15 || z < 0 || z > 15 || y < minHeight || y >= maxHeight) {
                            throw new IllegalArgumentException("outside the chunk: " + x + ", " + y + ", " + z);
                        }
                        yield copy.getOrDefault(blockKey(x, y, z), Material.AIR);
                    }
                    default -> FoliaFakes.UNHANDLED;
                });
    }

    /**
     * A block of this world, backed by the world's block map. Only the calls a block write needs work. Setting a
     * type that is not a block is refused, as CraftBlock does.
     */
    private Block block(int x, int y, int z) {
        return FoliaFakes.fake(Block.class, "FakeFoliaBlock[" + x + ", " + y + ", " + z + "]",
                (proxy, method, args) -> switch (method.getName() + "/" + args.length) {
                    case "getX/0" -> x;
                    case "getY/0" -> y;
                    case "getZ/0" -> z;
                    case "getWorld/0" -> world;
                    case "getType/0" -> {
                        record("getType");
                        yield blockType(x, y, z);
                    }
                    case "setType/1", "setType/2" -> {
                        record("setType");
                        Material type = (Material) args[0];
                        if (!type.isBlock()) {
                            throw new IllegalArgumentException("not a block: " + type);
                        }
                        setBlock(x, y, z, type);
                        yield null;
                    }
                    case "getBlockData/0" -> {
                        record("getBlockData");
                        Material type = blockType(x, y, z);
                        yield FoliaFakes.fake(BlockData.class, "FakeFoliaBlockData[" + type + "]",
                                (data, dataMethod, dataArgs) -> dataMethod.getName().equals("getMaterial")
                                        ? type : FoliaFakes.UNHANDLED);
                    }
                    case "setBlockData/1", "setBlockData/2" -> {
                        record("setBlockData");
                        setBlock(x, y, z, ((BlockData) args[0]).getMaterial());
                        yield null;
                    }
                    case "getState/0" -> {
                        record("getState");
                        Material type = blockType(x, y, z);
                        if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) {
                            throw new UnsupportedOperationException("only a player head's state is faked, not " + type);
                        }
                        yield skull(x, y, z, type);
                    }
                    default -> FoliaFakes.UNHANDLED;
                });
    }

    /**
     * A skull state that refuses a profile with neither a name nor textures, as CraftSkull does, and writes its
     * profile to the block on update while the block is still that head.
     */
    private Skull skull(int x, int y, int z, Material type) {
        BlockPosition position = new BlockPosition(x, y, z);
        PlayerProfile[] profile = {heads.get(position)};
        return FoliaFakes.fake(Skull.class, "FakeFoliaSkull[" + x + ", " + y + ", " + z + "]",
                (proxy, method, args) -> switch (method.getName() + "/" + args.length) {
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
                    default -> FoliaFakes.UNHANDLED;
                });
    }

    private void record(String method) {
        record(method, Optional.empty());
    }

    private void record(String method, Optional<Boolean> generate) {
        calls.add(new WorldCall(method, generate, FakeFoliaTicks.currentOwner(),
                Thread.currentThread().getName()));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) y << 8) | ((long) x << 4) | z;
    }
}
