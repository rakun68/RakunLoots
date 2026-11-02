package me.rakkoon69.rakunloots.config.menu;

import java.util.Objects;

/**
 * A button's click sound: a namespaced key the server knows, with its volume and pitch.
 */
public final class MenuSound {

    private final String key;
    private final double volume;
    private final double pitch;

    MenuSound(String key, double volume, double pitch) {
        this.key = Objects.requireNonNull(key, "key");
        this.volume = volume;
        this.pitch = pitch;
    }

    public String key() {
        return key;
    }

    public double volume() {
        return volume;
    }

    public double pitch() {
        return pitch;
    }
}
