package me.rakkoon69.rakunloots.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.rakkoon69.rakunloots.match.CloseMatcher;

/**
 * The {@code help [command]} node, for any root to add as a child. Without a command it lists what the sender can
 * run, one line each, between two dividers, then the other roots it was given that the sender can run, such as the
 * claim and wiki commands. With one it shows that command's usage, what it does, each argument and its type, an
 * example, its permission, and the commands under it. A command the sender cannot run is not listed and is unknown to
 * {@code help <command>}.
 */
public final class HelpCommand {

    private static final String ARGUMENT = "command";

    private HelpCommand() {
    }

    public static CommandNode node() {
        return node(List.of());
    }

    /**
     * @param others roots of their own that the list names after this root's commands, under their own names
     */
    public static CommandNode node(List<CommandNode> others) {
        List<CommandNode> listed = List.copyOf(others);
        return CommandNode.builder("help")
                .argument(CommandArgument.optional(ARGUMENT, new TopicArgument()))
                .handler(invocation -> run(invocation, listed))
                .build();
    }

    private static void run(CommandInvocation invocation, List<CommandNode> others) {
        CommandNode root = invocation.node().root();
        CommandSender sender = invocation.sender();
        if (invocation.has(ARGUMENT)) {
            detail(invocation, invocation.get(ARGUMENT, CommandNode.class));
            return;
        }
        List<CommandNode> commands = runnable(root, sender);
        List<CommandNode> reachable = others.stream().filter(other -> other.reachable(sender)).toList();
        invocation.reply("cmd_help_divider");
        invocation.reply("cmd_help_header", Map.of("count", Integer.toString(commands.size() + reachable.size())));
        entries(invocation, commands, invocation.label());
        if (!reachable.isEmpty()) {
            invocation.reply("cmd_help_others");
            for (CommandNode other : reachable) {
                entries(invocation, List.of(other), other.name());
            }
        }
        invocation.reply("cmd_help_footer", Map.of("label", invocation.label()));
        invocation.reply("cmd_help_divider");
    }

    private static void detail(CommandInvocation invocation, CommandNode node) {
        CommandSender sender = invocation.sender();
        invocation.reply("cmd_help_divider");
        invocation.reply("cmd_help_usage", usage(node, invocation.label(), sender));
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
            invocation.reply("cmd_help_subcommands");
            entries(invocation, below, invocation.label());
        }
        invocation.reply("cmd_help_divider");
    }

    private static void entries(CommandInvocation invocation, List<CommandNode> nodes, String label) {
        for (CommandNode node : nodes) {
            Map<String, String> values = new java.util.HashMap<>(usage(node, label, invocation.sender()));
            values.put("summary", invocation.message(node.helpKey("summary"), Map.of()));
            invocation.reply("cmd_help_entry", values);
        }
    }

    /**
     * The usage in two parts, so a message can color them apart: {@code command}, the slash, label, and words, and
     * {@code args}, the rest with its leading space, or blank.
     */
    private static Map<String, String> usage(CommandNode node, String label, CommandSender sender) {
        StringBuilder command = new StringBuilder("/").append(label);
        for (String word : node.path()) {
            command.append(' ').append(word);
        }
        String usage = node.usage(label, sender);
        return Map.of("command", command.toString(), "args", usage.substring(command.length()));
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
