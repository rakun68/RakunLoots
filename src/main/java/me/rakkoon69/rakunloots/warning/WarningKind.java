package me.rakkoon69.rakunloots.warning;

/**
 * A kind of warning a module can raise: its stable {@code warnings.yml} name and its default severity.
 */
public interface WarningKind {

    String name();

    default String stableName() {
        return name();
    }

    Severity defaultSeverity();
}
