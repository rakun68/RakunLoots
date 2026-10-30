package me.rakkoon69.rakunloots.hologram;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;

/**
 * Answers a hologram provider's questions from the running server. It tells Folia and Paper apart as server adapter
 * selection does.
 */
public final class BukkitHologramServer implements HologramServer {

    /**
     * Present only on Folia.
     */
    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private final Server server;

    public BukkitHologramServer(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean folia() {
        try {
            Class.forName(FOLIA_MARKER, false, BukkitHologramServer.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /**
     * Looks for Paper's async chunk call, which Paper declares on the 1.18 API and Spigot never has.
     */
    @Override
    public boolean paper() {
        try {
            World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    /**
     * Read from the Bukkit version, such as {@code 1.20.4-R0.1-SNAPSHOT}.
     */
    @Override
    public MinecraftVersion minecraftVersion() {
        return MinecraftVersion.parse(server.getBukkitVersion());
    }

    @Override
    public Optional<PluginState> plugin(String name) {
        Objects.requireNonNull(name, "name");
        Plugin plugin = server.getPluginManager().getPlugin(name);
        if (plugin == null) {
            return Optional.empty();
        }
        return Optional.of(new PluginState(plugin.isEnabled(), foliaSupported(plugin)));
    }

    /**
     * Reads {@code folia-supported} through the plugin's meta, which Paper plugins have, or else its description.
     * Neither accessor exists on the 1.18 API, so both are looked up by name; a server without them is not Folia.
     */
    static boolean foliaSupported(Plugin plugin) {
        for (String accessor : new String[]{"getPluginMeta", "getDescription"}) {
            try {
                Object meta = publicMethod(plugin.getClass(), accessor).invoke(plugin);
                if (meta == null) {
                    continue;
                }
                return Boolean.TRUE.equals(publicMethod(meta.getClass(), "isFoliaSupported").invoke(meta));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
                // Try the next accessor.
            }
        }
        return false;
    }

    /**
     * The method as a public type declares it, since the object's own class, such as a server's plugin meta, may not
     * be public.
     */
    private static Method publicMethod(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getMethod(name);
        if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            return method;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> face : current.getInterfaces()) {
                if (Modifier.isPublic(face.getModifiers())) {
                    try {
                        return face.getMethod(name);
                    } catch (NoSuchMethodException notHere) {
                        // Look further.
                    }
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + " on a public type");
    }
}
