package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ServerAdapterSelectionTest {

    private final Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
            new Class<?>[]{Plugin.class}, (proxy, method, args) -> {
                throw new UnsupportedOperationException("selection does not call the plugin: " + method);
            });

    @Test
    void neitherFoliaNorPaperSelectsSpigot() {
        FakeServerProbes probes = new FakeServerProbes(false, false);

        ServerAdapter adapter = ServerAdapters.select(plugin, probes);

        assertInstanceOf(BukkitServerAdapter.class, adapter);
        assertInstanceOf(SpigotChunkLoader.class, ServerAdapters.chunkLoader(plugin, probes));
        assertEquals(0, probes.foliaAdapterLoads(), "loaded the Folia adapter");
    }

    @Test
    void paperWithoutFoliaSelectsPaper() {
        FakeServerProbes probes = new FakeServerProbes(false, true);

        ServerAdapter adapter = ServerAdapters.select(plugin, probes);

        assertInstanceOf(BukkitServerAdapter.class, adapter);
        assertInstanceOf(PaperChunkLoader.class, ServerAdapters.chunkLoader(plugin, probes));
        assertEquals(0, probes.foliaAdapterLoads(), "loaded the Folia adapter");
    }

    @Test
    void thePaperApiWithoutFoliaSelectsPaperThroughTheRealProbes() {
        ServerAdapter adapter = ServerAdapters.select(plugin);

        assertInstanceOf(BukkitServerAdapter.class, adapter);
    }

    @Test
    void paperAndSpigotAreNotExperimental() {
        assertFalse(ServerAdapters.select(plugin, new FakeServerProbes(false, true)).experimental());
        assertFalse(ServerAdapters.select(plugin, new FakeServerProbes(false, false)).experimental());
    }
}
