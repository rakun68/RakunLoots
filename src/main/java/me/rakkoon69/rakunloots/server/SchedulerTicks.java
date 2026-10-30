package me.rakkoon69.rakunloots.server;

/**
 * Checks the delays and periods a task scheduler is given, so every adapter rejects the same ones.
 */
final class SchedulerTicks {

    private SchedulerTicks() {
    }

    /**
     * @throws IllegalArgumentException when the delay or period is below 1
     */
    static void requireAtLeastOne(String name, long ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException(name + " must be at least 1, got " + ticks);
        }
    }
}
