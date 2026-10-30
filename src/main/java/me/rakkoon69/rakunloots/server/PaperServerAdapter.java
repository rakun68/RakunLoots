package me.rakkoon69.rakunloots.server;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The server adapter for Paper and its forks, except Folia. Compiled against the 1.18 API. Unloaded chunks load
 * through Paper's async chunk call, so a read never blocks the main thread on a chunk load.
 */
public final class PaperServerAdapter implements ServerAdapter {

    private final Plugin plugin;

    public PaperServerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Looks the world and chunk up in one main-thread task. A chunk already loaded is snapshot there under every
     * policy, since nothing needs loading. An unloaded chunk is a miss under skip, and otherwise goes through the
     * async chunk call with the generate flag from the policy; Paper completes that call on the main thread, where
     * the snapshot is taken. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Optional<ChunkView>> readChunk(UUID worldKey, int chunkX, int chunkZ, UnloadedChunkPolicy policy) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(policy, "policy");
        return BukkitMainThread.supply(plugin, () -> readOnMainThread(worldKey, chunkX, chunkZ, policy))
                .thenCompose(Function.identity());
    }

    private static CompletableFuture<Optional<ChunkView>> readOnMainThread(UUID worldKey, int chunkX, int chunkZ,
                                                                           UnloadedChunkPolicy policy) {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
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
     * Loads through Paper's async chunk call and snapshots the chunk when the load completes, on the main thread.
     * Without generate, Paper completes with null for a chunk that is not generated, which is a miss.
     */
    private static CompletableFuture<Optional<ChunkView>> loadAsync(UUID worldKey, World world, int chunkX, int chunkZ,
                                                                    boolean generate) {
        return world.getChunkAtAsync(chunkX, chunkZ, generate).thenApply(chunk -> chunk == null
                ? Optional.empty()
                : Optional.of(BukkitChunkViews.of(worldKey, world, chunk)));
    }

    /**
     * Reads the loaded chunks in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Set<ChunkPosition>> loadedChunks(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        return BukkitMainThread.supply(plugin, () -> BukkitWorldState.loadedChunks(worldKey));
    }

    /**
     * Reads every player's position in one main-thread task, so the snapshot is one moment. The future completes
     * on the main thread.
     */
    @Override
    public CompletableFuture<List<PlayerPosition>> playerPositions(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        return BukkitMainThread.supply(plugin, () -> BukkitWorldState.playerPositions(worldKey));
    }

    /**
     * Reads the spawn in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Optional<BlockPosition>> worldSpawn(UUID worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        return BukkitMainThread.supply(plugin, () -> BukkitWorldState.worldSpawn(worldKey));
    }

    @Override
    public CompletableFuture<List<LoadedWorld>> loadedWorlds() {
        return BukkitMainThread.supply(plugin, () -> Bukkit.getWorlds().stream()
                .map(world -> new LoadedWorld(world.getUID(), world.getName()))
                .toList());
    }

    /**
     * Checks the block and head data on the calling thread, then sets the block in one main-thread task. The future
     * completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> setBlock(UUID worldKey, BlockPosition position, String blockId,
                                               Optional<String> headData) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        BukkitBlocks.Placement placement = BukkitBlocks.placement(blockId, headData);
        return BukkitMainThread.supply(plugin,
                () -> BukkitBlocks.set(plugin.getServer(), Bukkit.getWorld(worldKey), position, placement));
    }

    /**
     * Checks both ids and the head data on the calling thread, then reads and sets the block in one main-thread task.
     * The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> replaceBlock(UUID worldKey, BlockPosition position, String expectedBlockId,
                                                   String blockId, Optional<String> headData) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        Material expected = BukkitBlocks.blockType(expectedBlockId);
        BukkitBlocks.Placement placement = BukkitBlocks.placement(blockId, headData);
        return BukkitMainThread.supply(plugin, () -> BukkitBlocks.replace(plugin.getServer(),
                Bukkit.getWorld(worldKey), position, expected, placement));
    }

    /**
     * Reads the block in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Optional<String>> readBlock(UUID worldKey, BlockPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        return BukkitMainThread.supply(plugin, () -> BukkitBlocks.read(Bukkit.getWorld(worldKey), position));
    }

    /**
     * Reads the block on the calling thread, which must be the main thread.
     */
    @Override
    public Optional<String> readBlockHere(UUID worldKey, BlockPosition position) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("a block is read here only on the main thread");
        }
        return BukkitBlocks.read(Bukkit.getWorld(worldKey), position);
    }

    /**
     * Checks the id on the calling thread, then reads and removes the block in one main-thread task. The future
     * completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> removeBlock(UUID worldKey, BlockPosition position, String blockId) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        Material type = BukkitBlocks.blockType(blockId);
        return BukkitMainThread.supply(plugin, () -> BukkitBlocks.remove(Bukkit.getWorld(worldKey), position, type));
    }

    /**
     * Checks the id on the calling thread. In one main-thread task, a block in a loaded chunk is removed at once. A
     * block in an unloaded chunk goes through Paper's async chunk call, without generate, and is removed when the
     * load completes, on the main thread. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> removeBlockLoadingChunk(UUID worldKey, BlockPosition position, String blockId) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        Material type = BukkitBlocks.blockType(blockId);
        return BukkitMainThread.supply(plugin, () -> removeLoadingOnMainThread(worldKey, position, type))
                .thenCompose(Function.identity());
    }

    private static CompletableFuture<Boolean> removeLoadingOnMainThread(UUID worldKey, BlockPosition position,
                                                                        Material type) {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        int chunkX = position.x() >> 4;
        int chunkZ = position.z() >> 4;
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            return CompletableFuture.completedFuture(BukkitBlocks.remove(world, position, type));
        }
        return world.getChunkAtAsync(chunkX, chunkZ, false)
                .thenApply(chunk -> chunk != null && BukkitBlocks.removeLoading(world, position, type));
    }

    /**
     * In one main-thread task, adds the plugin's chunk ticket to a loaded chunk, or loads a chunk that is not loaded
     * through Paper's async chunk call, without generate, and adds the ticket when the load completes, on the main
     * thread. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> holdChunk(UUID worldKey, int chunkX, int chunkZ) {
        Objects.requireNonNull(worldKey, "worldKey");
        return BukkitMainThread.supply(plugin, () -> holdOnMainThread(worldKey, chunkX, chunkZ))
                .thenCompose(Function.identity());
    }

    private CompletableFuture<Boolean> holdOnMainThread(UUID worldKey, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            return CompletableFuture.completedFuture(true);
        }
        return world.getChunkAtAsync(chunkX, chunkZ, false).thenApply(chunk -> {
            if (chunk == null) {
                return false;
            }
            world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            return true;
        });
    }

    /**
     * Removes the ticket on the calling thread when that is the main thread, so a plugin that is stopping can release
     * without a scheduler. Otherwise in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> releaseChunk(UUID worldKey, int chunkX, int chunkZ) {
        Objects.requireNonNull(worldKey, "worldKey");
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(releaseOnMainThread(worldKey, chunkX, chunkZ));
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        return BukkitMainThread.supply(plugin, () -> releaseOnMainThread(worldKey, chunkX, chunkZ));
    }

    private boolean releaseOnMainThread(UUID worldKey, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldKey);
        return world != null && world.removePluginChunkTicket(chunkX, chunkZ, plugin);
    }

    /**
     * Copies the stacks on the calling thread, then drops them in one main-thread task. The future completes on the
     * main thread.
     */
    @Override
    public CompletableFuture<Boolean> dropStacks(UUID worldKey, BlockPosition position, List<ItemStack> stacks) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        List<ItemStack> copies = BukkitBlocks.copies(stacks);
        return BukkitMainThread.supply(plugin, () -> BukkitBlocks.drop(Bukkit.getWorld(worldKey), position, copies));
    }

    @Override
    public Optional<ItemStack> blockItem(String blockId, Optional<String> headData) {
        return BukkitBlocks.item(plugin.getServer(), BukkitBlocks.placement(blockId, headData));
    }

    /**
     * Resolves the particle names on the calling thread, then draws in one main-thread task. The future completes on
     * the main thread.
     */
    @Override
    public CompletableFuture<Boolean> drawParticles(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(owner, "owner");
        List<BukkitParticles.Resolved> resolved = BukkitParticles.resolve(draws);
        return BukkitMainThread.supply(plugin, () -> BukkitParticles.draw(Bukkit.getWorld(worldKey), owner, resolved));
    }

    /**
     * Sends the message in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> sendMessage(UUID playerId, String message) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(message, "message");
        return BukkitMainThread.supply(plugin, () -> BukkitMainThread.sendMessage(plugin.getServer(), playerId,
                message));
    }

    /**
     * Reads the room in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Integer> inventoryRoom(UUID playerId, ItemStack item) {
        Objects.requireNonNull(playerId, "playerId");
        ItemStack copy = item.clone();
        return BukkitMainThread.supply(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            return player == null ? 0 : BukkitInventories.room(player, copy);
        });
    }

    /**
     * Gives the items in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Integer> giveItems(UUID playerId, ItemStack item, int amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
        ItemStack copy = item.clone();
        return BukkitMainThread.supply(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            return player == null ? 0 : BukkitInventories.give(player, copy, amount);
        });
    }

    @Override
    public SharedInventory createSharedInventory(String title, int size, Map<Integer, ItemStack> contents) {
        return BukkitSharedInventory.create(plugin.getServer(), title, size, contents);
    }

    /**
     * Opens the inventory in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Boolean> openSharedInventory(UUID playerId, SharedInventory inventory) {
        Objects.requireNonNull(playerId, "playerId");
        BukkitSharedInventory shared = BukkitSharedInventory.of(inventory);
        return BukkitMainThread.supply(plugin, () -> shared.openOnMainThread(plugin.getServer(), playerId));
    }

    /**
     * Closes the views for the given players in one main-thread task. The future completes on the main thread.
     */
    @Override
    public CompletableFuture<Void> closeSharedInventory(SharedInventory inventory, Set<UUID> viewers) {
        BukkitSharedInventory shared = BukkitSharedInventory.of(inventory);
        Set<UUID> closing = Set.copyOf(viewers);
        shared.markClosed();
        return BukkitMainThread.supply(plugin, () -> {
            shared.closeOnMainThread(plugin.getServer(), closing);
            return null;
        });
    }

    @Override
    public MenuHandle createMenu(UUID playerId, String title, int size, Map<Integer, ItemStack> contents) {
        return BukkitMenuInventory.create(plugin.getServer(), playerId, title, size, contents);
    }

    /**
     * Opens the menu in one main-thread task. The future completes on the main thread, with no handle when the player
     * is not online or another plugin cancelled the open.
     */
    @Override
    public CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu) {
        return openMenu(playerId, menu, null);
    }

    @Override
    public CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu, MenuHandle expectedOld) {
        Objects.requireNonNull(playerId, "playerId");
        BukkitMenuInventory made = BukkitMenuInventory.of(menu);
        return BukkitMainThread.supply(plugin, () -> made.openOnMainThread(plugin.getServer(), playerId, expectedOld)
                ? Optional.<MenuHandle>of(made)
                : Optional.<MenuHandle>empty());
    }

    @Override
    public boolean isPhysicalOpenInProgress(UUID playerId) {
        return BukkitMenuInventory.isPhysicalOpen(playerId);
    }

    @Override
    public CompletableFuture<Void> updateMenu(MenuHandle menu, Map<Integer, ItemStack> contents) {
        BukkitMenuInventory made = BukkitMenuInventory.of(menu);
        return BukkitMainThread.supply(plugin, () -> {
            made.replace(contents);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> closeMenu(MenuHandle menu) {
        BukkitMenuInventory made = BukkitMenuInventory.of(menu);
        made.markClosed();
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            made.closeOnMainThread(plugin.getServer(), made.playerId());
            return CompletableFuture.completedFuture(null);
        }
        return BukkitMainThread.supply(plugin, () -> {
            made.closeOnMainThread(plugin.getServer(), made.playerId());
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> runOnPlayerNextTick(UUID playerId, Runnable action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        return BukkitMainThread.supply(plugin, () -> {
            if (plugin.getServer().getPlayer(playerId) == null) {
                return null;
            }
            action.run();
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> playSound(UUID playerId, String key, double volume, double pitch) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(key, "key");
        return BukkitMainThread.supply(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                return false;
            }
            player.playSound(player.getLocation(), key, (float) volume, (float) pitch);
            return true;
        });
    }

    /**
     * Runs on the main thread, which owns every world on this server.
     */
    @Override
    public TaskScheduler atLocation(UUID worldKey, int blockX, int blockZ) {
        Objects.requireNonNull(worldKey, "worldKey");
        return BukkitTaskScheduler.mainThread(plugin);
    }

    /**
     * Runs on the main thread, which owns every player on this server.
     */
    @Override
    public TaskScheduler forPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return BukkitTaskScheduler.mainThread(plugin);
    }

    /**
     * Runs on the main thread.
     */
    @Override
    public TaskScheduler global() {
        return BukkitTaskScheduler.mainThread(plugin);
    }

    /**
     * Runs on the Bukkit async scheduler's threads.
     */
    @Override
    public TaskScheduler async() {
        return BukkitTaskScheduler.async(plugin);
    }
}
