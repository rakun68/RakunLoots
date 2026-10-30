package me.rakkoon69.rakunloots.command;

import java.util.Collection;
import java.util.Map;

/**
 * A player who is online now. A known player who is offline is refused with its own reason.
 */
public final class OnlinePlayerArgument extends NameArgument {

    public static final OnlinePlayerArgument INSTANCE = new OnlinePlayerArgument();

    private OnlinePlayerArgument() {
        super("cmd_type_online_player", "cmd_reason_player_unknown");
    }

    @Override
    Collection<String> names(ArgumentContext context) {
        return context.sources().onlinePlayerNames();
    }

    @Override
    public ParseResult<String> parse(String token, ArgumentContext context) {
        ParseResult<String> online = super.parse(token, context);
        if (online.succeeded()) {
            return online;
        }
        for (String name : context.sources().playerNames()) {
            if (name.equalsIgnoreCase(token)) {
                return ParseResult.fail("cmd_reason_player_offline", Map.of("value", name));
            }
        }
        return online;
    }
}
