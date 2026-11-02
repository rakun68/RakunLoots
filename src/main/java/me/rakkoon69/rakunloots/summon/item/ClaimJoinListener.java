package me.rakkoon69.rakunloots.summon.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/**
 * Carries a join to {@link UnclaimedSummonItems#onJoin}, which tells the player their unclaimed summon items are
 * waiting. It holds no rule of its own.
 */
public final class ClaimJoinListener implements Listener {

    private final UnclaimedSummonItems unclaimed;

    public ClaimJoinListener(UnclaimedSummonItems unclaimed) {
        this.unclaimed = Objects.requireNonNull(unclaimed, "unclaimed");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        unclaimed.onJoin(event.getPlayer().getUniqueId());
    }
}
