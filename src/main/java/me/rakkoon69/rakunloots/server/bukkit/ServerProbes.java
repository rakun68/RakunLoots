package me.rakkoon69.rakunloots.server.bukkit;

/**
 * What adapter selection asks about the server. A test fakes it to pick any adapter.
 */
interface ServerProbes {

    /**
     * Whether the server is Folia.
     */
    boolean folia();

    /**
     * Whether the server has Paper's async chunk call.
     */
    boolean paperChunkCall();

    /**
     * Loads the Folia adapter class by name. Called only on Folia, so no other server loads a Folia class.
     *
     * @throws ClassNotFoundException when the class is missing from the JAR
     */
    Class<?> loadFoliaAdapter() throws ClassNotFoundException;
}
