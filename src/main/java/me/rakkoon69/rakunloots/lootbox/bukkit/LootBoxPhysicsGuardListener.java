package me.rakkoon69.rakunloots.lootbox.bukkit;

import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.util.Objects;

/**
 * Cancels physics at a loot box, so a gravity block box never falls and a neighbour's update never changes the box.
 * It asks the lifecycle whether the block holds a loot box and holds no other rule.
 *
 * <p>It is apart from {@link LootBoxGuardListener} because physics fires far more often than any other guarded
 * event, and Paper skips building the event while nothing listens to it; whoever registers the listeners can leave
 * this one out while no loot uses a gravity block. It schedules nothing, so it is safe on Paper, Spigot, and any
 * Folia region.
 */
public final class LootBoxPhysicsGuardListener implements Listener {

    private final LootBoxLifecycle lifecycle;

    public LootBoxPhysicsGuardListener(LootBoxLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (lifecycle.isLootBox(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
        }
    }
}
