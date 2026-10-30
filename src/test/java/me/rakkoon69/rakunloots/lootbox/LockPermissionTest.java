package me.rakkoon69.rakunloots.lootbox;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LockPermissionTest {

    /**
     * Acceptance: the shipped {@code plugin.yml} declares the permission the lock admits, for operators by default.
     * Bukkit's own description reader needs the snakeyaml 1 API, which the plugin's snakeyaml 2 replaces on the test
     * class path, so the file is read with the plugin's parser.
     */
    @Test
    void theShippedPluginYmlDeclaresTheBypassLockPermission() {
        Map<String, Object> pluginYml = new Yaml().load(LifecycleFixture.resource("/plugin.yml"));
        Map<?, ?> permissions = assertInstanceOf(Map.class, pluginYml.get("permissions"));

        Map<?, ?> bypassLock = assertInstanceOf(Map.class, permissions.get(LootBoxLifecycle.BYPASS_LOCK_PERMISSION),
                "declared: " + permissions.keySet());
        assertEquals("rakunloots.bypass.lock", LootBoxLifecycle.BYPASS_LOCK_PERMISSION);
        assertEquals("op", bypassLock.get("default"));
        assertInstanceOf(String.class, bypassLock.get("description"));
    }
}
