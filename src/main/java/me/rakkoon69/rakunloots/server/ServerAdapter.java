package me.rakkoon69.rakunloots.server;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * World access and scheduling for summon search and the loot box lifecycle. Paper, Spigot, and Folia each
 * implement it and keep their own server's threading rule, so callers never ask which server they are on. No Bukkit
 * type crosses it except item stacks. A returned future may complete on a server thread; the caller moves heavy work
 * to its own executor.
 */
public interface ServerAdapter {

    /**
     * Whether support for this server is experimental, so enable warns that it may misbehave. Only Folia is.
     */
    default boolean experimental() {
        return false;
    }

    /**
     * Whether GuardianBeam can show a laser on this server. It runs each beam on the Bukkit scheduler, which Folia
     * refuses, so only Folia answers false.
     */
    default boolean laserSupported() {
        return true;
    }

    /**
     * Whether the server's own shutdown drops every entity, so the plugin skips glow cleanup at stop. On Folia the
     * plugin stops on a region's shutdown thread, which may not touch an entity, and each region drops its entities
     * anyway.
     */
    default boolean dropsEntitiesAtStop() {
        return false;
    }

    /**
     * Reads one chunk as a view that is safe on any thread. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @param policy   what to do when the chunk is not loaded
     * @return the view, or empty for a miss: the world is not loaded, the chunk is unloaded under
     * {@link UnloadedChunkPolicy#SKIP}, or the chunk is not generated under {@link UnloadedChunkPolicy#LOAD}
     */
    CompletableFuture<Optional<ChunkView>> readChunk(UUID worldKey, int chunkX, int chunkZ, UnloadedChunkPolicy policy);

    /**
     * The chunks of a world that are loaded now. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @return the loaded chunks, or an empty set when the world is not loaded
     */
    CompletableFuture<Set<ChunkPosition>> loadedChunks(UUID worldKey);

    /**
     * One snapshot of where each player in a world stands. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @return one position per player in that world, or an empty list when the world is not loaded
     */
    CompletableFuture<List<PlayerPosition>> playerPositions(UUID worldKey);

    /**
     * The block a world's spawn is at. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @return the spawn block, or empty when the world is not loaded
     */
    CompletableFuture<Optional<BlockPosition>> worldSpawn(UUID worldKey);

    /**
     * Worlds loaded now, each with the name {@code worlds.yml} uses.
     * Never blocks the calling thread.
     */
    CompletableFuture<List<LoadedWorld>> loadedWorlds();

    /**
     * Sets a block, on the thread that owns it. A player head may carry head data: the base64 textures value, whose
     * skin the placed head keeps. Never loads a chunk. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @param blockId  a namespaced block id, such as {@code minecraft:barrel}
     * @param headData the head data, only for a player head
     * @return whether the block was set; false when the world or the block's chunk is not loaded
     * @throws IllegalArgumentException when the id is not a block, or the head data is set on another block or is
     *                                  not a textures value with a skin
     */
    CompletableFuture<Boolean> setBlock(UUID worldKey, BlockPosition position, String blockId,
                                        Optional<String> headData);

    /**
     * Sets a block, on the thread that owns it, only while it is still the expected block. The read and the write
     * run in one task, so nothing changes the block between them. Block data such as facing is not part of the
     * comparison. A player head may carry head data, as for {@link #setBlock}. Never loads a chunk. Never blocks the
     * calling thread.
     *
     * @param worldKey        the world's UUID
     * @param expectedBlockId the namespaced block id the block must still be, such as {@code minecraft:air}
     * @param blockId         the namespaced block id to set
     * @param headData        the head data, only for a player head
     * @return whether the block was set; false when it is another block, or the world or the block's chunk is not
     * loaded
     * @throws IllegalArgumentException when either id is not a block, or the head data is set on another block or is
     *                                  not a textures value with a skin
     */
    CompletableFuture<Boolean> replaceBlock(UUID worldKey, BlockPosition position, String expectedBlockId,
                                            String blockId, Optional<String> headData);

