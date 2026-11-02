package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads a world's loaded chunks, player positions, and spawn for the Paper and Spigot adapters. Call it on the main thread.
 */
final class BukkitWorldState {

    private BukkitWorldState() {
    }

    /**
     * Lists the world's chunks that are loaded now.
     *
     * @return the chunks loaded now, or an empty set when the world is not loaded
     */
    static Set<ChunkPosition> loadedChunks(UUID worldKey) {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            return Set.of();
        }
        return Arrays.stream(world.getLoadedChunks())
                .map(chunk -> new ChunkPosition(chunk.getX(), chunk.getZ()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Takes one snapshot of where each player in the world stands.
     *
     * @return one position per player in the world, or an empty list when the world is not loaded
     */
    static List<PlayerPosition> playerPositions(UUID worldKey) {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            return List.of();
        }
        List<PlayerPosition> positions = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            positions.add(new PlayerPosition(player.getUniqueId(), location.getX(), location.getY(), location.getZ()));
        }
        return List.copyOf(positions);
    }

    /**
     * Reads the block the world's spawn is at.
     *
     * @return the spawn block, or empty when the world is not loaded
     */
    static Optional<BlockPosition> worldSpawn(UUID worldKey) {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            return Optional.empty();
        }
        Location spawn = world.getSpawnLocation();
        return Optional.of(new BlockPosition(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()));
    }
}
