package me.rakkoon69.rakunloots.hologram;

import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;

import java.util.Objects;
import java.util.Optional;

/**
 * What a hologram plugin's provider needs of the server: the plugin installed and enabled, on Folia a plugin that
 * declares {@code folia-supported: true}, a Minecraft version in its range, and Paper when it needs Paper. A provider
 * for a hologram plugin answers {@link HologramProvider#canRun} with {@link #check}.
 *
 * @param plugin  the plugin's name, as in its {@code plugin.yml}
 * @param lowest  the oldest Minecraft release it runs on, if it has one
 * @param highest the newest Minecraft release it runs on, if it has one
 * @param paper   whether it needs Paper
 */
public record PluginRequirement(
        String plugin,
        Optional<MinecraftVersion> lowest,
        Optional<MinecraftVersion> highest,
        boolean paper
) {

    public PluginRequirement {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(lowest, "lowest");
        Objects.requireNonNull(highest, "highest");
    }

    /**
     * The plugin on any version and platform.
     */
    public static PluginRequirement of(String plugin) {
        return new PluginRequirement(plugin, Optional.empty(), Optional.empty(), false);
    }

    /**
     * Also needs this release or newer.
     */
    public PluginRequirement from(String lowest) {
        return new PluginRequirement(plugin, Optional.of(MinecraftVersion.parse(lowest)), highest, paper);
    }

    /**
     * Also needs this release or older.
     */
    public PluginRequirement upTo(String highest) {
        return new PluginRequirement(plugin, lowest, Optional.of(MinecraftVersion.parse(highest)), paper);
    }

    /**
     * Also needs Paper.
     */
    public PluginRequirement paperOnly() {
        return new PluginRequirement(plugin, lowest, highest, true);
    }

    /**
     * Whether this server meets the requirement, or the first thing it lacks.
     */
    public CanRun check(HologramServer server) {
        Objects.requireNonNull(server, "server");
        Optional<HologramServer.PluginState> state = server.plugin(plugin);
        if (state.isEmpty()) {
            return CanRun.no(plugin + " is not installed");
        }
        if (!state.get().enabled()) {
            return CanRun.no(plugin + " is not enabled");
        }
        if (server.folia() && !state.get().foliaSupported()) {
            return CanRun.no(plugin + " does not declare folia-supported: true, and this server is Folia");
        }
        if (paper && !server.paper()) {
            return CanRun.no(plugin + " needs Paper, and this server is Spigot");
        }
        MinecraftVersion version = server.minecraftVersion();
        if (lowest.isPresent() && version.isBelow(lowest.get())) {
            return CanRun.no(plugin + " needs Minecraft " + lowest.get() + " or newer, and this server runs "
                    + version);
        }
        if (highest.isPresent() && version.isAbove(highest.get())) {
            return CanRun.no(plugin + " runs up to Minecraft " + highest.get() + ", and this server runs " + version);
        }
        return CanRun.YES;
    }
}
