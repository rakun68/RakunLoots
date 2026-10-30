package me.rakkoon69.rakunloots.hologram;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A hologram provider that records each call and keeps what it would draw. It refuses what the provider contract
 * forbids: a create for an id it still draws, empty lines, and a call after it was told it cannot run.
 */
public final class RecordingHologramProvider implements HologramProvider {

    public enum Call {
        CREATE, UPDATE, REMOVE, REMOVE_ALL
    }

    /**
     * One call, with what it carried; absent parts are null.
     */
    public record Recorded(Call call, UUID hologramId, HologramPosition position, List<String> lines, Integer viewRange) {
    }

    private final CanRun canRun;
    private final List<Recorded> calls = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<String>> drawn = new HashMap<>();
    private int canRunAsked;

    public RecordingHologramProvider() {
        this(CanRun.YES);
    }

    public RecordingHologramProvider(CanRun canRun) {
        this.canRun = Objects.requireNonNull(canRun, "canRun");
    }

    @Override
    public synchronized CanRun canRun() {
        canRunAsked++;
        return canRun;
    }

    @Override
    public synchronized void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
        usable(lines);
        if (drawn.containsKey(hologramId)) {
            throw new IllegalStateException("already drawn: " + hologramId);
        }
        drawn.put(hologramId, List.copyOf(lines));
        calls.add(new Recorded(Call.CREATE, hologramId, position, List.copyOf(lines), viewRange));
    }

    @Override
    public synchronized void update(UUID hologramId, List<String> lines) {
        usable(lines);
        if (drawn.containsKey(hologramId)) {
            drawn.put(hologramId, List.copyOf(lines));
        }
        calls.add(new Recorded(Call.UPDATE, hologramId, null, List.copyOf(lines), null));
    }

    @Override
    public synchronized void remove(UUID hologramId) {
        refuseWhenCannotRun();
        drawn.remove(hologramId);
        calls.add(new Recorded(Call.REMOVE, hologramId, null, null, null));
    }

    @Override
    public synchronized void removeAll() {
        refuseWhenCannotRun();
        drawn.clear();
        calls.add(new Recorded(Call.REMOVE_ALL, null, null, null, null));
    }

    public List<Recorded> calls() {
        return List.copyOf(calls);
    }

    public List<Recorded> calls(Call call) {
        return calls.stream().filter(recorded -> recorded.call() == call).toList();
    }

    public void clear() {
        calls.clear();
    }

    /**
     * What it draws now, by hologram id.
     */
    public synchronized Map<UUID, List<String>> drawn() {
        return Map.copyOf(drawn);
    }

    public synchronized int canRunAsked() {
        return canRunAsked;
    }

    private void usable(List<String> lines) {
        refuseWhenCannotRun();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("no lines");
        }
    }

    private void refuseWhenCannotRun() {
        if (!canRun.yes()) {
            throw new IllegalStateException("called although it cannot run: " + canRun);
        }
    }
}
