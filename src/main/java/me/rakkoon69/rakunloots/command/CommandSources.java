package me.rakkoon69.rakunloots.command;

import java.util.Collection;

/**
 * The names an argument type completes and checks against. Read each time a command runs or completes, so a reload
 * is seen at once.
 */
public interface CommandSources {

    Collection<String> lootIds();

    Collection<String> worldNames();

    /**
     * The runtime key of the loaded world with this name, or empty when no such world is loaded.
     */
    java.util.Optional<java.util.UUID> worldKey(String worldName);

    /**
     * Every player the server knows, online or not. Includes the online players.
     */
    Collection<String> playerNames();

    Collection<String> onlinePlayerNames();

    /**
     * The id of the player with this name, online or not, or empty when the server does not know the name.
     */
    java.util.Optional<java.util.UUID> playerId(String playerName);
}
