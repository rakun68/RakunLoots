package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.LootBox;

import java.util.Objects;
import java.util.Optional;

/**
 * How one summon ended: the loot box it placed, or why it was abandoned.
 */
public final class SummonReport {

    private final LootBox box;
    private final AbandonReason abandoned;
    private final String message;

    private SummonReport(LootBox box, AbandonReason abandoned, String message) {
        this.box = box;
        this.abandoned = abandoned;
        this.message = message;
    }

    static SummonReport placed(LootBox box) {
        return new SummonReport(Objects.requireNonNull(box, "box"), null, "");
    }

    static SummonReport abandoned(AbandonReason reason, String message) {
        return new SummonReport(null, Objects.requireNonNull(reason, "reason"), Objects.requireNonNull(message));
    }

    public Optional<LootBox> box() {
        return Optional.ofNullable(box);
    }

    public Optional<AbandonReason> abandoned() {
        return Optional.ofNullable(abandoned);
    }

    /**
     * The default message of the abandon reason, in legacy color codes, with the loot's overrides. Blank when the
     * summon placed a box or the message is blank.
     */
    public String message() {
        return message;
    }
}
