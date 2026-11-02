package me.rakkoon69.rakunloots.summon;

/**
 * Why a world timer summon did not start.
 */
public enum TimerRefusal {

    /**
     * The world has no entry in {@code worlds.yml}.
     */
    WORLD_UNKNOWN,

    /**
     * The world is not loaded on the server.
     */
    WORLD_NOT_LOADED,

    /**
     * The world and the worlds in {@code also_count_players_from} hold fewer players than
     * {@code minimum_player_to_spawn_loot}.
     */
    BELOW_MINIMUM_PLAYERS,

    /**
     * The world holds {@code loot_spawn_limit} live loot boxes and pending summons, or more.
     */
    AT_SPAWN_LIMIT,

    /**
     * None of the world's loots has a chance above 0 and is in the config.
     */
    NO_LOOT
}
