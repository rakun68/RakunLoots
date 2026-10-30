package me.rakkoon69.rakunloots.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.rakkoon69.rakunloots.match.CloseMatcher;

/**
 * The {@code help [command]} node, for any root to add as a child. Without a command it lists what the sender can
 * run, one line each. With one it shows that command's usage, what it does, each argument and its type, an example,
 * and its permission. A command the sender cannot run is not listed and is unknown to {@code help <command>}.
 */
public final class HelpCommand {

    private static final String ARGUMENT = "command";

    private HelpCommand() {
    }

    public static CommandNode node() {
        return CommandNode.builder("help")
                .argument(CommandArgument.optional(ARGUMENT, new TopicArgument()))
                .handler(HelpCommand::run)
                .build();
    }

    private static void run(CommandInvocation invocation) {
        CommandNode root = invocation.node().root();
        CommandSender sender = invocation.sender();
        if (invocation.has(ARGUMENT)) {
            detail(invocation, invocation.get(ARGUMENT, CommandNode.class));
            return;
        }
        invocation.reply("cmd_help_header");
        entries(invocation, runnable(root, sender));
    }

    private static void detail(CommandInvocation invocation, CommandNode node) {
        CommandSender sender = invocation.sender();
        invocation.reply("cmd_help_usage", Map.of("usage", node.usage(invocation.label(), sender)));
        invocation.reply("cmd_help_description", Map.of("description", invocation.message(node.helpKey("description"), Map.of())));
        for (CommandArgument<?> argument : node.arguments()) {
            invocation.reply("cmd_help_argument", Map.of(
                    "name", argument.name(),
                    "type", invocation.message(argument.type().typeKey(), argument.type().typeValues()),
                    "description", invocation.message(node.argumentHelpKey(argument), Map.of())
            ));
        }
        invocation.reply("cmd_help_example", Map.of("example", invocation.message(node.helpKey("example"), Map.of())));
        invocation.reply("cmd_help_permission", Map.of("permission", node.permission()
                .orElseGet(() -> invocation.message("cmd_help_permission_everyone", Map.of()))));
        List<CommandNode> below = new ArrayList<>(runnable(node, sender));
        below.remove(node);
        if (!below.isEmpty()) {
            entries(invocation, below);
        }
    }

    private static void entries(CommandInvocation invocation, List<CommandNode> nodes) {
        for (CommandNode node : nodes) {
            invocation.reply("cmd_help_entry", Map.of(
                    "usage", node.usage(invocation.label(), invocation.sender()),
                    "summary", invocation.message(node.helpKey("summary"), Map.of())
            ));
        }
    }

    /**
     * Every node at or below this one that has a handler and that the sender may run, parents before children.
     */
    private static List<CommandNode> runnable(CommandNode from, CommandSender sender) {
        List<CommandNode> found = new ArrayList<>();
        collect(from, sender, found);
        return found;
    }

    private static void collect(CommandNode node, CommandSender sender, List<CommandNode> found) {
        if (node.handler().isPresent() && node.allows(sender)) {
            found.add(node);
        }
        for (CommandNode child : node.visibleChildren(sender)) {
            collect(child, sender, found);
        }
    }

    /**
     * A command written as its words, such as {@code remove world}, found among what the sender may see.
     */
    private static final class TopicArgument implements ArgumentType<CommandNode> {

        @Override
        public String typeKey() {
            return "cmd_type_command";
        }

        @Override
        public boolean greedy() {
            return true;
        }

        @Override
        public ParseResult<CommandNode> parse(String token, ArgumentContext context) {
            CommandNode node = context.root();
            for (String word : token.trim().split("\\s+")) {
                node = node.visibleChild(word, context.sender()).orElse(null);
                if (node == null) {
                    List<String> paths = new ArrayList<>();
                    for (CommandNode candidate : listed(context.root(), context.sender())) {
                        paths.add(String.join(" ", candidate.path()));
                    }
                    return ParseResult.fail("cmd_reason_help_unknown", Map.of("value", token.trim()),
                            CloseMatcher.of(paths).closest(token.trim()));
                }
            }
            return ParseResult.ok(node);
        }

        @Override
        public List<String> complete(String rest, ArgumentContext context) {
            String[] words = rest.split(" ", -1);
            CommandNode node = context.root();
            for (String word : Arrays.copyOf(words, words.length - 1)) {
                node = word.isEmpty() ? node : node.visibleChild(word, context.sender()).orElse(null);
                if (node == null) {
                    return List.of();
                }
            }
            String partial = words[words.length - 1].toLowerCase(Locale.ROOT);
            return node.visibleChildren(context.sender()).stream()
                    .map(CommandNode::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
        }

        private static List<CommandNode> listed(CommandNode from, CommandSender sender) {
            List<CommandNode> found = new ArrayList<>();
            for (CommandNode child : from.visibleChildren(sender)) {
                found.add(child);
                found.addAll(listed(child, sender));
            }
            return found;
        }
    }
}
