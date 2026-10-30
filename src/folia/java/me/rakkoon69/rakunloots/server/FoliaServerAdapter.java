package me.rakkoon69.rakunloots.server;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The server adapter for Folia. Compiled against folia-api 1.20.1 in its own source set, so the 1.18 core never
 * compiles against Folia API. It is meant to be loaded by class name only on Folia. Folia has no main thread, and its Bukkit
 * scheduler throws, so nothing here calls it. Chunk and block work runs on the region scheduler for that chunk, player
 * reads and inventory views on each player's entity scheduler, the world spawn on the global region scheduler, and
 * scheduled tasks on the region, entity, global region, or async scheduler.
 */
public final class FoliaServerAdapter implements ServerAdapter {

    private final Plugin plugin;
    private final AtomicBoolean unsupportedCloseWarned = new AtomicBoolean();

    public FoliaServerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Folia support is no longer experimental: 3 version test runs in a row on Folia 26.2 had no CHUNK_UNLOADED and no
     * ERROR. The laser stays off, with one low warning.
     */
    @Override
    public boolean experimental() {
        return false;
    }

    /**
     * GuardianBeam runs its beams on the Bukkit scheduler, which Folia refuses.
     */
    @Override
    public boolean laserSupported() {
        return false;
    }

    @Override
    public boolean dropsEntitiesAtStop() {
        return true;
    }

    /**
     * Looks the world up on the calling thread, since the region scheduler needs it; Folia does not load or unload
     * worlds after startup. The rest runs in a task on the region that owns the chunk. A chunk already loaded is
     * snapshot there under every policy. An unloaded chunk is a miss under skip, and otherwise goes through the async
     * chunk call with the generate flag from the policy. The snapshot of a chunk that call loaded is taken in another
     * task on that region, whatever thread completed the call. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Optional<ChunkView>> readChunk(UUID worldKey, int chunkX, int chunkZ, UnloadedChunkPolicy policy) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(policy, "policy");
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return onRegion(world, chunkX, chunkZ, () -> readOnRegion(worldKey, world, chunkX, chunkZ, policy))
                .thenCompose(Function.identity());
    }

    private CompletableFuture<Optional<ChunkView>> readOnRegion(UUID worldKey, World world, int chunkX, int chunkZ,
                                                                UnloadedChunkPolicy policy) {
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            return CompletableFuture.completedFuture(
                    Optional.of(BukkitChunkViews.of(worldKey, world, world.getChunkAt(chunkX, chunkZ))));
        }
        return switch (policy) {
            case SKIP -> CompletableFuture.completedFuture(Optional.empty());
            case LOAD -> loadAsync(worldKey, world, chunkX, chunkZ, false);
            case GENERATE -> loadAsync(worldKey, world, chunkX, chunkZ, true);
        };
    }

    /**
     * Loads through the async chunk call, then snapshots the chunk on its region. Without generate, the call
     * completes with null for a chunk that is not generated, which is a miss. Should the chunk unload before the
     * second region task runs, the snapshot reloads it there, on the region that owns it.
     */
    private CompletableFuture<Optional<ChunkView>> loadAsync(UUID worldKey, World world, int chunkX, int chunkZ,
                                                             boolean generate) {
        return world.getChunkAtAsync(chunkX, chunkZ, generate).thenCompose((Chunk chunk) -> chunk == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : onRegion(world, chunkX, chunkZ, () -> Optional.of(BukkitChunkViews.of(worldKey, world, chunk))));
    }

    /**
     * Runs the read in one task on the region that owns the chunk. The future completes on that region's thread with
     * what the read returned, or fails with what it threw. It also fails when the task cannot be scheduled, as for a
     * disabled plugin.
     */
    private <T> CompletableFuture<T> onRegion(World world, int chunkX, int chunkZ, Supplier<T> read) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                try {
                    result.complete(read.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException notScheduled) {
            result.completeExceptionally(notScheduled);
        }
        return result;
    }

