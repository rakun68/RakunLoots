package me.rakkoon69.rakunloots.command;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * One run of a node: who ran it, under which label, and the parsed arguments by name. An optional argument that was
 * left out is absent.
 */
public final class CommandInvocation {

    private static final Pattern COLOR_CODE = Pattern.compile("&[0-9a-fk-orA-FK-OR]");

    private final CommandSender sender;
    private final String label;
    private final CommandNode node;
    private final Map<String, Object> values;
    private final CommandText text;
    private final Consumer<String> debug;

    CommandInvocation(CommandSender sender, String label, CommandNode node, Map<String, Object> values, CommandText text,
                      Consumer<String> debug) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.label = Objects.requireNonNull(label, "label");
        this.node = Objects.requireNonNull(node, "node");
        this.values = Map.copyOf(values);
        this.text = Objects.requireNonNull(text, "text");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    public CommandSender sender() {
        return sender;
    }

    /**
     * The root name or alias the sender typed.
     */
    public String label() {
        return label;
    }

    public CommandNode node() {
        return node;
    }

    public boolean has(String argument) {
        return values.containsKey(argument);
    }

    public <T> Optional<T> find(String argument, Class<T> type) {
        Object value = values.get(argument);
        return value == null ? Optional.empty() : Optional.of(type.cast(value));
    }

    public <T> T get(String argument, Class<T> type) {
        return find(argument, type).orElseThrow(() -> new NoSuchElementException("no argument " + argument));
    }

    /**
     * A default message with its placeholders filled.
     */
    public String message(String key, Map<String, String> placeholders) {
        return text.format(key, placeholders);
    }

    /**
     * Sends a default message to the sender.
     */
    public void reply(String key, Map<String, String> placeholders) {
        String message = message(key, placeholders);
        sender.send(message);
        debug.accept("command /" + label + " " + String.join(" ", node.path()) + " by " + sender.name() + ": "
                + COLOR_CODE.matcher(message).replaceAll(""));
    }

    /**
     * Sends a default message to the sender with clickable buttons after it.
     */
    public void reply(String key, Map<String, String> placeholders, java.util.List<CommandButton> buttons) {
        String message = message(key, placeholders);
        sender.send(message, buttons);
        debug.accept("command /" + label + " " + String.join(" ", node.path()) + " by " + sender.name() + ": "
                + COLOR_CODE.matcher(message).replaceAll(""));
    }

    public void reply(String key) {
        reply(key, Map.of());
    }
}
