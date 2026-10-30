package me.rakkoon69.rakunloots.hologram.fancy;

import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the core reaches the provider: the loader loads it by class name on a Java 25 server where FancyHolograms can
 * run, and the shipped jar carries it beside the Java 17 core.
 */
class FancyHologramsProviderLoadTest {

    private static final String PROVIDER_ENTRY = "me/rakkoon69/rakunloots/hologram/fancy/FancyHologramsProvider.class";
    private static final String LOADER_ENTRY =
            "me/rakkoon69/rakunloots/hologram/fancy/FancyHologramsProviderLoader.class";
    private static final int JAVA_17 = 61;
    private static final int JAVA_21 = 65;

    /**
     * On Paper 1.21.4 with FancyHolograms enabled, the core's loader finds the provider by its class name.
     */
    @Test
    void theLoaderLoadsTheProviderByNameWhereItCanRun() {
        HologramProvider loaded = FancyHologramsProviderLoader.load(server("1.21.4-R0.1-SNAPSHOT", true));

        assertInstanceOf(FancyHologramsProvider.class, loaded);
        assertEquals(CanRun.YES, loaded.canRun());
        assertEquals(FancyHologramsProvider.class.getName(), FancyHologramsProviderLoader.PROVIDER_CLASS);
    }

    /**
     * Without FancyHolograms the loader gives a provider that cannot run and says why.
     */
    @Test
    void withoutFancyHologramsTheLoaderGivesAProviderThatCannotRun() {
        HologramProvider loaded = FancyHologramsProviderLoader.load(server("1.21.4-R0.1-SNAPSHOT", false));

        assertEquals(CanRun.no("FancyHolograms is not installed"), loaded.canRun());
        assertTrue(!(loaded instanceof FancyHologramsProvider));
    }

    /**
     * Acceptance: the shadow jar contains the provider as Java 21 bytecode, next to the core's Java 17 loader, and
     * carries none of FancyHolograms' own classes.
     */
    @Test
    void theShadowJarContainsTheProvider() throws IOException {
        Path jar = Path.of(System.getProperty("rakunloots.shadowJar"));
        assertTrue(Files.isRegularFile(jar), jar.toString());

        try (JarFile file = new JarFile(jar.toFile())) {
            assertEquals(JAVA_21, major(file, PROVIDER_ENTRY));
            assertEquals(JAVA_17, major(file, LOADER_ENTRY));
            assertEquals(JAVA_17, major(file, "me/rakkoon69/rakunloots/RakunLootsPlugin.class"));
            List<String> fancyHolograms = file.stream().map(JarEntry::getName)
                    .filter(name -> name.startsWith("de/oliver/")).toList();
            assertEquals(List.of(), fancyHolograms);
        }
    }

    private static int major(JarFile jar, String entry) throws IOException {
        JarEntry found = jar.getJarEntry(entry);
        assertNotNull(found, entry + " is not in " + jar.getName());
        try (InputStream in = jar.getInputStream(found); DataInputStream data = new DataInputStream(in)) {
            assertEquals(0xCAFEBABE, data.readInt());
            data.readUnsignedShort();
            return data.readUnsignedShort();
        }
    }

    private static Server server(String bukkitVersion, boolean fancyHolograms) {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (self, method, args) -> switch (method.getName()) {
                    case "isEnabled" -> true;
                    case "getName" -> "FancyHolograms";
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
                    case "getBukkitVersion" -> bukkitVersion;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
