package me.rakkoon69.rakunloots.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI;
import me.rakkoon69.rakunloots.hologram.decent.DecentHologramsProvider;
import me.rakkoon69.rakunloots.hologram.holographic.HolographicDisplaysProvider;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code folia-supported} read from a real plugin description on folia-api, which has the accessor the 1.18 API lacks.
 */
class FoliaSupportedTest {

    @Test
    void aPluginThatDeclaresFoliaSupportIsFoliaSupported() throws InvalidDescriptionException {
        assertTrue(BukkitHologramServer.foliaSupported(plugin("folia-supported: true\n")));
    }

    @Test
    void aPluginThatDoesNotDeclareItIsNot() throws InvalidDescriptionException {
        assertFalse(BukkitHologramServer.foliaSupported(plugin("")));
        assertFalse(BukkitHologramServer.foliaSupported(plugin("folia-supported: false\n")));
    }

    /**
     * The real DecentHolograms 2.10.1 jar's {@code plugin.yml} does not declare Folia support, so its provider cannot
     * run on Folia.
     */
    @Test
    void decentHolograms2101DoesNotDeclareFoliaSupport() throws Exception {
        PluginDescriptionFile description = description(DHAPI.class);
        assertEquals("DecentHolograms", description.getName());
        assertEquals("2.10.1", description.getVersion());

        boolean foliaSupported = BukkitHologramServer.foliaSupported(plugin(description));

        assertFalse(foliaSupported);
        assertFalse(DecentHologramsProvider.REQUIREMENT.check(FakeHologramServer.folia("1.20.4")
                .with("DecentHolograms", foliaSupported)).yes());
    }

    /**
     * The real HolographicDisplays 3.0.5 jar's {@code plugin.yml} does not declare Folia support either. Its provider
     * never runs on Folia in any case.
     */
    @Test
    void holographicDisplays305DoesNotDeclareFoliaSupport() throws Exception {
        PluginDescriptionFile description = description(HolographicDisplaysAPI.class);
        assertEquals("HolographicDisplays", description.getName());
        assertEquals("3.0.5", description.getVersion());

        boolean foliaSupported = BukkitHologramServer.foliaSupported(plugin(description));

        assertFalse(foliaSupported);
        assertEquals(HolographicDisplaysProvider.REQUIREMENT.plugin(), description.getName());
    }

    /**
     * The {@code plugin.yml} of the jar the class came from.
     */
    private static PluginDescriptionFile description(Class<?> type) throws Exception {
        URI jar = type.getProtectionDomain().getCodeSource().getLocation().toURI();
        try (JarFile file = new JarFile(new File(jar));
             Reader reader = new InputStreamReader(file.getInputStream(file.getEntry("plugin.yml")),
                     StandardCharsets.UTF_8)) {
            return new PluginDescriptionFile(reader);
        }
    }

    private static Plugin plugin(String extra) throws InvalidDescriptionException {
        return plugin(new PluginDescriptionFile(new StringReader(
                "name: DecentHolograms\nversion: 2.10.1\nmain: eu.decentsoftware.Main\n" + extra)));
    }

    private static Plugin plugin(PluginDescriptionFile description) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getPluginMeta", "getDescription" -> description;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