    /**
     * Reads the namespaced id of a block, on the thread that owns it. Block data such as facing is not part of the
     * id. Never loads a chunk. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @return the block id, or empty when the world or the block's chunk is not loaded
     */
    CompletableFuture<Optional<String>> readBlock(UUID worldKey, BlockPosition position);

    /**
     * Reads the namespaced id of a block now, on the calling thread, which must own the block, such as in an event at
     * that block. An event decides before a later task could run, so a read that must see the block as the event
     * does comes here. Block data such as facing is not part of the id. Never loads a chunk.
     *
     * @param worldKey the world's UUID
     * @return the block id, or empty when the world or the block's chunk is not loaded
     * @throws IllegalStateException when the calling thread does not own the block
     */
    Optional<String> readBlockHere(UUID worldKey, BlockPosition position);

    /**
     * Sets a block to air, on the thread that owns it, only while it is still the given block. The read and the
     * write run in one task, so nothing changes the block between them. Never loads a chunk. Never blocks the
     * calling thread.
     *
     * @param worldKey the world's UUID
     * @param blockId  the namespaced block id the block must still be
     * @return whether the block was removed; false when it is another block, or the world or the block's chunk is
     * not loaded
     * @throws IllegalArgumentException when the id is not a block
     */
    CompletableFuture<Boolean> removeBlock(UUID worldKey, BlockPosition position, String blockId);

    /**
     * Sets a block to air as {@link #removeBlock} does, but loads the block's chunk first when it is not loaded, so a
     * block in an unloaded chunk is removed too. The chunk is loaded only when it is already generated, never
     * generated, and nothing holds it loaded afterwards, so it unloads as any chunk does. The load runs through the
     * server's own way: Paper and Folia load it async, and Spigot loads it on the main thread, at most one chunk a
     * tick. The read and the write then run in one task on the thread that owns the block, so nothing changes the
     * block between them. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @param blockId  the namespaced block id the block must still be
     * @return whether the block was removed; false when it is another block, or the world is not loaded, or the
     * block's chunk is not generated
     * @throws IllegalArgumentException when the id is not a block
     */
    CompletableFuture<Boolean> removeBlockLoadingChunk(UUID worldKey, BlockPosition position, String blockId);

    /**
     * Keeps a chunk loaded with a plugin chunk ticket until {@link #releaseChunk} is called for it. The ticket is
     * made on the thread that owns the chunk: the main thread on Paper and Spigot, the region that owns the chunk on
     * Folia. A chunk that is not loaded is loaded first, only when it is already generated, never generated, as
     * {@link #removeBlockLoadingChunk} loads it. A plugin holds one ticket per chunk, so holding a chunk twice is one
     * hold, and one {@link #releaseChunk} frees it. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @return whether the chunk is held now; false when the world is not loaded or the chunk is not generated
     */
    CompletableFuture<Boolean> holdChunk(UUID worldKey, int chunkX, int chunkZ);

    /**
     * Gives up the plugin chunk ticket of {@link #holdChunk}, on the thread that owns the chunk, so the chunk unloads
     * as any chunk does. Called on the main thread of a Paper or Spigot server, it runs before it returns, so a
     * plugin that is stopping can release without a scheduler. Never loads a chunk. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @return whether a ticket was released; false when the world is not loaded or the chunk was not held
     */
    CompletableFuture<Boolean> releaseChunk(UUID worldKey, int chunkX, int chunkZ);

    /**
     * Drops each stack into the world at a block, on the thread that owns it, as a broken block drops its items.
     * The stacks are copied before this call returns. Never loads a chunk. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @param stacks   the stacks to drop; air is skipped
     * @return whether the stacks were dropped; false, with nothing dropped, when the world or the block's chunk is
     * not loaded
     */
    CompletableFuture<Boolean> dropStacks(UUID worldKey, BlockPosition position, List<ItemStack> stacks);

