package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandInvocation;
import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.CommandSources;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code list} node: the ids of the loaded loots, in order.
 */
final class ListCommand {

    private ListCommand() {
    }

    static CommandNode node(CommandSources sources) {
        return CommandNode.builder("list")
                .handler(invocation -> list(invocation, sources.lootIds()))
                .build();
    }

    private static void list(CommandInvocation invocation, Collection<String> lootIds) {
        if (lootIds.isEmpty()) {
            invocation.reply("cmd_list_none");
            return;
        }
        List<String> sorted = lootIds.stream()
                .sorted(Comparator.comparing((String id) -> id.toLowerCase(Locale.ROOT)))
                .toList();
        invocation.reply("cmd_list", Map.of("amount", Integer.toString(sorted.size()),
                "loots", String.join("&7, &f", sorted)));
    }
}
