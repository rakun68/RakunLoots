package me.rakkoon69.rakunloots.command;

import java.util.Collection;

/**
 * A player the server knows, online or not.
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
}
