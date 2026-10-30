package me.rakkoon69.rakunloots.hologram;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real server probes and owner names over MockBukkit, on the 1.18 Paper API.
 */
class BukkitHologramServerTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void thePaperApiIsPaperAndNotFolia() {
        HologramServer probes = new BukkitHologramServer(server);

        assertTrue(probes.paper());
        assertFalse(probes.folia());
    }

    @Test
    void theVersionIsReadFromTheBukkitVersion() {
        HologramServer probes = new BukkitHologramServer(server);

        assertEquals(MinecraftVersion.parse(server.getBukkitVersion()), probes.minecraftVersion());
        assertEquals(1, probes.minecraftVersion().major());
        assertEquals(18, probes.minecraftVersion().minor());
    }

    /**
     * An installed plugin is found by name, enabled or not. The 1.18 API has no {@code folia-supported}, so it reads
     * false.
     */
    @Test
    void aPluginIsFoundByNameEnabledOrNot() {
        HologramServer probes = new BukkitHologramServer(server);
        Plugin decent = MockBukkit.createMockPlugin("DecentHolograms");

        assertEquals(Optional.of(new HologramServer.PluginState(true, false)), probes.plugin("DecentHolograms"));
        assertEquals(Optional.empty(), probes.plugin("HolographicDisplays"));

        server.getPluginManager().disablePlugin(decent);

        assertEquals(Optional.of(new HologramServer.PluginState(false, false)), probes.plugin("DecentHolograms"));
    }

    /**
     * An online owner's name comes from the player.
     */
    @Test
    void anOnlineOwnersNameComesFromThePlayer() {
        PlayerMock online = server.addPlayer("Rakkoon");

        assertEquals(Optional.of("Rakkoon"), OwnerNames.of(server).name(online.getUniqueId()));
    }

    /**
     * An offline owner's name comes from the server's offline player, and an owner the server never saw has none:
     * the real server's offline player then has a null name. MockBukkit makes up a name for any id and cannot take a
     * player offline, so this server answers as the real one does.
     */
    @Test
    void anOfflineOwnersNameComesFromTheOfflinePlayer() {
        UUID known = UUID.fromString("88888888-0000-0000-0000-000000000000");
        UUID stranger = UUID.fromString("99999999-0000-0000-0000-000000000000");
        Server real = proxy(Server.class, (method, args) -> switch (method.getName()) {
            case "getPlayer" -> null;
            case "getOfflinePlayer" -> offlinePlayer(args[0].equals(known) ? "Tanuki" : null);
            default -> throw new UnsupportedOperationException(method.getName());
        });

        assertEquals(Optional.of("Tanuki"), OwnerNames.of(real).name(known));
        assertEquals(Optional.empty(), OwnerNames.of(real).name(stranger));
    }

    private static OfflinePlayer offlinePlayer(String name) {
        return proxy(OfflinePlayer.class, (method, args) -> {
            if (method.getName().equals("getName")) {
                return name;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> answer.apply(method, args)));
    }
}
