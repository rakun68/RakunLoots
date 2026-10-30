package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramPosition;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A packet adapter whose packets are records and whose sends are recorded, as a packet sink. It draws text displays,
 * one entity per hologram, or armor stands, one per line. It refuses what a real adapter refuses: empty lines, an id
 * count that does not fit the lines, and sending a packet it did not build.
 */
final class RecordingPacketAdapter implements PacketAdapter {

    sealed interface Packet permits Spawn, Meta, Destroy {
    }

    record Spawn(List<Integer> entityIds, HologramPosition position, List<String> lines) implements Packet {
    }

    record Meta(List<Integer> entityIds, List<String> lines) implements Packet {
    }

    record Destroy(List<Integer> entityIds) implements Packet {
    }

    /**
     * One packet sent to one player.
     */
    record Sent(UUID playerId, Packet packet) {
    }

    private final boolean armorStands;
    private final AtomicInteger entityCounter = new AtomicInteger(1000);
    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    private RecordingPacketAdapter(boolean armorStands) {
        this.armorStands = armorStands;
    }

    static RecordingPacketAdapter textDisplays() {
        return new RecordingPacketAdapter(false);
    }

    static RecordingPacketAdapter armorStands() {
        return new RecordingPacketAdapter(true);
    }

    @Override
    public int entityCount(int lineCount) {
        if (lineCount < 1) {
            throw new IllegalArgumentException("no lines");
        }
        return armorStands ? lineCount : 1;
    }

    @Override
    public int nextEntityId() {
        return entityCounter.incrementAndGet();
    }

    @Override
    public Object spawn(List<Integer> entityIds, HologramPosition position, List<String> lines) {
        fits(entityIds, lines);
        return new Spawn(List.copyOf(entityIds), Objects.requireNonNull(position, "position"), List.copyOf(lines));
    }

    @Override
    public Object meta(List<Integer> entityIds, List<String> lines) {
        fits(entityIds, lines);
        return new Meta(List.copyOf(entityIds), List.copyOf(lines));
    }

    @Override
    public Object destroy(List<Integer> entityIds) {
        if (entityIds.isEmpty()) {
            throw new IllegalArgumentException("no entities");
        }
        return new Destroy(List.copyOf(entityIds));
    }

    @Override
    public void send(UUID playerId, Object packet) {
        Objects.requireNonNull(playerId, "playerId");
        if (!(packet instanceof Packet built)) {
            throw new IllegalArgumentException("not a packet this adapter built: " + packet);
        }
        sent.add(new Sent(playerId, built));
    }

    List<Sent> sent() {
        return List.copyOf(sent);
    }

    /**
     * The packets one player got, in order.
     */
    List<Packet> sentTo(UUID playerId) {
        return sent.stream().filter(one -> one.playerId().equals(playerId)).map(Sent::packet).toList();
    }

    void clear() {
        sent.clear();
    }

    private void fits(List<Integer> entityIds, List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("no lines");
        }
        if (entityIds.size() != entityCount(lines.size())) {
            throw new IllegalArgumentException(entityIds.size() + " entities for " + lines.size() + " lines");
        }
    }
}
