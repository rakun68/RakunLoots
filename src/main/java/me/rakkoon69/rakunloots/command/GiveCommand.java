package me.rakkoon69.rakunloots.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code give <loot> [player] [amount]} node. It gives summon items through the give-back path, so an offline
 * player or a full inventory gets unclaimed summon items for what does not fit. Without a player it gives to the
 * sender, and the console must name one. The amount is 1 when left out.
 */
public final class GiveCommand {

    /**
     * The most summon items one give hands out.
     */
    public static final int MAX_AMOUNT = 1000;

    private GiveCommand() {
    }

    /**
     * Where a give goes: to the player's inventory as far as it fits, and the rest as unclaimed summon items.
     */
    @FunctionalInterface
    public interface Giver {

        void give(UUID playerId, String lootId, int amount);
    }

    public static CommandNode node(Giver giver, CommandSources sources) {
        return CommandNode.builder("give")
                .argument(CommandArgument.required("loot", LootIdArgument.INSTANCE))
                .argument(CommandArgument.optional("player", PlayerArgument.INSTANCE))
                .argument(CommandArgument.optional("amount", new IntegerArgument(1, MAX_AMOUNT)))
                .handler(invocation -> give(invocation, giver, sources))
                .build();
    }

    private static void give(CommandInvocation invocation, Giver giver, CommandSources sources) {
        String lootId = invocation.get("loot", String.class);
        int amount = invocation.find("amount", Integer.class).orElse(1);
        Optional<UUID> playerId;
        String playerName;
        if (invocation.has("player")) {
            playerName = invocation.get("player", String.class);
            playerId = sources.playerId(playerName);
        } else {
            playerName = invocation.sender().name();
            playerId = invocation.sender().playerId();
            if (playerId.isEmpty()) {
                invocation.reply("cmd_give_needs_player");
                return;
            }
        }
        if (playerId.isEmpty()) {
            invocation.reply("cmd_give_player_unknown", Map.of("player", playerName));
            return;
        }
        giver.give(playerId.get(), lootId, amount);
        invocation.reply("cmd_give_done", Map.of("amount", Integer.toString(amount), "loot", lootId,
                "player", playerName));
    }
}
