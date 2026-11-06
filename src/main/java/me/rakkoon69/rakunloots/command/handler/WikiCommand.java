package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.config.WikiCommandSettings;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.wiki.WikiMenu;

import java.util.Optional;
import java.util.UUID;

/**
 * The loots wiki command: a root of its own, named by the {@code wiki_command} block of {@code config.yml}. Only a
 * player can run it, because the list shows the timer chances of the world they stand in.
 */
final class WikiCommand {

    /**
     * The wiki command's permission. {@code plugin.yml} registers it, and the plugin sets its default from the config
     * at enable and on reload.
     */
    static final String PERMISSION = "rakunloots.wiki";

    private WikiCommand() {
    }

    static CommandNode node(WikiCommandSettings settings, WikiMenu menu) {
        return CommandNode.builder(settings.name())
                .aliases(settings.aliases().toArray(String[]::new))
                .permission(PERMISSION)
                .helpKeys("command_wiki")
                .handler(invocation -> {
                    Optional<UUID> playerId = invocation.sender().playerId();
                    Optional<SenderLocation> location = invocation.sender().location();
                    if (playerId.isEmpty() || location.isEmpty()) {
                        invocation.reply("cmd_wiki_needs_player");
                        return;
                    }
                    menu.open(playerId.get(), location.get());
                })
                .build();
    }
}
