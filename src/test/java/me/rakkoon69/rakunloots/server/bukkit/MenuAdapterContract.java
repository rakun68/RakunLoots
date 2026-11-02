package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.MenuHandle;
import me.rakkoon69.rakunloots.server.MenuHolder;
import me.rakkoon69.rakunloots.server.ServerAdapter;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menu half of the server adapter, through MockBukkit: create, open on the next tick, update in place, close, play
 * a sound, and defer player work.
 */
abstract class MenuAdapterContract {

    MockBukkitWorlds fakeServer;
    ServerAdapter adapter;
    RecordingPlayer player;
    UUID playerId;

    abstract ServerAdapter newAdapter(Plugin plugin);

    @BeforeEach
    void start() {
        fakeServer = MockBukkitWorlds.start();
        adapter = newAdapter(fakeServer.plugin());
        UUID world = fakeServer.createWorld(0, 256);
        playerId = fakeServer.addPlayer(world, 1, 64, 1);
        player = fakeServer.player(playerId);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aMenuOpensOnTheNextMainThreadTickAndUpdatesInPlace() throws Exception {
        MenuHandle menu = adapter.createMenu(playerId, "Menu", 27,
                Map.of(0, new ItemStack(Material.DIAMOND)));
        CompletableFuture<Optional<MenuHandle>> opening = adapter.openMenu(playerId, menu);
        assertFalse(opening.isDone(), "the open waits for the main thread");

        assertEquals(Optional.of(menu), fakeServer.runUntilDone(opening));
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder);
        assertEquals(Material.DIAMOND, player.getOpenInventory().getTopInventory().getItem(0).getType());

        CompletableFuture<Void> updating = adapter.updateMenu(menu,
                Map.of(1, new ItemStack(Material.EMERALD)));
        fakeServer.runUntilDone(updating);
        updating.join();
        assertEquals(Material.EMERALD, player.getOpenInventory().getTopInventory().getItem(1).getType());
        assertEquals(null, player.getOpenInventory().getTopInventory().getItem(0));
    }

    @Test
    void aClosedMenuLeavesTheViewAndAPlayerWhoLeftGetsNoMenu() throws Exception {
        MenuHandle menu = adapter.createMenu(playerId, "Menu", 27, Map.of());
        fakeServer.runUntilDone(adapter.openMenu(playerId, menu)).orElseThrow();

        CompletableFuture<Void> closing = adapter.closeMenu(menu);
        fakeServer.runUntilDone(closing);
        closing.join();
        assertTrue(player.getOpenInventory().getTopInventory() == null
                || !(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder));

        CompletableFuture<Optional<MenuHandle>> offline =
                adapter.openMenu(UUID.randomUUID(), adapter.createMenu(UUID.randomUUID(), "Gone", 9, Map.of()));
        fakeServer.runUntilDone(offline);
        assertEquals(Optional.empty(), offline.join());
    }

    @Test
    void createMenuRefusesASizeThatIsNotAChestAndAClosedOpenIsRefused() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.createMenu(playerId, "Bad", 10, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.createMenu(playerId, "Bad", 27, Map.of(27, new ItemStack(Material.DIAMOND))));
        MenuHandle menu = adapter.createMenu(playerId, "Menu", 27, Map.of());
        player.refuseInventoryOpens();
        fakeServer.runUntilDone(adapter.openMenu(playerId, menu));
        assertFalse(player.getOpenInventory().getTopInventory() != null
                && player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder);
    }

    @Test
    void aSoundIsPlayedOnTheMainThreadAndPlayerWorkIsDeferred() throws Exception {
        CompletableFuture<Boolean> sound = adapter.playSound(playerId, "minecraft:ui.button.click", 1.0, 1.0);
        assertTrue(fakeServer.runUntilDone(sound));

        AtomicInteger ran = new AtomicInteger();
        CompletableFuture<Void> deferred = adapter.runOnPlayerNextTick(playerId, ran::incrementAndGet);
        assertEquals(0, ran.get(), "the work waits for the next tick");
        fakeServer.runUntilDone(deferred);
        deferred.join();
        assertEquals(1, ran.get());
        assertEquals(0, fakeServer.server().getScheduler().runs().stream()
                .filter(run -> !run.mainThread()).count());
    }

    @Test
    void guardedOpenMenuAcceptsWhenExpectedOldIsCurrentVisibleMenu() throws Exception {
        MenuHandle first = adapter.createMenu(playerId, "First", 27, Map.of());
        fakeServer.runUntilDone(adapter.openMenu(playerId, first)).orElseThrow();
        assertEquals(first, player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder ? holder.menuHandle() : null);

        MenuHandle second = adapter.createMenu(playerId, "Second", 27, Map.of());
        CompletableFuture<Optional<MenuHandle>> replacement = adapter.openMenu(playerId, second, first);
        assertEquals(Optional.of(second), fakeServer.runUntilDone(replacement));
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder
                && holder.menuHandle() == second);
    }

    @Test
    void guardedOpenMenuRejectsWhenExpectedOldIsClosedOrNoLongerVisible() throws Exception {
        MenuHandle first = adapter.createMenu(playerId, "First", 27, Map.of());
        fakeServer.runUntilDone(adapter.openMenu(playerId, first)).orElseThrow();
        MenuHandle second = adapter.createMenu(playerId, "Second", 27, Map.of());

        // 1. Rejects when player closed the expected menu externally
        player.closeInventory();
        fakeServer.server().getScheduler().performOneTick();
        CompletableFuture<Optional<MenuHandle>> rejectedClosed = adapter.openMenu(playerId, second, first);
        assertEquals(Optional.empty(), fakeServer.runUntilDone(rejectedClosed));

        // 2. Rejects when expected menu was closed via closeMenu
        fakeServer.runUntilDone(adapter.openMenu(playerId, first)).orElseThrow();
        fakeServer.runUntilDone(adapter.closeMenu(first));
        CompletableFuture<Optional<MenuHandle>> rejectedViaCloseMenu = adapter.openMenu(playerId, second, first);
        assertEquals(Optional.empty(), fakeServer.runUntilDone(rejectedViaCloseMenu));

        // 3. Rejects when a different menu is now visible
        MenuHandle other = adapter.createMenu(playerId, "Other", 27, Map.of());
        fakeServer.runUntilDone(adapter.openMenu(playerId, other)).orElseThrow();
        CompletableFuture<Optional<MenuHandle>> rejectedDifferent = adapter.openMenu(playerId, second, first);
        assertEquals(Optional.empty(), fakeServer.runUntilDone(rejectedDifferent));
    }
}
