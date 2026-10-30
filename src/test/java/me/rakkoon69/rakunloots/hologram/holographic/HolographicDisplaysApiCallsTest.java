package me.rakkoon69.rakunloots.hologram.holographic;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI;
import me.filoghost.holographicdisplays.api.hologram.Hologram;
import me.filoghost.holographicdisplays.api.hologram.HologramLines;
import me.filoghost.holographicdisplays.api.hologram.PlaceholderSetting;
import me.filoghost.holographicdisplays.api.hologram.line.TextHologramLine;
import me.filoghost.holographicdisplays.api.internal.HolographicDisplaysAPIProvider;
import me.filoghost.holographicdisplays.core.CoreGlobalConfig;
import me.filoghost.holographicdisplays.core.api.current.APIHologramManager;
import me.filoghost.holographicdisplays.core.api.current.DefaultHolographicDisplaysAPIProvider;
import me.filoghost.holographicdisplays.core.base.BaseHologramLine;
import me.filoghost.holographicdisplays.core.listener.LineClickListener;
import me.filoghost.holographicdisplays.core.tracking.LineTrackerManager;
import me.filoghost.holographicdisplays.nms.common.NMSManager;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calls through HolographicDisplays' API, against the real HolographicDisplays 3.0.5 core from its plugin jar, on
 * MockBukkit, whose main thread is the test's thread. Only the packet layer is stubbed, since no player watches. The
 * space between lines is HolographicDisplays' default, 0.02; each text line is 0.23 high.
 */
class HolographicDisplaysApiCallsTest {

    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final double LINE = 0.23;
    private static final double SPACE = 0.02;

