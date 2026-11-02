package me.rakkoon69.rakunloots.command.bukkit;

import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.CommandRegistry;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Puts the roots of a {@link CommandRegistry} in the server's command map under their names and aliases, so no
 * {@code plugin.yml} command entry is needed. {@link #unregisterAll()} takes them out again.
 */
public final class BukkitCommandRegistrar {

    private final Plugin plugin;
    private final CommandRegistry registry;
    private final CommandMap commandMap;
    private final List<RootCommand> registered = new ArrayList<>();

    public BukkitCommandRegistrar(Plugin plugin, CommandRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.commandMap = commandMap(plugin);
    }

    /**
     * Adds the root to the registry and to the server's command map.
     */
    public void register(CommandNode root) {
        registry.register(root);
        RootCommand command = new RootCommand(root, registry);
        commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
        registered.add(command);
    }

    public void unregisterAll() {
        Map<String, Command> known = knownCommands(commandMap);
        for (RootCommand command : registered) {
            command.unregister(commandMap);
            known.values().removeIf(entry -> entry == command);
        }
        registered.clear();
    }

    /**
     * The command map's name-to-command table. Only Paper's API declares {@code getKnownCommands} on the
     * {@link CommandMap} interface, so a direct call throws {@link NoSuchMethodError} on Spigot. Spigot's command map
     * class has the same public method, so it is called on the map's own class.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(CommandMap commandMap) {
        try {
            return (Map<String, Command>) commandMap.getClass().getMethod("getKnownCommands").invoke(commandMap);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("this server's command map gives no known commands", failure);
        }
    }

    /**
     * Paper and Spigot both give the command map through the server object, though only Paper's API declares it.
     */
    private static CommandMap commandMap(Plugin plugin) {
        Object server = plugin.getServer();
        try {
            return (CommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("this server gives no command map", failure);
        }
    }

    private static final class RootCommand extends Command {

        private final CommandRegistry registry;

        RootCommand(CommandNode root, CommandRegistry registry) {
            super(root.name());
            setAliases(root.aliases());
            this.registry = registry;
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
            registry.dispatch(new BukkitCommandSender(sender), line(label, args));
            return true;
        }

        @Override
        public List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
            return registry.complete(new BukkitCommandSender(sender), line(alias, args));
        }

        private static String line(String label, String[] args) {
            return label + " " + String.join(" ", args);
        }
    }
}
