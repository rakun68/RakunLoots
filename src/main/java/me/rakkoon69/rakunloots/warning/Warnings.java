package me.rakkoon69.rakunloots.warning;

/**
 * The one sink services report warnings, debug lines, and failures to.
 */
@FunctionalInterface
public interface Warnings {

    void warn(Warning warning);

    default boolean debugEnabled() {
        return false;
    }

    default void debug(String line) {
    }

    default void failure(String what, Throwable cause) {
    }

    static Warnings ignore() {
        return warning -> {};
    }
}
