package me.rakkoon69.rakunloots.lootbox.bukkit;

import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Objects;

/**
 * Keeps everything but a player from taking from, moving, or changing a loot box. Each handler asks the lifecycle
 * whether a block holds a loot box and cancels the event when it does; it holds no other rule. Players reach a box
 * through the lifecycle's click and break only.
 *
 * <p>Guarded: hopper and hopper minecart moves out of or into a box's block, pistons that push or pull a box as any
 * of their moved blocks, fire burning a box, any entity changing a box's block (enderman, wither, ravager, a falling
 * block, and the rest), and vanilla changes at a box: fading, forming, spreading, growing, leaf decay, liquid or a
 * dragon egg moving into or out of it, a cauldron's level, a sponge soaking up water, and a tree or bone meal
 * changing it. A player placing a chest or trapped chest beside a chest loot box is refused, since the two would
 * join into a double chest. Physics, which drops a gravity block, is {@link LootBoxPhysicsGuardListener}.
 *
 * <p>The handlers run first and skip events already cancelled, so later listeners see a guarded event cancelled.
 * They schedule nothing and read the lifecycle's registry only, so they are safe on Paper, Spigot, and any Folia
 * region.
 */
public final class LootBoxGuardListener implements Listener {

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final LootBoxLifecycle lifecycle;

    public LootBoxGuardListener(LootBoxLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /**
     * A hopper or a hopper minecart pulling from a box's block, or anything pushing into it. The box's own block
     * holds no items, and its loot lives in the shared inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        cancelIf(event, holdsLootBox(event.getSource()) || holdsLootBox(event.getDestination()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        cancelIf(event, anyLootBox(event.getBlocks()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        cancelIf(event, anyLootBox(event.getBlocks()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    /**
     * An enderman, a wither, a ravager, a falling block, or any other entity changing a box's block.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    /**
     * Also a snow golem's trail or frost walker, which share this event.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    /**
     * Liquid flowing into a box, or a dragon egg box teleporting away.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        cancelIf(event, isLootBox(event.getToBlock()) || isLootBox(event.getBlock()));
    }

    /**
     * Rain, dripstone, or anything else filling or emptying a cauldron box, which changes its block id.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    /**
     * A sponge box turning wet.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        cancelIf(event, isLootBox(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        cancelIf(event, anyLootBoxState(event.getBlocks()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        cancelIf(event, anyLootBoxState(event.getBlocks()));
    }

    /**
     * A chest or trapped chest placed against a chest loot box, which would join it into a double chest.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Material type = placed.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return;
        }
        for (BlockFace face : HORIZONTAL) {
            if (isLootBox(placed.getRelative(face))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isLootBox(Block block) {
        return lifecycle.isLootBox(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private boolean anyLootBox(List<Block> blocks) {
        for (Block block : blocks) {
            if (isLootBox(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyLootBoxState(List<BlockState> states) {
        for (BlockState state : states) {
            if (lifecycle.isLootBox(state.getWorld().getUID(), state.getX(), state.getY(), state.getZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the inventory belongs to a box's block, or a double chest with a box as either half. An entity's
     * inventory, such as a hopper minecart's, belongs to no block.
     */
    private boolean holdsLootBox(Inventory inventory) {
        return holdsLootBox(inventory.getHolder());
    }

    private boolean holdsLootBox(InventoryHolder holder) {
        if (holder instanceof BlockInventoryHolder block) {
            return isLootBox(block.getBlock());
        }
        if (holder instanceof DoubleChest chest) {
            return holdsLootBox(chest.getLeftSide()) || holdsLootBox(chest.getRightSide());
        }
        return false;
    }

    private static void cancelIf(Cancellable event, boolean lootBox) {
        if (lootBox) {
            event.setCancelled(true);
        }
    }
}
