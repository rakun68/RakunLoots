package me.rakkoon69.rakunloots;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.command.handler.RakunLootsCommands;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim command's permission default comes from {@code claim_command.permission_default} in config.yml, on
 * MockBukkit with the shipped data folder. Every test reads what online players get from the permission.
 */
class ClaimPermissionDefaultTest {

    private ServerMock server;
    private RakunLootsPlugin plugin;
    private PlayerMock player;
    private PlayerMock operator;

    @BeforeEach
    void start() {
        server = StreamableServerMock.mock();
        plugin = MockBukkit.loadWith(RakunLootsPlugin.class, new PluginDescriptionFile("RakunLoots",
                "1.0.0-SNAPSHOT", RakunLootsPlugin.class.getName()));
        player = server.addPlayer("Steve");
        operator = server.addPlayer("Alex");
        operator.setOp(true);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    /**
     * Acceptance: the shipped config.yml says true, so everyone has the permission, as plugin.yml did.
     */
    @Test
    void theShippedConfigGivesTheClaimPermissionToEveryone() throws IOException {
        assertTrue(Files.readString(config()).contains("permission_default: true"));

        assertGranted(true, true);
        assertEquals(PermissionDefault.TRUE, permission().getDefault());
    }

    /**
     * Acceptance: each value takes effect for online players on reload: true everyone, false nobody, op only
     * operators, not op everyone but operators. Each reload follows the previous value, so a value that stays from
     * the one before shows up as a failure.
     */
    @Test
    void eachValueTakesEffectOnReloadForPlayersAlreadyOnline() throws IOException {
        for (String[] row : new String[][]{{"false", "false", "false"}, {"op", "false", "true"},
                {"not op", "true", "false"}, {"true", "true", "true"}, {"op", "false", "true"}}) {
            writeDefault("\"" + row[0] + "\"");

            reload();

            assertGranted(Boolean.parseBoolean(row[1]), Boolean.parseBoolean(row[2]), row[0]);
        }
    }

    /**
     * An unquoted yes or no in YAML is a boolean, and still reads.
     */
    @Test
    void anUnquotedYamlBooleanReadsToo() throws IOException {
        writeDefault("false");
        reload();
        assertGranted(false, false);

        writeDefault("true");
        reload();
        assertGranted(true, true);
    }

    /**
     * Acceptance: enable reads it too. The value written before the plugin enables is the one it starts with.
     */
    @Test
    void enableAppliesTheWrittenValue() throws IOException {
        writeDefault("\"not op\"");
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);

        assertTrue(plugin.isEnabled());
        assertGranted(true, false);
    }

    /**
     * A value that names no default warns high, as the rest of the block does, and leaves everyone with the
     * permission.
     */
    @Test
    void aBadValueKeepsTheDefaultOfEveryone() throws IOException {
        writeDefault("\"sometimes\"");

        reload();

        assertGranted(true, true);
        assertTrue(plugin.config().warnings().stream().anyMatch(warning ->
                warning.key().equals(java.util.Optional.of("permission_default"))));
    }

    private void assertGranted(boolean playerHas, boolean operatorHas) {
        assertGranted(playerHas, operatorHas, "");
    }

    private void assertGranted(boolean playerHas, boolean operatorHas, String value) {
        assertEquals(playerHas, player.hasPermission(RakunLootsCommands.CLAIM_PERMISSION), "player with " + value);
        assertEquals(operatorHas, operator.hasPermission(RakunLootsCommands.CLAIM_PERMISSION), "operator with " + value);
    }

    private Permission permission() {
        return server.getPluginManager().getPermission(RakunLootsCommands.CLAIM_PERMISSION);
    }

    private Path config() {
        return plugin.getDataFolder().toPath().resolve("config.yml");
    }

    private void writeDefault(String value) throws IOException {
        List<String> lines = Files.readAllLines(config(), StandardCharsets.UTF_8).stream()
                .map(line -> line.startsWith("  permission_default:") ? "  permission_default: " + value : line)
                .toList();
        Files.write(config(), lines, StandardCharsets.UTF_8);
    }

    private void reload() {
        // The operator runs it: MockBukkit's console does not implement permission checks.
        assertTrue(server.dispatchCommand(operator, "rkl reload"));
    }
}
