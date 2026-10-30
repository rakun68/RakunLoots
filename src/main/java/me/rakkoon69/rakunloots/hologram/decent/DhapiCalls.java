package me.rakkoon69.rakunloots.hologram.decent;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The DecentHolograms calls made through DHAPI on the running plugin. Only its methods name DecentHolograms' classes,
 * so building it loads none of them.
 */
final class DhapiCalls implements DecentHologramsCalls {

    private final Server server;

    DhapiCalls(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Creates the hologram with no lines, which shows it to nobody, sets its origin and ranges, then sets its lines and
     * shows it to the players in range. DecentHolograms shows it to players who come in range later.
     * When a step after the creation fails, the hologram is removed again.
     *
     * @throws IllegalStateException when the position's world is not loaded
     */
    @Override
    public void create(NewHologram spec) {
        HologramPosition position = spec.position();
        World world = server.getWorld(position.worldKey());
        if (world == null) {
            throw new IllegalStateException("world " + position.worldKey() + " is not loaded");
        }
        Location location = new Location(world, position.x(), position.y(), position.z());
        Hologram hologram = DHAPI.createHologram(spec.name(), location, spec.saveToFile(), List.of());
        completeOrRemove(spec.name(), () -> {
            hologram.setDownOrigin(spec.downOrigin());
            hologram.setDisplayRange(spec.displayRange());
            hologram.setUpdateRange(spec.displayRange());
            DHAPI.setHologramLines(hologram, spec.lines());
            hologram.showAll();
        }, DHAPI::removeHologram);
    }

    /**
     * Runs the steps that finish a created hologram. When one fails, removes the hologram so none is left half made,
     * and rethrows that failure; a failure of the removal is added to it.
     */
    static void completeOrRemove(String name, Runnable steps, Consumer<String> remove) {
        try {
            steps.run();
        } catch (RuntimeException failure) {
            try {
                remove.accept(name);
            } catch (RuntimeException removal) {
                failure.addSuppressed(removal);
            }
            throw failure;
        }
    }

    @Override
    public boolean setLines(String name, List<String> lines) {
        Hologram hologram = DHAPI.getHologram(name);
        if (hologram == null) {
            return false;
        }
        DHAPI.setHologramLines(hologram, lines);
        return true;
    }

    @Override
    public void remove(String name) {
        DHAPI.removeHologram(name);
    }
}
