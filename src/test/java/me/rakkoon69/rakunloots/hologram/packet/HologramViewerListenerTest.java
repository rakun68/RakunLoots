package me.rakkoon69.rakunloots.hologram.packet;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Destroy;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Packet;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Spawn;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The viewer listener, registered on a MockBukkit server, carries join, quit, teleport, and world change to a real
 * viewer tracker, seen by what the tracker sends through a recording packet adapter and whom it knows. Each world has
 * one hologram at its origin with a view range of 48.
 */
class HologramViewerListenerTest {

    private static final int RANGE = 48;

    private ServerMock server;
    private WorldMock overworld;
    private WorldMock nether;
    private PlayerMock player;
    private final RecordingPacketAdapter adapter = RecordingPacketAdapter.textDisplays();
    private final ViewerTracker tracker = new ViewerTracker(adapter, new PlayerWorlds());
    private final UUID overworldHologram = UUID.randomUUID();
    private final UUID netherHologram = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("world");
        nether = server.addSimpleWorld("world_nether");
        player = server.addPlayer();
        tracker.add(overworldHologram, new HologramPosition(overworld.getUID(), 0.5, 66.0, 0.5), List.of("§6O"), RANGE);
        tracker.add(netherHologram, new HologramPosition(nether.getUID(), 0.5, 66.0, 0.5), List.of("§cN"), RANGE);
        server.getPluginManager().registerEvents(new HologramViewerListener(tracker), MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Acceptance: a join reaches the tracker with the player's world and position, so the hologram there spawns.
     */
    @Test
    void aJoinReachesTheTrackerWithTheWorldAndPosition() {
        player.setLocation(new Location(overworld, 10.0, 66.0, 0.0));

        fire(new PlayerJoinEvent(player, "joined"));

        assertEquals(Set.of(player.getUniqueId()), tracker.viewers(overworldHologram));
        assertEquals(Set.of(), tracker.viewers(netherHologram));
        assertEquals(1, sent().size());
    }

    /**
     * Acceptance: a quit reaches the tracker, which forgets the player.
     */
    @Test
    void aQuitReachesTheTrackerWhichForgetsThePlayer() {
        player.setLocation(new Location(overworld, 10.0, 66.0, 0.0));
        fire(new PlayerJoinEvent(player, "joined"));
        assertTrue(tracker.knows(player.getUniqueId()));

        fire(new PlayerQuitEvent(player, "left"));

        assertFalse(tracker.knows(player.getUniqueId()));
        assertEquals(Set.of(), tracker.viewers(overworldHologram));
    }

    /**
     * Acceptance: a teleport within a world reaches the tracker with where the player lands. A cancelled teleport,
     * and one to another world, make no call; the world change that follows the latter carries it.
     */
    @Test
    void aTeleportReachesTheTrackerWithWhereThePlayerLands() {
        Location far = new Location(overworld, 300.0, 66.0, 0.0);
        player.setLocation(far);
        fire(new PlayerJoinEvent(player, "joined"));
        assertEquals(List.of(), sent());

        PlayerTeleportEvent cancelled = new PlayerTeleportEvent(player, far, new Location(overworld, 1.0, 66.0, 0.0));
        cancelled.setCancelled(true);
        fire(cancelled);
        fire(new PlayerTeleportEvent(player, far, new Location(nether, 1.0, 66.0, 0.0)));
        assertEquals(List.of(), sent());

        fire(new PlayerTeleportEvent(player, far, new Location(overworld, 1.0, 66.0, 0.0)));

        assertEquals(Set.of(player.getUniqueId()), tracker.viewers(overworldHologram));
        assertEquals(1, sent().size());
    }

    /**
     * Acceptance: a world change reaches the tracker with the player's new world and position, so the old world's
     * hologram is destroyed and the new world's spawns.
     */
    @Test
    void aWorldChangeReachesTheTrackerWithTheNewWorld() {
        player.setLocation(new Location(overworld, 0.0, 66.0, 0.0));
        fire(new PlayerJoinEvent(player, "joined"));
        Spawn old = assertInstanceOf(Spawn.class, sent().get(0));

        player.setLocation(new Location(nether, 3.0, 66.0, 0.0));
        fire(new PlayerChangedWorldEvent(player, overworld));

        List<Packet> got = sent();
        assertEquals(3, got.size(), got.toString());
        assertEquals(new Destroy(old.entityIds()), got.get(1));
        assertEquals(nether.getUID(), assertInstanceOf(Spawn.class, got.get(2)).position().worldKey());
        assertEquals(Set.of(player.getUniqueId()), tracker.viewers(netherHologram));
    }

    /**
     * Acceptance: a respawn reaches the tracker with the respawn location, so a hologram there spawns again with no
     * destroy, and one out of range of it is no longer seen.
     */
    @Test
    void aRespawnReachesTheTrackerWithTheRespawnLocation() {
        player.setLocation(new Location(overworld, 0.0, 66.0, 0.0));
        fire(new PlayerJoinEvent(player, "joined"));
        Spawn first = assertInstanceOf(Spawn.class, sent().get(0));

        fire(new PlayerRespawnEvent(player, new Location(overworld, 5.0, 66.0, 0.0), false));
        assertEquals(List.of(first, first), sent());
        assertEquals(Set.of(player.getUniqueId()), tracker.viewers(overworldHologram));

        fire(new PlayerRespawnEvent(player, new Location(overworld, 300.0, 66.0, 0.0), false));
        assertEquals(List.of(first, first), sent());
        assertEquals(Set.of(), tracker.viewers(overworldHologram));
    }

    private void fire(Event event) {
        server.getPluginManager().callEvent(event);
    }

    private List<Packet> sent() {
        return adapter.sentTo(player.getUniqueId());
    }
}
