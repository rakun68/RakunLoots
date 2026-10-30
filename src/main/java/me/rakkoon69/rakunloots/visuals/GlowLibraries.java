package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.server.ServerAdapter;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Picks the glow library for the server's Minecraft version (ADR 0016). From 1.19.4, where block display entities
 * exist, glow is a block display, which has no hitbox, so a click reaches the box. Below 1.19.4 it is GlowingBlocks,
 * which is Paper only.
 */
public final class GlowLibraries {

    private GlowLibraries() {
    }

    /**
     * What the pick asks about the server. A test fakes it.
     */
    interface Probes {

        /**
         * Whether this server has block display entities, which are Minecraft 1.19.4 and newer.
         */
        boolean blockDisplay();

        /**
         * Loads the block display glow by name: it compiles against a newer API than the core, so it is loaded only
         * where block displays exist.
         *
         * @throws ClassNotFoundException when the class is missing from the JAR
         */
        Class<?> loadBlockDisplayGlow() throws ClassNotFoundException;
    }

    /**
     * Builds the glow library for this server.
     *
     * @throws RuntimeException when the library cannot be built, such as GlowingBlocks on a server that is not Paper;
     *                          the box visuals service turns glow off with one low warning
     */
    public static GlowLibrary create(Plugin plugin, ServerAdapter server) {
        return create(plugin, server, new ClassLoaderProbes(GlowLibraries.class.getClassLoader()));
    }

    static GlowLibrary create(Plugin plugin, ServerAdapter server, Probes probes) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(probes, "probes");
        if (!probes.blockDisplay()) {
            return new GlowingBlocksGlow(plugin, server);
        }
        try {
            return probes.loadBlockDisplayGlow()
                    .asSubclass(GlowLibrary.class)
                    .getConstructor(Plugin.class, ServerAdapter.class)
                    .newInstance(plugin, server);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("cannot create the block display glow", failure.getCause());
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException("cannot create the block display glow", failure);
        }
    }

    /**
     * Probes the plugin's own class loader, which sees the server's classes and the block display glow packed in the JAR.
     */
    private static final class ClassLoaderProbes implements Probes {

        private static final String BLOCK_DISPLAY = "org.bukkit.entity.BlockDisplay";
        private static final String BLOCK_DISPLAY_GLOW = "me.rakkoon69.rakunloots.visuals.BlockDisplayGlow";

        private final ClassLoader loader;

        ClassLoaderProbes(ClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public boolean blockDisplay() {
            try {
                Class.forName(BLOCK_DISPLAY, false, loader);
                return true;
            } catch (ClassNotFoundException | LinkageError absent) {
                return false;
            }
        }

        @Override
        public Class<?> loadBlockDisplayGlow() throws ClassNotFoundException {
            return Class.forName(BLOCK_DISPLAY_GLOW, true, loader);
        }
    }
}
