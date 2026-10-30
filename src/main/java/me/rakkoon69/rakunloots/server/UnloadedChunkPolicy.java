package me.rakkoon69.rakunloots.server;

/**
 * What a chunk read does with a chunk that is not loaded. The caller chooses it.
 */
public enum UnloadedChunkPolicy {

    /**
     * Never load the chunk. An unloaded chunk is a miss.
     */
    SKIP,

    /**
     * Load the chunk only when it is already generated. An ungenerated chunk is a miss and stays ungenerated.
     */
    LOAD,

    /**
     * Load the chunk, and generate it when it is missing.
     */
    GENERATE
}
