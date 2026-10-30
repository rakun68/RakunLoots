package me.rakkoon69.rakunloots.hologram.fancy;

import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramServer;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;

/**
 * The FancyHolograms entry as the core builds it, for tests outside the Java 25 source set: the real loader over these
 * probes on Java 25, with a recording provider standing in for the Java 25 provider, which these tests cannot load.
 */
public final class RecordingFancyHolograms {

    private RecordingFancyHolograms() {
    }

    public static HologramProvider provider(HologramServer server) {
        return FancyHologramsProviderLoader.load(server, FancyHologramsProviderLoader.JAVA,
                RecordingHologramProvider::new);
    }
}
