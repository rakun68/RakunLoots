package me.rakkoon69.rakunloots.hologram.holographic;

import me.rakkoon69.rakunloots.hologram.BukkitHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramServer;
import me.rakkoon69.rakunloots.hologram.PluginRequirement;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.TaskScheduler;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Loot box holograms drawn by HolographicDisplays. It can run when HolographicDisplays is enabled, on Minecraft 1.20.4
 * and below, and never on Folia: HolographicDisplays is archived upstream at 1.20.4, and its API refuses every call off
 * the main thread.
 *
 * <p>Each call is handed to the global scheduler, which runs it on the main thread on Paper and Spigot, and returns at
 * once. The calls run there in the order they were made. Which holograms it draws is known on the main thread only, so
 * an update or remove of an id it does not draw does nothing there, and a create HolographicDisplays refuses, such as in
 * a world that is not loaded, is not recorded. A call that throws on the main thread is logged by the scheduler.
 *
 * <p>Holograms are HolographicDisplays API holograms, which it keeps in memory only and never writes to its files. Each
 * is lifted so its lowest line ends at the position. HolographicDisplays has no range per hologram: players see them
 * within its own {@code holograms-view-range} setting, and the view range is not used.
 *
 * <p>Remove all is called when the plugin stops, on the main thread, where the scheduler takes no more tasks from a
 * disabling plugin. Called on the main thread, it therefore runs in place, and a call still waiting from before it
 * never runs.
 */
public final class HolographicDisplaysProvider implements HologramProvider {

    /**
     * HolographicDisplays, up to Minecraft 1.20.4, its last release.
     */
    public static final PluginRequirement REQUIREMENT = PluginRequirement.of("HolographicDisplays").upTo("1.20.4");

    private final HologramServer server;
    private final TaskScheduler mainThread;
    private final BooleanSupplier onMainThread;
    private final HolographicDisplaysCalls calls;
    /**
     * Where each drawn hologram's lowest line ends. Touched on the main thread only.
     */
    private final Map<UUID, HologramPosition> drawn = new HashMap<>();
    /**
     * Counts each remove all run in place; a call made before one of them does not run.
     */
    private volatile long removals;

    HolographicDisplaysProvider(HologramServer server, TaskScheduler mainThread, BooleanSupplier onMainThread,
                                HolographicDisplaysCalls calls) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.onMainThread = Objects.requireNonNull(onMainThread, "onMainThread");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /**
     * The provider for the running server, scheduling through the adapter's global scheduler. Building it touches
     * nothing of HolographicDisplays, so it builds whether or not HolographicDisplays is installed.
     */
    public static HolographicDisplaysProvider of(Plugin plugin, ServerAdapter adapter) {
        Server running = plugin.getServer();
        return new HolographicDisplaysProvider(new BukkitHologramServer(running), adapter.global(),
                running::isPrimaryThread, new HolographicDisplaysApiCalls(plugin));
    }

    @Override
    public CanRun canRun() {
        CanRun requirement = REQUIREMENT.check(server);
        if (!requirement.yes()) {
            return requirement;
        }
        if (server.folia()) {
            return CanRun.no(REQUIREMENT.plugin() + " never runs on Folia: its API works only on the main thread");
        }
        return CanRun.YES;
    }

    @Override
    public void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
        Objects.requireNonNull(hologramId, "hologramId");
        Objects.requireNonNull(position, "position");
        List<String> copy = List.copyOf(lines);
        onMain(() -> {
            calls.create(hologramId, position, copy);
            drawn.put(hologramId, position);
        });
    }

    @Override
    public void update(UUID hologramId, List<String> lines) {
        Objects.requireNonNull(hologramId, "hologramId");
        List<String> copy = List.copyOf(lines);
        onMain(() -> {
            if (drawn.containsKey(hologramId)) {
                calls.setLines(hologramId, copy);
            }
        });
    }

    @Override
    public void remove(UUID hologramId) {
        Objects.requireNonNull(hologramId, "hologramId");
        onMain(() -> {
            if (drawn.remove(hologramId) != null) {
                calls.remove(hologramId);
            }
        });
    }

    /**
     * Removes each hologram this provider draws, in place on the main thread, or else through the scheduler. One that
     * fails does not stop the others; the first failure is thrown after them.
     */
    @Override
    public void removeAll() {
        if (onMainThread.getAsBoolean()) {
            removals++;
            removeDrawn();
        } else {
            onMain(this::removeDrawn);
        }
    }

    private void removeDrawn() {
        RuntimeException first = null;
        for (UUID hologramId : List.copyOf(drawn.keySet())) {
            drawn.remove(hologramId);
            try {
                calls.remove(hologramId);
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
     * Hands the work to the main thread, unless a remove all ran in place before it gets there.
     */
    private void onMain(Runnable work) {
        long before = removals;
        mainThread.run(() -> {
            if (removals == before) {
                work.run();
            }
        });
    }
}
