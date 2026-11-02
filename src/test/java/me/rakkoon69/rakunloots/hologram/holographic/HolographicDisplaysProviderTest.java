package me.rakkoon69.rakunloots.hologram.holographic;

import me.rakkoon69.rakunloots.hologram.FakeHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.config.HologramProviderChoice;
import me.rakkoon69.rakunloots.hologram.HologramProviders;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.OwnerNames;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.hologram.decent.FakeDecentHolograms;
import me.rakkoon69.rakunloots.hologram.holographic.FakeHolographicDisplays.Call;
import me.rakkoon69.rakunloots.hologram.holographic.FakeHolographicDisplays.Drawn;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapters;
import me.rakkoon69.rakunloots.hologram.packet.PacketHologramProvider;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static me.rakkoon69.rakunloots.config.HologramProviderChoice.AUTO;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.DECENTHOLOGRAMS;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.FANCYHOLOGRAMS;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.HOLOGRAPHICDISPLAYS;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.PACKETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HolographicDisplays provider under the real hologram service over the shipped config, against a fake
 * HolographicDisplays that, like HolographicDisplays 3.0.5, refuses every call off the main thread. The service is
 * driven from the test's own thread, which is not the main thread, as the lifecycle calls it from region and scheduler
 * threads. The shipped airdrop's hologram sits 2 blocks above its block.
 */
class HolographicDisplaysProviderTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final HologramPosition BOX_BOTTOM = new HologramPosition(WORLD, 2.5, 66.0, 3.5);
    private static final OwnerNames NO_NAMES = id -> Optional.empty();

    private final FakeHolographicDisplays plugin = new FakeHolographicDisplays(WORLD);
    private final HolographicDisplaysProvider provider = FakeHolographicDisplays.provider(
            FakeHologramServer.paper("1.20.4").with("HolographicDisplays", false), plugin);
    private final HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NO_NAMES);

    /**
     * Acceptance: against a fake that refuses calls off the main thread, create draws the rendered lines with the
     * lowest line at the position. The call waits for the main thread: nothing reaches HolographicDisplays until the
     * scheduler runs it there.
     */
    @Test
    void createDrawsTheLinesOnTheMainThread() {
        service.shown(box(BOX, 10));

        assertEquals(Map.of(), plugin.holograms(), "nothing reaches HolographicDisplays off the main thread");
        assertEquals(1, plugin.waitingTasks());

        plugin.runTasks();

        assertEquals(Map.of(BOX, new Drawn(BOX_BOTTOM, List.of(
                "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                "§7Owner: §e§7No owner",
                "§7Lock: §c0:10",
                "§7Leaves in: §e6:00"))), plugin.holograms());
    }

    /**
     * Acceptance: a tick that changes the lines updates them in place, and the box's end removes the hologram, each on
     * the main thread.
     */
    @Test
    void updateReplacesTheLinesAndRemoveTakesTheHologramAway() {
        service.shown(box(BOX, 10));
        plugin.runTasks();

        service.ticked(box(BOX, 9));
        plugin.runTasks();
        assertEquals("§7Lock: §c0:09", plugin.holograms().get(BOX).lines().get(2));
        assertEquals(BOX_BOTTOM, plugin.holograms().get(BOX).position());

        service.ended(box(BOX, 9));
        plugin.runTasks();
        assertEquals(Map.of(), plugin.holograms());
    }

    /**
     * Acceptance: remove all, off the main thread, removes every hologram the provider draws, on the main thread.
     */
    @Test
    void removeAllTakesAwayEveryHologramItDraws() {
        service.shown(box(BOX, 10));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        plugin.runTasks();
        assertEquals(Set.of(BOX, OTHER_BOX), plugin.holograms().keySet());

        service.stop();
        assertEquals(Set.of(BOX, OTHER_BOX), plugin.holograms().keySet(), "remove all waits for the main thread");
        plugin.runTasks();

        assertEquals(Map.of(), plugin.holograms());
    }

    /**
     * Acceptance: every call reaches HolographicDisplays on the main thread, in the order the service made them,
     * through a whole life of boxes.
     */
    @Test
    void everyCallReachesHolographicDisplaysOnTheMainThreadInOrder() {
        service.shown(box(BOX, 10));
        service.ticked(box(BOX, 9));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        service.outOfView(box(BOX, 9));
        service.shown(box(BOX, 9));
        service.ticked(box(BOX, 8));
        service.ended(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        service.stop();

        plugin.runTasks();

        List<Call> calls = plugin.calls();
        assertEquals(List.of("create", "setLines", "create", "remove", "create", "setLines", "remove", "remove"),
                calls.stream().map(Call::name).toList());
        assertEquals(List.of(BOX, BOX, OTHER_BOX, BOX, BOX, BOX, OTHER_BOX, BOX),
                calls.stream().map(Call::hologramId).toList());
        assertEquals(Set.of("Server thread"), Set.copyOf(calls.stream().map(Call::thread).toList()));
        assertEquals(Map.of(), plugin.holograms());
    }

    /**
     * The fake refuses a call off the main thread with HolographicDisplays' own exception and message, and takes it on
     * the main thread. {@code HolographicDisplaysApiCallsTest} checks the same refusal against the real
     * HolographicDisplays.
     */
    @Test
    void theFakeRefusesCallsOffTheMainThread() {
        List<String> lines = List.of("§6Air Supply");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> plugin.create(BOX, BOX_BOTTOM, lines));
        assertEquals(FakeHolographicDisplays.OFF_MAIN_THREAD, refused.getMessage());
        assertThrows(IllegalStateException.class, () -> plugin.setLines(BOX, lines));
        assertThrows(IllegalStateException.class, () -> plugin.remove(BOX));
        assertEquals(List.of(), plugin.calls());

        plugin.onMain(() -> plugin.create(BOX, BOX_BOTTOM, lines));
        assertEquals(Map.of(BOX, new Drawn(BOX_BOTTOM, lines)), plugin.holograms());
    }

    /**
     * The plugin stops on the main thread, where its scheduler takes no more tasks. Remove all then runs in place and
     * removes every hologram, and a call that was still waiting never draws one after it.
     */
    @Test
    void removeAllAtDisableRunsInPlaceOnTheMainThread() {
        service.shown(box(BOX, 10));
        plugin.runTasks();
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));

        plugin.onMain(() -> {
            plugin.disablePlugin();
            service.stop();
        });

        assertEquals(Map.of(), plugin.holograms());
        assertEquals(List.of("create", "remove"), plugin.calls().stream().map(Call::name).toList());
    }

    /**
     * Remove all on the main thread while the plugin still runs: a call that waited from before it is dropped, and a
     * call made after it goes through.
     */
    @Test
    void aCallWaitingFromBeforeRemoveAllOnTheMainThreadIsDropped() {
        provider.create(BOX, BOX_BOTTOM, List.of("§6before"), 48);

        plugin.onMain(provider::removeAll);
        provider.create(OTHER_BOX, BOX_BOTTOM, List.of("§6after"), 48);
        plugin.runTasks();

        assertEquals(Map.of(OTHER_BOX, new Drawn(BOX_BOTTOM, List.of("§6after"))), plugin.holograms());
    }

    /**
     * Update and remove of an id the provider does not draw do nothing.
     */
    @Test
    void updateAndRemoveOfAnUnknownIdDoNothing() {
        provider.update(BOX, List.of("§cnot drawn"));
        provider.remove(BOX);
        plugin.runTasks();

        assertEquals(List.of(), plugin.calls());
    }

    /**
     * A create HolographicDisplays refuses, such as in a world that is not loaded, is not recorded, so a later update
     * and remove of that id do nothing.
     */
    @Test
    void aRefusedCreateIsNotRecorded() {
        HologramPosition unloaded = new HologramPosition(UUID.randomUUID(), 0.5, 64.0, 0.5);
        provider.create(BOX, unloaded, List.of("§6Air Supply"), 48);

        assertThrows(IllegalStateException.class, plugin::runTasks);
        provider.update(BOX, List.of("§6Air Supply 2"));
        provider.remove(BOX);
        plugin.runTasks();

        assertEquals(List.of("create"), plugin.calls().stream().map(Call::name).toList());
    }

    /**
     * Acceptance: can run is false when HolographicDisplays is missing or disabled, above Minecraft 1.20.4, and on
     * Folia, even when a HolographicDisplays there declared {@code folia-supported: true}.
     */
    @ParameterizedTest(name = "{0} {1} with {2}")
    @CsvSource({
            "paper, 1.20.4, enabled, ''",
            "spigot, 1.18, enabled, ''",
            "spigot, 1.20.4, enabled, ''",
            "paper, 1.20.4, missing, HolographicDisplays is not installed",
            "paper, 1.20.4, disabled, HolographicDisplays is not enabled",
            "paper, 1.20.5, enabled, "
                    + "'HolographicDisplays runs up to Minecraft 1.20.4, and this server runs 1.20.5'",
            "spigot, 26.1, enabled, 'HolographicDisplays runs up to Minecraft 1.20.4, and this server runs 26.1'",
            "folia, 1.20.4, missing, HolographicDisplays is not installed",
            "folia, 1.20.4, enabled, "
                    + "'HolographicDisplays does not declare folia-supported: true, and this server is Folia'",
            "folia, 1.20.4, folia, "
                    + "'HolographicDisplays never runs on Folia: its API works only on the main thread'",
    })
    void canRunNeedsHolographicDisplaysEnabledUpTo1204AndNeverOnFolia(String platform, String version, String plugin,
                                                                     String refusal) {
        FakeHologramServer server = server(platform, version);
        switch (plugin) {
            case "enabled" -> server.with("HolographicDisplays", false);
            case "folia" -> server.with("HolographicDisplays", true);
            case "disabled" -> server.withDisabled("HolographicDisplays");
            default -> {
            }
        }

        CanRun canRun = FakeHolographicDisplays.provider(server, new FakeHolographicDisplays()).canRun();

        assertEquals(refusal.isEmpty() ? CanRun.YES : CanRun.no(refusal), canRun);
    }

    /**
     * Acceptance: {@code auto} picks HolographicDisplays only when nothing earlier in its order can run: packets,
     * DecentHolograms, and FancyHolograms each win over it when they can, and it is not asked then.
     */
    @ParameterizedTest(name = "{0} can run")
    @CsvSource({
            "nothing earlier, HOLOGRAPHICDISPLAYS",
            "packets, PACKETS",
            "decent, DECENTHOLOGRAMS",
            "fancy, FANCYHOLOGRAMS",
    })
    void autoPicksHolographicDisplaysOnlyWhenNothingEarlierCanRun(String earlier, HologramProviderChoice expected) {
        FakeHologramServer server = FakeHologramServer.paper("1.20.4").with("HolographicDisplays", false);
        if (earlier.equals("decent")) {
            server.with("DecentHolograms", false);
        }
        HologramProvider packets = earlier.equals("packets") ? new RecordingHologramProvider()
                : new PacketHologramProvider(PacketAdapters.select(server), new FakeServerAdapter(UUID.randomUUID()));
        HologramProvider decent = FakeDecentHolograms.provider(server, new FakeDecentHolograms());
        HologramProvider fancy = new RecordingHologramProvider(earlier.equals("fancy") ? CanRun.YES
                : CanRun.no("FancyHolograms is not installed"));
        CountingProvider holographic = new CountingProvider(FakeHolographicDisplays.provider(server,
                new FakeHolographicDisplays(WORLD)));
        Map<HologramProviderChoice, HologramProvider> providers = Map.of(PACKETS, packets, DECENTHOLOGRAMS, decent,
                FANCYHOLOGRAMS, fancy, HOLOGRAPHICDISPLAYS, holographic);

        HologramProvider chosen = HologramProviders.choose(AUTO, providers, warning -> {
        });

        assertSame(providers.get(expected), chosen);
        if (expected == HOLOGRAPHICDISPLAYS) {
            assertEquals(1, holographic.canRunAsked);
        } else {
            assertNotSame(holographic, chosen);
            assertEquals(0, holographic.canRunAsked, "asked after an earlier one could run");
        }
    }

    /**
     * Building the provider for a server without HolographicDisplays touches none of HolographicDisplays' classes, and
     * it cannot run. The provider's classes load here in a class loader that has no HolographicDisplays at all.
     */
    @Test
    void withoutHolographicDisplaysTheProviderBuildsAndCannotRun() throws ReflectiveOperationException {
        ClassLoader without = new WithoutHolographicDisplays(HolographicDisplaysProviderTest.class.getClassLoader());
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "me.filoghost.holographicdisplays.api.HolographicDisplaysAPI", false, without));
        Class<?> type = Class.forName(HolographicDisplaysProvider.class.getName(), true, without);
        assertFalse(type == HolographicDisplaysProvider.class);

        Object built;
        try {
            built = type.getMethod("of", Plugin.class, ServerAdapter.class)
                    .invoke(null, pluginWithoutOthers(), new FakeServerAdapter(UUID.randomUUID()));
        } catch (InvocationTargetException failure) {
            throw new AssertionError("building touched HolographicDisplays", failure.getCause());
        }

        assertEquals(CanRun.no("HolographicDisplays is not installed"), type.getMethod("canRun").invoke(built));
    }

    /**
     * Acceptance: the shipped {@code plugin.yml} lists HolographicDisplays, under the name the provider checks, as a
     * soft depend, so it enables first when installed.
     */
    @Test
    void theShippedPluginYmlSoftDependsOnHolographicDisplays() throws IOException {
        Map<String, Object> pluginYml;
        try (InputStream in = HolographicDisplaysProviderTest.class.getResourceAsStream("/plugin.yml")) {
            pluginYml = new Yaml().load(in);
        }

        assertEquals("RakunLoots", pluginYml.get("name"));
        assertEquals("HolographicDisplays", HolographicDisplaysProvider.REQUIREMENT.plugin());
        assertTrue(((List<?>) pluginYml.get("softdepend")).contains("HolographicDisplays"), pluginYml.toString());
    }

    private static FakeHologramServer server(String platform, String version) {
        return switch (platform) {
            case "paper" -> FakeHologramServer.paper(version);
            case "spigot" -> FakeHologramServer.spigot(version);
            default -> FakeHologramServer.folia(version);
        };
    }

    private static Plugin pluginWithoutOthers() {
        PluginManager plugins = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class}, (self, method, args) -> switch (method.getName()) {
                    case "getPlugin" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> plugins;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getServer" -> server;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BoxEvent box(UUID boxId, int lock) {
        return new BoxEvent(boxId, "airdrops", WORLD, new BlockPosition(2, 64, 3), Optional.empty(), lock,
                OptionalInt.of(360));
    }

    private static BoxEvent boxAt(UUID boxId, BlockPosition position) {
        return new BoxEvent(boxId, "airdrops", WORLD, position, Optional.empty(), 10, OptionalInt.of(360));
    }

    /**
     * Counts how often the choice asks it whether it can run.
     */
    private static final class CountingProvider implements HologramProvider {

        private final HologramProvider provider;
        private int canRunAsked;

        CountingProvider(HologramProvider provider) {
            this.provider = provider;
        }

        @Override
        public CanRun canRun() {
            canRunAsked++;
            return provider.canRun();
        }

        @Override
        public void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
            provider.create(hologramId, position, lines, viewRange);
        }

        @Override
        public void update(UUID hologramId, List<String> lines) {
            provider.update(hologramId, lines);
        }

        @Override
        public void remove(UUID hologramId) {
            provider.remove(hologramId);
        }

        @Override
        public void removeAll() {
            provider.removeAll();
        }
    }

    /**
     * Hides every HolographicDisplays class, and loads this package's main classes itself, so they link against no
     * HolographicDisplays. Everything else comes from the test's own loader.
     */
    private static final class WithoutHolographicDisplays extends ClassLoader {

        private static final String OWN = HolographicDisplaysProvider.class.getPackageName() + ".";

        WithoutHolographicDisplays(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("me.filoghost.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(OWN)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (in == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = in.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
