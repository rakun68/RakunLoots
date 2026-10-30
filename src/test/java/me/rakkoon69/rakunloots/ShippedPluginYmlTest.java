package me.rakkoon69.rakunloots;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped {@code plugin.yml} against the servers ADR 0001 supports.
 */
class ShippedPluginYmlTest {

    /**
     * A server refuses a plugin whose api-version is newer than itself ("Unsupported API version 1.20" on Paper 1.18.2),
     * so the api-version is the oldest supported server's.
     */
    @Test
    void theApiVersionIsTheOldestSupportedServers() throws IOException {
        try (InputStream in = ShippedPluginYmlTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml");
            Map<String, Object> plugin = new Yaml().load(in);

            assertEquals("1.18", plugin.get("api-version"));
        }
    }
}