    /**
     * Lists the loaded chunks on the calling thread, and returns a future that is already complete. Folia puts no
     * thread check on {@link World#getLoadedChunks()}, since it reads the chunk system's concurrent chunk table, and
     * the chunks it returns are read only for their x and z. No region owns every chunk, so no scheduler would make
     * the read safer.
     */
    @Override
    public CompletableFuture<Set<ChunkPosition>> loadedChunks(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(Set.of());
        }
        return CompletableFuture.completedFuture(Arrays.stream(world.getLoadedChunks())
                .map(chunk -> new ChunkPosition(chunk.getX(), chunk.getZ()))
                .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Lists the world's players on the calling thread, which Folia allows, since it keeps them in a copy-on-write
     * list. Each player's position is then read in a task on that player's entity scheduler, which runs on the region
     * that owns the player. A player who leaves before their task runs, or who is in another world by then, is left
     * out, and the gather still completes. The future completes on the thread that finished the last read.
     */
    @Override
    public CompletableFuture<List<PlayerPosition>> playerPositions(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<Optional<PlayerPosition>>> reads = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            reads.add(positionOnEntity(player, worldKey));
        }
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new))
                .thenApply(done -> reads.stream().map(CompletableFuture::join).flatMap(Optional::stream).toList());
    }

    /**
     * Reads one player's position on their entity scheduler. Empty when the player left, and so their scheduler
     * retired, or when they are no longer in the world.
     */
    private CompletableFuture<Optional<PlayerPosition>> positionOnEntity(Player player, UUID worldKey) {
        return onEntity(player, () -> {
            Location location = player.getLocation();
            World currentWorld = location.getWorld();
            return currentWorld != null && currentWorld.getUID().equals(worldKey)
                    ? Optional.of(new PlayerPosition(player.getUniqueId(), location.getX(), location.getY(),
                    location.getZ()))
                    : Optional.empty();
        }, Optional.empty());
    }

    /**
     * Runs the work in one task on the player's entity scheduler. The future completes on the thread that owns the
     * player with what the work returned, or fails with what it threw. It completes with the left-out value when the
     * player left, and so their scheduler retired, before the task ran. It also completes with it when the plugin is
     * already disabled, as during its own stop: Folia drops the callbacks of a disabled plugin, so waiting on one
     * would never end. It fails when the task cannot be scheduled.
     */
    private <T> CompletableFuture<T> onEntity(Player player, Supplier<T> work, T leftOutValue) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!plugin.isEnabled()) {
            result.complete(leftOutValue);
            return result;
        }
        Runnable leftOut = () -> result.complete(leftOutValue);
        try {
            boolean scheduled = player.getScheduler().execute(plugin, () -> {
                try {
                    result.complete(work.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            }, leftOut, 1L);
            if (!scheduled) {
                leftOut.run();
            }
        } catch (RuntimeException notScheduled) {
            result.completeExceptionally(notScheduled);
        }
        return result;
    }

    /**
     * Lists loaded worlds on the global region, the same scheduler that reads the world spawn.
     */
    @Override
    public CompletableFuture<List<LoadedWorld>> loadedWorlds() {
        return onGlobal(() -> plugin.getServer().getWorlds().stream()
                .map(world -> new LoadedWorld(world.getUID(), world.getName()))
                .toList());
    }

    /**
     * Looks the world up and reads its spawn in one task on the global region scheduler, the region that owns the
     * world spawn. The future completes on the global region's thread.
     */
    @Override
    public CompletableFuture<Optional<BlockPosition>> worldSpawn(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        return onGlobal(() -> {
            World world = plugin.getServer().getWorld(worldKey);
            if (world == null) {
                return Optional.empty();
            }
            Location spawn = world.getSpawnLocation();
            return Optional.of(new BlockPosition(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()));
        });
    }

    /**
     * Runs the read in one task on the global region scheduler. The future completes on the global region's thread
     * with what the read returned, or fails with what it threw. It also fails when the task cannot be scheduled.
     */
    private <T> CompletableFuture<T> onGlobal(Supplier<T> read) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                try {
                    result.complete(read.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException notScheduled) {
            result.completeExceptionally(notScheduled);
        }
        return result;
    }

    /**
     * Checks the block and head data, and looks the world up, on the calling thread. The block is set in one task
     * on the region that owns it. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Boolean> setBlock(UUID worldKey, BlockPosition position, String blockId,
                                               Optional<String> headData) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        BukkitBlocks.Placement placement = BukkitBlocks.placement(blockId, headData);
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onRegion(world, position, () -> BukkitBlocks.set(plugin.getServer(), world, position, placement));
    }

    /**
     * Checks both ids and the head data, and looks the world up, on the calling thread. The block is read and set in
     * one task on the region that owns it. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Boolean> replaceBlock(UUID worldKey, BlockPosition position, String expectedBlockId,
                                                   String blockId, Optional<String> headData) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        Material expected = BukkitBlocks.blockType(expectedBlockId);
        BukkitBlocks.Placement placement = BukkitBlocks.placement(blockId, headData);
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onRegion(world, position,
                () -> BukkitBlocks.replace(plugin.getServer(), world, position, expected, placement));
    }

    /**
     * Looks the world up on the calling thread, and reads the block in one task on the region that owns it. The
     * future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Optional<String>> readBlock(UUID worldKey, BlockPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return onRegion(world, position, () -> BukkitBlocks.read(world, position));
    }

    /**
     * Looks the world up and reads the block on the calling thread, which must be the region that owns the block.
     */
    @Override
    public Optional<String> readBlockHere(UUID worldKey, BlockPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return Optional.empty();
        }
        if (!plugin.getServer().isOwnedByCurrentRegion(world, position.x() >> 4, position.z() >> 4)) {
            throw new IllegalStateException("a block is read here only on the region that owns it");
        }
        return BukkitBlocks.read(world, position);
    }

    /**
     * Checks the id, and looks the world up, on the calling thread. The block is read and removed in one task on
     * the region that owns it. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Boolean> removeBlock(UUID worldKey, BlockPosition position, String blockId) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        Material type = BukkitBlocks.blockType(blockId);
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onRegion(world, position, () -> BukkitBlocks.remove(world, position, type));
    }

    /**
     * Checks the id, and looks the world up, on the calling thread. The chunk is loaded first through the async
     * chunk call, without generate, which Folia lets any thread start and which is a no-op for a loaded chunk. The
     * block is then read and removed in one task on the region that owns it, so no region task waits on a chunk
     * that is not loaded. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Boolean> removeBlockLoadingChunk(UUID worldKey, BlockPosition position, String blockId) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        Material type = BukkitBlocks.blockType(blockId);
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return world.getChunkAtAsync(position.x() >> 4, position.z() >> 4, false).thenCompose(chunk -> chunk == null
                ? CompletableFuture.completedFuture(false)
                : onRegion(world, position, () -> BukkitBlocks.removeLoading(world, position, type)));
    }

    /**
     * Looks the world up on the calling thread. The chunk is loaded first through the async chunk call, without
     * generate, as for {@link #removeBlockLoadingChunk}. The plugin's chunk ticket is then added in one task on the
     * region that owns the chunk, since a ticket changes what that region ticks. The future completes on that
     * region's thread.
     */
    @Override
    public CompletableFuture<Boolean> holdChunk(UUID worldKey, int chunkX, int chunkZ) {
        Objects.requireNonNull(worldKey, "worldKey");
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return world.getChunkAtAsync(chunkX, chunkZ, false).thenCompose(chunk -> chunk == null
                ? CompletableFuture.completedFuture(false)
                : onRegion(world, chunkX, chunkZ, () -> {
                    world.addPluginChunkTicket(chunkX, chunkZ, plugin);
                    return true;
                }));
    }

    /**
     * Looks the world up on the calling thread. The plugin's chunk ticket is removed in one task on the region that
     * owns the chunk. When that task cannot be scheduled, as for a plugin that is stopping, the ticket is removed on
     * the calling thread instead, so a stop still gives it back. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Boolean> releaseChunk(UUID worldKey, int chunkX, int chunkZ) {
        Objects.requireNonNull(worldKey, "worldKey");
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onRegion(world, chunkX, chunkZ, () -> world.removePluginChunkTicket(chunkX, chunkZ, plugin))
                .handle((released, failure) -> {
                    if (failure == null) {
                        return released;
                    }
                    if (plugin.isEnabled()) {
                        throw new CompletionException(failure);
                    }
                    return world.removePluginChunkTicket(chunkX, chunkZ, plugin);
                });
    }

    /**
     * Copies the stacks, and looks the world up, on the calling thread. The stacks drop in one task on the region
     * that owns the block. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Boolean> dropStacks(UUID worldKey, BlockPosition position, List<ItemStack> stacks) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        List<ItemStack> copies = BukkitBlocks.copies(stacks);
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onRegion(world, position, () -> BukkitBlocks.drop(world, position, copies));
    }

    private <T> CompletableFuture<T> onRegion(World world, BlockPosition position, Supplier<T> work) {
        return onRegion(world, position.x() >> 4, position.z() >> 4, work);
    }

    /**
     * Resolves the particle names, and looks the world up, on the calling thread. The particles are drawn in one task
     * on the region that owns the owner block. The future completes on that region's thread.
     */
    @Override
    public CompletableFuture<Boolean> drawParticles(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(owner, "owner");
        List<BukkitParticles.Resolved> resolved = BukkitParticles.resolve(draws);
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onRegion(world, owner, () -> BukkitParticles.draw(world, owner, resolved));
    }

    @Override
    public Optional<ItemStack> blockItem(String blockId, Optional<String> headData) {
        return BukkitBlocks.item(plugin.getServer(), BukkitBlocks.placement(blockId, headData));
    }

    /**
     * Looks the player up on the calling thread, which Folia allows, and sends the message in a task on the player's
     * entity scheduler. A player who leaves before it runs does not get it. The future completes on the thread that
     * owns the player.
     */
    @Override
    public CompletableFuture<Boolean> sendMessage(UUID playerId, String message) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(message, "message");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onEntity(player, () -> {
            BukkitMainThread.sendLines(player, message);
            return true;
        }, false);
    }

    /**
     * Looks the player up on the calling thread and reads the room in a task on the player's entity scheduler. The
     * future completes on the thread that owns the player, with 0 for a player who is not online or leaves first.
     */
    @Override
    public CompletableFuture<Integer> inventoryRoom(UUID playerId, ItemStack item) {
        Objects.requireNonNull(playerId, "playerId");
        ItemStack copy = item.clone();
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(0);
        }
        return onEntity(player, () -> BukkitInventories.room(player, copy), 0);
    }

    /**
     * Looks the player up on the calling thread and gives the items in a task on the player's entity scheduler. The
     * future completes on the thread that owns the player, with 0 for a player who is not online or leaves first.
     */
    @Override
    public CompletableFuture<Integer> giveItems(UUID playerId, ItemStack item, int amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
        ItemStack copy = item.clone();
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(0);
        }
        return onEntity(player, () -> BukkitInventories.give(player, copy, amount), 0);
    }

    @Override
    public SharedInventory createSharedInventory(String title, int size, Map<Integer, ItemStack> contents) {
        return BukkitSharedInventory.create(plugin.getServer(), title, size, contents);
    }

    /**
     * Looks the player up on the calling thread, which Folia allows, and opens the inventory in a task on the
     * player's entity scheduler. A player who leaves before it runs does not see it, and neither does one it was
     * closed for first. The future completes on the thread that owns the player.
     */
    @Override
    public CompletableFuture<Boolean> openSharedInventory(UUID playerId, SharedInventory inventory) {
        Objects.requireNonNull(playerId, "playerId");
        BukkitSharedInventory shared = BukkitSharedInventory.of(inventory);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onEntity(player, () -> shared.open(player), false);
    }

    /**
     * Closes the inventory for good, then closes each given viewer's view in a task on that viewer's entity
     * scheduler. A viewer who is not online is skipped. The future completes once every close has run.
     */
    @Override
    public CompletableFuture<Void> closeSharedInventory(SharedInventory inventory, Set<UUID> viewers) {
        BukkitSharedInventory shared = BukkitSharedInventory.of(inventory);
        shared.markClosed();
        List<CompletableFuture<?>> closes = new ArrayList<>();
        for (UUID playerId : Set.copyOf(viewers)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            closes.add(onEntity(player, () -> {
                shared.close(player);
                return null;
            }, null));
        }
        return CompletableFuture.allOf(closes.toArray(CompletableFuture[]::new));
    }

    @Override
    public MenuHandle createMenu(UUID playerId, String title, int size, Map<Integer, ItemStack> contents) {
        return BukkitMenuInventory.create(plugin.getServer(), playerId, title, size, contents);
    }

    /**
     * Looks the player up on the calling thread, which Folia allows, and opens the menu in a task on the player's
     * entity scheduler. A player who leaves before it runs does not see it, and neither does one it was closed for
     * first. The future completes on the thread that owns the player.
     */
    @Override
    public CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu) {
        return openMenu(playerId, menu, null);
    }

    @Override
    public CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu, MenuHandle expectedOld) {
        Objects.requireNonNull(playerId, "playerId");
        BukkitMenuInventory made = BukkitMenuInventory.of(menu);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return onEntity(player, () -> made.open(player, expectedOld) ? Optional.<MenuHandle>of(made) : Optional.<MenuHandle>empty(),
                Optional.empty());
    }

    @Override
    public boolean isPhysicalOpenInProgress(UUID playerId) {
        return BukkitMenuInventory.isPhysicalOpen(playerId);
    }

    @Override
    public CompletableFuture<Void> updateMenu(MenuHandle menu, Map<Integer, ItemStack> contents) {
        BukkitMenuInventory made = BukkitMenuInventory.of(menu);
        Player player = plugin.getServer().getPlayer(made.playerId());
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        return onEntity(player, () -> {
            made.replace(contents);
            return null;
        }, null);
    }

    /**
     * Closes the menu on the player's entity scheduler. At server shutdown Folia stops region ticking before it
     * disables plugins, with players still online. The shutdown thread owns those players according to the public
     * ownership API, so a close issued there can write the physical inventory at once; the disabled plugin cannot
     * schedule a task that would never run. A disabled close from a thread that does not own the player completes
     * without writing another region's player, which the server cleans up when it drops the view. That unsupported
     * live-disable branch warns once per adapter, since the server may leave the menu items accessible.
     */
    @Override
    public CompletableFuture<Void> closeMenu(MenuHandle menu) {
        BukkitMenuInventory made = BukkitMenuInventory.of(menu);
        made.markClosed();
        Player player = plugin.getServer().getPlayer(made.playerId());
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!plugin.isEnabled()) {
            try {
                if (plugin.getServer().isOwnedByCurrentRegion(player)) {
                    made.close(player);
                } else if (unsupportedCloseWarned.compareAndSet(false, true)) {
                    plugin.getLogger().warning("Live unloading RakunLoots on Folia is unsupported: a menu could not "
                            + "be closed safely, so its menu items may remain accessible. Restart the server after "
                            + "changing the plugin; use the plugin's own reload for config changes.");
                }
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
            return CompletableFuture.completedFuture(null);
        }
        return onEntity(player, () -> {
            made.close(player);
            return null;
        }, null);
    }

    @Override
    public CompletableFuture<Void> runOnPlayerNextTick(UUID playerId, Runnable action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        return onEntity(player, () -> {
            action.run();
            return null;
        }, null);
    }

    /**
     * Plays the sound on the player's entity scheduler. The key is the server's own sound name, so it needs no enum
     * lookup and no version adapter.
     */
    @Override
    public CompletableFuture<Boolean> playSound(UUID playerId, String key, double volume, double pitch) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(key, "key");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        return onEntity(player, () -> {
            player.playSound(player.getLocation(), key, (float) volume, (float) pitch);
            return true;
        }, false);
    }

    /**
     * Runs on the region scheduler for the chunk that holds the block. The world is looked up when each task is
     * scheduled, and scheduling fails with {@link IllegalStateException} while it is not loaded.
     */
    @Override
    public TaskScheduler atLocation(UUID worldKey, int blockX, int blockZ) {
        Objects.requireNonNull(worldKey, "worldKey");
        return FoliaTaskScheduler.region(plugin, worldKey, blockX, blockZ);
    }

    /**
     * Runs on the player's entity scheduler, on the region that owns the player. A task never runs for a player who
     * is not online when it is scheduled, and stops when the player leaves.
     */
    @Override
    public TaskScheduler forPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return FoliaTaskScheduler.entity(plugin, playerId);
    }

    /**
     * Runs on the global region scheduler.
     */
    @Override
    public TaskScheduler global() {
        return FoliaTaskScheduler.global(plugin);
    }

    /**
     * Runs on Folia's async scheduler, which counts time; each tick is 50 milliseconds.
     */
    @Override
    public TaskScheduler async() {
        return FoliaTaskScheduler.async(plugin);
    }
}
