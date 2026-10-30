package me.rakkoon69.rakunloots.hologram.fancy;

import me.rakkoon69.rakunloots.hologram.FakeHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core's side of the FancyHolograms provider, which is Java 25 bytecode in its own source set: the loader checks
 * the server and builds the provider only where it can run. These tests also run on Java 17, where the core must load
 * with the provider's class never touched.
 */
class FancyHologramsProviderLoaderTest {

    private static final String PROVIDER_REFERENCE = "me/rakkoon69/rakunloots/hologram/fancy/FancyHologramsProvider";

    /**
     * Acceptance: can run is false when FancyHolograms is missing, below 1.21.4, on Spigot, and on Folia without
     * {@code folia-supported: true}; the provider is never built there.
     */
    @ParameterizedTest(name = "{0} {1} with {2}")
    @CsvSource({
            "paper, 1.21.4, missing, FancyHolograms is not installed",
            "paper, 1.21.4, disabled, FancyHolograms is not enabled",
            "paper, 1.21.3, enabled, 'FancyHolograms needs Minecraft 1.21.4 or newer, and this server runs 1.21.3'",
            "paper, 1.20.4, enabled, 'FancyHolograms needs Minecraft 1.21.4 or newer, and this server runs 1.20.4'",
            "spigot, 1.21.4, enabled, 'FancyHolograms needs Paper, and this server is Spigot'",
            "spigot, 26.1, enabled, 'FancyHolograms needs Paper, and this server is Spigot'",
            "folia, 1.21.4, enabled, "
                    + "'FancyHolograms does not declare folia-supported: true, and this server is Folia'",
    })
    void canRunIsFalseAndNothingIsBuiltWhereFancyHologramsCannotRun(String platform, String version,
                                                                   String installed, String refusal) {
        HologramProvider loaded = FancyHologramsProviderLoader.load(server(platform, version, installed), 25,
                FancyHologramsProviderLoaderTest::neverBuilt);

        assertEquals(CanRun.no(refusal), loaded.canRun());
    }

    /**
     * Where FancyHolograms can run on Java 25, the loader hands over the provider it builds.
     */
    @ParameterizedTest(name = "{0} {1} with {2} on Java {3}")
    @CsvSource({
            "paper, 1.21.4, enabled, 25",
            "paper, 26.1, enabled, 26",
            "folia, 1.21.4, folia, 25",
    })
    void whereFancyHologramsCanRunTheBuiltProviderIsUsed(String platform, String version, String installed,
                                                        int java) {
        RecordingHologramProvider built = new RecordingHologramProvider();

        HologramProvider loaded = FancyHologramsProviderLoader.load(server(platform, version, installed), java,
                () -> built);

        assertSame(built, loaded);
    }

    /**
     * FancyHolograms 2.12.0 runs only on Java 25, but an older FancyHolograms runs on Java 21, where the provider's
     * Java 25 bytecode cannot load, so it is not built there.
     */
    @Test
    void belowJava25TheProviderIsNotBuilt() {
        HologramProvider loaded = FancyHologramsProviderLoader.load(server("paper", "1.21.4", "enabled"), 21,
                FancyHologramsProviderLoaderTest::neverBuilt);

        assertEquals(CanRun.no("the FancyHolograms provider needs Java 25, and this server runs Java 21"),
                loaded.canRun());
    }

    /**
     * A provider class that fails to load or build cannot run, with the failure as the reason.
     */
    @Test
    void aProviderThatFailsToLoadCannotRun() {
        FakeHologramServer server = server("paper", "1.21.4", "enabled");

        HologramProvider tooNew = FancyHologramsProviderLoader.load(server, 25, () -> {
            throw new UnsupportedClassVersionError("FancyHologramsProvider has been compiled by a more recent version");
        });
        HologramProvider thrown = FancyHologramsProviderLoader.load(server, 25, () -> {
            throw new InvocationTargetException(new NoClassDefFoundError("de/oliver/fancyholograms/api/HologramManager"));
        });

        assertEquals(CanRun.no("the FancyHolograms provider cannot load: java.lang.UnsupportedClassVersionError: "
                + "FancyHologramsProvider has been compiled by a more recent version"), tooNew.canRun());
        assertEquals(CanRun.no("the FancyHolograms provider cannot load: java.lang.NoClassDefFoundError: "
                + "de/oliver/fancyholograms/api/HologramManager"), thrown.canRun());
    }

