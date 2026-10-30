package me.rakkoon69.rakunloots.hologram.decent;

import eu.decentsoftware.holograms.api.DHAPI;
import me.rakkoon69.rakunloots.hologram.FakeHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.hologram.HologramProviderChoice;
import me.rakkoon69.rakunloots.hologram.HologramProviders;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.HologramWarning;
import me.rakkoon69.rakunloots.hologram.OwnerNames;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.hologram.decent.FakeDecentHolograms.Drawn;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapters;
import me.rakkoon69.rakunloots.hologram.packet.PacketHologramProvider;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static me.rakkoon69.rakunloots.hologram.HologramProviderChoice.AUTO;
import static me.rakkoon69.rakunloots.hologram.HologramProviderChoice.DECENTHOLOGRAMS;
import static me.rakkoon69.rakunloots.hologram.HologramProviderChoice.PACKETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DecentHolograms provider under the real hologram service over the shipped config, against a fake DecentHolograms
 * that refuses what DHAPI 2.10.1 refuses. The shipped airdrop's hologram sits 2 blocks above its block with the global
 * view range of 48.
 */
class DecentHologramsProviderTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final String BOX_NAME = "rakunloots_aaaaaaaa-0000-0000-0000-000000000001";
    private static final String OTHER_NAME = "rakunloots_aaaaaaaa-0000-0000-0000-000000000002";
    private static final OwnerNames NO_NAMES = id -> Optional.empty();

    private final FakeDecentHolograms plugin = new FakeDecentHolograms();
    private final DecentHologramsProvider provider = FakeDecentHolograms.provider(
            FakeHologramServer.paper("1.20.4").with("DecentHolograms", false), plugin);
    private final HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NO_NAMES);

    /**
     * Acceptance: create draws the rendered lines under a name from the box id, with the position as the hologram's
     * bottom (down origin), since DecentHolograms anchors the top line otherwise, and the view range as its display
     * range.
     */
    @Test
    void createDrawsTheLinesAtTheBottomUnderANameFromTheBoxId() {
        service.shown(box(BOX, 10));

        assertEquals(Set.of(BOX_NAME), plugin.holograms().keySet());
        Drawn drawn = plugin.holograms().get(BOX_NAME);
        assertEquals(new HologramPosition(WORLD, 2.5, 66.0, 3.5), drawn.position());
        assertTrue(drawn.downOrigin(), "the position is the lowest line");
        assertEquals(48, drawn.displayRange());
        assertEquals(List.of(
                "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                "§7Owner: §e§7No owner",
                "§7Lock: §c0:10",
                "§7Leaves in: §e6:00"), drawn.lines());
    }

    /**
     * Acceptance: a tick that changes the lines updates them, and the box's end removes the hologram.
     */
    @Test
    void updateReplacesTheLinesAndRemoveTakesTheHologramAway() {
        service.shown(box(BOX, 10));

        service.ticked(box(BOX, 9));
        assertEquals("§7Lock: §c0:09", plugin.holograms().get(BOX_NAME).lines().get(2));
        assertEquals(new HologramPosition(WORLD, 2.5, 66.0, 3.5), plugin.holograms().get(BOX_NAME).position());

        service.ended(box(BOX, 9));
        assertEquals(Map.of(), plugin.holograms());
    }

    /**
     * Acceptance: remove all, run when the plugin stops, removes every hologram the provider draws and no other
     * hologram DecentHolograms has.
     */
    @Test
    void removeAllTakesAwayEveryHologramItDrawsAndNoOther() {
        plugin.createForeign("spawn_welcome", new HologramPosition(WORLD, 0.5, 70.0, 0.5));
        service.shown(box(BOX, 10));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        assertEquals(Set.of("spawn_welcome", BOX_NAME, OTHER_NAME), plugin.holograms().keySet());

        service.stop();

        assertEquals(Set.of("spawn_welcome"), plugin.holograms().keySet());
    }

    /**
     * Update and remove of an id the provider does not draw do nothing, even when DecentHolograms has a hologram of
     * the name that id would give.
     */
    @Test
    void updateAndRemoveOfAnUnknownIdDoNothing() {
        plugin.createForeign(BOX_NAME, new HologramPosition(WORLD, 0.5, 70.0, 0.5));

        provider.update(BOX, List.of("§cnot mine"));
        provider.remove(BOX);
        provider.removeAll();

        assertEquals(List.of("someone else's"), plugin.holograms().get(BOX_NAME).lines());
    }

    /**
     * Acceptance: the fake refuses what DecentHolograms refuses. A name outside the name rule is refused by the real
     * DHAPI 2.10.1 and by the fake alike; the name from a box id passes the real DHAPI's name check, which then needs a
     * running DecentHolograms; a name DecentHolograms already has is refused by the fake as DHAPI does.
     */
    @Test
    void theFakeRefusesWhatDecentHologramsRefuses() {
        Location nowhere = new Location(null, 0, 64, 0);
        HologramPosition position = new HologramPosition(WORLD, 0.5, 64.0, 0.5);

        IllegalArgumentException real = assertThrows(IllegalArgumentException.class,
                () -> DHAPI.createHologram("loot box", nowhere, false, List.of("a")));
        IllegalArgumentException fake = assertThrows(IllegalArgumentException.class, () -> plugin.create(
                new DecentHologramsCalls.NewHologram("loot box", position, List.of("a"), false, true, 48)));
        assertEquals(real.getMessage(), fake.getMessage());

        String name = DecentHologramsProvider.name(BOX);
        assertEquals(BOX_NAME, name);
        Throwable pastTheName = assertThrows(Throwable.class,
                () -> DHAPI.createHologram(name, nowhere, false, List.of("a")));
        assertFalse(pastTheName instanceof IllegalArgumentException, "the real DHAPI refused the name: " + pastTheName);

        plugin.create(new DecentHologramsCalls.NewHologram(name, position, List.of("a"), false, true, 48));
        assertThrows(IllegalArgumentException.class, () -> plugin.create(
                new DecentHologramsCalls.NewHologram(name, position, List.of("b"), false, true, 48)));
    }

    /**
     * When DecentHolograms already has a hologram under a box's name, create is refused and the provider leaves that
     * hologram alone on remove and remove all.
     */
    @Test
    void aNameDecentHologramsAlreadyHasIsRefusedAndLeftAlone() {
        plugin.createForeign(BOX_NAME, new HologramPosition(WORLD, 0.5, 70.0, 0.5));

        assertThrows(IllegalArgumentException.class, () -> provider.create(BOX,
                new HologramPosition(WORLD, 2.5, 66.0, 3.5), List.of("§6Air Supply"), 48));
        provider.remove(BOX);
        provider.removeAll();

        assertEquals(List.of("someone else's"), plugin.holograms().get(BOX_NAME).lines());
    }

    /**
     * Acceptance: no hologram is created with save to file on, through a whole life of boxes including a redraw after
     * DecentHolograms' own reload, so DecentHolograms writes no file.
     */
    @Test
    void noHologramIsCreatedWithSaveToFileOn() {
        service.shown(box(BOX, 10));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        plugin.reload();
        service.ticked(box(BOX, 9));
        service.outOfView(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        service.stop();

        assertEquals(List.of(BOX_NAME, OTHER_NAME, BOX_NAME, OTHER_NAME),
                plugin.created().stream().map(DecentHologramsCalls.NewHologram::name).toList());
        assertEquals(List.of(false, false, false, false),
                plugin.created().stream().map(DecentHologramsCalls.NewHologram::saveToFile).toList());
        assertEquals(Set.of(), plugin.files());
    }

    /**
     * {@code /dh reload} drops every hologram it did not load from a file, so the next update draws the box's
     * hologram again where it was, with the new lines.
     */
    @Test
    void aHologramDecentHologramsDroppedIsDrawnAgainOnTheNextUpdate() {
        service.shown(box(BOX, 10));
        plugin.reload();
        assertEquals(Map.of(), plugin.holograms());

        service.ticked(box(BOX, 9));

        Drawn drawn = plugin.holograms().get(BOX_NAME);
        assertEquals(new HologramPosition(WORLD, 2.5, 66.0, 3.5), drawn.position());
        assertEquals("§7Lock: §c0:09", drawn.lines().get(2));
        assertTrue(drawn.downOrigin());
        assertEquals(48, drawn.displayRange());
    }

    /**
     * Acceptance: can run is false when DecentHolograms is missing or disabled, on Folia without
     * {@code folia-supported: true}, and below Minecraft 1.18.
     */
    @ParameterizedTest(name = "{0} {1} with {2}")
    @CsvSource({
            "paper, 1.20.4, enabled, ''",
            "spigot, 1.18, enabled, ''",
            "paper, 26.1, enabled, ''",
            "folia, 1.20.4, folia, ''",
            "paper, 1.20.4, missing, DecentHolograms is not installed",
            "paper, 1.20.4, disabled, DecentHolograms is not enabled",
            "folia, 1.20.4, enabled, "
                    + "'DecentHolograms does not declare folia-supported: true, and this server is Folia'",
            "spigot, 1.17.1, enabled, 'DecentHolograms needs Minecraft 1.18 or newer, and this server runs 1.17.1'",
    })
    void canRunNeedsDecentHologramsEnabledFrom118AndFoliaSupportOnFolia(String platform, String version, String plugin,
                                                                       String refusal) {
        FakeHologramServer server = switch (platform) {
            case "paper" -> FakeHologramServer.paper(version);
            case "spigot" -> FakeHologramServer.spigot(version);
            default -> FakeHologramServer.folia(version);
        };
        switch (plugin) {
            case "enabled" -> server.with("DecentHolograms", false);
            case "folia" -> server.with("DecentHolograms", true);
            case "disabled" -> server.withDisabled("DecentHolograms");
            default -> {
            }
        }

        CanRun canRun = FakeDecentHolograms.provider(server, new FakeDecentHolograms()).canRun();

        assertEquals(refusal.isEmpty() ? CanRun.YES : CanRun.no(refusal), canRun);
    }

    /**
     * Acceptance: {@code auto} picks DecentHolograms when packets cannot run and DecentHolograms can, and moves past it
     * when it cannot.
     */
    @Test
    void autoPicksDecentHologramsWhenPacketsCannotRunAndItCan() {
        FakeHologramServer server = FakeHologramServer.paper("1.20.4").with("DecentHolograms", false);
        DecentHologramsProvider decent = FakeDecentHolograms.provider(server, new FakeDecentHolograms());
        PacketHologramProvider packets = new PacketHologramProvider(PacketAdapters.select(server),
                new FakeServerAdapter(UUID.randomUUID()));
        RecordingHologramProvider holographic = new RecordingHologramProvider();
        List<HologramWarning> warnings = new ArrayList<>();

        HologramProvider chosen = HologramProviders.choose(AUTO, Map.of(PACKETS, packets, DECENTHOLOGRAMS, decent,
                HologramProviderChoice.HOLOGRAPHICDISPLAYS, holographic), warning -> warnings.add((HologramWarning) warning));

        assertSame(decent, chosen);
        assertEquals(List.of(HologramWarning.Kind.PACKETS_CANNOT_RUN),
                warnings.stream().map(HologramWarning::kind).toList());

        DecentHologramsProvider missing = FakeDecentHolograms.provider(FakeHologramServer.paper("1.20.4"),
                new FakeDecentHolograms());
        chosen = HologramProviders.choose(AUTO, Map.of(PACKETS, packets, DECENTHOLOGRAMS, missing,
                HologramProviderChoice.HOLOGRAPHICDISPLAYS, holographic), warning -> warnings.add((HologramWarning) warning));
        assertSame(holographic, chosen);
    }

    /**
     * Building the provider for a server without DecentHolograms touches none of DecentHolograms' classes, and it
     * cannot run. The provider's classes load here in a class loader that has no DecentHolograms at all.
     */
    @Test
    void withoutDecentHologramsTheProviderBuildsAndCannotRun() throws ReflectiveOperationException {
        ClassLoader withoutDecent = new WithoutDecentHolograms(DecentHologramsProviderTest.class.getClassLoader());
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(DHAPI.class.getName(), false, withoutDecent));
        Class<?> type = Class.forName(DecentHologramsProvider.class.getName(), true, withoutDecent);
        assertFalse(type == DecentHologramsProvider.class);

        Object built;
        try {
            built = type.getMethod("of", Server.class).invoke(null, serverWithoutPlugins());
        } catch (InvocationTargetException failure) {
            throw new AssertionError("building touched DecentHolograms", failure.getCause());
        }

        assertEquals(CanRun.no("DecentHolograms is not installed"), type.getMethod("canRun").invoke(built));
    }

    /**
     * Acceptance: the shipped {@code plugin.yml} lists DecentHolograms, under the name the provider checks, as a soft
     * depend, so it enables first when installed. It left depend when the old summon path went.
     */
    @Test
    void theShippedPluginYmlSoftDependsOnDecentHolograms() throws IOException {
        Map<String, Object> pluginYml;
        try (InputStream in = DecentHologramsProviderTest.class.getResourceAsStream("/plugin.yml")) {
            pluginYml = new Yaml().load(in);
        }

        assertEquals("DecentHolograms", DecentHologramsProvider.REQUIREMENT.plugin());
        assertTrue(((List<?>) pluginYml.get("softdepend")).contains("DecentHolograms"), pluginYml.toString());
        assertNull(pluginYml.get("depend"));
    }

    private static Server serverWithoutPlugins() {
        PluginManager plugins = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class}, (self, method, args) -> switch (method.getName()) {
                    case "getPlugin" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> plugins;
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
     * Hides every DecentHolograms class, and loads this package's main classes itself, so they link against no
     * DecentHolograms. Everything else comes from the test's own loader.
     */
    private static final class WithoutDecentHolograms extends ClassLoader {

        private static final String OWN = DecentHologramsProvider.class.getPackageName() + ".";

        WithoutDecentHolograms(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("eu.decentsoftware.")) {
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
