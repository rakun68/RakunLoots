package me.rakkoon69.rakunloots.command;

import java.util.Collection;

/**
 * A world name.
 */
public final class WorldArgument extends NameArgument {

    public static final WorldArgument INSTANCE = new WorldArgument();

    private WorldArgument() {
        super("cmd_type_world", "cmd_reason_world_unknown");
    }

    @Override
    Collection<String> names(ArgumentContext context) {
        return context.sources().worldNames();
    }
}
