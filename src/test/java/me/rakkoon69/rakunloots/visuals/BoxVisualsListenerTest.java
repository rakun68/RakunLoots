package me.rakkoon69.rakunloots.visuals;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.config.GlowColor;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Call;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Op;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The visuals listener, registered on a MockBukkit server, carries join, world change, respawn, and quit to a real box
 * visuals service, seen by what a fake glow library is asked to show.
 */
class BoxVisualsListenerTest {

    private static final BlockPosition BLOCK = new BlockPosition(2, 64, 3);

    private ServerMock server;
    private WorldMock overworld;
    private WorldMock nether;
    private PlayerMock player;
    private final FakeGlowLibrary glow = new FakeGlowLibrary();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("world");
        nether = server.addSimpleWorld("world_nether");
        player = server.addPlayer();
        BoxVisualsService service = new BoxVisualsService(ShippedHologramConfig.load(),
                new FakeServerAdapter(overworld.getUID()), () -> glow, FakeLaserLibrary::new, warning -> {
        });
        service.shown(new BoxEvent(UUID.randomUUID(), "airdrops", overworld.getUID(), BLOCK, Optional.empty(), 10,
                OptionalInt.of(360)));
        server.getPluginManager().registerEvents(new BoxVisualsListener(service), MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Acceptance: a player who joins in the box's world sees its glow, and one who joins in another world does not.
     */
    @Test
    void aJoinInTheBoxsWorldShowsTheGlowAndAJoinElsewhereDoesNot() {
        player.setLocation(new Location(nether, 0, 64, 0));
        fire(new PlayerJoinEvent(player, "joined"));
        assertEquals(List.of(), glow.calls());

        player.setLocation(new Location(overworld, 0, 64, 0));
        fire(new PlayerQuitEvent(player, "left"));
        fire(new PlayerJoinEvent(player, "joined"));

        assertEquals(List.of(new Call(Op.SHOW, overworld.getUID(), BLOCK, player.getUniqueId(), GlowColor.GOLD)),
                glow.calls());
    }

    @Test
    void aWorldChangeIntoTheBoxsWorldShowsTheGlow() {
        player.setLocation(new Location(overworld, 0, 64, 0));

        fire(new PlayerChangedWorldEvent(player, nether));

        assertEquals(List.of(new Call(Op.SHOW, overworld.getUID(), BLOCK, player.getUniqueId(), GlowColor.GOLD)),
                glow.calls());
    }

    @Test
    void aRespawnIntoTheBoxsWorldShowsTheGlowAgain() {
        player.setLocation(new Location(overworld, 0, 64, 0));
        fire(new PlayerJoinEvent(player, "joined"));
        glow.clear();

        fire(new PlayerRespawnEvent(player, new Location(overworld, 0, 64, 0), false));

        assertEquals(List.of(new Call(Op.SHOW, overworld.getUID(), BLOCK, player.getUniqueId(), GlowColor.GOLD)),
                glow.calls());
    }

    private void fire(Event event) {
        server.getPluginManager().callEvent(event);
    }
}
