package me.rakkoon69.rakunloots.animation;

import me.rakkoon69.rakunloots.config.AnimationMoment;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.Objects;
import java.util.Optional;

/**
 * A problem the animation service found. The service does not write to the log; it hands these to its warning sink.
 * Each is always high, and the service reports each distinct one once per config.
 */
public final class AnimationWarning implements Warning {

    public enum Kind implements WarningKind {
        /**
         * A loot names an animation id that {@code animations.yml} does not define, so nothing plays for it. Carries the
         * loot id, the moment, and the animation id.
         */
        UNKNOWN_ANIMATION(Severity.HIGH, "ANIMATION_UNKNOWN_ANIMATION"),
        /**
         * An animation's {@code type} is in no registry entry, so it plays nothing. Carries the animation id and the
         * type.
         */
        UNKNOWN_TYPE(Severity.HIGH),
        /**
         * An animation's variables cannot be used by its type, so it plays nothing. Carries the animation id and the
         * reason.
         */
        UNUSABLE_VARIABLES(Severity.HIGH),
        /**
         * Drawing an animation's particles failed, such as a particle name this server does not have. Carries the
         * animation id and the failure; the same failure of the same animation is reported once.
         */
        DRAW_FAILED(Severity.HIGH);

        private final Severity defaultSeverity;
        private final String stableName;

        Kind(Severity defaultSeverity) {
            this(defaultSeverity, null);
        }

        Kind(Severity defaultSeverity, String stableName) {
            this.defaultSeverity = defaultSeverity;
            this.stableName = stableName;
        }

        @Override
        public String stableName() {
            return stableName != null ? stableName : name();
        }

        @Override
        public Severity defaultSeverity() {
            return defaultSeverity;
        }
    }

    private final Kind kind;
    private final Optional<String> lootId;
    private final Optional<AnimationMoment> moment;
    private final String animationId;
    private final Optional<String> detail;
    private final Optional<Throwable> cause;

    private AnimationWarning(Kind kind, Optional<String> lootId, Optional<AnimationMoment> moment,
                             String animationId, Optional<String> detail, Optional<Throwable> cause) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.lootId = lootId;
        this.moment = moment;
        this.animationId = Objects.requireNonNull(animationId, "animationId");
        this.detail = detail;
        this.cause = cause;
    }

    static AnimationWarning unknownAnimation(String lootId, AnimationMoment moment, String animationId) {
        return new AnimationWarning(Kind.UNKNOWN_ANIMATION, Optional.of(lootId), Optional.of(moment), animationId,
                Optional.empty(), Optional.empty());
    }

    static AnimationWarning unknownType(String animationId, String type) {
        return new AnimationWarning(Kind.UNKNOWN_TYPE, Optional.empty(), Optional.empty(), animationId,
                Optional.of(type), Optional.empty());
    }

    static AnimationWarning unusableVariables(String animationId, String reason) {
        return new AnimationWarning(Kind.UNUSABLE_VARIABLES, Optional.empty(), Optional.empty(), animationId,
                Optional.of(reason), Optional.empty());
    }

    static AnimationWarning drawFailed(String animationId, Throwable cause) {
        return new AnimationWarning(Kind.DRAW_FAILED, Optional.empty(), Optional.empty(), animationId,
                Optional.of(String.valueOf(cause)), Optional.of(cause));
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    /**
     * The loot that named a missing animation, for {@link Kind#UNKNOWN_ANIMATION}.
     */
    public Optional<String> lootId() {
        return lootId;
    }

    /**
     * The moment the missing animation would have played at, for {@link Kind#UNKNOWN_ANIMATION}.
     */
    public Optional<AnimationMoment> moment() {
        return moment;
    }

    public String animationId() {
        return animationId;
    }

    /**
     * The unknown type, the reason the variables cannot be used, or the failure's text.
     */
    public Optional<String> detail() {
        return detail;
    }

    @Override
    public Optional<Throwable> cause() {
        return cause;
    }

    @Override
    public Optional<String> subject() {
        return Optional.of(animationId);
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("loot", lootId),
                Warning.field("moment", moment),
                Warning.field("animation", Optional.of(animationId)),
                Warning.field("detail", detail)
        );
    }

    /**
     * What makes two warnings the same one, so the service reports it once.
     */
    String identity() {
        return kind + "|" + lootId.orElse("") + "|" + moment.map(Enum::name).orElse("") + "|" + animationId + "|"
                + detail.orElse("");
    }

    @Override
    public String toString() {
        return kind + " " + lootId.map(id -> id + " ").orElse("") + moment.map(m -> m + " ").orElse("") + animationId
                + detail.map(text -> " " + text).orElse("") + cause.map(failure -> " " + failure).orElse("");
    }
}
