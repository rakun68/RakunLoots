package me.rakkoon69.rakunloots.lootbox;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of one summon: the loot box it placed, or why it placed none.
 */
public final class SummonOutcome {

    private final LootBox box;
    private final SummonRefusal refusal;

    private SummonOutcome(LootBox box, SummonRefusal refusal) {
        this.box = box;
        this.refusal = refusal;
    }

    static SummonOutcome summoned(LootBox box) {
        Objects.requireNonNull(box, "box");
        return new SummonOutcome(box, null);
    }

    static SummonOutcome refused(SummonRefusal refusal) {
        Objects.requireNonNull(refusal, "refusal");
        return new SummonOutcome(null, refusal);
    }

    public Optional<LootBox> box() {
        return Optional.ofNullable(box);
    }

    public Optional<SummonRefusal> refusal() {
        return Optional.ofNullable(refusal);
    }

    public boolean isSummoned() {
        return box != null;
    }
}
