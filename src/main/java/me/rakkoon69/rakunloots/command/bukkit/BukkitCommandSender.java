package me.rakkoon69.rakunloots.command.bukkit;

import me.rakkoon69.rakunloots.command.CommandButton;
import me.rakkoon69.rakunloots.command.CommandSender;
import me.rakkoon69.rakunloots.server.SenderLocation;
import org.bukkit.ChatColor;

import java.util.Objects;

/**
 * A Bukkit sender seen as a plugin-type {@link CommandSender}. Text keeps {@code &} codes until it is sent.
 */
final class BukkitCommandSender implements CommandSender {

    private final org.bukkit.command.CommandSender sender;

    BukkitCommandSender(org.bukkit.command.CommandSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public java.util.Optional<SenderLocation> location() {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            return java.util.Optional.empty();
        }
        org.bukkit.Location at = player.getLocation();
        return java.util.Optional.of(new SenderLocation(player.getWorld().getUID(), at.getX(), at.getY(), at.getZ()));
    }

    @Override
    public java.util.Optional<java.util.UUID> playerId() {
        return sender instanceof org.bukkit.entity.Player player
                ? java.util.Optional.of(player.getUniqueId())
                : java.util.Optional.empty();
    }

    @Override
    public void send(String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @Override
    public void send(String message, java.util.List<CommandButton> buttons) {
        if (!(sender instanceof org.bukkit.entity.Player player) || buttons.isEmpty()) {
            send(message);
            return;
        }
        net.md_5.bungee.api.chat.ComponentBuilder line = new net.md_5.bungee.api.chat.ComponentBuilder(
                ChatColor.translateAlternateColorCodes('&', message));
        for (CommandButton button : buttons) {
            line.append(" ", net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE)
                    .append(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                            ChatColor.translateAlternateColorCodes('&', button.label())),
                            net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE)
                    .event(new net.md_5.bungee.api.chat.ClickEvent(
                            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, button.command()));
        }
        player.spigot().sendMessage(line.create());
    }
}
