package me.rakkoon69.rakunloots;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.command.WikiCommand;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wiki command's permission default comes from {@code wiki_command.permission_default} in config.yml, on
 * MockBukkit with the shipped data folder.
 */
class WikiPermissionDefaultTest {

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

    @Test
    void theShippedConfigGivesTheWikiPermissionToEveryone() throws IOException {
        String shipped = Files.readString(config());
        assertTrue(shipped.contains("wiki_command:"));
        assertTrue(shipped.contains("name: loots"));
        assertTrue(shipped.contains("rklloots"));

        assertGranted(true, true);
        assertEquals(PermissionDefault.TRUE, permission().getDefault());
    }

    @Test
    void eachValueTakesEffectOnReloadForPlayersAlreadyOnline() throws IOException {
        for (String[] row : new String[][]{{"false", "false", "false"}, {"op", "false", "true"},
                {"not op", "true", "false"}, {"true", "true", "true"}, {"op", "false", "true"}}) {
            writeDefault("\"" + row[0] + "\"");

            reload();

            assertGranted(Boolean.parseBoolean(row[1]), Boolean.parseBoolean(row[2]), row[0]);
        }
    }

    @Test
    void enableAppliesTheWrittenValue() throws IOException {
        writeDefault("\"not op\"");
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);

        assertTrue(plugin.isEnabled());
        assertGranted(true, false);
    }

    @Test
    void aBadValueKeepsTheDefaultOfEveryone() throws IOException {
        writeDefault("\"sometimes\"");

        reload();

        assertGranted(true, true);
        assertTrue(plugin.config().warnings().stream().anyMatch(warning ->
                warning.kind() == me.rakkoon69.rakunloots.config.ConfigWarning.Kind.UNUSABLE_WIKI_COMMAND_VALUE
                        && warning.key().equals(java.util.Optional.of("permission_default"))));
    }

    private void assertGranted(boolean playerHas, boolean operatorHas) {
        assertGranted(playerHas, operatorHas, "");
    }

    private void assertGranted(boolean playerHas, boolean operatorHas, String value) {
        assertEquals(playerHas, player.hasPermission(WikiCommand.PERMISSION), "player with " + value);
        assertEquals(operatorHas, operator.hasPermission(WikiCommand.PERMISSION), "operator with " + value);
    }

    private Permission permission() {
        return server.getPluginManager().getPermission(WikiCommand.PERMISSION);
    }

    private Path config() {
        return plugin.getDataFolder().toPath().resolve("config.yml");
    }

    private void writeDefault(String value) throws IOException {
        List<String> lines = new ArrayList<>();
        boolean inWikiCommand = false;
        for (String line : Files.readAllLines(config(), StandardCharsets.UTF_8)) {
            if (line.startsWith("wiki_command:")) {
                inWikiCommand = true;
            } else if (inWikiCommand && !line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
                inWikiCommand = false;
            }
            if (inWikiCommand && line.startsWith("  permission_default:")) {
                line = "  permission_default: " + value;
            }
            lines.add(line);
        }
        Files.write(config(), lines, StandardCharsets.UTF_8);
    }

    private void reload() {
        assertTrue(server.dispatchCommand(operator, "rkl reload"));
    }
}
