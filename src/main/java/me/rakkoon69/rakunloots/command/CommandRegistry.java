package me.rakkoon69.rakunloots.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import me.rakkoon69.rakunloots.match.CloseMatcher;

/**
 * Holds the root nodes, runs a typed line against them, and completes one. Both walk the tree token by token, and a
 * node the sender may not run is as good as absent to both.
 *
 * <p>A failed line answers in two lines: the line as typed with the failing token underlined, then the reason and
 * the expected type. A missing argument underlines the end of the line.
 */
public final class CommandRegistry {

    private static final Pattern WORD = Pattern.compile("\\S+");
    private static final String UNDERLINE_ON = "&c&n";
    private static final String UNDERLINE_OFF = "&r&f";

    private record Token(String text, int start, int end) {
    }

    private final CommandSources sources;
    private final CommandText text;
    private final Consumer<String> debug;
    private final Map<String, CommandNode> roots = new LinkedHashMap<>();

    public CommandRegistry(CommandSources sources, CommandMessages messages) {
        this(sources, messages, line -> {
        });
    }

    /**
     * @param debug takes one line for each reply a command makes, as it was sent to the sender, so debug mode can show
     *              what only the sender saw. It writes only while debug mode is on.
     */
    public CommandRegistry(CommandSources sources, CommandMessages messages, Consumer<String> debug) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.text = new CommandText(messages);
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    /**
     * Adds a root. Its name and aliases may not belong to another root.
     */
    public void register(CommandNode root) {
        Objects.requireNonNull(root, "root");
        if (root.parent().isPresent()) {
            throw new IllegalArgumentException(root.name() + " is not a root");
        }
        List<String> words = new ArrayList<>();
        words.add(root.name());
        words.addAll(root.aliases());
        for (String word : words) {
            if (roots.containsKey(key(word))) {
                throw new IllegalArgumentException("a root named " + word + " is already registered");
            }
        }
        words.forEach(word -> roots.put(key(word), root));
    }

    public Optional<CommandNode> root(String label) {
        return Optional.ofNullable(roots.get(key(label)));
    }

    public Collection<CommandNode> roots() {
        return roots.values().stream().distinct().toList();
    }

    /**
     * Runs one typed line, label first, without the leading slash.
     *
     * @return whether a handler ran
     */
    public boolean dispatch(CommandSender sender, String line) {
        Objects.requireNonNull(sender, "sender");
        String typed = line.strip();
        List<Token> tokens = tokens(typed);
        if (tokens.isEmpty()) {
            return false;
        }
        CommandNode root = roots.get(key(tokens.get(0).text()));
        if (root == null) {
            return false;
        }
        if (!root.reachable(sender)) {
            sender.send(text.format("cmd_unknown", Map.of()));
            return false;
        }
        String label = tokens.get(0).text();
        Context context = new Context(sender, root);

        CommandNode node = root;
        int at = 1;
        while (at < tokens.size()) {
            Optional<CommandNode> child = node.visibleChild(tokens.get(at).text(), sender);
            if (child.isEmpty()) {
                break;
            }
            node = child.get();
            at++;
        }

        if (node.arguments().isEmpty() && node.handler().isEmpty()) {
            return subcommandMissing(sender, label, typed, tokens, node, at);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        List<CommandArgument<?>> arguments = node.arguments();
        for (CommandArgument<?> argument : arguments) {
            if (at >= tokens.size()) {
                if (!argument.isOptional()) {
                    failMissing(sender, typed, argument);
                    return false;
                }
                break;
            }
            Token start = tokens.get(at);
            Token end = argument.type().greedy() ? tokens.get(tokens.size() - 1) : start;
            String token = argument.type().greedy() ? typed.substring(start.start(), end.end()) : start.text();
            ParseResult<?> result = argument.type().parse(token, context);
            if (!result.succeeded()) {
                fail(sender, typed, start.start(), end.end(), result, argument.type());
                return false;
            }
            values.put(argument.name(), result.value());
            at = argument.type().greedy() ? tokens.size() : at + 1;
        }
        if (at < tokens.size()) {
            Token extra = tokens.get(at);
            fail(sender, typed, extra.start(), tokens.get(tokens.size() - 1).end(), "cmd_reason_too_many",
                    Map.of("value", typed.substring(extra.start())), List.of(), "cmd_type_nothing", Map.of());
            return false;
        }
        node.handler().orElseThrow().run(new CommandInvocation(sender, label, node, values, text, debug));
        return true;
    }

    /**
     * Completions for one typed line, label first, where a trailing space starts a new word.
     */
    public List<String> complete(CommandSender sender, String line) {
        Objects.requireNonNull(sender, "sender");
        String typed = line.stripLeading();
        List<Token> tokens = new ArrayList<>(tokens(typed));
        if (typed.isEmpty() || Character.isWhitespace(typed.charAt(typed.length() - 1))) {
            tokens.add(new Token("", typed.length(), typed.length()));
        }
        if (tokens.size() < 2) {
            return List.of();
        }
        CommandNode root = roots.get(key(tokens.get(0).text()));
        if (root == null || !root.reachable(sender)) {
            return List.of();
        }
        Context context = new Context(sender, root);
        int last = tokens.size() - 1;

        CommandNode node = root;
        int at = 1;
        while (at < last) {
            Optional<CommandNode> child = node.visibleChild(tokens.get(at).text(), sender);
            if (child.isEmpty()) {
                break;
            }
            node = child.get();
            at++;
        }

        TreeSet<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String partial = tokens.get(last).text().toLowerCase(Locale.ROOT);
        if (at == last) {
            for (CommandNode child : node.visibleChildren(sender)) {
                if (child.name().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    found.add(child.name());
                }
            }
        }
        for (CommandArgument<?> argument : node.arguments()) {
            if (argument.type().greedy()) {
                if (at <= last) {
                    String rest = typed.substring(tokens.get(at).start());
                    found.addAll(argument.type().complete(rest, context));
                }
                return List.copyOf(found);
            }
            if (at == last) {
                argument.type().complete(partial, context).stream()
                        .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(partial))
                        .forEach(found::add);
                break;
            }
            if (!argument.type().parse(tokens.get(at).text(), context).succeeded()) {
                return List.of();
            }
            at++;
        }
        return List.copyOf(found);
    }

