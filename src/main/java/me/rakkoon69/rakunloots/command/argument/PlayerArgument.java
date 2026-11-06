package me.rakkoon69.rakunloots.command.argument;

import me.rakkoon69.rakunloots.command.ArgumentContext;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A player the server knows, online or not. Completion offers only the players online now, so it never lists every
 * player who ever joined.
 */
public final class PlayerArgument extends NameArgument {

    public static final PlayerArgument INSTANCE = new PlayerArgument();

    private PlayerArgument() {
        super("cmd_type_player", "cmd_reason_player_unknown");
    }

    @Override
    Collection<String> names(ArgumentContext context) {
        return context.sources().playerNames();
    }

    @Override
    public List<String> complete(String partial, ArgumentContext context) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return context.sources().onlinePlayerNames().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
