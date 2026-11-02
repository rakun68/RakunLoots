package me.rakkoon69.rakunloots.summon.search;

/**
 * Millisecond clock for idle timing.
 */
@FunctionalInterface
public interface SearchClock {

    long millis();
}
