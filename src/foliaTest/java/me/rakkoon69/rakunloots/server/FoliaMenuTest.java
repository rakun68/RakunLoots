package me.rakkoon69.rakunloots.server;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaMenuTest {

    private final FakeFoliaServer fakeServer = new FakeFoliaServer();
    private final ServerAdapter adapter = new FoliaServerAdapter(fakeServer.plugin());

    @AfterEach
    void stop() {
        fakeServer.shutdown();
    }

    private static ConfigSnapshot config() {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", "menu_button_previous_page_name: \"prev\"\n"
                        + "menu_button_previous_page_lore: \"{page}/{pages}\"\n"
                        + "menu_button_next_page_name: \"next\"\n"
                        + "menu_button_next_page_lore: \"{page}/{pages}\"\n")
                .menu("claim", """
                        rows: 3
                        content_slots: ['1-8']
                        filler: {id: gray_stained_glass_pane}
                        buttons:
                          claim_all:
                            slot: 0
                            item: {id: diamond}
                            sound: {key: "minecraft:ui.button.click", volume: 0.7, pitch: 1.2}
                        """)
                .build(), new NameCatalog() {
                    @Override
                    public Optional<String> currentId(String id) {
                        return Optional.of(id);
                    }

                    @Override
                    public boolean soundExists(String key) {
                        return "minecraft:ui.button.click".equals(key);
                    }
                }, WarningKinds.all());
    }

    @Test
    void menuWorkRunsOnTheEntitySchedulerAndClosesOnStop() throws Exception {
        UUID world = fakeServer.createWorld(0, 256);
        UUID playerId = fakeServer.addPlayer(world, 1, 64, 1);
        FakeFoliaPlayer player = fakeServer.player(playerId);
        MenuService menus = new MenuService(adapter);
        AtomicInteger clicks = new AtomicInteger();
        ConfigSnapshot config = config();
        MenuIcon icon = new MenuIcon(new ItemStack(Material.DIAMOND), Optional.of(clicks::incrementAndGet),
                config.claimMenu().buttons().get("claim_all").sound());
        MenuView view = new MenuView("Menu", 3, Map.of(0, icon));

        CompletableFuture<Void> opened = menus.open(playerId, view);
        fakeServer.runUntilDone(opened);
        opened.join();

        assertTrue(player.topInventory().getHolder() instanceof MenuHolder);
        assertEquals(Optional.of(new FakeFoliaTicks.Entity(playerId)),
                player.inventoryCalls().get(0).owner(), "the open ran on the player's entity scheduler");

        CompletableFuture<Void> clicked = menus.click(playerId, 0);
        fakeServer.runUntilDone(clicked);
        clicked.join();
        assertEquals(1, clicks.get());
        assertEquals(1, player.sounds().size());
        assertEquals("minecraft:ui.button.click", player.sounds().get(0).key());
        assertEquals(0.7f, player.sounds().get(0).volume());
        assertEquals(1.2f, player.sounds().get(0).pitch());
        assertEquals(Optional.of(new FakeFoliaTicks.Entity(playerId)), player.sounds().get(0).owner());

        CompletableFuture<Void> refreshed = menus.refresh(playerId, new MenuView("Menu", 3, Map.of(1,
                new MenuIcon(new ItemStack(Material.EMERALD), Optional.empty(), Optional.empty()))));
        fakeServer.runUntilDone(refreshed);
        refreshed.join();
        assertEquals(Material.EMERALD, player.topInventory().getItem(1).getType());

        CompletableFuture<Void> closed = menus.closeAll();
        fakeServer.runUntilDone(closed);
        closed.join();
        assertNull(player.topInventory());
        assertTrue(player.inventoryCalls().stream()
                .anyMatch(call -> call.method().equals("closeInventory")
                        && call.owner().equals(Optional.of(new FakeFoliaTicks.Entity(playerId)))));
        assertEquals(List.of(), fakeServer.bukkitSchedulerCalls(), "the Bukkit scheduler was never called");
    }

    /**
     * At plugin stop Folia's entity scheduler drops callbacks for the disabled plugin. A menu close must still complete
     * and retire the menu, rather than leave its cleanup future waiting for a task that can never run.
     */
    @Test
    void aCloseAtPluginStopCompletesAndRetiresTheMenuWithoutTheDisabledScheduler() throws Exception {
        UUID world = fakeServer.createWorld(0, 256);
        UUID playerId = fakeServer.addPlayer(world, 1, 64, 1);
        FakeFoliaPlayer player = fakeServer.player(playerId);
        MenuService menus = new MenuService(adapter);
        MenuView view = new MenuView("Menu", 3, Map.of(0, new MenuIcon(new ItemStack(Material.DIAMOND),
                Optional.empty(), Optional.empty())));
        CompletableFuture<Void> opened = menus.open(playerId, view);
        fakeServer.runUntilDone(opened);
        opened.join();
        assertTrue(player.topInventory().getHolder() instanceof MenuHolder);

        fakeServer.disablePlugin();
        CompletableFuture<Void> closed = menus.closeAll();

        assertTrue(closed.isDone(), "the close does not wait for the disabled scheduler");
        assertFalse(closed.isCompletedExceptionally());
        assertTrue(menus.refresh(playerId, view).isDone(), "the service retired the menu");
        assertTrue(menus.click(playerId, 0).isDone(), "a click after stop has nothing to dispatch");
    }

    @Test
    void aDeferredClickRunsOnTheEntitySchedulerAndNotInsideTheEvent() throws Exception {
        UUID world = fakeServer.createWorld(0, 256);
        UUID playerId = fakeServer.addPlayer(world, 1, 64, 1);
        FakeFoliaPlayer player = fakeServer.player(playerId);
        MenuService menus = new MenuService(adapter);
        AtomicInteger clicks = new AtomicInteger();
        CompletableFuture<Void> opened = menus.open(playerId, new MenuView("Menu", 3, Map.of(0,
                new MenuIcon(new ItemStack(Material.DIAMOND), Optional.of(clicks::incrementAndGet),
                        Optional.empty()))));
        fakeServer.runUntilDone(opened);
        opened.join();

        CompletableFuture<Void> deferred = menus.clickLater(playerId, 0);
        assertEquals(0, clicks.get());
        fakeServer.tick();
        deferred.join();

        assertEquals(1, clicks.get());
        assertEquals(List.of(), fakeServer.bukkitSchedulerCalls(), "the Bukkit scheduler was never called");
    }

    @Test
    void aPlayerWhoLeftBeforeTheOpenLandsGetsNoMenu() throws Exception {
        UUID world = fakeServer.createWorld(0, 256);
        UUID playerId = fakeServer.addPlayer(world, 1, 64, 1);
        fakeServer.removePlayer(playerId);
        MenuService menus = new MenuService(adapter);

        CompletableFuture<Void> opened = menus.open(playerId, new MenuView("Menu", 3, Map.of()));
        fakeServer.runUntilDone(opened);
        opened.join();

        assertFalse(fakeServer.player(playerId) != null && fakeServer.player(playerId).topInventory() != null);
        assertEquals(List.of(), fakeServer.bukkitSchedulerCalls());
    }
}
