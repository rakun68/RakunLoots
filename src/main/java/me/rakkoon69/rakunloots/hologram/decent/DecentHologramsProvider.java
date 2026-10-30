package me.rakkoon69.rakunloots.hologram.decent;

import me.rakkoon69.rakunloots.hologram.BukkitHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramServer;
import me.rakkoon69.rakunloots.hologram.PluginRequirement;
import me.rakkoon69.rakunloots.hologram.decent.DecentHologramsCalls.NewHologram;
import org.bukkit.Server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Loot box holograms drawn by DecentHolograms. It can run when DecentHolograms is enabled, on Minecraft 1.18 and up,
 * and on Folia only when DecentHolograms declares {@code folia-supported: true}; DecentHolograms 2.10.1 does not.
 *
 * <p>Each hologram is named {@code rakunloots_<box id>}, which fits DecentHolograms' name rule, and is created with
 * save to file off, so DecentHolograms never writes it to its {@code holograms} folder and forgets it on restart. It
 * uses DecentHolograms' down origin, so the position is the hologram's bottom, as the provider contract says, and the
 * lines realign when their number changes. Its display range is the view range.
 *
 * <p>DecentHolograms' own reload drops every hologram it did not load from a file, so an update of a hologram
 * DecentHolograms no longer has draws it again where it was. Remove and remove all touch only holograms this provider
 * created.
 *
 * <p>Calls go straight to DecentHolograms on the caller's thread. Its API is safe off the main thread: it ticks its
 * holograms on its own async task and guards each hologram's viewers with a lock.
 */
public final class DecentHologramsProvider implements HologramProvider {

    /**
     * DecentHolograms, from Minecraft 1.18, the plugin's own floor.
     */
    public static final PluginRequirement REQUIREMENT = PluginRequirement.of("DecentHolograms").from("1.18");

    private static final String NAME_PREFIX = "rakunloots_";

    private final HologramServer server;
    private final DecentHologramsCalls calls;
    private final Map<UUID, Placed> drawn = new LinkedHashMap<>();

    DecentHologramsProvider(HologramServer server, DecentHologramsCalls calls) {
        this.server = Objects.requireNonNull(server, "server");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /**
     * The provider for the running server. Building it touches nothing of DecentHolograms, so it builds whether or not
     * DecentHolograms is installed.
     */
    public static DecentHologramsProvider of(Server server) {
        return new DecentHologramsProvider(new BukkitHologramServer(server), new DhapiCalls(server));
    }

    /**
     * The DecentHolograms name of a box's hologram.
     */
    static String name(UUID hologramId) {
        return NAME_PREFIX + hologramId;
    }

    @Override
    public CanRun canRun() {
        return REQUIREMENT.check(server);
    }

    /**
     * @throws IllegalArgumentException when DecentHolograms already has a hologram of this box's name; that hologram
     *                                  is left alone
     */
    @Override
    public synchronized void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
        Placed placed = new Placed(position, viewRange);
        calls.create(placed.hologram(name(hologramId), lines));
        drawn.put(hologramId, placed);
    }

    @Override
    public synchronized void update(UUID hologramId, List<String> lines) {
        Placed placed = drawn.get(hologramId);
        if (placed == null) {
            return;
        }
        if (!calls.setLines(name(hologramId), lines)) {
            calls.create(placed.hologram(name(hologramId), lines));
        }
    }

    @Override
    public synchronized void remove(UUID hologramId) {
        if (drawn.remove(hologramId) != null) {
            calls.remove(name(hologramId));
        }
    }

    /**
     * Removes each hologram this provider draws. One that fails does not stop the others; the first failure is thrown
     * after them.
     */
    @Override
    public synchronized void removeAll() {
        RuntimeException first = null;
        for (UUID hologramId : List.copyOf(drawn.keySet())) {
            drawn.remove(hologramId);
            try {
                calls.remove(name(hologramId));
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
     * Where a drawn hologram sits, kept to draw it again.
     */
    private record Placed(HologramPosition position, int viewRange) {

        NewHologram hologram(String name, List<String> lines) {
            return new NewHologram(name, position, lines, false, true, viewRange);
        }
    }
}