    private boolean subcommandMissing(
            CommandSender sender, String label, String typed, List<Token> tokens, CommandNode node, int at
    ) {
        List<CommandNode> visible = node.visibleChildren(sender);
        String choices = String.join(", ", visible.stream().map(CommandNode::name).toList());
        if (at >= tokens.size()) {
            if (node.parent().isEmpty()) {
                new CommandInvocation(sender, label, node, Map.of(), text, debug).reply("cmd_use_help",
                        Map.of("usage", "/" + label + " help"));
                return false;
            }
            failEnd(sender, typed, "cmd_reason_subcommand_missing", Map.of(), "cmd_type_subcommand",
                    Map.of("choices", choices));
            return false;
        }
        Token token = tokens.get(at);
        List<String> names = new ArrayList<>();
        for (CommandNode child : visible) {
            names.add(child.name());
            names.addAll(child.aliases());
        }
        fail(sender, typed, token.start(), token.end(), "cmd_reason_subcommand_unknown",
                Map.of("value", token.text()), CloseMatcher.of(names).closest(token.text()),
                "cmd_type_subcommand", Map.of("choices", choices));
        return false;
    }

    private void failMissing(CommandSender sender, String typed, CommandArgument<?> argument) {
        Map<String, String> reasonValues = Map.of("name", argument.name());
        Map<String, String> typeValues = argument.type().typeValues();
        failEnd(sender, typed, "cmd_reason_missing", reasonValues, argument.type().typeKey(), typeValues);
    }

    private void fail(
            CommandSender sender, String typed, int from, int to, ParseResult<?> result, ArgumentType<?> type
    ) {
        fail(sender, typed, from, to, result.reasonKey(), result.values(), result.suggestions(), type.typeKey(),
                type.typeValues());
    }

    private void fail(
            CommandSender sender, String typed, int from, int to, String reasonKey, Map<String, String> reasonValues,
            List<String> suggestions, String typeKey, Map<String, String> typeValues
    ) {
        String underlined = typed.substring(0, from) + UNDERLINE_ON + typed.substring(from, to) + UNDERLINE_OFF
                + typed.substring(to);
        send(sender, underlined, reasonKey, reasonValues, suggestions, typeKey, typeValues);
    }

    private void failEnd(
            CommandSender sender, String typed, String reasonKey, Map<String, String> reasonValues, String typeKey,
            Map<String, String> typeValues
    ) {
        String underlined = typed + " " + UNDERLINE_ON + " " + UNDERLINE_OFF;
        send(sender, underlined, reasonKey, reasonValues, List.of(), typeKey, typeValues);
    }

    private void send(
            CommandSender sender, String typedLine, String reasonKey, Map<String, String> reasonValues,
            List<String> suggestions, String typeKey, Map<String, String> typeValues
    ) {
        sender.send(text.format("cmd_failure_typed", Map.of("line", typedLine)));
        Map<String, String> all = new LinkedHashMap<>(typeValues);
        all.putAll(reasonValues);
        String reason = text.format(reasonKey, all);
        String type = text.format(typeKey, all);
        String matches = suggestions.isEmpty()
                ? ""
                : text.format("cmd_failure_suggestions", Map.of("matches", String.join(", ", suggestions)));
        sender.send(text.format("cmd_failure_reason", Map.of("reason", reason, "type", type, "suggestions", matches)));
    }

    private static List<Token> tokens(String line) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(line);
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(), matcher.start(), matcher.end()));
        }
        return tokens;
    }

    private static String key(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    private final class Context implements ArgumentContext {

        private final CommandSender sender;
        private final CommandNode root;

        private Context(CommandSender sender, CommandNode root) {
            this.sender = sender;
            this.root = root;
        }

        @Override
        public CommandSender sender() {
            return sender;
        }

        @Override
        public CommandSources sources() {
            return sources;
        }

        @Override
        public CommandNode root() {
            return root;
        }
    }
}
