package me.rakkoon69.rakunloots.server;

/**
 * Where a fake world's chunk stands. Loaded chunks are also generated.
 */
enum ChunkState {
    UNGENERATED,
    GENERATED,
    LOADED
}