    /**
     * The item a block drops as itself: the plain block as an item, and on a player head, the skin its head data
     * names. Safe on any thread. It holds nothing of the world.
     *
     * @param blockId  a namespaced block id, such as {@code minecraft:barrel}
     * @param headData the head data, only for a player head
     * @return one plain item of that block, or empty when the block has no item, such as water
     * @throws IllegalArgumentException when the id is not a block, or the head data is set on another block or is
     *                                  not a textures value with a skin
     */
    Optional<ItemStack> blockItem(String blockId, Optional<String> headData);

    /**
     * Draws particles in a world, in one task on the thread that owns the block at {@code owner}, such as the spot an
     * animation plays at. The points may lie anywhere near it. Never loads a chunk. Never blocks the calling thread.
     *
     * @param worldKey the world's UUID
     * @param owner    the block whose owning thread draws
     * @param draws    the points and their particles; an empty list draws nothing
     * @return whether they were drawn; false, with nothing drawn, when the world or the owner's chunk is not loaded
     * @throws IllegalArgumentException when a particle name is no particle on this server
     */
    CompletableFuture<Boolean> drawParticles(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws);

    /**
     * Sends a chat message to a player, on the thread that owns the player. Never blocks the calling thread.
     *
     * @param playerId the player's UUID
     * @param message  the text, in legacy color codes
     * @return whether the player got it; false when the player is not online
     */
    CompletableFuture<Boolean> sendMessage(UUID playerId, String message);

    /**
     * How many of an item fit in a player's own inventory now: the main inventory and the hotbar, counting empty
     * slots and the room left in stacks of the same item. Reads on the thread that owns the player. Never blocks the
     * calling thread.
     *
     * @param playerId the player's UUID
     * @param item     the item; its amount is ignored
     * @return the room, or 0 when the player is not online
     */
    CompletableFuture<Integer> inventoryRoom(UUID playerId, ItemStack item);

    /**
     * Puts an amount of an item into a player's own inventory, on the thread that owns the player, split into
     * stacks of the item's stack size. What does not fit is not given and never dropped. Never blocks the calling
     * thread.
     *
     * @param playerId the player's UUID
     * @param item     the item; its amount is ignored
     * @param amount   at least 1
     * @return how many were given, from 0 to the amount; 0 when the player is not online
     * @throws IllegalArgumentException when the amount is below 1
     */
    CompletableFuture<Integer> giveItems(UUID playerId, ItemStack item, int amount);

    /**
     * Makes one inventory that several players view at once, so a stack one player takes is gone for every other.
     * Safe on any thread. It holds nothing of the world.
     *
     * @param title    the title players see, in legacy color codes
     * @param size     a multiple of 9, from 9 to 54
     * @param contents a stack for each slot that holds one; they are copied
     * @throws IllegalArgumentException when the size is not a chest size, or a slot is outside it
     */
    SharedInventory createSharedInventory(String title, int size, Map<Integer, ItemStack> contents);

    /**
     * Shows a shared inventory to a player, on the thread that owns the player. Never blocks the calling thread.
     *
     * @param playerId  the player's UUID
     * @param inventory a shared inventory a server adapter made
     * @return whether the player sees it; false when the player is not online
     * @throws IllegalArgumentException when no server adapter made the inventory
     */
    CompletableFuture<Boolean> openSharedInventory(UUID playerId, SharedInventory inventory);

    /**
     * Closes the views of a shared inventory for the given players, each on the thread that owns that viewer, and
     * closes the inventory for good: an open that has not run yet does nothing and completes false. A player who
     * opened another inventory since is left alone, and a player who is not online is skipped. Never blocks the
     * calling thread.
     *
     * @param inventory a shared inventory a server adapter made
     * @param viewers   the players to close it for, whether or not their open has run yet
     * @return completes once every view is closed
     * @throws IllegalArgumentException when no server adapter made the inventory
     */
    CompletableFuture<Void> closeSharedInventory(SharedInventory inventory, Set<UUID> viewers);

    /**
     * Makes one menu inventory for a player. Safe on any thread. It holds nothing of the world.
     *
     * @param playerId the player's UUID
     * @param title    the title the player sees, in legacy color codes
     * @param size     a multiple of 9, from 9 to 54
     * @param contents a stack for each slot that holds one; they are copied
     * @throws IllegalArgumentException when the size is not a chest size, or a slot is outside it
     */
    MenuHandle createMenu(UUID playerId, String title, int size, Map<Integer, ItemStack> contents);

