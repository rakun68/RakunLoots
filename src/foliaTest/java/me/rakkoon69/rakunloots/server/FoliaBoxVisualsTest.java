package me.rakkoon69.rakunloots.server;

import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.visuals.BoxVisualsService;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Call;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Op;
import me.rakkoon69.rakunloots.visuals.FakeLaserLibrary;
import me.rakkoon69.rakunloots.visuals.GlowColor;
import me.rakkoon69.rakunloots.visuals.VisualsWarning;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The box visuals service on the real Folia adapter, over the shipped config and a fake glow and laser: the laser is
 * off on Folia with one low warning and the box runs on, and glow cleanup is skipped when the plugin stops. It lives
 * beside the Folia adapter, whose fake server is package private.
 */
class FoliaBoxVisualsTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final BlockPosition BLOCK = new BlockPosition(2, 64, 3);

    private final ServerAdapter folia = new FoliaServerAdapter(new FakeFoliaServer().plugin());
    private final FakeGlowLibrary glow = new FakeGlowLibrary().drawnForAll();
    private final FakeLaserLibrary laser = new FakeLaserLibrary();
    private final List<VisualsWarning> warnings = new CopyOnWriteArrayList<>();

    private BoxVisualsService service(AtomicInteger laserSetups) {
        return new BoxVisualsService(ShippedHologramConfig.load(), folia, () -> glow, () -> {
            laserSetups.incrementAndGet();
            return laser;
        }, w -> warnings.add((VisualsWarning) w));
    }

    private static BoxEvent event() {
        return new BoxEvent(BOX, "airdrops", WORLD, BLOCK, Optional.empty(), 10, OptionalInt.of(360));
    }

    /**
     * Acceptance: on Folia the laser is unavailable with one low warning through the existing failure path, and the
     * box runs on with its glow.
     */
    @Test
    void onFoliaTheLaserIsOffWithOneLowWarningAndTheBoxRunsOn() {
        AtomicInteger laserSetups = new AtomicInteger();

        BoxVisualsService service = service(laserSetups);
        service.shown(event());
        service.reload(ShippedHologramConfig.load());
        service.ended(event());
        service.shown(event());

        assertFalse(service.laserAvailable());
        assertEquals(0, laserSetups.get());
        assertEquals(List.of(VisualsWarning.Kind.LASER_UNAVAILABLE),
                warnings.stream().map(VisualsWarning::kind).toList());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, warnings.get(0).severity());
        assertEquals(List.of(), laser.starts());
        assertTrue(glow.calls().contains(new Call(Op.SHOW_ALL, WORLD, BLOCK, null, GlowColor.GOLD)));
    }

    /**
     * Acceptance: glow cleanup during region shutdown is skipped, so a glow whose removal would fail on the shutdown
     * thread logs no GLOW_FAILED when the plugin stops.
     */
    @Test
    void glowCleanupAtStopIsSkippedOnFoliaSoNoGlowFailedIsLogged() {
        BoxVisualsService service = service(new AtomicInteger());
        service.shown(event());
        glow.failFutures(new IllegalStateException("Cannot remove entity off its region thread"));
        glow.throwOnCalls(new IllegalStateException("Cannot remove entity off its region thread"));

        service.stop();

        assertFalse(glow.calls().stream().anyMatch(call -> call.op() == Op.HIDE_ALL));
        assertEquals(List.of(VisualsWarning.Kind.LASER_UNAVAILABLE),
                warnings.stream().map(VisualsWarning::kind).toList());
    }
}
