package me.rakkoon69.rakunloots.summonitem;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/**
 * Carries a main hand right click to {@link SummonItemUse#use} and a join to {@link SummonItemUse#refresh}. It holds
 * no rule of its own. Both handlers run on the thread that owns the player.
 *
 * <p>The click runs last and skips an event whose item use something denied, so a click the loot box listener or a
 * protection plugin cancelled never summons. It reads {@link PlayerInteractEvent#useItemInHand()} and not
 * {@code ignoreCancelled}, because an event for a click in the air counts as cancelled from the start. Register it
 * after the loot box interaction listener, which runs at the same priority.
 */
public final class SummonItemListener implements Listener {

    private final SummonItemUse use;

    public SummonItemListener(SummonItemUse use) {
        this.use = Objects.requireNonNull(use, "use");
    }

    /**
     * A right click in the air or on a block with the main hand. The off hand's click the server may fire after it
     * makes no call.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (use.use(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        use.refresh(event.getPlayer());
    }
}
