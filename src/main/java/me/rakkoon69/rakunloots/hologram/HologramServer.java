package me.rakkoon69.rakunloots.hologram;

import java.util.Optional;

/**
 * What a hologram provider asks about the server to decide whether it can run. {@link BukkitHologramServer} asks the
 * running server; a test fakes it to be any server.
 */
public interface HologramServer {

    /**
     * Whether the server is Folia.
     */
    boolean folia();

    /**
     * Whether the server is Paper or one of its forks, as opposed to Spigot.
     */
    boolean paper();

    /**
     * The Minecraft release the server runs.
     */
    MinecraftVersion minecraftVersion();

    /**
     * The plugin of that name, or empty when it is not installed.
     */
    Optional<PluginState> plugin(String name);

    /**
     * An installed plugin.
     *
     * @param enabled        whether it is enabled now
     * @param foliaSupported whether its description declares {@code folia-supported: true}
     */
    record PluginState(boolean enabled, boolean foliaSupported) {
    }
}
