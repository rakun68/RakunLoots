package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A problem the box visuals service found. The service does not write to the log; it hands these to its warning sink.
 * Severities are the config severities, so the warning level filters them the same way. Every one is low: a box without
 * its glow or laser is a box that still works.
 */
public final class VisualsWarning implements Warning {

    public enum Kind implements WarningKind {
        /**
         * The glow library could not be set up, such as GlowingBlocks on a server that is not Paper. No box glows until
         * the next start. Carries the failure. Told once, at start.
         */
        GLOW_UNAVAILABLE(Severity.LOW),
        /**
         * The laser library could not be set up, such as GuardianBeam on Folia. No box shows a laser until the next
         * start. Carries the failure. Told once, at start.
         */
        LASER_UNAVAILABLE(Severity.LOW),
        /**
         * Glow failed for one box, and the box went on without it. Carries the box's loot and the failure. Told once
         * for each reload, for the first box that failed.
         */
        GLOW_FAILED(Severity.LOW),
        /**
         * The laser failed for one box, and the box went on without it. Carries the box's loot and the failure. Told
         * once for each reload, for the first box that failed.
         */
        LASER_FAILED(Severity.LOW),
        /**
         * A guardian laser was asked for in a peaceful world, where the client does not create a guardian, so the box
         * draws a crystal laser instead. Carries the box's loot and no failure. Told once for each loot for each
         * reload.
         */
        GUARDIAN_IN_PEACEFUL(Severity.LOW);

        private final Severity defaultSeverity;

        Kind(Severity defaultSeverity) {
            this.defaultSeverity = defaultSeverity;
        }

        @Override
        public Severity defaultSeverity() {
            return defaultSeverity;
        }
    }

    private final Kind kind;
    private final Optional<String> lootId;
    private final Optional<UUID> boxId;
    private final Optional<Throwable> cause;

    private VisualsWarning(Kind kind, Optional<String> lootId, Optional<UUID> boxId, Optional<Throwable> cause) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.lootId = Objects.requireNonNull(lootId, "lootId");
        this.boxId = Objects.requireNonNull(boxId, "boxId");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    static VisualsWarning unavailable(Kind kind, Throwable cause) {
        return new VisualsWarning(kind, Optional.empty(), Optional.empty(), Optional.of(cause));
    }

    static VisualsWarning failed(Kind kind, String lootId, UUID boxId, Throwable cause) {
        return new VisualsWarning(kind, Optional.of(lootId), Optional.of(boxId), Optional.of(cause));
    }

    static VisualsWarning guardianInPeaceful(String lootId, UUID boxId) {
        return new VisualsWarning(Kind.GUARDIAN_IN_PEACEFUL, Optional.of(lootId), Optional.of(boxId),
                Optional.empty());
    }

    @Override
    public Severity severity() {
        return Severity.LOW;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    public Optional<String> lootId() {
        return lootId;
    }

    public Optional<UUID> boxId() {
        return boxId;
    }

    @Override
    public Optional<Throwable> cause() {
        return cause;
    }

    @Override
    public Optional<String> subject() {
        return lootId.or(() -> boxId.map(Object::toString));
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("loot", lootId),
                Warning.field("box", boxId),
                Warning.field("cause", cause)
        );
    }

    @Override
    public String toString() {
        return kind + " " + lootId.orElse("-") + " " + cause.map(Throwable::toString).orElse("-");
    }
}
