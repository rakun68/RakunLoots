package me.rakkoon69.rakunloots.storage;

/**
 * How a loot box was summoned. A box keeps it for its whole life and across restarts, so a later rule can tell the
 * boxes the world timer summoned from the others.
 */
public enum SummonKind {
    /**
     * The world timer summoned it.
     */
    WORLD_TIMER,
    /**
     * A player used a summon item.
     */
    SUMMON_ITEM,
    /**
     * An admin summoned it with the summon command.
     */
    ADMIN_COMMAND,
    /**
     * Not recorded: a box stored before summon kinds were kept.
     */
    UNKNOWN;

    /**
     * The kind stored under the given name, or {@link #UNKNOWN} for a name this plugin does not know.
     */
    static SummonKind fromStored(String name) {
        for (SummonKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
