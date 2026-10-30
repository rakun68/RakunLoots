package me.rakkoon69.rakunloots.hologram.fancy;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import me.rakkoon69.rakunloots.hologram.BukkitHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramServer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Loot box holograms drawn by FancyHolograms as text holograms. It can run when FancyHolograms is enabled, on Paper
 * from Minecraft 1.21.4, and on Folia only when FancyHolograms declares {@code folia-supported: true}; FancyHolograms
 * 2.12.0 does. This class is Java 25 bytecode, like FancyHolograms 2.12.0, and only
 * {@link FancyHologramsProviderLoader} loads it, by name, on a server where it can run.
 *
 * <p>Each hologram is named {@code rakunloots_<box id>} and created with persistence off. FancyHolograms saves only
 * persistent holograms to its {@code holograms.yml}, and its own reload unloads and loads only those, so a loot box
 * hologram is never written to its files and lives through {@code /fancyholograms reload}. The hologram's location is
 * the position: a text display's text stands on its location and its lines stack upward, as the provider contract
 * says. Its visibility distance is the view range.
 *
 * <p>FancyHolograms adds a hologram under a name it already has by replacing that one, so create refuses such a name
 * and leaves the other hologram alone. Remove and remove all touch only holograms this provider created.
 *
 * <p>Calls go straight to FancyHolograms on the caller's thread. Its API is safe off the main thread: its manager keeps
 * holograms in a concurrent map, and its own hologram thread shows, updates, and hides them, picking up changed lines
 * within a second.
 */
public final class FancyHologramsProvider implements HologramProvider {

    private static final String NAME_PREFIX = "rakunloots_";

    private final HologramServer server;
    private final Supplier<HologramManager> manager;
    private final Function<UUID, World> worlds;
    private final Map<UUID, Hologram> drawn = new LinkedHashMap<>();

    /**
     * @param manager FancyHolograms' hologram manager, asked on each call
     * @param worlds  the loaded world with that key, or null
     */
    FancyHologramsProvider(HologramServer server, Supplier<HologramManager> manager, Function<UUID, World> worlds) {
        this.server = Objects.requireNonNull(server, "server");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /**
     * The provider for the running server. {@link FancyHologramsProviderLoader} calls it by name.
     */
    public static FancyHologramsProvider of(Server server) {
        Objects.requireNonNull(server, "server");
        return new FancyHologramsProvider(new BukkitHologramServer(server),
                () -> FancyHologramsPlugin.get().getHologramManager(), server::getWorld);
    }

    /**
     * The FancyHolograms name of a box's hologram.
     */
    static String name(UUID hologramId) {
        return NAME_PREFIX + hologramId;
    }

    @Override
    public CanRun canRun() {
        return FancyHologramsProviderLoader.REQUIREMENT.check(server);
    }

    /**
     * @throws IllegalArgumentException when FancyHolograms already has a hologram of this box's name; that hologram
     *                                  is left alone
     * @throws IllegalStateException    when the position's world is not loaded
     */
    @Override
    public synchronized void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
        World world = worlds.apply(position.worldKey());
        if (world == null) {
            throw new IllegalStateException("world " + position.worldKey() + " is not loaded");
        }
        String name = name(hologramId);
        HologramManager holograms = manager.get();
        if (holograms.getHologram(name).isPresent()) {
            throw new IllegalArgumentException("FancyHolograms already has a hologram named " + name);
        }
        TextHologramData data = new TextHologramData(name,
                new Location(world, position.x(), position.y(), position.z()));
        data.setPersistent(false);
        data.setVisibilityDistance(viewRange);
        data.setText(new ArrayList<>(lines));
        Hologram hologram = holograms.create(data);
        holograms.addHologram(hologram);
        drawn.put(hologramId, hologram);
    }

    /**
     * FancyHolograms' hologram thread sends the new lines to the players who see it.
     */
    @Override
    public synchronized void update(UUID hologramId, List<String> lines) {
        Hologram hologram = drawn.get(hologramId);
        if (hologram != null) {
            ((TextHologramData) hologram.getData()).setText(new ArrayList<>(lines));
        }
    }

    @Override
    public synchronized void remove(UUID hologramId) {
        Hologram hologram = drawn.remove(hologramId);
        if (hologram != null) {
            removeFromFancyHolograms(hologramId, hologram);
        }
    }

    /**
     * Removes each hologram this provider draws. One that fails does not stop the others; the first failure is thrown
     * after them.
     */
    @Override
    public synchronized void removeAll() {
        RuntimeException first = null;
        for (Map.Entry<UUID, Hologram> entry : List.copyOf(drawn.entrySet())) {
            drawn.remove(entry.getKey());
            try {
                removeFromFancyHolograms(entry.getKey(), entry.getValue());
            } catch (RuntimeException failure) {
                if (first == null) {
                    first = failure;
                } else {
                    first.addSuppressed(failure);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /**
     * Removes the hologram only while FancyHolograms still has this very one under its name, so another hologram that
     * took the name is left alone.
     */
    private void removeFromFancyHolograms(UUID hologramId, Hologram hologram) {
        HologramManager holograms = manager.get();
        Optional<Hologram> current = holograms.getHologram(name(hologramId));
        if (current.isPresent() && current.get() == hologram) {
            holograms.removeHologram(hologram);
        }
    }
}
