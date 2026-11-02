package me.rakkoon69.rakunloots.visuals;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.GlowColor;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The glow library is picked by server version: a block display from 1.19.4, GlowingBlocks below (ADR 0016).
 */
class GlowLibrariesTest {

    /**
     * A glow library the pick builds by name, as it builds the real block display glow.
     */
    public static final class NamedGlow implements GlowLibrary {

        static Plugin builtWith;
        static ServerAdapter builtOn;

        public NamedGlow(Plugin plugin, ServerAdapter server) {
            builtWith = plugin;
            builtOn = server;
        }

        @Override
        public CompletableFuture<Void> show(UUID worldKey, BlockPosition position, UUID playerId, GlowColor color) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> hide(UUID worldKey, BlockPosition position, UUID playerId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }

    private static GlowLibraries.Probes probes(boolean blockDisplay, Class<?> glow) {
        return new GlowLibraries.Probes() {
            @Override
            public boolean blockDisplay() {
                return blockDisplay;
            }

            @Override
            public Class<?> loadBlockDisplayGlow() throws ClassNotFoundException {
                if (glow == null) {
                    throw new ClassNotFoundException("BlockDisplayGlow");
                }
                return glow;
            }
        };
    }

    private Plugin plugin;
    private final FakeServerAdapter server = new FakeServerAdapter(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        StreamableServerMock.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Acceptance: on 1.19.4 and newer, glow is the block display library, built with the plugin and the server adapter.
     */
    @Test
    void aServerWithBlockDisplaysGetsTheBlockDisplayGlow() {
        GlowLibrary glow = GlowLibraries.create(plugin, server, probes(true, NamedGlow.class));

        assertInstanceOf(NamedGlow.class, glow);
        assertSame(plugin, NamedGlow.builtWith);
        assertSame(server, NamedGlow.builtOn);
    }

    /**
     * Acceptance: below 1.19.4, glow is GlowingBlocks, which throws at setup on a server that is not Paper, so the
     * service turns glow off with one low warning as before.
     */
    @Test
    void aServerWithoutBlockDisplaysGetsGlowingBlocks() {
        AtomicBoolean asked = new AtomicBoolean();
        GlowLibraries.Probes noDisplays = new GlowLibraries.Probes() {
            @Override
            public boolean blockDisplay() {
                return false;
            }

            @Override
            public Class<?> loadBlockDisplayGlow() {
                asked.set(true);
                return NamedGlow.class;
            }
        };

        assertThrows(Throwable.class, () -> GlowLibraries.create(plugin, server, noDisplays),
                "GlowingBlocks is built, and it cannot be set up on MockBukkit");
        assertFalse(asked.get(), "the block display glow is never loaded below 1.19.4");
    }

    /**
     * A block display glow that is missing from the JAR fails the pick with a cause, so glow is off with one low
     * warning, as for any library that cannot be set up.
     */
    @Test
    void aBlockDisplayGlowMissingFromTheJarFailsThePick() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> GlowLibraries.create(plugin, server, probes(true, null)));

        assertInstanceOf(ClassNotFoundException.class, failure.getCause());
    }

    /**
     * The real probe, on the 1.18.2 API this test source set runs, finds no block display and so picks GlowingBlocks.
     */
    @Test
    void theRealProbeOnThe1182ApiPicksGlowingBlocks() {
        assertFalse(hasBlockDisplayClass(), "the 1.18.2 API has no BlockDisplay");
        assertThrows(Throwable.class, () -> GlowLibraries.create(plugin, server),
                "GlowingBlocks is built, and it cannot be set up on MockBukkit");
    }

    private static boolean hasBlockDisplayClass() {
        try {
            Class.forName("org.bukkit.entity.BlockDisplay");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
