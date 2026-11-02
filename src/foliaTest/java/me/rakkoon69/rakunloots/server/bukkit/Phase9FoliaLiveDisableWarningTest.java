package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

/**
 * An unsupported live disable leaves a disabled non-owner close with no safe way to write the player's inventory.
 * It warns once per adapter and keeps the physical view; ordinary enabled, owned, and offline closes stay quiet.
 */
class Phase9FoliaLiveDisableWarningTest {

    private static final NameCatalog CATALOG = new NameCatalog() {
        @Override
        public Optional<String> currentId(String id) {
            return Optional.of(id);
        }

        @Override
        public boolean soundExists(String key) {
            return "minecraft:ui.button.click".equals(key);
        }
    };

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

    private static String shipped(String path) throws IOException {
        try (InputStream in = Phase9FoliaLiveDisableWarningTest.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                throw new IOException("missing shipped resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ConfigSnapshot shippedClaimConfig() throws IOException {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", "text: test\n")
                .menu("claim", shipped("menus/claim.yml"))
                .build(), CATALOG, WarningKinds.all());
    }

    @Test
    void theShippedClaimLayoutRowsBecomeThePhysicalFoliaViewSize() throws Exception {
        ConfigSnapshot config = shippedClaimConfig();
        assertEquals(6, config.claimMenu().rows(), "the shipped claim file's row count");

        MenuService menus = new MenuService(adapter);
        UUID world = fake.createWorld(0, 256);
        UUID playerId = fake.addPlayer(world, 1, 64, 1);
        FakeFoliaPlayer player = fake.player(playerId);
        int firstContentSlot = config.claimMenu().contentSlots().get(0);
        fake.runUntilDone(menus.open(playerId, new MenuView("Claim", config.claimMenu().rows(),
                Map.of(firstContentSlot, new MenuIcon(new ItemStack(Material.DIAMOND), Optional.empty(),
                        Optional.empty())))));

        assertEquals(config.claimMenu().rows() * 9, player.topInventory().getSize(),
                "the layout rows size the real physical view");
    }

    @Test
    void unsafeDisabledClosesWarnOnceAcrossPlayersWithoutTouchingThem() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID first = openMenu(menus);
        UUID second = openMenu(menus);
        FakeFoliaPlayer firstPlayer = fake.player(first);
        FakeFoliaPlayer secondPlayer = fake.player(second);
        List<FakeFoliaPlayer.StateRead> firstCalls = firstPlayer.inventoryCalls();
        List<FakeFoliaPlayer.StateRead> secondCalls = secondPlayer.inventoryCalls();

        fake.disablePlugin();
        CompletableFuture<Void> firstClosed = menus.close(first);
        CompletableFuture<Void> secondClosed = menus.close(second);
        assertTrue(firstClosed.isDone(), "the unsafe close completes without the stopped scheduler");
        assertTrue(secondClosed.isDone(), "the unsafe close completes without the stopped scheduler");
        assertFalse(firstClosed.isCompletedExceptionally());
        assertFalse(secondClosed.isCompletedExceptionally());
        firstClosed.join();
        secondClosed.join();
        CompletableFuture<Void> allClosed = menus.closeAll();
        assertTrue(allClosed.isDone());
        allClosed.join();
        fake.tick();

        assertEquals(1, fake.warnings().size(), "repeated unsafe closes, here for two players, warn only once");
        String warning = fake.warnings().get(0);
        assertTrue(warning.contains("unsupported"), warning);
        assertTrue(warning.contains("could not be closed safely"), warning);
        assertTrue(warning.contains("may remain accessible"), warning);
        assertTrue(warning.contains("Restart"), warning);
        assertTrue(warning.contains("reload"), warning);
        assertEquals(firstCalls, firstPlayer.inventoryCalls(), "the unsafe branch calls no player inventory method");
        assertEquals(secondCalls, secondPlayer.inventoryCalls(), "the unsafe branch calls no player inventory method");
        assertNotNull(firstPlayer.topInventory(), "the server drops the unsafe view, not the adapter");
        assertNotNull(secondPlayer.topInventory(), "the server drops the unsafe view, not the adapter");
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }

    @Test
    void anEnabledCloseDoesNotWarn() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID playerId = openMenu(menus);
        FakeFoliaPlayer player = fake.player(playerId);

        CompletableFuture<Void> closed = menus.closeAll();
        assertFalse(closed.isDone(), "an enabled close waits for the player's entity scheduler");
        fake.tick();
        closed.join();

        assertNull(player.topInventory());
        assertEquals(List.of(), fake.warnings());
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }

    @Test
    void aDisabledOwnerThreadCloseDoesNotWarn() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID playerId = openMenu(menus);
        FakeFoliaPlayer player = fake.player(playerId);

        fake.disablePlugin();
        fake.runAsPlayer(playerId, () -> {
            CompletableFuture<Void> closed = menus.closeAll();
            assertTrue(closed.isDone(), "the shutdown owner closes at once");
            closed.join();
        });

        assertNull(player.topInventory());
        assertEquals(List.of(), fake.warnings());
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }

    @Test
    void anOfflinePlayerCloseDoesNotWarnEvenWhenDisabled() throws Exception {
        MenuService menus = new MenuService(adapter);
        UUID playerId = openMenu(menus);
        fake.removePlayer(playerId);

        fake.disablePlugin();
        CompletableFuture<Void> closed = menus.closeAll();

        assertTrue(closed.isDone());
        closed.join();
        assertEquals(List.of(), fake.warnings());
        assertEquals(List.of(), fake.bukkitSchedulerCalls());
    }
}
