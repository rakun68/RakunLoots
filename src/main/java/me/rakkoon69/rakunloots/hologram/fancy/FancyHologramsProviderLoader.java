package me.rakkoon69.rakunloots.hologram.fancy;

import me.rakkoon69.rakunloots.hologram.BukkitHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramServer;
import me.rakkoon69.rakunloots.hologram.PluginRequirement;
import org.bukkit.Server;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the FancyHolograms provider on a server where it can run, and otherwise a provider that says why it cannot.
 *
 * <p>FancyHolograms 2.12.0 and its API are Java 25 bytecode, so the provider is compiled on the JDK 25 toolchain, to Java
 * 21 bytecode so Paper's plugin remapper can read it, in its own source set, while the core stays Java 17 bytecode. The core names the provider only by {@link #PROVIDER_CLASS} here, and
 * loads it only when FancyHolograms can run: installed and enabled, Paper from Minecraft 1.21.4, on Folia only when
 * FancyHolograms declares {@code folia-supported: true}, and Java 25 or newer. Anywhere else the provider's class is
 * never touched, so the core loads on Java 17.
 */
public final class FancyHologramsProviderLoader {

    /**
     * FancyHolograms on Paper, from Minecraft 1.21.4.
     */
    public static final PluginRequirement REQUIREMENT =
            PluginRequirement.of("FancyHolograms").from("1.21.4").paperOnly();

    /**
     * The Java release the provider's bytecode needs.
     */
    public static final int JAVA = 25;

    /**
     * The provider in the FancyHolograms source set. It has a public static {@code of(Server)}.
     */
    static final String PROVIDER_CLASS = "me.rakkoon69.rakunloots.hologram.fancy.FancyHologramsProvider";

    private FancyHologramsProviderLoader() {
    }

    /**
     * The FancyHolograms provider for the running server, loaded by class name when FancyHolograms can run here, or a
     * provider that cannot run, with the reason.
     */
    public static HologramProvider load(Server server) {
        Objects.requireNonNull(server, "server");
        return load(new BukkitHologramServer(server), Runtime.version().feature(), () -> byName(server));
    }

    /**
     * Checks the server first and builds the provider only when it passes.
     *
     * @param java  the Java release the server runs on
     * @param build builds the provider; never called when the server fails the check
     */
    static HologramProvider load(HologramServer server, int java, Build build) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(build, "build");
        HologramProvider.CanRun canRun = REQUIREMENT.check(server);
        if (!canRun.yes()) {
            return new Unavailable(canRun);
        }
        if (java < JAVA) {
            return new Unavailable(HologramProvider.CanRun.no("the FancyHolograms provider needs Java " + JAVA
                    + ", and this server runs Java " + java));
        }
        try {
            return Objects.requireNonNull(build.build(), "built provider");
        } catch (InvocationTargetException failure) {
            return new Unavailable(cannotLoad(failure.getCause() == null ? failure : failure.getCause()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return new Unavailable(cannotLoad(failure));
        }
    }

    private static HologramProvider.CanRun cannotLoad(Throwable failure) {
        return HologramProvider.CanRun.no("the FancyHolograms provider cannot load: " + failure);
    }

    private static HologramProvider byName(Server server) throws ReflectiveOperationException {
        Class<? extends HologramProvider> type = Class.forName(PROVIDER_CLASS, true,
                FancyHologramsProviderLoader.class.getClassLoader()).asSubclass(HologramProvider.class);
        return type.cast(type.getMethod("of", Server.class).invoke(null, server));
    }

    /**
     * Builds the provider.
     */
    @FunctionalInterface
    interface Build {

        HologramProvider build() throws ReflectiveOperationException;
    }

    /**
     * The FancyHolograms provider on a server where it cannot run. Only {@link #canRun} is ever asked of it.
     */
    private static final class Unavailable implements HologramProvider {

        private final CanRun canRun;

        Unavailable(CanRun canRun) {
            this.canRun = canRun;
        }

        @Override
        public CanRun canRun() {
            return canRun;
        }

        @Override
        public void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
            throw cannotRun();
        }

        @Override
        public void update(UUID hologramId, List<String> lines) {
            throw cannotRun();
        }

        @Override
        public void remove(UUID hologramId) {
            throw cannotRun();
        }

        @Override
        public void removeAll() {
            throw cannotRun();
        }

        /**
         * A provider that cannot run is never chosen.
         */
        private IllegalStateException cannotRun() {
            return new IllegalStateException("the FancyHolograms provider cannot run: "
                    + canRun.refusal().orElseThrow());
        }
    }
}
