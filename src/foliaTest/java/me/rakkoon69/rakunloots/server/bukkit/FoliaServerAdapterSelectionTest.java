package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Selection on Folia. It lives beside the Folia adapter, since the main test source set cannot see that class.
 */
class FoliaServerAdapterSelectionTest {

    @Test
    void foliaSelectsFoliaEvenThoughFoliaHasPapersChunkCall() {
        Plugin plugin = FoliaFakes.fake(Plugin.class, "Plugin", (proxy, method, args) -> FoliaFakes.UNHANDLED);
        FakeServerProbes probes = new FakeServerProbes(true, true);

        ServerAdapter adapter = ServerAdapters.select(plugin, probes);

        assertInstanceOf(FoliaServerAdapter.class, adapter);
        assertEquals(1, probes.foliaAdapterLoads());
    }

    @Test
    void foliaIsNotExperimental() {
        Plugin plugin = FoliaFakes.fake(Plugin.class, "Plugin", (proxy, method, args) -> FoliaFakes.UNHANDLED);

        ServerAdapter adapter = ServerAdapters.select(plugin, new FakeServerProbes(true, true));

        assertFalse(adapter.experimental());
    }
}
