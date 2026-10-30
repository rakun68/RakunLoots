package me.rakkoon69.rakunloots.command;

import me.rakkoon69.rakunloots.server.SenderLocation;

/**
 * Who typed a command, in plugin types. Text sent to it keeps its {@code &} color codes; the Bukkit side translates
 * them.
 */
public interface CommandSender {

    String name();

    boolean hasPermission(String permission);

    void send(String message);

    /**
     * Sends a message with clickable buttons after it. A sender that cannot click shows the message alone.
     */
    default void send(String message, java.util.List<CommandButton> buttons) {
        send(message);
    }

    /**
     * Where the sender stands, or empty when it is not a player, such as the console.
     */
    java.util.Optional<SenderLocation> location();

    /**
     * The player's id, or empty when the sender is not a player, such as the console.
     */
    java.util.Optional<java.util.UUID> playerId();
}
