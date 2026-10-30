package me.rakkoon69.rakunloots.hologram.packet;

import java.util.Objects;
import java.util.Optional;

/**
 * The packet adapter picked for this server, or why there is none.
 *
 * @param adapter the adapter; empty when none matched
 * @param refusal why none matched, naming the Minecraft version; empty when one did
 */
public record PacketAdapterMatch(Optional<PacketAdapter> adapter, Optional<String> refusal) {

    public PacketAdapterMatch {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(refusal, "refusal");
        if (adapter.isPresent() == refusal.isPresent()) {
            throw new IllegalArgumentException("a match has an adapter or a refusal, never both or neither");
        }
    }

    public static PacketAdapterMatch matched(PacketAdapter adapter) {
        return new PacketAdapterMatch(Optional.of(Objects.requireNonNull(adapter, "adapter")), Optional.empty());
    }

    public static PacketAdapterMatch none(String reason) {
        return new PacketAdapterMatch(Optional.empty(), Optional.of(Objects.requireNonNull(reason, "reason")));
    }
}
