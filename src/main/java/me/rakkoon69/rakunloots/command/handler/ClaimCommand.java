package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.summon.item.ClaimMenu;
import me.rakkoon69.rakunloots.summon.item.UnclaimedSummonItems;
import me.rakkoon69.rakunloots.config.ClaimCommandSettings;

import java.util.Optional;
import java.util.UUID;

/**
 * The claim command: a root of its own, named by the {@code claim_command} block of {@code config.yml}. With no
 * argument it opens the claim menu, or says nothing is waiting and opens nothing. Its {@code all} child gives
 * everything that fits at once. Who may run either by default comes from the {@code claim_command} block's
 * {@code permission_default}, through {@value #PERMISSION}; only a player can, because the items go into their
 * inventory.
 */
final class ClaimCommand {

    /**
     * The claim command's permission. {@code plugin.yml} registers it, and the plugin sets its default from the
     * config at enable and on reload.
     */
    static final String PERMISSION = "rakunloots.claim";

    private ClaimCommand() {
    }

    static CommandNode node(ClaimCommandSettings settings, UnclaimedSummonItems claims, ClaimMenu menu) {
        return CommandNode.builder(settings.name())
                .aliases(settings.aliases().toArray(String[]::new))
                .permission(PERMISSION)
                .helpKeys("command_claim")
                .handler(invocation -> {
                    Optional<UUID> playerId = invocation.sender().playerId();
                    if (playerId.isEmpty()) {
                        invocation.reply("cmd_claim_needs_player");
                        return;
                    }
                    menu.open(playerId.get(), () -> invocation.reply("claim_nothing"));
                })
                .child(CommandNode.builder("all")
                        .permission(PERMISSION)
                        .handler(invocation -> {
                            Optional<UUID> playerId = invocation.sender().playerId();
                            if (playerId.isEmpty()) {
                                invocation.reply("cmd_claim_needs_player");
                                return;
                            }
                            claims.claim(playerId.get());
                        })
                        .build())
                .build();
    }
}
