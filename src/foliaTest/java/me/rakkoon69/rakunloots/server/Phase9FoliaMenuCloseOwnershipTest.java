package me.rakkoon69.rakunloots.server;

import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase9FoliaMenuCloseOwnershipTest {

    private final FakeFoliaServer fake = new FakeFoliaServer();
    private final ServerAdapter adapter = new FoliaServerAdapter(fake.plugin());

    @AfterEach
    void stop() {
        fake.shutdown();
    }

    private UUID openMenu(MenuService menus) throws Exception {
        UUID world = fake.createWorld(0, 256);
        UUID playerId = fake.addPlayer(world, 1, 64, 1);
        fake.runUntilDone(menus.open(playerId, new MenuView("Menu", 3, Map.of(0, new MenuIcon(
                new ItemStack(Material.DIAMOND), Optional.empty(), Optional.empty())))));
        assertNotNull(fake.player(playerId).topInventory());
        return playerId;
    }

    @Test
    void aDisabledOwnerCloseClosesThePhysicalMenuOnTheShutdownThread() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID playerId = openMenu(menus);
        FakeFoliaPlayer player = fake.player(playerId);

        fake.disablePlugin();
        fake.runAsPlayer(playerId, () -> {
            CompletableFuture<Void> closed = menus.closeAll();
            assertTrue(closed.isDone(), "the shutdown close runs on the player's owner, not the stopped scheduler");
            closed.join();
        });

        assertNull(player.topInventory(), "the owned shutdown close closes the physical inventory");
        assertEquals(Optional.of(new FakeFoliaTicks.Entity(playerId)),
                player.inventoryCalls().get(player.inventoryCalls().size() - 1).owner());
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }

    @Test
    void aDisabledNonOwnerCloseCompletesWithoutTheStoppedScheduler() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID playerId = openMenu(menus);
        FakeFoliaPlayer player = fake.player(playerId);

        fake.disablePlugin();
        CompletableFuture<Void> closed = menus.closeAll();

        assertTrue(closed.isDone());
        assertFalse(closed.isCompletedExceptionally());
        closed.join();
        assertNotNull(player.topInventory(),
                "a non-owner cannot write the player's inventory; the server drops the view at shutdown");
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }

    @Test
    void anEnabledCloseIsRoutedToThePlayersScheduler() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID playerId = openMenu(menus);
        FakeFoliaPlayer player = fake.player(playerId);

        CompletableFuture<Void> closed = menus.closeAll();

        assertFalse(closed.isDone(), "the close waits for the player's entity scheduler");
        assertNotNull(player.topInventory());
        fake.tick();
        closed.join();
        assertNull(player.topInventory());
        assertEquals(Optional.of(new FakeFoliaTicks.Entity(playerId)),
                player.inventoryCalls().get(player.inventoryCalls().size() - 1).owner());
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }

    @Test
    void aRetiredPlayerIsSkippedWhenTheMenuCloses() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID playerId = openMenu(menus);
        fake.removePlayer(playerId);

        fake.disablePlugin();
        CompletableFuture<Void> closed = menus.closeAll();

        assertTrue(closed.isDone());
        closed.join();
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }
}
