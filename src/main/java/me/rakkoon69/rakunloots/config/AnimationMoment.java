package me.rakkoon69.rakunloots.config;

/**
 * The five moments of a loot box at which a loot can play animations. The key is how a loot file names the moment
 * under {@code animations:}.
 */
public enum AnimationMoment {

    /**
     * During the summon delay, at the pending summon's spot.
     */
    SUMMON("summon"),
    /**
     * While the box lives and is shown.
     */
    ALIVE("alive"),
    /**
     * Once, when the box's lock reaches 0.
     */
    UNLOCKED("unlocked"),
    /**
     * When a player opens the box.
     */
    OPEN("open"),
    /**
     * When the box ends, whatever ended it.
     */
    REMOVE("remove");

    private final String key;

    AnimationMoment(String key) {
        this.key = key;
    }

    /**
     * The key a loot file uses under {@code animations:}.
     */
    public String key() {
        return key;
    }
}
