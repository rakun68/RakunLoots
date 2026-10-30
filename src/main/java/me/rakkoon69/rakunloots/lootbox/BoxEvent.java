package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.SummonKind;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * One loot box as the lifecycle saw it when it reported an event to its {@link BoxObserver}. It holds no item stacks,
 * so it is safe to keep and to read on any thread.
 *
 * @param boxId                the box
 * @param lootId               the loot it was summoned from
 * @param worldKey             the world of its block
 * @param position             its block
 * @param owner                the player the lock admits, or empty for none
 * @param remainingLockSeconds lock left in seconds; 0 means unlocked
 * @param remainingLifeSeconds life left in seconds; empty means unending life
 * @param source               how the box was summoned
 */
public record BoxEvent(
        UUID boxId,
        String lootId,
        UUID worldKey,
        BlockPosition position,
        Optional<UUID> owner,
        int remainingLockSeconds,
        OptionalInt remainingLifeSeconds,
        SummonKind source
) {

    public BoxEvent {
        Objects.requireNonNull(boxId, "boxId");
        Objects.requireNonNull(lootId, "lootId");
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(remainingLifeSeconds, "remainingLifeSeconds");
        Objects.requireNonNull(source, "source");
    }

    /**
     * An event for a box whose summon kind does not matter to the caller, such as a hologram test: its source is
     * {@link SummonKind#UNKNOWN}.
     */
    public BoxEvent(UUID boxId, String lootId, UUID worldKey, BlockPosition position, Optional<UUID> owner,
                    int remainingLockSeconds, OptionalInt remainingLifeSeconds) {
        this(boxId, lootId, worldKey, position, owner, remainingLockSeconds, remainingLifeSeconds,
                SummonKind.UNKNOWN);
    }

    static BoxEvent of(LootBox box) {
        return new BoxEvent(box.boxId(), box.lootId(), box.worldKey(), box.position(), box.owner(),
                box.remainingLockSeconds(), box.remainingLifeSeconds(), box.source());
    }
}
