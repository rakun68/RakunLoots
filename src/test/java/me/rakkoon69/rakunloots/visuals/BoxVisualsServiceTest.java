package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.GlowColor;
import me.rakkoon69.rakunloots.config.LaserType;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Call;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Op;
import me.rakkoon69.rakunloots.visuals.FakeLaserLibrary.Start;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The box visuals service driven through box observer events over the shipped config, checked by what fake glow and
 * laser libraries were asked to show.
 */
class BoxVisualsServiceTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
    private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c0c0");
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID LATE = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final BlockPosition BLOCK = new BlockPosition(2, 64, 3);
    private static final BlockPosition OTHER_BLOCK = new BlockPosition(8, 64, 3);

    private final FakeServerAdapter server = new FakeServerAdapter(WORLD);
    private final FakeGlowLibrary glow = new FakeGlowLibrary();
    private final FakeLaserLibrary laser = new FakeLaserLibrary();
    private final List<VisualsWarning> warnings = new CopyOnWriteArrayList<>();

    private BoxVisualsService service(ConfigSnapshot config) {
        return new BoxVisualsService(config, server, () -> glow, () -> laser,
                w -> warnings.add((VisualsWarning) w));
    }

    private BoxVisualsService service() {
        return service(ShippedHologramConfig.load());
    }

    /**
     * Acceptance: glow and laser start on shown. The shipped airdrop glows gold for the player in its world, and shows
     * a guardian laser from 50 blocks.
     */
    @Test
    void aShownBoxGlowsForThePlayersInItsWorldAndShowsItsLaser() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = service();

        service.shown(event(BOX, "airdrops", BLOCK));

        assertEquals(List.of(new Call(Op.SHOW, WORLD, BLOCK, PLAYER, GlowColor.GOLD)), glow.calls());
        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.GUARDIAN, 50, 0)), laser.starts());
        assertEquals(List.of(), warnings);
    }

    /**
     * Acceptance: glow and laser stop on out of view, and start again when the box is shown.
     */
    @Test
    void aBoxThatGoesOutOfViewStopsItsGlowAndLaserAndStartsThemWhenShownAgain() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = service();
        service.shown(event(BOX, "airdrops", BLOCK));
        glow.clear();
        laser.clear();

        service.outOfView(event(BOX, "airdrops", BLOCK));

        assertEquals(List.of(new Call(Op.HIDE, WORLD, BLOCK, PLAYER, null)), glow.calls());
        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.GUARDIAN, 50, 0)), laser.stops());

        glow.clear();
        laser.clear();
        service.shown(event(BOX, "airdrops", BLOCK));

        assertEquals(List.of(new Call(Op.SHOW, WORLD, BLOCK, PLAYER, GlowColor.GOLD)), glow.calls());
        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.GUARDIAN, 50, 0)), laser.starts());
    }

    /**
     * Acceptance: glow and laser stop on ended, and an event for a box that already ended does nothing.
     */
    @Test
    void anEndedBoxStopsItsGlowAndLaserOnce() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = service();
        service.shown(event(BOX, "airdrops", BLOCK));
        glow.clear();
        laser.clear();

        service.ended(event(BOX, "airdrops", BLOCK));
        service.ended(event(BOX, "airdrops", BLOCK));

        assertEquals(List.of(new Call(Op.HIDE, WORLD, BLOCK, PLAYER, null)), glow.calls());
        assertEquals(1, laser.stops().size());
    }

    /**
     * Acceptance: a joining player in range sees the glow of the boxes already there; a player in another world does
     * not, and one who changes into the world does.
     */
    @Test
    void aPlayerWhoJoinsOrArrivesInTheBoxsWorldSeesItsGlow() {
        BoxVisualsService service = service();
        service.shown(event(BOX, "airdrops", BLOCK));
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));
        assertEquals(List.of(), glow.calls(), "nobody is online yet");

        service.playerJoined(OTHER_WORLD, LATE);
        assertEquals(List.of(), glow.calls(), "another world");

        service.playerJoined(WORLD, PLAYER);
        assertEquals(List.of(
                        new Call(Op.SHOW, WORLD, BLOCK, PLAYER, GlowColor.GOLD),
                        new Call(Op.SHOW, WORLD, OTHER_BLOCK, PLAYER, GlowColor.GOLD)),
                glow.calls());

        glow.clear();
        service.playerChangedWorld(WORLD, LATE);
        assertEquals(List.of(
                        new Call(Op.SHOW, WORLD, BLOCK, LATE, GlowColor.GOLD),
                        new Call(Op.SHOW, WORLD, OTHER_BLOCK, LATE, GlowColor.GOLD)),
                glow.calls());

        glow.clear();
        service.playerJoined(WORLD, PLAYER);
        assertEquals(List.of(), glow.calls(), "a player who already sees it is not sent it again");
    }

    @Test
    void aPlayerWhoQuitsOrRespawnsSeesTheGlowAgainWhenTheyReturn() {
        BoxVisualsService service = service();
        service.shown(event(BOX, "airdrops", BLOCK));
        service.playerJoined(WORLD, PLAYER);
        glow.clear();

        service.playerQuit(PLAYER);
        assertEquals(List.of(), glow.calls(), "nothing is sent to a player who left");
        service.playerJoined(WORLD, PLAYER);
        assertEquals(List.of(new Call(Op.SHOW, WORLD, BLOCK, PLAYER, GlowColor.GOLD)), glow.calls());

        glow.clear();
        service.playerRespawned(WORLD, PLAYER);
        assertEquals(List.of(new Call(Op.SHOW, WORLD, BLOCK, PLAYER, GlowColor.GOLD)), glow.calls());
    }

    @Test
    void aBoxEndsItsGlowForPlayersWhoJoinedAfterItWasShown() {
        BoxVisualsService service = service();
        service.shown(event(BOX, "airdrops", BLOCK));
        service.playerJoined(WORLD, PLAYER);
        glow.clear();

        service.ended(event(BOX, "airdrops", BLOCK));

        assertEquals(List.of(new Call(Op.HIDE, WORLD, BLOCK, PLAYER, null)), glow.calls());
    }

    /**
     * A loot with no glow and no laser shows neither.
     */
    @Test
    void aLootWithNeitherGlowNorLaserShowsNeither() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = service(ShippedHologramConfig.load("en", Map.of("plain", plainLoot(""))));

        service.shown(event(BOX, "plain", BLOCK));
        service.playerJoined(WORLD, LATE);

        assertEquals(List.of(), glow.calls());
        assertEquals(List.of(), laser.starts());
    }

    /**
     * Acceptance: a failure for one box leaves it running, with one low warning per reload. A glow that throws leaves
     * the laser of the same box, and the later boxes, running.
     */
    @Test
    void aGlowThatThrowsLeavesTheBoxRunningWithOneLowWarningForEachReload() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        glow.throwOnCalls(new IllegalStateException("no packets"));
        ConfigSnapshot config = ShippedHologramConfig.load();
        BoxVisualsService service = service(config);

        service.shown(event(BOX, "airdrops", BLOCK));
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));

        assertEquals(2, laser.starts().size(), "the lasers still run");
        assertEquals(List.of(VisualsWarning.Kind.GLOW_FAILED), warnings.stream().map(VisualsWarning::kind).toList());
        VisualsWarning warning = warnings.get(0);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, warning.severity());
        assertEquals(Optional.of("airdrops"), warning.lootId());
        assertEquals(Optional.of(BOX), warning.boxId());
        assertInstanceOf(IllegalStateException.class, warning.cause().orElseThrow());

        service.reload(config);
        service.ended(event(OTHER_BOX, "airdrops", OTHER_BLOCK));
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));

        assertEquals(2, warnings.size(), "one more for the reload");
    }

    @Test
    void aGlowWhoseFutureFailsLeavesTheBoxRunningWithOneLowWarning() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3), new PlayerPosition(LATE, 3, 64, 3)));
        glow.failFutures(new CompletableFutureFailure("player is gone"));
        BoxVisualsService service = service();

        service.shown(event(BOX, "airdrops", BLOCK));

        assertEquals(1, laser.starts().size());
        assertEquals(List.of(VisualsWarning.Kind.GLOW_FAILED), warnings.stream().map(VisualsWarning::kind).toList());
        assertInstanceOf(CompletableFutureFailure.class, warnings.get(0).cause().orElseThrow());
    }

    @Test
    void aLaserThatFailsToStartLeavesTheBoxGlowingWithOneLowWarningForEachReload() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        laser.failStarts(new ReflectiveOperationException("no beam"));
        ConfigSnapshot config = ShippedHologramConfig.load();
        BoxVisualsService service = service(config);

        service.shown(event(BOX, "airdrops", BLOCK));
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));

        assertEquals(2, glow.calls().size(), "both boxes still glow");
        assertEquals(List.of(VisualsWarning.Kind.LASER_FAILED), warnings.stream().map(VisualsWarning::kind).toList());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, warnings.get(0).severity());
        assertEquals(Optional.of(BOX), warnings.get(0).boxId());

        service.reload(config);
        service.outOfView(event(OTHER_BOX, "airdrops", OTHER_BLOCK));
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));

        assertEquals(2, warnings.size());
    }

    /**
     * Acceptance: a glow library that throws at setup, as GlowingBlocks does on Spigot, leaves the service starting
     * with glow off and one low warning. The laser keeps working.
     */
    @Test
    void aGlowLibraryThatThrowsAtSetupLeavesTheServiceStartingWithGlowOffAndOneLowWarning() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = new BoxVisualsService(ShippedHologramConfig.load(), server, () -> {
            throw new UnsupportedOperationException("The GlowingBlocks util can only be used on a Paper server.");
        }, () -> laser, w -> warnings.add((VisualsWarning) w));

        service.shown(event(BOX, "airdrops", BLOCK));
        service.playerJoined(WORLD, LATE);
        service.ended(event(BOX, "airdrops", BLOCK));

        assertFalse(service.glowAvailable());
        assertTrue(service.laserAvailable());
        assertEquals(List.of(VisualsWarning.Kind.GLOW_UNAVAILABLE), warnings.stream().map(VisualsWarning::kind).toList());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, warnings.get(0).severity());
        assertInstanceOf(UnsupportedOperationException.class, warnings.get(0).cause().orElseThrow());
        assertEquals(List.of(), glow.calls());
        assertEquals(1, laser.starts().size());
        assertEquals(1, laser.stops().size());
    }

    @Test
    void aLaserLibraryThatThrowsAtSetupLeavesTheServiceStartingWithTheLaserOffAndOneLowWarning() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = new BoxVisualsService(ShippedHologramConfig.load(), server, () -> glow, () -> {
            throw new NoClassDefFoundError("fr/skytasul/guardianbeam/Laser");
        }, w -> warnings.add((VisualsWarning) w));

        service.shown(event(BOX, "airdrops", BLOCK));

        assertTrue(service.glowAvailable());
        assertFalse(service.laserAvailable());
        assertEquals(List.of(VisualsWarning.Kind.LASER_UNAVAILABLE), warnings.stream().map(VisualsWarning::kind).toList());
        assertEquals(1, glow.calls().size());
    }

    /**
     * A reload that edits a loot's glow or laser applies from the next tick.
     */
    @Test
    void aReloadThatEditsALootsGlowAppliesFromTheNextTick() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        ConfigSnapshot before = ShippedHologramConfig.load("en", Map.of("plain", plainLoot("""
                glow:
                  enabled: true
                  color: RED
                """)));
        BoxVisualsService service = service(before);
        service.shown(event(BOX, "plain", BLOCK));
        glow.clear();

        service.reload(ShippedHologramConfig.load("en", Map.of("plain", plainLoot("""
                glow:
                  enabled: true
                  color: BLUE
                laser:
                  type: CRYSTAL
                  view_distance: 20
                """))));
        assertEquals(List.of(), glow.calls(), "not before the tick");

        service.ticked(event(BOX, "plain", BLOCK));

        assertEquals(List.of(
                        new Call(Op.HIDE, WORLD, BLOCK, PLAYER, null),
                        new Call(Op.SHOW, WORLD, BLOCK, PLAYER, GlowColor.BLUE)),
                glow.calls());
        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.CRYSTAL, 20, 0)), laser.starts());

        glow.clear();
        service.ticked(event(BOX, "plain", BLOCK));
        assertEquals(List.of(), glow.calls(), "an unchanged loot is left alone");
    }

    /**
     * A reload that changes only a loot's laser start offset restarts the box's laser at the new offset from the next
     * tick, and leaves the glow alone.
     */
    @Test
    void aReloadThatChangesOnlyTheLaserStartOffsetRestartsTheLaserAtTheNewOffset() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        String laserAt0 = "laser:\n  type: GUARDIAN\n  view_distance: 20\n";
        BoxVisualsService service = service(ShippedHologramConfig.load("en", Map.of("plain", plainLoot(laserAt0))));
        service.shown(event(BOX, "plain", BLOCK));
        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.GUARDIAN, 20, 0)), laser.starts());
        laser.clear();

        service.reload(ShippedHologramConfig.load("en", Map.of("plain", plainLoot(laserAt0 + "  start_offset: 7\n"))));
        assertEquals(List.of(), laser.starts(), "not before the tick");
        service.ticked(event(BOX, "plain", BLOCK));

        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.GUARDIAN, 20, 0)), laser.stops());
        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.GUARDIAN, 20, 7)), laser.starts());

        laser.clear();
        service.ticked(event(BOX, "plain", BLOCK));
        assertEquals(List.of(), laser.starts(), "an unchanged offset is left alone");
    }

    @Test
    void aTickForABoxOutOfViewStartsNothing() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = service();
        service.shown(event(BOX, "airdrops", BLOCK));
        service.outOfView(event(BOX, "airdrops", BLOCK));
        glow.clear();
        laser.clear();

        service.ticked(event(BOX, "airdrops", BLOCK));

        assertEquals(List.of(), glow.calls());
        assertEquals(List.of(), laser.starts());
    }

    /**
     * Stop takes every glow and laser away and lets the glow library go; later events show nothing.
     */
    @Test
    void stopTakesEveryGlowAndLaserAwayAndClosesTheGlowLibrary() {
        server.setPlayers(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));
        BoxVisualsService service = service();
        service.shown(event(BOX, "airdrops", BLOCK));
        glow.clear();
        laser.clear();

        service.stop();

        assertEquals(List.of(new Call(Op.HIDE, WORLD, BLOCK, PLAYER, null), new Call(Op.CLOSE, null, null, null, null)),
                glow.calls());
        assertEquals(1, laser.stops().size());

        glow.clear();
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));
        service.playerJoined(WORLD, LATE);
        assertEquals(List.of(), glow.calls());
        assertEquals(List.of(), laser.starts(), "no later laser starts");
    }

    /**
     * A box that ends before the player snapshot arrives is not glowed afterwards.
     */
    @Test
    void aBoxThatEndsBeforeThePlayerSnapshotArrivesIsNotGlowed() {
        CompletableFuture<List<PlayerPosition>> pending = new CompletableFuture<>();
        BoxVisualsService service = new BoxVisualsService(ShippedHologramConfig.load(),
                withPlayers(pending), () -> glow, () -> laser, w -> warnings.add((VisualsWarning) w));

        service.shown(event(BOX, "airdrops", BLOCK));
        service.ended(event(BOX, "airdrops", BLOCK));
        pending.complete(List.of(new PlayerPosition(PLAYER, 2, 64, 3)));

        assertEquals(List.of(), glow.calls());
    }

    /**
     * Acceptance: a glow drawn once for every player, as a block display is, starts once for the box whether or not a
     * player is online, and stops with no player left to hide it for.
     */
    @Test
    void aGlowSharedByAllPlayersStartsOnceForTheBoxAndStopsWithNoPlayerOnline() {
        FakeGlowLibrary shared = new FakeGlowLibrary().drawnForAll();
        BoxVisualsService service = new BoxVisualsService(ShippedHologramConfig.load(), server, () -> shared,
                () -> laser, w -> warnings.add((VisualsWarning) w));

        service.shown(event(BOX, "airdrops", BLOCK));
        service.playerJoined(WORLD, PLAYER);
        service.playerRespawned(WORLD, PLAYER);
        service.playerChangedWorld(WORLD, LATE);

        assertEquals(List.of(new Call(Op.SHOW_ALL, WORLD, BLOCK, null, GlowColor.GOLD)), shared.calls());

        shared.clear();
        service.outOfView(event(BOX, "airdrops", BLOCK));
        service.ended(event(BOX, "airdrops", BLOCK));

        assertEquals(List.of(new Call(Op.HIDE_ALL, WORLD, BLOCK, null, null)), shared.calls());
        assertEquals(List.of(), warnings);
    }

    /**
     * A shared glow follows a reload that edits it, stops at plugin stop before the library closes, and warns once when
     * it fails, as a glow for each player does.
     */
    @Test
    void aGlowSharedByAllPlayersStopsAtStopAndFailsWithOneLowWarning() {
        FakeGlowLibrary shared = new FakeGlowLibrary().drawnForAll();
        BoxVisualsService service = new BoxVisualsService(ShippedHologramConfig.load(), server, () -> shared,
                () -> laser, w -> warnings.add((VisualsWarning) w));
        service.shown(event(BOX, "airdrops", BLOCK));
        shared.clear();

        service.stop();

        assertEquals(List.of(new Call(Op.HIDE_ALL, WORLD, BLOCK, null, null),
                new Call(Op.CLOSE, null, null, null, null)), shared.calls());

        FakeGlowLibrary failing = new FakeGlowLibrary().drawnForAll();
        failing.failFutures(new IllegalStateException("no world"));
        List<VisualsWarning> failures = new CopyOnWriteArrayList<>();
        BoxVisualsService other = new BoxVisualsService(ShippedHologramConfig.load(), server, () -> failing,
                () -> laser, w -> failures.add((VisualsWarning) w));
        other.shown(event(BOX, "airdrops", BLOCK));
        other.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));

        assertEquals(List.of(VisualsWarning.Kind.GLOW_FAILED), failures.stream().map(VisualsWarning::kind).toList());
        assertEquals("LOW", failures.get(0).severity().name());
    }

    /**
     * Acceptance: the client does not create a hostile mob in a peaceful world, so a guardian laser there draws as a
     * crystal laser, high enough for the crystal to clear the box, with one low warning for the loot for each reload.
     */
    @Test
    void aGuardianLaserInAPeacefulWorldDrawsAsACrystalWithOneLowWarningForTheLoot() {
        laser.setPeaceful(WORLD);
        ConfigSnapshot config = ShippedHologramConfig.load();
        BoxVisualsService service = service(config);

        service.shown(event(BOX, "airdrops", BLOCK));
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));

        assertEquals(List.of(
                        new Start(WORLD, BLOCK, LaserType.CRYSTAL, 50, LaserPlacement.CRYSTAL_CLEAR_OFFSET),
                        new Start(WORLD, OTHER_BLOCK, LaserType.CRYSTAL, 50, LaserPlacement.CRYSTAL_CLEAR_OFFSET)),
                laser.starts());
        assertEquals(List.of(VisualsWarning.Kind.GUARDIAN_IN_PEACEFUL),
                warnings.stream().map(VisualsWarning::kind).toList());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, warnings.get(0).severity());
        assertEquals(Optional.of("airdrops"), warnings.get(0).lootId());
        assertEquals(Optional.empty(), warnings.get(0).cause());

        service.reload(config);
        service.outOfView(event(OTHER_BOX, "airdrops", OTHER_BLOCK));
        service.shown(event(OTHER_BOX, "airdrops", OTHER_BLOCK));

        assertEquals(2, warnings.size(), "one more for the reload");
    }

    /**
     * A crystal laser in a peaceful world, and a guardian laser in a world that is not, draw as written.
     */
    @Test
    void onlyAGuardianLaserInAPeacefulWorldChangesType() {
        laser.setPeaceful(OTHER_WORLD);
        BoxVisualsService service = service(ShippedHologramConfig.load("en",
                Map.of("plain", plainLoot("laser:\n  type: GUARDIAN\n  view_distance: 20\n"))));

        service.shown(event(BOX, "plain", BLOCK));

        assertEquals(List.of(new Start(WORLD, BLOCK, LaserType.GUARDIAN, 20, 0)), laser.starts());
        assertEquals(List.of(), warnings);

        laser.clear();
        laser.setPeaceful(WORLD);
        BoxVisualsService crystal = service(ShippedHologramConfig.load("en",
                Map.of("plain", plainLoot("laser:\n  type: CRYSTAL\n  view_distance: 20\n  start_offset: 6\n"))));
        crystal.shown(event(OTHER_BOX, "plain", OTHER_BLOCK));

        assertEquals(List.of(new Start(WORLD, OTHER_BLOCK, LaserType.CRYSTAL, 20, 6)), laser.starts());
        assertEquals(List.of(), warnings);
    }

    private static String plainLoot(String extra) {
        return """
                language: en
                display_name: 'Plain'
                open_mode: drop
                lock: 5
                life: 60
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """ + extra;
    }

    private static BoxEvent event(UUID boxId, String lootId, BlockPosition position) {
        return new BoxEvent(boxId, lootId, WORLD, position, Optional.empty(), 10, OptionalInt.of(360));
    }

    /**
     * The fake server with a player snapshot the test completes itself.
     */
    private ServerAdapter withPlayers(CompletableFuture<List<PlayerPosition>> snapshot) {
        return (ServerAdapter) Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("playerPositions")) {
                        return snapshot;
                    }
                    try {
                        return method.invoke(server, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private static final class CompletableFutureFailure extends RuntimeException {

        private CompletableFutureFailure(String message) {
            super(message);
        }
    }
}
