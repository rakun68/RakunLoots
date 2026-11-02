package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.config.*;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.menu.*;
import me.rakkoon69.rakunloots.server.*;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.server.bukkit.ServerAdapters;
import me.rakkoon69.rakunloots.storage.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class Phase9ClaimCloseWhileReplacingTest {

    @TempDir
    Path folder;

    private StreamableServerMock server;
    private PlayerMock player;
    private LootBoxStore store;
    private ConfigSnapshot config;
    private List<String> failures;
    private MenuService menus;
    private ClaimMenu menu;
    private final AtomicBoolean holdOpen = new AtomicBoolean();
    private final AtomicReference<Runnable> pendingOpen = new AtomicReference<>();
    private final AtomicInteger openCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = StreamableServerMock.mock();
        var plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        Path subFolder = Files.createTempDirectory(folder, "test-store");
        store = SqliteStores.open(subFolder);
        failures = new ArrayList<>();

        config = loadShippedClaimSnapshot();
        // Assert the real two-loot fixture is loaded
        assertTrue(config.loot("alpha").isPresent(), "alpha loot fixture must be loaded");
        assertTrue(config.loot("beta").isPresent(), "beta loot fixture must be loaded");

        var realAdapter = ServerAdapters.select(plugin);
        ServerAdapter adapter = (ServerAdapter) Proxy.newProxyInstance(
                ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("openMenu") && holdOpen.get()) {
                        openCount.incrementAndGet();
                        var delayed = new CompletableFuture<Optional<MenuHandle>>();
                        pendingOpen.set(() -> {
                            try {
                                @SuppressWarnings("unchecked")
                                CompletableFuture<Optional<MenuHandle>> invoked =
                                        (CompletableFuture<Optional<MenuHandle>>) method.invoke(realAdapter, args);
                                invoked.whenComplete((val, err) -> {
                                    if (err != null) delayed.completeExceptionally(err);
                                    else delayed.complete(val);
                                });
                            } catch (Exception err) {
                                delayed.completeExceptionally(err);
                            }
                        });
                        return delayed;
                    }
                    if (method.getName().equals("openMenu")) {
                        openCount.incrementAndGet();
                    }
                    try {
                        return method.invoke(realAdapter, args);
                    } catch (InvocationTargetException err) {
                        throw err.getCause();
                    }
                }
        );

        var sources = ItemSourceRegistry.create();
        var maker = new SummonItemMaker(sources);
        var claims = new UnclaimedSummonItems(adapter, () -> config, store, maker, (what, cause) -> failures.add(what));
        var renderer = new MenuRenderer(sources, key -> config.message("", key), me.rakkoon69.rakunloots.warning.Warnings.ignore());
        menus = new MenuService(adapter, renderer);
        server.getPluginManager().registerEvents(new MenuListener(menus), plugin);
        menu = new ClaimMenu(adapter, () -> config, claims, maker, menus, renderer, (what, cause) -> failures.add(what));
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Pumps the MockBukkit tick loop and asynchronous scheduler tasks until the specified observable state
     * condition becomes true, bounded by a deadlock timeout.
     * The timeout is purely a test deadlock bound (preventing hangs if an async pipeline never completes);
     * under normal execution the condition is satisfied promptly upon scheduler completion.
     */
    private void awaitCondition(String description, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000L;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for " + description + " within deadlock bound (5s). isMenuOpen="
                        + isMenuOpen(player) + ", owed=" + owedCount(player.getUniqueId())
                        + ", openCount=" + openCount.get() + ", failures=" + failures);
            }
            server.getScheduler().performOneTick();
            server.getScheduler().waitAsyncTasksFinished();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted waiting for " + description);
            }
        }
    }

    private void pumpServer() {
        for (int i = 0; i < 3; i++) {
            server.getScheduler().performOneTick();
            server.getScheduler().waitAsyncTasksFinished();
        }
    }

    private int owedCount(UUID playerId) {
        try {
            return store.unclaimedSummonItems(playerId, config).size();
        } catch (LootBoxStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static MenuHandle currentMenuHandle(PlayerMock player) {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder holder ? holder.menuHandle() : null;
    }

    /**
     * Models the Spigot 1.18.2 CraftHumanEntity.openInventory(Inventory) / openCustomInventory behavior,
     * where an external plugin opening an inventory displaces the current custom menu without firing
     * an InventoryCloseEvent for the old custom menu. MockBukkit normally fires closeInventory() from
     * PlayerMock.openInventory(Inventory), so we set the inventoryView directly via reflection to simulate
     * the platform omission on Spigot 1.18.2.
     */
    private void openForeignInventoryWithoutCloseEvent(PlayerMock player, Inventory inventory) {
        try {
            var field = PlayerMock.class.getDeclaredField("inventoryView");
            field.setAccessible(true);
            field.set(player, new be.seeseemelk.mockbukkit.inventory.PlayerInventoryViewMock(player, inventory));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isMenuOpen(PlayerMock player) {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    @Test
    void claimMenuMustRemainClosedIfPlayerClosesItWhileReplacementWaits() throws Exception {
        UUID playerId = player.getUniqueId();
        store.addUnclaimedSummonItems(playerId, "alpha", 5);
        store.addUnclaimedSummonItems(playerId, "beta", 7);
        assertEquals(2, store.unclaimedSummonItems(playerId, config).size(), "real two-loot fixture owed");

        // 1. Open the claim menu initially
        menu.open(playerId, () -> fail("owed items must open claim menu"));
        awaitCondition("initial visible menu", () -> isMenuOpen(player));

        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder,
                "initial claim menu must be open");
        assertEquals(1, openCount.get());

        // 2. Set hold for the NEXT physical open (the title-changing replacement)
        holdOpen.set(true);

        // 3. Claim alpha (slot 0)
        menus.click(playerId, 0);
        awaitCondition("held pending replacement open", () -> pendingOpen.get() != null);

        // 4. Assert the title-changing open is actually pending
        assertNotNull(pendingOpen.get(), "the title-changing open must actually be pending");
        assertEquals(2, openCount.get());
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder,
                "old menu is still visible while replacement open is pending");

        // 5. Close the still-visible old menu through the real close-event path
        player.closeInventory();
        assertEquals(InventoryType.CRAFTING, player.getOpenInventory().getType(),
                "player is at default crafting view after closing");
        assertFalse(isMenuOpen(player));

        // 6. Release the held replacement open
        holdOpen.set(false);
        pendingOpen.getAndSet(null).run();
        pumpServer();

        // 7. The menu must remain closed!
        assertFalse(isMenuOpen(player),
                "the menu must remain closed after player closed it");
        assertEquals(InventoryType.CRAFTING, player.getOpenInventory().getType(),
                "player should still be at default crafting view");
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void unrelatedInventoryOpenedWhileReplacementWaitsMustStayVisible() throws Exception {
        UUID playerId = player.getUniqueId();
        store.addUnclaimedSummonItems(playerId, "alpha", 5);
        store.addUnclaimedSummonItems(playerId, "beta", 7);

        menu.open(playerId, () -> fail("owed items must open claim menu"));
        awaitCondition("initial visible menu", () -> isMenuOpen(player));

        holdOpen.set(true);
        menus.click(playerId, 0);
        awaitCondition("held pending replacement open", () -> pendingOpen.get() != null);

        assertNotNull(pendingOpen.get(), "the title-changing open must actually be pending");

        // Player opens an unrelated inventory meanwhile
        Inventory unrelated = server.createInventory(null, 9, "Unrelated Chest");
        player.openInventory(unrelated);
        assertSame(unrelated, player.getOpenInventory().getTopInventory(), "unrelated inventory is open");

        // Release the held replacement open
        holdOpen.set(false);
        pendingOpen.getAndSet(null).run();
        pumpServer();

        // Unrelated inventory must stay visible!
        assertSame(unrelated, player.getOpenInventory().getTopInventory(),
                "unrelated inventory must remain open and not displaced by the delayed claim replacement");
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void normalReplacementDrivenCloseAllowsTitleRefreshAndSubsequentClaim() throws Exception {
        UUID playerId = player.getUniqueId();
        store.addUnclaimedSummonItems(playerId, "alpha", 5);
        store.addUnclaimedSummonItems(playerId, "beta", 7);

        menu.open(playerId, () -> fail("owed items must open claim menu"));
        awaitCondition("initial visible menu", () -> isMenuOpen(player));

        MenuHandle initialHandle = currentMenuHandle(player);
        assertNotNull(initialHandle, "initial menu handle must be present");
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder);

        // Claim alpha without holding
        menus.click(playerId, 0);
        awaitCondition("post-claim store and replacement menu state",
                () -> isMenuOpen(player) && currentMenuHandle(player) != initialHandle && owedCount(playerId) == 1);

        // Replacement menu is open and title updated
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder,
                "replacement menu should be open");

        // Now claim beta (slot 0 on the new menu)
        menus.click(playerId, 0);
        awaitCondition("post-second-claim store and closed menu state",
                () -> !isMenuOpen(player) && owedCount(playerId) == 0);

        // Now owed is empty, menu should close
        assertEquals(0, store.unclaimedSummonItems(playerId, config).size(), "all items claimed");
        assertEquals(InventoryType.CRAFTING, player.getOpenInventory().getType(),
                "menu closes when empty");
        assertFalse(isMenuOpen(player), "menu must be closed when empty");
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void freshExplicitOpenRecoversAfterForeignInventorySwitchAndClose() throws Exception {
        UUID playerId = player.getUniqueId();
        store.addUnclaimedSummonItems(playerId, "alpha", 5);
        store.addUnclaimedSummonItems(playerId, "beta", 7);

        // 1. Open the claim menu initially
        menu.open(playerId, () -> fail("owed items must open claim menu"));
        awaitCondition("initial visible menu", () -> isMenuOpen(player));

        MenuHandle initialHandle = currentMenuHandle(player);
        assertNotNull(initialHandle, "initial menu handle must be present");

        // 2. Another plugin opens an unrelated inventory without firing an InventoryCloseEvent
        // for the old custom menu (Spigot 1.18.2 CraftHumanEntity.openInventory(Inventory) behavior)
        Inventory foreign = server.createInventory(null, 9, "Foreign Inventory");
        openForeignInventoryWithoutCloseEvent(player, foreign);
        assertSame(foreign, player.getOpenInventory().getTopInventory(), "foreign inventory is now top view");

        // 3. Player closes the foreign inventory
        player.closeInventory();
        assertEquals(InventoryType.CRAFTING, player.getOpenInventory().getType(),
                "player is at default crafting view after foreign inventory close");

        // 4. Player runs /claim: a fresh user-requested menu open must recover and open successfully
        menu.open(playerId, () -> fail("owed items must open claim menu"));
        awaitCondition("claim menu recovered after foreign inventory switch and close", () -> isMenuOpen(player));

        assertTrue(isMenuOpen(player), "claim menu must be open after explicit user request");
        MenuHandle newHandle = currentMenuHandle(player);
        assertNotNull(newHandle);
        assertNotSame(initialHandle, newHandle, "a fresh handle must be installed");

        // 5. Subsequent claiming works normally
        menus.click(playerId, 0);
        awaitCondition("post-claim store and replacement menu state",
                () -> isMenuOpen(player) && currentMenuHandle(player) != newHandle && owedCount(playerId) == 1);

        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void delayedBackgroundReplacementCannotStealForeignViewInNoCloseEventScenario() throws Exception {
        UUID playerId = player.getUniqueId();
        store.addUnclaimedSummonItems(playerId, "alpha", 5);
        store.addUnclaimedSummonItems(playerId, "beta", 7);

        menu.open(playerId, () -> fail("owed items must open claim menu"));
        awaitCondition("initial visible menu", () -> isMenuOpen(player));

        // Hold the next physical open (the background replacement)
        holdOpen.set(true);
        menus.click(playerId, 0);
        awaitCondition("held pending replacement open", () -> pendingOpen.get() != null);
        assertNotNull(pendingOpen.get(), "background replacement must be pending");

        // Displace the menu with a foreign inventory without close event (Spigot 1.18.2 behavior)
        Inventory foreign = server.createInventory(null, 9, "Foreign Chest");
        openForeignInventoryWithoutCloseEvent(player, foreign);
        assertSame(foreign, player.getOpenInventory().getTopInventory(), "foreign view is active");

        // Release the held replacement open
        holdOpen.set(false);
        pendingOpen.getAndSet(null).run();
        pumpServer();

        // Foreign inventory must stay visible! The delayed background replacement must not steal it
        assertSame(foreign, player.getOpenInventory().getTopInventory(),
                "foreign inventory must stay visible and not be stolen by delayed background replacement");
        assertFalse(isMenuOpen(player), "menu must not be open");
        assertTrue(failures.isEmpty(), failures.toString());
    }

    private static ConfigSnapshot loadShippedClaimSnapshot() {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", resource("/languages/en.yml"))
                .menu("claim", resource("/menus/claim.yml"));
        sources.loot("alpha", "display_name: 'Alpha'\nopen_mode: drop\nblock: barrel\n"
                + "summon_item:\n  id: diamond\nroll:\n  entries:\n    a:\n      item:\n        id: diamond\n");
        sources.loot("beta", "display_name: 'Beta'\nopen_mode: drop\nblock: barrel\n"
                + "summon_item:\n  id: emerald\nroll:\n  entries:\n    a:\n      item:\n        id: emerald\n");
        return ConfigLoader.load(sources.build(), new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                return Optional.of(writtenId.contains(":") ? writtenId : "minecraft:" + writtenId);
            }

            @Override
            public boolean soundExists(String key) {
                return true;
            }
        }, WarningKinds.all());
    }

    private static String resource(String path) {
        try (InputStream in = Phase9ClaimCloseWhileReplacingTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
