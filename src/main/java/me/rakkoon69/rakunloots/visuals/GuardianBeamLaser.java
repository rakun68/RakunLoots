package me.rakkoon69.rakunloots.visuals;

import fr.skytasul.guardianbeam.Laser;
import me.rakkoon69.rakunloots.config.LaserType;
import me.rakkoon69.rakunloots.server.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;

/**
 * A laser through GuardianBeam, shaded and relocated. GuardianBeam runs each beam on the Bukkit scheduler, which Folia
 * refuses, so the box visuals service never builds it where the server adapter says the laser is not supported.
 *
 * <p>Its entities are client side. The client picks a crystal like a real entity, so {@link LaserPlacement} puts each
 * entity where the loot's start offset says.
 */
public final class GuardianBeamLaser implements LaserLibrary {

    private final Plugin plugin;

    public GuardianBeamLaser(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Beam start(UUID worldKey, BlockPosition position, LaserType type, int viewDistance, int startOffset)
            throws Exception {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            throw new IllegalStateException("world " + worldKey + " is not loaded");
        }
        LaserPlacement.Placed placed = LaserPlacement.of(type, position, startOffset, world.getMaxHeight());
        Location start = location(world, placed.start());
        Location end = location(world, placed.end());
        Laser laser = type == LaserType.GUARDIAN
                ? new Laser.GuardianLaser(start, end, -1, viewDistance)
                : new Laser.CrystalLaser(start, end, -1, viewDistance);
        laser.start(plugin);
        return laser::stop;
    }

    @Override
    public boolean peaceful(UUID worldKey) {
        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            throw new IllegalStateException("world " + worldKey + " is not loaded");
        }
        return world.getDifficulty() == Difficulty.PEACEFUL;
    }

    private static Location location(World world, LaserPlacement.Point point) {
        return new Location(world, point.x(), point.y(), point.z());
    }
}
