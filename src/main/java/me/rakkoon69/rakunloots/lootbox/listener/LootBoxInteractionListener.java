package me.rakkoon69.rakunloots.lootbox.listener;

import me.rakkoon69.rakunloots.lootbox.Interaction;
import me.rakkoon69.rakunloots.lootbox.InteractionResult;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.SharedInventory;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Carries what players and the world do to a loot box to the lifecycle, one call per event, and does what it
 * answers: a right-click and a break become {@link LootBoxLifecycle#interact}, an entity or block explosion
 * {@link LootBoxLifecycle#explode}, a chunk load {@link LootBoxLifecycle#chunkLoaded}, a chunk unload
 * {@link LootBoxLifecycle#chunkUnloaded}, and a shared inventory close {@link LootBoxLifecycle#inventoryClosed}. It
 * holds no rule of its own.
 *
 * <p>A click or break the lifecycle cancels is cancelled; a click on a box always is, so a container block's own
 * inventory never opens. A break it lets through is not cancelled, and the server's own drops and experience for
 * that block are turned off, since the lifecycle drops what is left and the block item itself. A break on a block that
 * is no longer a loot box is left as it is. An explosion loses
 * exactly the blocks the lifecycle keeps. The click handler ignores the off hand, and passes only whether the
 * player has {@link LootBoxLifecycle#BYPASS_LOCK_PERMISSION}; sneaking never reaches the lifecycle.
 *
 * <p>Click, break, and explosion run last and skip events already cancelled, so a protection plugin's refusal
 * stands and the lifecycle acts only on what will happen. Every handler runs on the thread that fired its event,
 * which owns the block, the chunk, or the player, as each lifecycle call asks, so it is safe on Paper, Spigot, and
 * Folia.
 */
public final class LootBoxInteractionListener implements Listener {

    private final LootBoxLifecycle lifecycle;

    public LootBoxInteractionListener(LootBoxLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /**
     * A main hand right-click on a block. The off hand's click that the server may fire after it makes no call.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || block == null) {
            return;
        }
        InteractionResult result = interact(event.getPlayer(), block, Interaction.CLICK);
        if (result == InteractionResult.REFUSED || result == InteractionResult.ALLOWED) {
            event.setCancelled(true);
        }
    }

    /**
     * A player breaking a block. A break the lifecycle refuses is cancelled. One it allows is not, and the server's
     * own drops and experience are turned off. A box that ended because its block changed leaves the event alone,
     * so the block that replaced it drops what it drops.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        switch (interact(event.getPlayer(), event.getBlock(), Interaction.BREAK)) {
            case REFUSED -> event.setCancelled(true);
            case ALLOWED -> {
                event.setDropItems(false);
                event.setExpToDrop(0);
            }
            case ENDED_CHANGED, NOT_A_BOX -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.getLocation().getWorld(), event.blockList());
    }

    /**
     * A bed, a respawn anchor, or another block exploding.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.getBlock().getWorld(), event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        lifecycle.chunkLoaded(chunk.getWorld().getUID(), new ChunkPosition(chunk.getX(), chunk.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        lifecycle.chunkUnloaded(chunk.getWorld().getUID(), new ChunkPosition(chunk.getX(), chunk.getZ()));
    }

    /**
     * A player closing a box's shared inventory, which holds its Bukkit inventory. Any other inventory is left alone.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SharedInventory shared) {
            lifecycle.inventoryClosed(shared, event.getPlayer().getUniqueId());
        }
    }

    private InteractionResult interact(Player player, Block block, Interaction interaction) {
        return lifecycle.interact(player.getUniqueId(), block.getWorld().getUID(), positionOf(block),
                player.hasPermission(LootBoxLifecycle.BYPASS_LOCK_PERMISSION), interaction);
    }

    private void explode(World world, List<Block> blocks) {
        if (world == null || blocks.isEmpty()) {
            return;
        }
        List<BlockPosition> positions = new ArrayList<>(blocks.size());
        blocks.forEach(block -> positions.add(positionOf(block)));
        Set<BlockPosition> kept = lifecycle.explode(world.getUID(), positions);
        if (!kept.isEmpty()) {
            blocks.removeIf(block -> kept.contains(positionOf(block)));
        }
    }

    private static BlockPosition positionOf(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }
}
