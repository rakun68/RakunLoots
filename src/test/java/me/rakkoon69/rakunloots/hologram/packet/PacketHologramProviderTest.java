package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.OwnerNames;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Destroy;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Meta;
import me.rakkoon69.rakunloots.hologram.packet.RecordingPacketAdapter.Spawn;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packet provider under the real hologram service over the shipped config, with a recording packet adapter as
 * the packet sink. The shipped airdrop's hologram sits 2 blocks above its block with the global view range of 48.
 */
class PacketHologramProviderTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID ALEX = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID STEVE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID FAR = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000003");
    private static final OwnerNames NO_NAMES = id -> Optional.empty();

    private final RecordingPacketAdapter adapter = RecordingPacketAdapter.textDisplays();
    private final PlayerWorlds server = new PlayerWorlds();
    private final PacketHologramProvider provider =
            new PacketHologramProvider(PacketAdapterMatch.matched(adapter), server);
    private final HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NO_NAMES);

    /**
     * A shown box's hologram spawns to players in range with its rendered lines, a tick that changes them sends meta
     * to its viewers, and its end destroys it for them.
     */
    @Test
    void aShownBoxDrawsForPlayersInRangeUpdatesAndEnds() {
        ViewerTracker tracker = provider.tracker().orElseThrow();
        tracker.joined(WORLD, new PlayerPosition(ALEX, 2.5, 64.0, 10.0));
        tracker.joined(WORLD, new PlayerPosition(FAR, 200.0, 64.0, 0.0));

        service.shown(box(BOX, 10));
        Spawn spawn = assertInstanceOf(Spawn.class, adapter.sentTo(ALEX).get(0));
        assertEquals(new HologramPosition(WORLD, 2.5, 66.0, 3.5), spawn.position());
        assertEquals("§7Lock: §c0:10", spawn.lines().get(2));

        service.ticked(box(BOX, 9));
        Meta meta = assertInstanceOf(Meta.class, adapter.sentTo(ALEX).get(1));
        assertEquals(spawn.entityIds(), meta.entityIds());
        assertEquals("§7Lock: §c0:09", meta.lines().get(2));

        service.ended(box(BOX, 9));
        assertEquals(new Destroy(spawn.entityIds()), adapter.sentTo(ALEX).get(2));
        assertEquals(3, adapter.sentTo(ALEX).size());
        assertEquals(List.of(), adapter.sentTo(FAR));
    }

    /**
     * Acceptance: remove reaches every viewer; remove all runs on stop, destroying every hologram for its viewers and
     * stopping the movement check.
     */
    @Test
    void removeReachesEveryViewerAndRemoveAllRunsOnStop() {
        ViewerTracker tracker = provider.tracker().orElseThrow();
        tracker.joined(WORLD, new PlayerPosition(ALEX, 2.5, 64.0, 10.0));
        tracker.joined(WORLD, new PlayerPosition(STEVE, 20.0, 64.0, 3.5));
        tracker.joined(WORLD, new PlayerPosition(FAR, 200.0, 64.0, 0.0));
        service.shown(box(BOX, 10));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        List<Integer> first = ((Spawn) adapter.sentTo(ALEX).get(0)).entityIds();
        List<Integer> second = ((Spawn) adapter.sentTo(ALEX).get(1)).entityIds();
        adapter.clear();

        service.outOfView(box(BOX, 10));

        assertEquals(List.of(new Destroy(first)), adapter.sentTo(ALEX));
        assertEquals(List.of(new Destroy(first)), adapter.sentTo(STEVE));
        adapter.clear();

        service.stop();
        server.globalScheduler().stepTicks(40);

        assertEquals(List.of(new Destroy(second)), adapter.sentTo(ALEX));
        assertEquals(List.of(new Destroy(second)), adapter.sentTo(STEVE));
        assertEquals(List.of(), adapter.sentTo(FAR));
        assertEquals(List.of(), server.snapshotsAsked(), "the movement check stopped");
        assertEquals(0, server.globalScheduler().pending());
    }

    /**
     * Acceptance: with no adapter matched, the provider cannot run, its reason names the version, and it has no
     * tracker. A call it should never get is refused.
     */
    @Test
    void withNoAdapterMatchedTheProviderCannotRun() {
        PacketHologramProvider none = new PacketHologramProvider(
                PacketAdapterMatch.none("there is no packet adapter for Minecraft 1.17.1"), server);

        assertEquals(CanRun.no("there is no packet adapter for Minecraft 1.17.1"), none.canRun());
        assertTrue(none.tracker().isEmpty());
        assertThrows(IllegalStateException.class, () -> none.create(BOX,
                new HologramPosition(WORLD, 0.5, 66.0, 0.5), List.of("§6Air Supply"), 48));
        assertThrows(IllegalStateException.class, none::removeAll);
        assertEquals(CanRun.YES, provider.canRun());
    }

    private static BoxEvent box(UUID boxId, int lock) {
        return new BoxEvent(boxId, "airdrops", WORLD, new BlockPosition(2, 64, 3), Optional.empty(), lock,
                OptionalInt.of(360));
    }

    private static BoxEvent boxAt(UUID boxId, BlockPosition position) {
        return new BoxEvent(boxId, "airdrops", WORLD, position, Optional.empty(), 10, OptionalInt.of(360));
    }
}
