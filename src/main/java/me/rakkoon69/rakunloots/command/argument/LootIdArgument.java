package me.rakkoon69.rakunloots.command.argument;

import me.rakkoon69.rakunloots.command.ArgumentContext;

import java.util.Collection;

/**
 * A loot id from the loaded loots.
 */
public final class LootIdArgument extends NameArgument {

    public static final LootIdArgument INSTANCE = new LootIdArgument();

    private LootIdArgument() {
        super("cmd_type_loot", "cmd_reason_loot_unknown");
    }

    @Override
    Collection<String> names(ArgumentContext context) {
        return context.sources().lootIds();
    }
}
