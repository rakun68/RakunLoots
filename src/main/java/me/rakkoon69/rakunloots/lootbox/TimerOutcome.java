package me.rakkoon69.rakunloots.lootbox;

import java.util.Objects;
import java.util.Optional;

/**
 * How one world timer summon went: refused before it started, or started and ended as the summon report says.
 */
public final class TimerOutcome {

    private final TimerRefusal refusal;
    private final SummonReport report;

    private TimerOutcome(TimerRefusal refusal, SummonReport report) {
        this.refusal = refusal;
        this.report = report;
    }

    static TimerOutcome refused(TimerRefusal refusal) {
        return new TimerOutcome(Objects.requireNonNull(refusal, "refusal"), null);
    }

    static TimerOutcome summoned(SummonReport report) {
        return new TimerOutcome(null, Objects.requireNonNull(report, "report"));
    }

    /**
     * Why no summon started, or empty when one did.
     */
    public Optional<TimerRefusal> refusal() {
        return Optional.ofNullable(refusal);
    }

    /**
     * How the summon ended: its box, or why it was abandoned. Empty when the timer refused before it started.
     */
    public Optional<SummonReport> report() {
        return Optional.ofNullable(report);
    }
}