    /**
     * A provider that cannot run is never chosen, so every call but can run is refused.
     */
    @Test
    void aProviderThatCannotRunRefusesEveryOtherCall() {
        HologramProvider loaded = FancyHologramsProviderLoader.load(server("spigot", "1.21.4", "enabled"), 25,
                FancyHologramsProviderLoaderTest::neverBuilt);
        UUID id = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> loaded.create(id,
                new HologramPosition(UUID.randomUUID(), 0.5, 64.0, 0.5), List.of("a"), 48));
        assertThrows(IllegalStateException.class, () -> loaded.update(id, List.of("a")));
        assertThrows(IllegalStateException.class, () -> loaded.remove(id));
        assertThrows(IllegalStateException.class, loaded::removeAll);
    }

    /**
     * Acceptance: on the running server the loader never asks for the provider's class where FancyHolograms cannot
     * run: without FancyHolograms, and with it but on a Java below 25, such as the Java 17 this test also runs on.
     * The loader's classes load here in a class loader that records each class asked for and has no FancyHolograms.
     */
    @Test
    void theRunningServerLoaderNeverTouchesTheProviderWhereItCannotRun() throws ReflectiveOperationException {
        RecordingLoader recording = new RecordingLoader(FancyHologramsProviderLoaderTest.class.getClassLoader());
        Class<?> loader = Class.forName(FancyHologramsProviderLoader.class.getName(), true, recording);
        assertFalse(loader == FancyHologramsProviderLoader.class);

        HologramProvider missing = (HologramProvider) loader.getMethod("load", Server.class).invoke(null,
                server(false));
        assertEquals(CanRun.no("FancyHolograms is not installed"), missing.canRun());

        int java = Runtime.version().feature();
        if (java < FancyHologramsProviderLoader.JAVA) {
            HologramProvider tooOld = (HologramProvider) loader.getMethod("load", Server.class).invoke(null,
                    server(true));
            assertEquals(CanRun.no("the FancyHolograms provider needs Java 25, and this server runs Java " + java),
                    tooOld.canRun());
        }

        assertFalse(recording.asked().contains(FancyHologramsProviderLoader.PROVIDER_CLASS), recording.asked()
                .toString());
        assertTrue(recording.asked().stream().noneMatch(name -> name.startsWith("de.oliver.")),
                recording.asked().toString());
    }

    /**
     * Acceptance: the core is Java 17 bytecode, and no core class refers to the provider or to FancyHolograms other
     * than by the loader's class name string. The provider's class is not in the core's classes at all.
     */
    @Test
    void theCoreIsJava17AndNeverRefersToTheProvider() throws IOException, URISyntaxException {
        Path core = Path.of(FancyHologramsProviderLoader.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        List<Path> classes;
        try (Stream<Path> walk = Files.walk(core)) {
            classes = walk.filter(path -> path.toString().endsWith(".class")).toList();
        }
        assertTrue(classes.size() > 100, core + " holds " + classes.size() + " classes");
        assertFalse(Files.exists(core.resolve(PROVIDER_REFERENCE + ".class")));

        List<String> problems = new ArrayList<>();
        for (Path path : classes) {
            ClassFile file = ClassFile.read(path);
            if (file.major() > 61) {
                problems.add(core.relativize(path) + " is class file version " + file.major());
            }
            for (String text : file.utf8()) {
                if (text.equals(PROVIDER_REFERENCE) || text.contains("L" + PROVIDER_REFERENCE + ";")
                        || text.startsWith("de/oliver/") || text.contains("Lde/oliver/")) {
                    problems.add(core.relativize(path) + " refers to " + text);
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /**
     * Acceptance: the shipped {@code plugin.yml} lists FancyHolograms, under the name the loader checks, as a soft
     * depend, so it enables first when installed.
     */
    @Test
    void theShippedPluginYmlSoftDependsOnFancyHolograms() throws IOException {
        Map<String, Object> pluginYml;
        try (InputStream in = FancyHologramsProviderLoaderTest.class.getResourceAsStream("/plugin.yml")) {
            pluginYml = new Yaml().load(in);
        }

        assertEquals("FancyHolograms", FancyHologramsProviderLoader.REQUIREMENT.plugin());
        assertTrue(((List<?>) pluginYml.get("softdepend")).contains("FancyHolograms"), pluginYml.toString());
    }

    /**
     * Set by the Java 17 test task, so that run fails when it did not run on Java 17.
     */
    @Test
    @EnabledIfSystemProperty(named = "rakunloots.java", matches = "\\d+")
    void runsOnTheJavaTheTaskAskedFor() {
        assertEquals(Integer.parseInt(System.getProperty("rakunloots.java")), Runtime.version().feature());
    }

    private static HologramProvider neverBuilt() {
        throw new AssertionError("the provider was built");
    }

    private static FakeHologramServer server(String platform, String version, String installed) {
        FakeHologramServer server = switch (platform) {
            case "paper" -> FakeHologramServer.paper(version);
            case "spigot" -> FakeHologramServer.spigot(version);
            default -> FakeHologramServer.folia(version);
        };
        switch (installed) {
            case "enabled" -> server.with("FancyHolograms", false);
            case "folia" -> server.with("FancyHolograms", true);
            case "disabled" -> server.withDisabled("FancyHolograms");
            default -> {
            }
        }
        return server;
    }

    /**
     * A Paper 1.21.4 server, with FancyHolograms enabled or not installed.
     */
    private static Server server(boolean fancyHolograms) {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (self, method, args) -> switch (method.getName()) {
                    case "isEnabled" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        PluginManager plugins = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class}, (self, method, args) -> switch (method.getName()) {
                    case "getPlugin" -> fancyHolograms && "FancyHolograms".equals(args[0]) ? plugin : null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> plugins;
                    case "getBukkitVersion" -> "1.21.4-R0.1-SNAPSHOT";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Records every class asked for, hides FancyHolograms and the provider, and loads this package's main classes
     * itself, so they link against it. Everything else comes from the test's own loader.
     */
    private static final class RecordingLoader extends ClassLoader {

        private static final String OWN = FancyHologramsProviderLoader.class.getPackageName() + ".";
        private static final Set<String> TEST_CLASSES = Set.of(FancyHologramsProviderLoaderTest.class.getName());

        private final List<String> asked = new CopyOnWriteArrayList<>();

        RecordingLoader(ClassLoader parent) {
            super(parent);
        }

        List<String> asked() {
            return List.copyOf(asked);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                asked.add(name);
                if (name.startsWith("de.oliver.") || name.equals(FancyHologramsProviderLoader.PROVIDER_CLASS)) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(OWN) || TEST_CLASSES.stream().anyMatch(name::startsWith)) {
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

    /**
     * A class file's version and the text in its constant pool, which holds every class it refers to.
     */
    private record ClassFile(int major, List<String> utf8) {

        static ClassFile read(Path path) throws IOException {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
                if (in.readInt() != 0xCAFEBABE) {
                    throw new IOException(path + " is not a class file");
                }
                in.readUnsignedShort();
                int major = in.readUnsignedShort();
                int count = in.readUnsignedShort();
                List<String> utf8 = new ArrayList<>();
                for (int index = 1; index < count; index++) {
                    int tag = in.readUnsignedByte();
                    switch (tag) {
                        case 1 -> utf8.add(in.readUTF());
                        case 7, 8, 16, 19, 20 -> in.skipNBytes(2);
                        case 15 -> in.skipNBytes(3);
                        case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                        case 5, 6 -> {
                            in.skipNBytes(8);
                            index++;
                        }
                        default -> throw new IOException(path + " has constant pool tag " + tag);
                    }
                }
                return new ClassFile(major, utf8);
            }
        }
    }
}
