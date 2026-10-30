package me.rakkoon69.rakunloots.server;

import java.util.Objects;
import java.util.UUID;

/**
 * A world that is loaded now: its runtime key and the name in {@code worlds.yml}.
 */
public record LoadedWorld(UUID key, String name) {

    public LoadedWorld {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
    }
}
