package me.rakkoon69.rakunloots.command.argument;

import me.rakkoon69.rakunloots.command.ArgumentContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The name of a world, or of a player who is online. A name that is both a world and a player counts as the world.
 */
public final class WorldOrPlayerArgument extends NameArgument {

    public static final WorldOrPlayerArgument INSTANCE = new WorldOrPlayerArgument();

    private WorldOrPlayerArgument() {
        super("cmd_type_world_or_player", "cmd_reason_world_or_player_unknown");
    }

    @Override
    Collection<String> names(ArgumentContext context) {
        List<String> names = new ArrayList<>(context.sources().worldNames());
        names.addAll(context.sources().onlinePlayerNames());
        return names;
    }
}
