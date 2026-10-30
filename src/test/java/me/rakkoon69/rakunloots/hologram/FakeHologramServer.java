package me.rakkoon69.rakunloots.hologram;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server probes set by the test: Paper, Spigot, or Folia, a Minecraft version, and the installed plugins.
 */
public final class FakeHologramServer implements HologramServer {

    private final boolean folia;
    private final boolean paper;
    private final MinecraftVersion version;
    private final Map<String, PluginState> plugins = new HashMap<>();

    private FakeHologramServer(boolean folia, boolean paper, String version) {
        this.folia = folia;
        this.paper = paper;
        this.version = MinecraftVersion.parse(version);
    }

    public static FakeHologramServer spigot(String version) {
        return new FakeHologramServer(false, false, version);
    }

    public static FakeHologramServer paper(String version) {
        return new FakeHologramServer(false, true, version);
    }

    public static FakeHologramServer folia(String version) {
        return new FakeHologramServer(true, true, version);
    }

    /**
     * Installs an enabled plugin.
     */
    public FakeHologramServer with(String plugin, boolean foliaSupported) {
        plugins.put(plugin, new PluginState(true, foliaSupported));
        return this;
    }

    public FakeHologramServer withDisabled(String plugin) {
        plugins.put(plugin, new PluginState(false, true));
        return this;
    }

    @Override
    public boolean folia() {
        return folia;
    }

    @Override
    public boolean paper() {
        return paper;
    }

    @Override
    public MinecraftVersion minecraftVersion() {
        return version;
    }

    @Override
    public Optional<PluginState> plugin(String name) {
        return Optional.ofNullable(plugins.get(name));
    }
}
