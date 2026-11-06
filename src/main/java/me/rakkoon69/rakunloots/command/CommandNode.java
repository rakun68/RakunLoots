package me.rakkoon69.rakunloots.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One node of a command tree. It has a name, aliases, a permission, typed arguments, a handler, and help keys, and it
 * may have child nodes, so {@code remove world <world>} is the {@code world} child of {@code remove}.
 *
 * <p>The permission of a node under a root with a permission base is {@code <base>.<path>}, where the path is the
 * names from below the root, joined with dots: {@code rakunloots.command.remove.world}. A node may set its own
 * permission, or none. Help keys are {@code command_<path>_summary}, {@code _description}, {@code _example}, and
 * {@code _arg_<name>} for each argument, with the path joined by underscores, unless the node names its own base.
 */
public final class CommandNode {

    private final String name;
    private final List<String> aliases;
    private final String explicitPermission;
    private final boolean everyone;
    private final String permissionBase;
    private final List<CommandArgument<?>> arguments;
    private final CommandHandler handler;
    private final List<CommandNode> children;
    private final String helpBase;
    private CommandNode parent;

    private CommandNode(Builder builder) {
        this.name = builder.name;
        this.aliases = List.copyOf(builder.aliases);
        this.explicitPermission = builder.permission;
        this.everyone = builder.everyone;
        this.permissionBase = builder.permissionBase;
        this.arguments = List.copyOf(builder.arguments);
        this.handler = builder.handler;
        this.children = List.copyOf(builder.children);
        this.helpBase = builder.helpBase;
        for (CommandNode child : children) {
            child.parent = this;
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public Optional<CommandNode> parent() {
        return Optional.ofNullable(parent);
    }

    public CommandNode root() {
        CommandNode node = this;
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }

    /**
     * The names from below the root down to this node. A root has an empty path.
     */
    public List<String> path() {
        List<String> path = new ArrayList<>();
        for (CommandNode node = this; node.parent != null; node = node.parent) {
            path.add(node.name);
        }
        Collections.reverse(path);
        return path;
    }

    public List<CommandArgument<?>> arguments() {
        return arguments;
    }

    public Optional<CommandHandler> handler() {
        return Optional.ofNullable(handler);
    }

    public List<CommandNode> children() {
        return children;
    }

    /**
     * The permission a sender needs to run this node, or empty when everyone may.
     */
    public Optional<String> permission() {
        if (explicitPermission != null) {
            return Optional.of(explicitPermission);
        }
        String base = root().permissionBase;
        if (everyone || parent == null || base == null) {
            return Optional.empty();
        }
        return Optional.of(base + "." + String.join(".", path()));
    }

    /**
     * Whether the sender may run this node. A node the sender may not run is hidden from help and completion, and
     * answers as unknown.
     */
    public boolean allows(CommandSender sender) {
        return permission().map(sender::hasPermission).orElse(true);
    }

    /**
     * Whether the sender may run this node or something under it. A node with only children, none of which the sender
     * may run, is as closed to the sender as one whose own permission they lack.
     */
    boolean reachable(CommandSender sender) {
        return allows(sender) && (handler().isPresent() || !arguments().isEmpty()
                || children.stream().anyMatch(child -> child.reachable(sender)));
    }

    boolean matches(String word) {
        return name.equalsIgnoreCase(word) || aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(word));
    }

    List<CommandNode> visibleChildren(CommandSender sender) {
        return children.stream().filter(child -> child.allows(sender)).toList();
    }

    Optional<CommandNode> visibleChild(String word, CommandSender sender) {
        return children.stream().filter(child -> child.matches(word) && child.allows(sender)).findFirst();
    }

    /**
     * Message key of one help part: {@code summary}, {@code description}, or {@code example}.
     */
    public String helpKey(String part) {
        return helpBase() + "_" + part;
    }

    /**
     * Message key of the help line for one argument of this node.
     */
    public String argumentHelpKey(CommandArgument<?> argument) {
        return helpBase() + "_arg_" + argument.name();
    }

    private String helpBase() {
        if (helpBase != null) {
            return helpBase;
        }
        List<String> path = path();
        return path.isEmpty() ? "command" : "command_" + String.join("_", path);
    }

    /**
     * {@code /label path arguments}. A node with children and no arguments lists the children the sender may run.
     */
    String usage(String label, CommandSender sender) {
        StringBuilder usage = new StringBuilder("/").append(label);
        for (String word : path()) {
            usage.append(' ').append(word);
        }
        if (arguments.isEmpty()) {
            List<CommandNode> visible = visibleChildren(sender);
            if (!visible.isEmpty()) {
                // A node that runs on its own takes its children as optional words.
                boolean optional = handler != null;
                usage.append(optional ? " [" : " <")
                        .append(String.join("|", visible.stream().map(CommandNode::name).toList()))
                        .append(optional ? ']' : '>');
            }
        }
        for (CommandArgument<?> argument : arguments) {
            usage.append(' ').append(argument.usage());
        }
        return usage.toString();
    }

    public static final class Builder {

        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private String permission;
        private boolean everyone;
        private String permissionBase;
        private final List<CommandArgument<?>> arguments = new ArrayList<>();
        private CommandHandler handler;
        private final List<CommandNode> children = new ArrayList<>();
        private String helpBase;

        private Builder(String name) {
            this.name = word(name, "name");
        }

        public Builder aliases(String... aliases) {
            for (String alias : aliases) {
                this.aliases.add(word(alias, "alias"));
            }
            return this;
        }

        /**
         * Sets this node's own permission in place of the one its path gives.
         */
        public Builder permission(String permission) {
            this.permission = Objects.requireNonNull(permission, "permission");
            return this;
        }

        /**
         * Lets everyone run this node, whatever its path.
         */
        public Builder everyone() {
            this.everyone = true;
            return this;
        }

        /**
         * On a root: the permission prefix every node below it takes, such as {@code rakunloots.command}.
         */
        public Builder permissionBase(String base) {
            this.permissionBase = Objects.requireNonNull(base, "base");
            return this;
        }

        public Builder argument(CommandArgument<?> argument) {
            Objects.requireNonNull(argument, "argument");
            if (!arguments.isEmpty()) {
                CommandArgument<?> last = arguments.get(arguments.size() - 1);
                if (last.type().greedy()) {
                    throw new IllegalArgumentException("nothing may follow the greedy argument " + last.name());
                }
                if (last.isOptional() && !argument.isOptional()) {
                    throw new IllegalArgumentException("required argument " + argument.name()
                            + " follows optional " + last.name());
                }
            }
            for (CommandArgument<?> existing : arguments) {
                if (existing.name().equals(argument.name())) {
                    throw new IllegalArgumentException("two arguments named " + argument.name());
                }
            }
            arguments.add(argument);
            return this;
        }

        public Builder handler(CommandHandler handler) {
            this.handler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        public Builder child(CommandNode child) {
            Objects.requireNonNull(child, "child");
            if (child.parent != null) {
                throw new IllegalArgumentException(child.name + " already has a parent");
            }
            children.add(child);
            return this;
        }

        /**
         * Names the base of this node's help keys, in place of {@code command_<path>}.
         */
        public Builder helpKeys(String base) {
            this.helpBase = word(base, "help base");
            return this;
        }

        public CommandNode build() {
            if (handler == null && children.isEmpty()) {
                throw new IllegalStateException(name + " has no handler and no children");
            }
            Set<String> taken = new HashSet<>();
            for (CommandNode child : children) {
                for (String word : words(child)) {
                    if (!taken.add(word.toLowerCase(Locale.ROOT))) {
                        throw new IllegalStateException(name + " has two children named " + word);
                    }
                }
            }
            return new CommandNode(this);
        }

        private static List<String> words(CommandNode node) {
            List<String> words = new ArrayList<>();
            words.add(node.name);
            words.addAll(node.aliases);
            return words;
        }

        private static String word(String value, String what) {
            if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("a " + what + " is one word: '" + value + "'");
            }
            return value;
        }
    }
}
