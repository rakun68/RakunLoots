package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.storage.SummonKind;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a summon came from: the world timer, an admin command, or a player's summon item. A box keeps the
 * {@link #kind()}. An abandon uses the summon item's player to give the item back; a summon from the world timer or
 * an admin has nothing to give back and is only refused.
 */
public final class SummonSource {

    private static final SummonSource WORLD_TIMER = new SummonSource(SummonKind.WORLD_TIMER, null);
    private static final SummonSource ADMIN_COMMAND = new SummonSource(SummonKind.ADMIN_COMMAND, null);

    private final SummonKind kind;
    private final UUID summonItemPlayer;

    private SummonSource(SummonKind kind, UUID summonItemPlayer) {
        this.kind = kind;
        this.summonItemPlayer = summonItemPlayer;
    }

    /**
     * The world timer summoned it.
     */
    public static SummonSource worldTimer() {
        return WORLD_TIMER;
    }

    /**
     * An admin summoned it with the summon command.
     */
    public static SummonSource adminCommand() {
        return ADMIN_COMMAND;
    }

    /**
     * A summon item the given player used and that was taken at the click.
     */
    public static SummonSource summonItem(UUID playerId) {
        return new SummonSource(SummonKind.SUMMON_ITEM, Objects.requireNonNull(playerId, "playerId"));
    }

    public SummonKind kind() {
        return kind;
    }

    /**
     * The player whose summon item was taken, or empty for a summon that used none.
     */
    public Optional<UUID> summonItemPlayer() {
        return Optional.ofNullable(summonItemPlayer);
    }
}
