package me.rakkoon69.rakunloots.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The roots go in the server's command map under the name and aliases the shipped {@code config.yml} gives, and a line
 * typed on the server reaches the command tree.
 */
class BukkitCommandRegistrarTest {

    private ServerMock server;
    private Plugin plugin;
    private BukkitCommandRegistrar registrar;
    private PlayerMock player;

    @BeforeEach
    void startServer() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        var snapshot = ShippedHologramConfig.load();
        CommandRegistry registry = new CommandRegistry(new FakeCommandSources(), key -> snapshot.message("", key));
        registrar = new BukkitCommandRegistrar(plugin, registry);

        var names = snapshot.adminCommand();
        registrar.register(CommandNode.builder(names.name())
                .aliases(names.aliases().toArray(String[]::new))
                .permissionBase("rakunloots.command")
                .child(HelpCommand.node())
                .build());
        player = server.addPlayer();
        player.addAttachment(plugin, "rakunloots.command.help", true);
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void theRootIsInTheServersCommandMapUnderTheNameAndAliasesFromConfig() {
        assertNotNull(server.getCommandMap().getCommand("rkl"));
        assertEquals(server.getCommandMap().getCommand("rkl"), server.getCommandMap().getCommand("rkloots"));
        assertEquals(List.of("rkloots"), server.getCommandMap().getCommand("rkl").getAliases());
    }

    @Test
    void aLineTypedOnTheServerRunsThroughTheNameOrAnAlias() {
        server.dispatchCommand(player, "rkloots help help");

        assertEquals("§f[§bRakunLoots§f]§7 §7Usage: §e/rkloots help [command]", player.nextMessage());
    }

    @Test
    void aFailedLineShowsItsUnderlineWithTheColorCodesTranslated() {
        server.dispatchCommand(player, "rkl bogus");

        assertEquals("§f[§bRakunLoots§f]§7 §f/rkl §c§nbogus§r§f", player.nextMessage());
        assertEquals("§8> §cno subcommand named §fbogus§c§7, expected §eone of help§7.", player.nextMessage());
    }

    @Test
    void tabCompletionOffersWhatTheSenderMayRun() {
        assertEquals(List.of("help"), server.getCommandMap().tabComplete(player, "rkl h"));

        PlayerMock nobody = server.addPlayer();
        assertEquals(List.of(), server.getCommandMap().tabComplete(nobody, "rkl h"));
    }

    @Test
    void unregisteringTakesTheRootAndItsAliasesOutOfTheCommandMap() {
        registrar.unregisterAll();

        assertNull(server.getCommandMap().getCommand("rkl"));
        assertNull(server.getCommandMap().getCommand("rkloots"));
    }
}
