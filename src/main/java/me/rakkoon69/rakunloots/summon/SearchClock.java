package me.rakkoon69.rakunloots.summon;

/**
 * Millisecond clock for idle timing.
 */
@FunctionalInterface
public interface SearchClock {

    long millis();
}
