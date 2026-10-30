package me.rakkoon69.rakunloots.visuals;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The real glow pick and GuardianBeam wrapper, set up on a server that cannot run them: setup throws, the service
 * still starts, and the visual is off with one low warning. MockBukkit has no real Minecraft server behind it for
 * GlowingEntities to map, as Spigot has none of the Paper classes GlowingBlocks needs.
 */
class RealLibrariesSetupTest {

    private final List<VisualsWarning> warnings = new CopyOnWriteArrayList<>();
    private Plugin plugin;

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
     * Acceptance: GlowingBlocks throws at setup on a server that cannot run it, and enable goes on with glow off.
     */
    @Test
    void theRealGlowLibraryThatCannotSetUpTurnsGlowOffWithOneLowWarning() {
        FakeServerAdapter server = new FakeServerAdapter(UUID.randomUUID());

        BoxVisualsService service = new BoxVisualsService(ShippedHologramConfig.load(), server,
                () -> GlowLibraries.create(plugin, server), () -> new FakeLaserLibrary(),
                w -> warnings.add((VisualsWarning) w));

        assertFalse(service.glowAvailable());
        assertEquals(List.of(VisualsWarning.Kind.GLOW_UNAVAILABLE), warnings.stream().map(VisualsWarning::kind).toList());
        assertEquals("LOW", warnings.get(0).severity().name());
    }
}
