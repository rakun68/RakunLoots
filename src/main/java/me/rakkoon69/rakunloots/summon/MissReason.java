package me.rakkoon69.rakunloots.summon;

/**
 * Why a summon search found no spot.
 */
public enum MissReason {
    WORLD_NOT_LOADED,
    UNKNOWN_LOOT,
    ATTEMPTS_USED_UP,
    EMPTY_HEIGHT_RANGE,
    NO_PLAYERS,
    NO_LOADED_CHUNKS_IN_RING
}
