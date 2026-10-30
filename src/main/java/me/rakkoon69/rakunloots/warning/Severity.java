package me.rakkoon69.rakunloots.warning;

/**
 * The importance of a warning: {@link #LOW} is quiet and maps to info, {@link #HIGH} maps to warning, and
 * {@link #SEVERE} maps to severe.
 */
public enum Severity {
    LOW,
    HIGH,
    SEVERE;

    int rank() {
        return ordinal();
    }
}