    /**
     * Shows a menu inventory to a player, on the thread that owns the player, and records the handle only when the
     * player sees it. Never blocks the calling thread.
     *
     * @param playerId the player's UUID
     * @param menu     a menu inventory a server adapter made
     * @return the handle when the player sees it, or empty when the player is not online or the open was cancelled
     * @throws IllegalArgumentException when no server adapter made the menu
     */
    default CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu) {
        return openMenu(playerId, menu, null);
    }

    /**
     * Shows a menu inventory to a player as a replacement for an expected currently open menu, on the thread that
     * owns the player.
     *
     * @param playerId    the player's UUID
     * @param menu        a menu inventory a server adapter made
     * @param expectedOld the menu handle expected to be currently open, or null if no menu is expected
     * @return the handle when the player sees it, or empty when not online, cancelled, or not viewing expectedOld
     * @throws IllegalArgumentException when no server adapter made the menu
     */
    CompletableFuture<Optional<MenuHandle>> openMenu(UUID playerId, MenuHandle menu, MenuHandle expectedOld);

    /**
     * Whether a physical menu open is actively in progress on the current thread for the player.
     *
     * @param playerId the player's UUID
     * @return true while a physical view transition for the player is actively executing
     */
    boolean isPhysicalOpenInProgress(UUID playerId);

    /**
     * Replaces a menu's contents in place, on the thread that owns the viewer. A closed menu is left closed. Never
     * blocks the calling thread.
     *
     * @param menu     a menu inventory a server adapter made
     * @param contents a stack for each slot that holds one; they are copied
     * @throws IllegalArgumentException when no server adapter made the menu, or a slot is outside it
     */
    CompletableFuture<Void> updateMenu(MenuHandle menu, Map<Integer, ItemStack> contents);

    /**
     * Closes a menu for good and the viewer's view, on the thread that owns the viewer. The viewer is left alone when
     * they opened another inventory since. The adapter must synchronously make the handle ineligible for a pending
     * {@link #openMenu(UUID, MenuHandle)} before scheduling the physical close; this lets callers cancel an open that
     * has not reached the owner thread yet. Never blocks the calling thread.
     *
     * @param menu a menu inventory a server adapter made
     * @throws IllegalArgumentException when no server adapter made the menu
     */
    CompletableFuture<Void> closeMenu(MenuHandle menu);

    /**
     * Runs work on the thread that owns a player, on a later tick than the call. A click handler uses it so a menu
     * action never opens or closes an inventory inside the click event. Never blocks the calling thread.
     *
     * @param playerId the player's UUID
     * @param action   the work to run
     * @return when the work ran; failed with the work's failure, or empty when the player is not online
     */
    CompletableFuture<Void> runOnPlayerNextTick(UUID playerId, Runnable action);

    /**
     * Plays a sound to a player, on the thread that owns the player. Never blocks the calling thread.
     *
     * @param playerId the player's UUID
     * @param key      a namespaced sound key
     * @param volume   the volume
     * @param pitch    the pitch
     * @return whether the player heard it; false when the player is not online
     */
    CompletableFuture<Boolean> playSound(UUID playerId, String key, double volume, double pitch);

    /**
     * Runs tasks on the thread that owns a block of a world.
     *
     * @param worldKey the world's UUID
     * @param blockX   the block x
     * @param blockZ   the block z
     */
    TaskScheduler atLocation(UUID worldKey, int blockX, int blockZ);

    /**
     * Runs tasks on the thread that owns a player.
     *
     * @param playerId the player's UUID
     */
    TaskScheduler forPlayer(UUID playerId);

    /**
     * Runs tasks that belong to no world location or player, such as the summon timing from world settings.
     */
    TaskScheduler global();

    /**
     * Runs tasks off every server thread, for summon search and storage work.
     */
    TaskScheduler async();
}
