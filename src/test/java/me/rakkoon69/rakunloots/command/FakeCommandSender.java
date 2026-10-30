package me.rakkoon69.rakunloots.command;

import me.rakkoon69.rakunloots.server.SenderLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A sender that holds the permissions it was given and records what it was sent.
 */
final class FakeCommandSender implements CommandSender {

    private final String name;
    private final Set<String> permissions = new HashSet<>();
    private final List<String> messages = new ArrayList<>();
    private final List<CommandButton> buttons = new ArrayList<>();
    private SenderLocation location;
    private java.util.UUID playerId;

    FakeCommandSender(String name, String... permissions) {
        this.name = name;
        this.permissions.addAll(List.of(permissions));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    FakeCommandSender at(SenderLocation location) {
        this.location = location;
        return this;
    }

    @Override
    public java.util.Optional<SenderLocation> location() {
        return java.util.Optional.ofNullable(location);
    }

    /**
     * Makes the sender a player with this id.
     */
    FakeCommandSender asPlayer(java.util.UUID playerId) {
        this.playerId = playerId;
        return this;
    }

    @Override
    public java.util.Optional<java.util.UUID> playerId() {
        return java.util.Optional.ofNullable(playerId);
    }

    @Override
    public void send(String message) {
        messages.add(message);
    }

    /**
     * Records the message like {@link #send(String)}, and the buttons with it, as a sender that can click does.
     */
    @Override
    public void send(String message, List<CommandButton> buttons) {
        messages.add(message);
        this.buttons.addAll(buttons);
    }

    /**
     * Every button sent, in order.
     */
    List<CommandButton> buttons() {
        return List.copyOf(buttons);
    }

    List<String> messages() {
        return List.copyOf(messages);
    }
}
