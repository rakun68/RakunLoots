package me.rakkoon69.rakunloots.command.bukkit;

import me.rakkoon69.rakunloots.command.CommandSources;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The names the argument types check against, read from the running server and the current config each time a
 * command runs or completes, so a reload and a joining player are seen at once.
 */
public final class BukkitCommandSources implements CommandSources {

    private final Server server;
    private final Supplier<ConfigSnapshot> config;

    public BukkitCommandSources(Server server, Supplier<ConfigSnapshot> config) {
        this.server = server;
        this.config = config;
    }

    @Override
    public Collection<String> lootIds() {
        return config.get().loots().keySet();
    }

    /**
     * The worlds loaded now, and the worlds {@code worlds.yml} has settings for.
     */
    @Override
    public Collection<String> worldNames() {
        Set<String> names = new LinkedHashSet<>();
        for (World world : server.getWorlds()) {
            names.add(world.getName());
        }
        names.addAll(config.get().worlds().keySet());
        return names;
    }

    @Override
    public Optional<UUID> worldKey(String worldName) {
        World world = server.getWorld(worldName);
        return world == null ? Optional.empty() : Optional.of(world.getUID());
    }

    @Override
    public Collection<String> playerNames() {
        Set<String> names = new LinkedHashSet<>(onlinePlayerNames());
        for (OfflinePlayer player : server.getOfflinePlayers()) {
            if (player.getName() != null) {
                names.add(player.getName());
            }
        }
        return names;
    }

    @Override
    public Collection<String> onlinePlayerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Player player : server.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    @Override
    public Optional<UUID> playerId(String playerName) {
        Player online = server.getPlayerExact(playerName);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        for (OfflinePlayer player : server.getOfflinePlayers()) {
            if (playerName.equalsIgnoreCase(player.getName())) {
                return Optional.of(player.getUniqueId());
            }
        }
        return Optional.empty();
    }
}
