package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.server.bukkit.FoliaFakes;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real pick over the Folia API, which has block displays: it finds them and builds the shipped block display glow
 * by its class name.
 */
class GlowLibrariesBlockDisplayTest {

    /**
     * Acceptance: on a server with block displays the real probe picks the real block display glow, not GlowingBlocks.
     */
    @Test
    void theRealPickOnAServerWithBlockDisplaysBuildsTheBlockDisplayGlow() {
        Server bukkit = FoliaFakes.fake(Server.class, "server", (proxy, method, args) -> switch (method.getName()) {
            case "getWorlds" -> List.of();
            case "getPluginManager" -> FoliaFakes.fake(PluginManager.class, "plugin manager",
                    (manager, call, callArgs) -> call.getName().equals("registerEvents") ? null : FoliaFakes.UNHANDLED);
            default -> FoliaFakes.UNHANDLED;
        });
        Plugin plugin = FoliaFakes.fake(Plugin.class, "plugin", (proxy, method, args) ->
                method.getName().equals("getServer") ? bukkit : FoliaFakes.UNHANDLED);

        GlowLibrary glow = GlowLibraries.create(plugin, new FakeServerAdapter(UUID.randomUUID()));

        assertInstanceOf(BlockDisplayGlow.class, glow);
        assertTrue(glow.sharedByAll());
    }
}
