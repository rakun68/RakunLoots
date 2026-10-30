package me.rakkoon69.rakunloots.hologram;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds a loot box owner's name for the {@code {owner}} placeholder. Called on any thread, while the lifecycle holds
 * its lock, so it must not wait for another thread.
 */
@FunctionalInterface
public interface OwnerNames {

    /**
     * The player's name, or empty when the server does not know it.
     */
    Optional<String> name(UUID playerId);

    /**
     * Asks the server: an online player's name, or else the name the server keeps for an offline player. Neither
     * asks Mojang.
     */
    static OwnerNames of(Server server) {
        Objects.requireNonNull(server, "server");
        return playerId -> {
            Player online = server.getPlayer(playerId);
            if (online != null) {
                return Optional.of(online.getName());
            }
            OfflinePlayer offline = server.getOfflinePlayer(playerId);
            return Optional.ofNullable(offline.getName());
        };
    }
}
