package me.rakkoon69.rakunloots.visuals;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Objects;

/**
 * Carries join, quit, respawn, and world change to the box visuals service, so a player who arrives sees the glow of
 * the boxes already there. It holds no rule of its own. Every handler runs on the thread that fired its event, which
 * owns the player.
 */
public final class BoxVisualsListener implements Listener {

    private final BoxVisualsService service;

    public BoxVisualsListener(BoxVisualsService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        service.playerJoined(player.getWorld().getUID(), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        service.playerChangedWorld(player.getWorld().getUID(), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.playerRespawned(event.getRespawnLocation().getWorld().getUID(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.playerQuit(event.getPlayer().getUniqueId());
    }
}
