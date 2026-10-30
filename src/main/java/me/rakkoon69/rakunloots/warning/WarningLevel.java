package me.rakkoon69.rakunloots.warning;

/**
 * What the log lets through: {@link #LOW} lets every warning through, {@link #HIGH} lets high and severe warnings
 * through, and {@link #SEVERE} lets only severe warnings through.
 */
public enum WarningLevel {
    LOW,
    HIGH,
    SEVERE;

    public boolean admits(Severity severity) {
        return severity.rank() >= rank();
    }

    int rank() {
        return ordinal();
    }
}
