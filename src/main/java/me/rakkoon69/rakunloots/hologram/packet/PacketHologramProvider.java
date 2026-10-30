package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.server.ServerAdapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The plugin's own holograms: fake entities sent only to players in range, so no entity exists in the world. It can
 * run when a packet adapter matched this server's Minecraft version. Each call goes to its viewer tracker, which
 * decides who sees what; remove sends destroy to every viewer, and remove all, run when the plugin stops, sends it
 * for every hologram and stops the movement check.
 */
public final class PacketHologramProvider implements HologramProvider {

    private final CanRun canRun;
    private final Optional<ViewerTracker> tracker;

    /**
     * Builds nothing of the server beyond the tracker; the movement check starts with the first hologram.
     */
    public PacketHologramProvider(PacketAdapterMatch match, ServerAdapter server) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(server, "server");
        this.canRun = match.refusal().map(CanRun::no).orElse(CanRun.YES);
        this.tracker = match.adapter().map(adapter -> new ViewerTracker(adapter, server));
    }

    /**
     * The tracker the join, quit, teleport, and world change listener feeds; empty when no adapter matched.
     */
    public Optional<ViewerTracker> tracker() {
        return tracker;
    }

    @Override
    public CanRun canRun() {
        return canRun;
    }

    @Override
    public void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
        running().add(hologramId, position, lines, viewRange);
    }

    @Override
    public void update(UUID hologramId, List<String> lines) {
        running().update(hologramId, lines);
    }

    @Override
    public void remove(UUID hologramId) {
        running().remove(hologramId);
    }

    @Override
    public void removeAll() {
        running().removeAll();
    }

    /**
     * @throws IllegalStateException when no adapter matched, since a provider that cannot run is never chosen
     */
    private ViewerTracker running() {
        return tracker.orElseThrow(() -> new IllegalStateException(
                "the packet provider cannot run: " + canRun.refusal().orElseThrow()));
    }
}
