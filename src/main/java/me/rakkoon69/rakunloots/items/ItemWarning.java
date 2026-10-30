package me.rakkoon69.rakunloots.items;

import me.rakkoon69.rakunloots.warning.Severity;

import java.util.Objects;
import java.util.Optional;

/**
 * A resolve-time problem. Resolve does not write to the log; the caller reports these.
 */
public final class ItemWarning {

    public enum Kind {
        UNKNOWN_SOURCE,
        BLANK_ID,
        PLUGIN_ABSENT,
        UNKNOWN_ID,
        SKIPPED_ADDITION
    }

    private final Severity severity;
    private final Kind kind;
    private final Optional<String> detail;

    public ItemWarning(Severity severity, Kind kind, Optional<String> detail) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public static ItemWarning unknownSource(String source) {
        return new ItemWarning(Severity.HIGH, Kind.UNKNOWN_SOURCE, Optional.ofNullable(source));
    }

    public static ItemWarning blankId() {
        return new ItemWarning(Severity.HIGH, Kind.BLANK_ID, Optional.empty());
    }

    public static ItemWarning pluginAbsent(String source) {
        return new ItemWarning(Severity.HIGH, Kind.PLUGIN_ABSENT, Optional.ofNullable(source));
    }

    public static ItemWarning unknownId(String id) {
        return new ItemWarning(Severity.HIGH, Kind.UNKNOWN_ID, Optional.ofNullable(id));
    }

    public static ItemWarning skippedAddition(String detail) {
        return new ItemWarning(Severity.HIGH, Kind.SKIPPED_ADDITION, Optional.ofNullable(detail));
    }

    public Severity severity() {
        return severity;
    }

    public Kind kind() {
        return kind;
    }

    public Optional<String> detail() {
        return detail;
    }
}