    private ServerMock server;
    private World world;
    private Plugin plugin;
    private HolographicDisplaysApiCalls calls;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        CoreGlobalConfig.spaceBetweenLines = SPACE;
        LineTrackerManager trackers = new LineTrackerManager(stub(NMSManager.class), null, new LineClickListener());
        HolographicDisplaysAPIProvider.setImplementation(
                new DefaultHolographicDisplaysAPIProvider(new APIHologramManager(trackers), null));
        calls = new HolographicDisplaysApiCalls(plugin);
    }

    @AfterEach
    void tearDown() {
        HolographicDisplaysAPIProvider.setImplementation(null);
        CoreGlobalConfig.spaceBetweenLines = 0;
        MockBukkit.unmock();
    }

    /**
     * HolographicDisplays puts the top of the first line at the hologram's position and stacks the lines down, so
     * create lifts the hologram by the lines' height: 4 lines are 4 x 0.23 + 3 x 0.02 = 0.98 high, and the lowest line
     * ends at the position. The hologram is one of this plugin's API holograms, which HolographicDisplays keeps in
     * memory only, with placeholders off.
     */
    @Test
    void createLiftsTheHologramSoItsLowestLineEndsAtThePosition() {
        List<String> lines = List.of("§6Air Supply", "§7Owner: §eNo owner", "§7Lock: §c0:10", "§7Leaves in: §e6:00");

        calls.create(BOX, new HologramPosition(world.getUID(), 2.5, 66.0, 3.5), lines);

        Hologram hologram = only();
        assertEquals(66.0 + 4 * LINE + 3 * SPACE, hologram.getPosition().getY(), 1e-9);
        assertEquals(2.5, hologram.getPosition().getX());
        assertEquals(3.5, hologram.getPosition().getZ());
        assertEquals("world", hologram.getPosition().getWorldName());
        assertEquals(lines, texts(hologram));
        assertEquals(66.0, lowestLineBottom(hologram), 1e-9);
        assertEquals(PlaceholderSetting.DISABLE_ALL, hologram.getPlaceholderSetting());
    }

    /**
     * Set lines replaces the text in place. When the number of lines changes, the hologram moves so its lowest line
     * still ends at the position.
     */
    @Test
    void setLinesKeepsTheLowestLineAtThePosition() {
        calls.create(BOX, new HologramPosition(world.getUID(), 2.5, 66.0, 3.5), List.of("a", "b", "c", "d"));
        Hologram hologram = only();
        TextHologramLine first = (TextHologramLine) hologram.getLines().get(0);

        calls.setLines(BOX, List.of("A", "B", "C", "D"));
        assertEquals(List.of("A", "B", "C", "D"), texts(hologram));
        assertTrue(first == hologram.getLines().get(0), "the same line, with new text");
        assertEquals(66.0, lowestLineBottom(hologram), 1e-9);

        calls.setLines(BOX, List.of("one", "two"));
        assertEquals(List.of("one", "two"), texts(hologram));
        assertEquals(66.0 + 2 * LINE + SPACE, hologram.getPosition().getY(), 1e-9);
        assertEquals(66.0, lowestLineBottom(hologram), 1e-9);

        calls.setLines(BOX, List.of("1", "2", "3", "4", "5"));
        assertEquals(List.of("1", "2", "3", "4", "5"), texts(hologram));
        assertEquals(66.0, lowestLineBottom(hologram), 1e-9);
    }

    @Test
    void removeDeletesTheHologram() {
        calls.create(BOX, new HologramPosition(world.getUID(), 2.5, 66.0, 3.5), List.of("a"));
        Hologram hologram = only();

        calls.remove(BOX);
        calls.remove(BOX);
        calls.setLines(BOX, List.of("b"));

        assertTrue(hologram.isDeleted());
        assertEquals(List.of(), List.copyOf(HolographicDisplaysAPI.get(plugin).getHolograms()));
    }

    /**
     * The real HolographicDisplays refuses each call off the main thread with the exception and message the fake uses,
     * and the refused create leaves no hologram behind.
     */
    @Test
    void theRealHolographicDisplaysRefusesCallsOffTheMainThread() throws InterruptedException {
        HologramPosition position = new HologramPosition(world.getUID(), 2.5, 66.0, 3.5);

        RuntimeException create = offMainThread(() -> calls.create(BOX, position, List.of("a")));
        assertInstanceOf(IllegalStateException.class, create);
        assertEquals(FakeHolographicDisplays.OFF_MAIN_THREAD, create.getMessage());
        assertEquals(List.of(), List.copyOf(HolographicDisplaysAPI.get(plugin).getHolograms()));

        calls.create(BOX, position, List.of("a"));
        RuntimeException setLines = offMainThread(() -> calls.setLines(BOX, List.of("b")));
        RuntimeException remove = offMainThread(() -> calls.remove(BOX));
        assertInstanceOf(IllegalStateException.class, setLines);
        assertEquals(FakeHolographicDisplays.OFF_MAIN_THREAD, setLines.getMessage());
        assertInstanceOf(IllegalStateException.class, remove);
        assertEquals(FakeHolographicDisplays.OFF_MAIN_THREAD, remove.getMessage());
    }

    @Test
    void anUnloadedWorldOrATakenIdIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> calls.create(BOX, new HologramPosition(UUID.randomUUID(), 0.5, 64.0, 0.5), List.of("a")));
        assertEquals(List.of(), List.copyOf(HolographicDisplaysAPI.get(plugin).getHolograms()));

        calls.create(BOX, new HologramPosition(world.getUID(), 0.5, 64.0, 0.5), List.of("a"));
        assertThrows(IllegalArgumentException.class,
                () -> calls.create(BOX, new HologramPosition(world.getUID(), 0.5, 64.0, 0.5), List.of("b")));
        assertEquals(List.of("a"), texts(only()));
    }

    private Hologram only() {
        List<Hologram> holograms = List.copyOf(HolographicDisplaysAPI.get(plugin).getHolograms());
        assertEquals(1, holograms.size(), holograms.toString());
        return holograms.get(0);
    }

    private static List<String> texts(Hologram hologram) {
        HologramLines lines = hologram.getLines();
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            texts.add(((TextHologramLine) lines.get(index)).getText());
        }
        return texts;
    }

    /**
     * Where HolographicDisplays placed the lowest line's bottom.
     */
    private static double lowestLineBottom(Hologram hologram) {
        HologramLines lines = hologram.getLines();
        return ((BaseHologramLine) lines.get(lines.size() - 1)).getCoordinates().getY();
    }

    private static RuntimeException offMainThread(Runnable call) throws InterruptedException {
        AtomicReference<RuntimeException> thrown = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                call.run();
            } catch (RuntimeException failure) {
                thrown.set(failure);
            }
        }, "region thread");
        thread.start();
        thread.join();
        return thrown.get();
    }

    /**
     * A stand-in for HolographicDisplays' packet layer: every call answers with another stand-in, 0, or false.
     */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            Class<?> returned = method.getReturnType();
            if (returned == int.class) {
                return 0;
            }
            if (returned == boolean.class) {
                return false;
            }
            if (returned.isInterface()) {
                return stub(returned);
            }
            return null;
        });
    }
}
