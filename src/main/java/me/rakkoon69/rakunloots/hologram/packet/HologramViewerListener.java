package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.server.PlayerPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * Carries join, quit, respawn, teleport, and world change to the viewer tracker, one call per event, with the player's world
 * and position. It holds no rule of its own.
 *
 * <p>A respawn carries the respawn location, as the client has dropped every entity by then.
 *
 * <p>A teleport to another world makes no call: the client drops every entity when it enters the new world, so
 * spawn packets sent before would be lost. The world change event that follows the move carries it instead, with the
 * position the player has in the new world.
 *
 * <p>Teleport runs last and skips a cancelled teleport, so only a move that happens reaches the tracker. Every handler
 * runs on the thread that fired its event, which owns the player, so it is safe on Paper, Spigot, and Folia.
 */
public final class HologramViewerListener implements Listener {

    private final ViewerTracker tracker;

    public HologramViewerListener(ViewerTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location at = player.getLocation();
        tracker.joined(worldKey(at), position(player.getUniqueId(), at));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tracker.quit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !to.getWorld().equals(from.getWorld())) {
            return;
        }
        tracker.teleported(worldKey(to), position(event.getPlayer().getUniqueId(), to));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Location at = event.getRespawnLocation();
        tracker.respawned(worldKey(at), position(event.getPlayer().getUniqueId(), at));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Location at = player.getLocation();
        tracker.changedWorld(worldKey(at), position(player.getUniqueId(), at));
    }

    private static UUID worldKey(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "a player's location has a world");
        return world.getUID();
    }

    private static PlayerPosition position(UUID playerId, Location location) {
        return new PlayerPosition(playerId, location.getX(), location.getY(), location.getZ());
    }
}
