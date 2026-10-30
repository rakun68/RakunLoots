package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Destroy;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Meta;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Packet;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Spawn;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The viewer tracker over a recording packet adapter, as the packet sink, and fake player position snapshots with a
 * hand-stepped scheduler. Movement is checked every 20 ticks from the first hologram on.
 */
class ViewerTrackerTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID HOLOGRAM = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID FAR_HOLOGRAM = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID NETHER_HOLOGRAM = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
    private static final UUID ALEX = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID STEVE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final HologramPosition AT_ORIGIN = new HologramPosition(WORLD, 0.5, 66.0, 0.5);
    private static final HologramPosition FAR_AWAY = new HologramPosition(WORLD, 500.5, 66.0, 0.5);
    private static final HologramPosition IN_NETHER = new HologramPosition(NETHER, 0.5, 66.0, 0.5);
    private static final List<String> LINES = List.of("§6Air Supply", "§7Lock: §c0:10");
    private static final int RANGE = 48;

    private final RecordingPacketAdapter adapter = RecordingPacketAdapter.textDisplays();
    private final PlayerWorlds server = new PlayerWorlds();
    private final ViewerTracker tracker = new ViewerTracker(adapter, server);

    /**
     * Acceptance: a player walking into range gets spawn once, and walking out gets destroy once. Range is the
     * distance from the hologram's position: 47 blocks away is in, 49 is out.
     */
    @Test
    void aPlayerWalkingIntoRangeGetsSpawnOnceAndWalkingOutGetsDestroyOnce() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        server.stand(ALEX, WORLD, 100.5, 66.0, 0.5);
        second();
        assertEquals(List.of(), adapter.sentTo(ALEX), "out of range");

        server.stand(ALEX, WORLD, 47.5, 66.0, 0.5);
        second();
        second();
        second();

        List<Packet> got = adapter.sentTo(ALEX);
        assertEquals(1, got.size(), got.toString());
        Spawn spawn = assertInstanceOf(Spawn.class, got.get(0));
        assertEquals(AT_ORIGIN, spawn.position());
        assertEquals(LINES, spawn.lines());
        assertEquals(Set.of(ALEX), tracker.viewers(HOLOGRAM));

        server.stand(ALEX, WORLD, 20.5, 66.0, 0.5);
        second();
        assertEquals(1, adapter.sentTo(ALEX).size(), "moving within range sends nothing");

        server.stand(ALEX, WORLD, 49.5, 66.0, 0.5);
        second();
        second();

        got = adapter.sentTo(ALEX);
        assertEquals(List.of(spawn, new Destroy(spawn.entityIds())), got);
        assertEquals(Set.of(), tracker.viewers(HOLOGRAM));
    }

    /**
     * Range is three dimensional: a player straight above, farther than the range, does not see it.
     */
    @Test
    void rangeCountsHeightToo() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        server.stand(ALEX, WORLD, 0.5, 66.0 + RANGE + 1, 0.5);
        server.stand(STEVE, WORLD, 0.5, 66.0 + RANGE, 0.5);
        second();

        assertEquals(List.of(), adapter.sentTo(ALEX));
        assertEquals(1, adapter.sentTo(STEVE).size());
    }

    /**
     * Movement is checked once per second, with one snapshot per world that has a hologram, and not before the first
     * hologram.
     */
    @Test
    void movementIsCheckedOncePerSecondWithOneSnapshotPerWorldThatHasAHologram() {
        server.globalScheduler().stepTicks(40);
        assertEquals(List.of(), server.snapshotsAsked(), "nothing to check without a hologram");

        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.add(FAR_HOLOGRAM, FAR_AWAY, LINES, RANGE);
        tracker.add(NETHER_HOLOGRAM, IN_NETHER, LINES, RANGE);
        server.globalScheduler().stepTicks(19);
        assertEquals(List.of(), server.snapshotsAsked());

        server.globalScheduler().stepTicks(1);
        assertEquals(List.of(WORLD, NETHER), server.snapshotsAsked());

        server.globalScheduler().stepTicks(20);
        assertEquals(List.of(WORLD, NETHER, WORLD, NETHER), server.snapshotsAsked());
        assertEquals(1, server.globalScheduler().pending(), "one movement check");
    }

    /**
     * Acceptance: join spawns every hologram in range at once, and no other.
     */
    @Test
    void joinSpawnsEveryHologramInRange() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.add(FAR_HOLOGRAM, FAR_AWAY, LINES, RANGE);
        tracker.add(NETHER_HOLOGRAM, IN_NETHER, LINES, RANGE);
        UUID near = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004");
        tracker.add(near, new HologramPosition(WORLD, 30.5, 70.0, -10.5), List.of("§eNear"), RANGE);

        tracker.joined(WORLD, new PlayerPosition(ALEX, 10.0, 66.0, 0.0));

        assertEquals(Set.of(AT_ORIGIN, new HologramPosition(WORLD, 30.5, 70.0, -10.5)), spawnedPositions(ALEX));
        assertEquals(2, adapter.sentTo(ALEX).size());
        assertEquals(Set.of(ALEX), tracker.viewers(HOLOGRAM));
        assertEquals(Set.of(ALEX), tracker.viewers(near));
        assertEquals(Set.of(), tracker.viewers(FAR_HOLOGRAM));
        assertEquals(Set.of(), tracker.viewers(NETHER_HOLOGRAM));
    }

    /**
     * Acceptance: teleport spawns every hologram in range of where the player lands, and destroys what they left.
     */
    @Test
    void teleportSpawnsEveryHologramInRangeAndDestroysWhatWasLeft() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.add(FAR_HOLOGRAM, FAR_AWAY, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        Spawn origin = (Spawn) adapter.sentTo(ALEX).get(0);
        adapter.clear();

        tracker.teleported(WORLD, new PlayerPosition(ALEX, 490.0, 66.0, 0.0));

        List<Packet> got = adapter.sentTo(ALEX);
        assertEquals(2, got.size(), got.toString());
        assertEquals(new Destroy(origin.entityIds()), got.get(0));
        assertEquals(FAR_AWAY, assertInstanceOf(Spawn.class, got.get(1)).position());
        assertEquals(Set.of(), tracker.viewers(HOLOGRAM));
        assertEquals(Set.of(ALEX), tracker.viewers(FAR_HOLOGRAM));
    }

    /**
     * Acceptance: world change destroys the old world's holograms and spawns the new world's.
     */
    @Test
    void worldChangeDestroysTheOldWorldsAndSpawnsTheNewWorlds() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.add(NETHER_HOLOGRAM, IN_NETHER, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        Spawn overworld = (Spawn) adapter.sentTo(ALEX).get(0);
        assertEquals(AT_ORIGIN, overworld.position());
        adapter.clear();

        tracker.changedWorld(NETHER, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));

        List<Packet> got = adapter.sentTo(ALEX);
        assertEquals(2, got.size(), got.toString());
        assertEquals(new Destroy(overworld.entityIds()), got.get(0));
        assertEquals(IN_NETHER, assertInstanceOf(Spawn.class, got.get(1)).position());
        assertEquals(Set.of(), tracker.viewers(HOLOGRAM));
        assertEquals(Set.of(ALEX), tracker.viewers(NETHER_HOLOGRAM));
    }

    /**
     * Acceptance: a respawn in range of a hologram the player saw spawns it again, and sends no destroy, since the
     * client dropped its entities when it respawned.
     */
    @Test
    void aRespawnInRangeSpawnsTheHologramAgainWithoutDestroy() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        Spawn first = assertInstanceOf(Spawn.class, adapter.sentTo(ALEX).get(0));
        adapter.clear();

        tracker.respawned(WORLD, new PlayerPosition(ALEX, 5.0, 66.0, 0.0));

        assertEquals(List.of(first), adapter.sentTo(ALEX));
        assertEquals(Set.of(ALEX), tracker.viewers(HOLOGRAM));
    }

    /**
     * A respawn out of range of a hologram the player saw sends nothing, and the player no longer sees it.
     */
    @Test
    void aRespawnOutOfRangeSendsNothingAndEndsTheView() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        adapter.clear();

        tracker.respawned(WORLD, new PlayerPosition(ALEX, 300.0, 66.0, 0.0));

        assertEquals(List.of(), adapter.sent());
        assertEquals(Set.of(), tracker.viewers(HOLOGRAM));
        assertTrue(tracker.knows(ALEX));
    }

    /**
     * A respawn in another world spawns that world's holograms in range and sends nothing for the old world's.
     */
    @Test
    void aRespawnInAnotherWorldSpawnsOnlyThatWorldsHolograms() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.add(NETHER_HOLOGRAM, IN_NETHER, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        adapter.clear();

        tracker.respawned(NETHER, new PlayerPosition(ALEX, 3.0, 66.0, 0.0));

        List<Packet> got = adapter.sentTo(ALEX);
        assertEquals(1, got.size(), got.toString());
        assertEquals(IN_NETHER, assertInstanceOf(Spawn.class, got.get(0)).position());
        assertEquals(Set.of(), tracker.viewers(HOLOGRAM));
        assertEquals(Set.of(ALEX), tracker.viewers(NETHER_HOLOGRAM));
    }

    /**
     * Acceptance: quit forgets the player. Nothing is sent to them then, and nothing later: not a line update of a
     * hologram they saw, and not a new hologram where they stood.
     */
    @Test
    void quitForgetsThePlayer() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        adapter.clear();

        tracker.quit(ALEX);
        tracker.update(HOLOGRAM, List.of("§6Air Supply", "§7Lock: §c0:09"));
        tracker.add(FAR_HOLOGRAM, new HologramPosition(WORLD, 1.5, 66.0, 1.5), LINES, RANGE);
        second();

        assertEquals(List.of(), adapter.sent());
        assertFalse(tracker.knows(ALEX));
        assertEquals(Set.of(), tracker.viewers(HOLOGRAM));
    }

    /**
     * Acceptance: a line update reaches only current viewers, as one meta with the new lines.
     */
    @Test
    void aLineUpdateReachesOnlyCurrentViewers() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.add(FAR_HOLOGRAM, FAR_AWAY, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        tracker.joined(WORLD, new PlayerPosition(STEVE, 400.0, 66.0, 0.0));
        Spawn spawn = (Spawn) adapter.sentTo(ALEX).get(0);
        adapter.clear();
        List<String> next = List.of("§6Air Supply", "§7Lock: §c0:09");

        tracker.update(HOLOGRAM, next);

        assertEquals(List.of(new RecordingPacketAdapter.Sent(ALEX, new Meta(spawn.entityIds(), next))),
                adapter.sent());

        tracker.teleported(WORLD, new PlayerPosition(STEVE, 5.0, 66.0, 0.0));
        assertEquals(List.of(new Spawn(spawn.entityIds(), AT_ORIGIN, next)), adapter.sentTo(STEVE),
                "a new viewer sees the new lines");
    }

    /**
     * A line update to a hologram of armor stands with another number of lines destroys the old stands and spawns the
     * new ones for current viewers, since meta cannot add or take an entity.
     */
    @Test
    void aLineCountChangeRespawnsForCurrentViewers() {
        RecordingPacketAdapter stands = RecordingPacketAdapter.armorStands();
        ViewerTracker standTracker = new ViewerTracker(stands, server);
        standTracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        standTracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        Spawn first = (Spawn) stands.sentTo(ALEX).get(0);
        assertEquals(2, first.entityIds().size());
        stands.clear();
        List<String> three = List.of("§6Air Supply", "§7Owner: §eRakkoon", "§7Lock: §c0:09");

        standTracker.update(HOLOGRAM, three);

        List<Packet> got = stands.sentTo(ALEX);
        assertEquals(2, got.size(), got.toString());
        assertEquals(new Destroy(first.entityIds()), got.get(0));
        Spawn respawn = assertInstanceOf(Spawn.class, got.get(1));
        assertEquals(three, respawn.lines());
        assertEquals(3, respawn.entityIds().size());

        stands.clear();
        standTracker.remove(HOLOGRAM);
        assertEquals(List.of(new Destroy(respawn.entityIds())), stands.sentTo(ALEX));
    }

    /**
     * Acceptance: remove reaches every viewer and no one else, and the hologram is forgotten.
     */
    @Test
    void removeReachesEveryViewer() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        UUID far = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000003");
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        tracker.joined(WORLD, new PlayerPosition(STEVE, 10.0, 66.0, 0.0));
        tracker.joined(WORLD, new PlayerPosition(far, 300.0, 66.0, 0.0));
        List<Integer> ids = ((Spawn) adapter.sentTo(ALEX).get(0)).entityIds();
        adapter.clear();

        tracker.remove(HOLOGRAM);

        assertEquals(List.of(new Destroy(ids)), adapter.sentTo(ALEX));
        assertEquals(List.of(new Destroy(ids)), adapter.sentTo(STEVE));
        assertEquals(List.of(), adapter.sentTo(far));
        tracker.teleported(WORLD, new PlayerPosition(far, 0.0, 66.0, 0.0));
        tracker.update(HOLOGRAM, LINES);
        assertEquals(2, adapter.sent().size(), "a removed hologram spawns and updates for no one");
    }

    /**
     * Remove all destroys every hologram for its viewers and stops the movement check.
     */
    @Test
    void removeAllDestroysEveryHologramAndStopsTheCheck() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.add(NETHER_HOLOGRAM, IN_NETHER, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        tracker.joined(NETHER, new PlayerPosition(STEVE, 0.0, 66.0, 0.0));
        List<Integer> overworld = ((Spawn) adapter.sentTo(ALEX).get(0)).entityIds();
        List<Integer> nether = ((Spawn) adapter.sentTo(STEVE).get(0)).entityIds();
        adapter.clear();

        tracker.removeAll();
        server.globalScheduler().stepTicks(40);

        assertEquals(List.of(new Destroy(overworld)), adapter.sentTo(ALEX));
        assertEquals(List.of(new Destroy(nether)), adapter.sentTo(STEVE));
        assertEquals(List.of(), server.snapshotsAsked());
        assertEquals(0, server.globalScheduler().pending());
    }

    /**
     * A snapshot adds players it lists, such as those online before the listener, and forgets a player of that world
     * it no longer lists, destroying what they saw.
     */
    @Test
    void aSnapshotAddsListedPlayersAndForgetsThoseNoLongerListed() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        server.stand(ALEX, WORLD, 0.0, 66.0, 0.0);
        second();
        List<Integer> ids = ((Spawn) adapter.sentTo(ALEX).get(0)).entityIds();

        server.leave(ALEX);
        second();

        assertEquals(new Destroy(ids), adapter.sentTo(ALEX).get(1));
        assertFalse(tracker.knows(ALEX));
    }

    /**
     * A snapshot asked for before a teleport never moves the player back: the teleport's spawns stay, and the next
     * snapshot is followed again.
     */
    @Test
    void aSnapshotOlderThanAnEventDoesNotUndoIt() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        server.stand(ALEX, WORLD, 300.0, 66.0, 0.0);
        server.holdSnapshots();
        second();

        tracker.teleported(WORLD, new PlayerPosition(ALEX, 0.0, 66.0, 0.0));
        server.stand(ALEX, WORLD, 0.0, 66.0, 0.0);
        server.releaseSnapshots();

        assertEquals(1, adapter.sentTo(ALEX).size(), adapter.sentTo(ALEX).toString());
        assertEquals(Set.of(ALEX), tracker.viewers(HOLOGRAM));

        server.stand(ALEX, WORLD, 300.0, 66.0, 0.0);
        second();
        assertInstanceOf(Destroy.class, adapter.sentTo(ALEX).get(1));
    }

    /**
     * A snapshot asked for before a quit never adds the player back: they stay unknown, and a hologram added later
     * sends them nothing.
     */
    @Test
    void aSnapshotOlderThanAQuitDoesNotAddThePlayerBack() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        server.stand(ALEX, WORLD, 0.0, 66.0, 0.0);
        server.holdSnapshots();
        second();

        tracker.quit(ALEX);
        server.releaseSnapshots();
        tracker.add(FAR_HOLOGRAM, new HologramPosition(WORLD, 1.5, 66.0, 1.5), LINES, RANGE);

        assertFalse(tracker.knows(ALEX));
        assertEquals(List.of(), adapter.sentTo(ALEX));
        assertEquals(Set.of(), tracker.viewers(HOLOGRAM));
        assertEquals(Set.of(), tracker.viewers(FAR_HOLOGRAM));
    }

    /**
     * A join after a quit clears it, so an older snapshot then still lists the player as it did before, and a snapshot
     * asked for after the quit is followed.
     */
    @Test
    void aJoinAfterAQuitClearsIt() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        server.holdSnapshots();
        second();
        tracker.quit(ALEX);
        server.releaseSnapshots();
        tracker.joined(WORLD, new PlayerPosition(ALEX, 300.0, 66.0, 0.0));

        server.stand(ALEX, WORLD, 0.0, 66.0, 0.0);
        second();

        assertEquals(Set.of(ALEX), tracker.viewers(HOLOGRAM));
    }

    /**
     * A snapshot asked for before a teleport does not forget the player it no longer lists, and still adds the
     * players it does list.
     */
    @Test
    void aSnapshotOlderThanAnEventDoesNotForgetThatPlayer() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);
        tracker.joined(WORLD, new PlayerPosition(ALEX, 300.0, 66.0, 0.0));
        server.stand(STEVE, WORLD, 0.0, 66.0, 0.0);
        server.holdSnapshots();
        second();

        tracker.teleported(WORLD, new PlayerPosition(ALEX, 1.0, 66.0, 0.0));
        server.releaseSnapshots();

        assertEquals(Set.of(ALEX, STEVE), tracker.viewers(HOLOGRAM),
                "the snapshot does not list Alex, but he teleported after it was asked for");
    }

    @Test
    void addRefusesWhatTheProviderContractForbids() {
        tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE);

        assertThrows(IllegalStateException.class, () -> tracker.add(HOLOGRAM, AT_ORIGIN, LINES, RANGE));
        assertThrows(IllegalArgumentException.class, () -> tracker.add(FAR_HOLOGRAM, FAR_AWAY, List.of(), RANGE));
        assertThrows(IllegalArgumentException.class, () -> tracker.add(FAR_HOLOGRAM, FAR_AWAY, LINES, 0));
        assertThrows(IllegalArgumentException.class, () -> tracker.update(HOLOGRAM, List.of()));
        assertTrue(tracker.viewers(FAR_HOLOGRAM).isEmpty());
    }

    private void second() {
        server.globalScheduler().stepTicks(ViewerTracker.CHECK_PERIOD_TICKS);
    }

    private Set<HologramPosition> spawnedPositions(UUID playerId) {
        return Set.copyOf(adapter.sentTo(playerId).stream()
                .filter(Spawn.class::isInstance)
                .map(packet -> ((Spawn) packet).position())
                .toList());
    }
}
