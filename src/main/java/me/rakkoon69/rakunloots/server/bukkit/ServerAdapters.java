package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Picks the server adapter for the server the plugin runs on.
 */
public final class ServerAdapters {

    private ServerAdapters() {
    }

    /**
     * Picks the adapter for this server. Folia is picked when its marker class exists, and its adapter is then
     * created by class name, so no other server loads a Folia class. Otherwise the Bukkit adapter, with the chunk
     * loader for this server.
     *
     * @param plugin the plugin the adapter schedules for
     * @throws IllegalStateException when the server is Folia and the Folia adapter cannot be created
     */
    public static ServerAdapter select(Plugin plugin) {
        return select(plugin, new ClassLoaderProbes(ServerAdapters.class.getClassLoader()));
    }

    static ServerAdapter select(Plugin plugin, ServerProbes probes) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(probes, "probes");
        if (probes.folia()) {
            return folia(plugin, probes);
        }
        return new BukkitServerAdapter(plugin, chunkLoader(plugin, probes));
    }

    /**
     * Picks Paper's chunk loader when {@code World#getChunkAtAsync} exists, which covers Paper's forks. Otherwise
     * Spigot's.
     */
    static ChunkLoader chunkLoader(Plugin plugin, ServerProbes probes) {
        if (probes.paperChunkCall()) {
            return new PaperChunkLoader();
        }
        return new SpigotChunkLoader(plugin);
    }

    private static ServerAdapter folia(Plugin plugin, ServerProbes probes) {
        try {
            return probes.loadFoliaAdapter()
                    .asSubclass(ServerAdapter.class)
                    .getConstructor(Plugin.class)
                    .newInstance(plugin);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException("cannot create the Folia server adapter", failure);
        }
    }

    /**
     * Probes the plugin's own class loader, which sees the server's classes and the Folia adapter packed in the JAR.
     */
    private static final class ClassLoaderProbes implements ServerProbes {

        /**
         * Present only on Folia. Folia's scheduler classes are on plain Paper too, so they do not identify it.
         */
        private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

        /**
         * The Folia adapter lives in its own source set, compiled against folia-api, so it is loaded only by name.
         */
        private static final String FOLIA_ADAPTER = "me.rakkoon69.rakunloots.server.bukkit.FoliaServerAdapter";

        private final ClassLoader loader;

        ClassLoaderProbes(ClassLoader loader) {
            this.loader = loader;
        }

        /**
         * Looks the marker up without initializing it.
         */
        @Override
        public boolean folia() {
            try {
                Class.forName(FOLIA_MARKER, false, loader);
                return true;
            } catch (ClassNotFoundException | LinkageError absent) {
                return false;
            }
        }

        /**
         * Looks for the overload the Paper chunk loader calls. Paper declares it on the 1.18 API; Spigot has none.
         */
        @Override
        public boolean paperChunkCall() {
            try {
                World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
                return true;
            } catch (NoSuchMethodException absent) {
                return false;
            }
        }

        @Override
        public Class<?> loadFoliaAdapter() throws ClassNotFoundException {
            return Class.forName(FOLIA_ADAPTER, true, loader);
        }
    }
}
