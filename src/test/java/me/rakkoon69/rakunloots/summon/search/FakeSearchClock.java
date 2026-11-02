package me.rakkoon69.rakunloots.summon.search;

/**
 * Mutable millisecond clock for idle tests.
 */
final class FakeSearchClock implements SearchClock {

    private long millis;

    @Override
    public long millis() {
        return millis;
    }

    void advanceMillis(long delta) {
        millis += delta;
    }

    void setMillis(long millis) {
        this.millis = millis;
    }
}
