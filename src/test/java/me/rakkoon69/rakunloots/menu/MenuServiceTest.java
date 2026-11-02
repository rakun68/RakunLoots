package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuServiceTest {

    private final UUID player = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private final FakeServerAdapter adapter = new FakeServerAdapter(UUID.randomUUID());
    private final MenuService menus = new MenuService(adapter);

    private static MenuView view(String title, Material material) {
        return new MenuView(title, 3, Map.of(0, new MenuIcon(new ItemStack(material), Optional.empty(),
                Optional.empty())));
    }

    private static MenuView actionView(String title, Material material, AtomicInteger clicks) {
        return new MenuView(title, 3, Map.of(0, new MenuIcon(new ItemStack(material),
                Optional.of(clicks::incrementAndGet), Optional.empty())));
    }

    @Test
    void anOfflinePlayerGetsNoMenu() {
        menus.open(player, view("Menu", Material.DIAMOND)).join();

        assertEquals(0, adapter.menuOpens().size());
    }

    @Test
    void aReplacementIsNotOpenedUntilTheAdapterShowsItAndThenClosesTheOldMenu() {
        adapter.addOnlinePlayer(player);
        AtomicInteger oldClicks = new AtomicInteger();
        AtomicInteger newClicks = new AtomicInteger();
        menus.open(player, actionView("Old", Material.DIAMOND, oldClicks)).join();

        adapter.holdMenuOpens();
        CompletableFuture<Void> replacing = menus.open(player, actionView("New", Material.EMERALD, newClicks));
        assertFalse(replacing.isDone());
        assertEquals(1, adapter.menuOpens().size(), "the old menu is still the one shown");
        assertEquals(0, adapter.menuCloses().size(), "the old menu stays until the new one is shown");

        adapter.releaseMenuOpens();
        replacing.join();
        assertEquals(2, adapter.menuOpens().size());
        assertEquals(1, adapter.menuCloses().size());
        menus.click(player, 0).join();
        assertEquals(0, oldClicks.get(), "the old action is retired");
        assertEquals(1, newClicks.get(), "the new action is live");
    }

    @Test
    void aFailedReplacementLeavesTheOldMenuAlone() {
        adapter.addOnlinePlayer(player);
        AtomicInteger clicks = new AtomicInteger();
        menus.open(player, actionView("Old", Material.DIAMOND, clicks)).join();
        adapter.failMenuOpens();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> menus.open(player, view("New", Material.EMERALD)).join());
        assertTrue(failure.getCause() instanceof IllegalStateException);

        assertEquals(1, adapter.menuOpens().size());
        assertEquals(0, adapter.menuCloses().size());
        menus.click(player, 0).join();
        assertEquals(1, clicks.get(), "the old menu still works");
    }

    @Test
    void anOldCloseEventCannotRetireItsReplacement() {
        adapter.addOnlinePlayer(player);
        AtomicInteger oldClicks = new AtomicInteger();
        AtomicInteger newClicks = new AtomicInteger();
        menus.open(player, actionView("Old", Material.DIAMOND, oldClicks)).join();
        menus.open(player, actionView("New", Material.EMERALD, newClicks)).join();
        var oldHandle = adapter.menuCloses().get(0).menu();

        menus.closed(player, oldHandle);
        menus.click(player, 0).join();

        assertEquals(1, newClicks.get(), "the new menu is still active");
        assertEquals(0, oldClicks.get());
    }

    @Test
    void anOpenThatLandsAfterCloseAllIsClosedAndRetired() {
        adapter.addOnlinePlayer(player);
        AtomicInteger clicks = new AtomicInteger();
        menus.open(player, actionView("Old", Material.DIAMOND, clicks)).join();
        adapter.holdMenuOpens();
        CompletableFuture<Void> late = menus.open(player, actionView("Late", Material.EMERALD, clicks));

        menus.closeAll().join();
        assertEquals(2, adapter.menuCloses().size(), "the shown and pending menus are invalidated at reload");
        adapter.releaseMenuOpens();
        late.join();

        assertEquals(2, adapter.menuCloses().size(), "the late open is closed instead of tracked");
        menus.click(player, 0).join();
        assertEquals(0, clicks.get());
    }

    @Test
    void refreshUpdatesTheSameMenuInPlace() {
        adapter.addOnlinePlayer(player);
        menus.open(player, view("Menu", Material.DIAMOND)).join();
        menus.refresh(player, new MenuView("Menu", 3, Map.of(1,
                new MenuIcon(new ItemStack(Material.EMERALD), Optional.empty(), Optional.empty())))).join();

        assertEquals(1, adapter.menuOpens().size());
        assertEquals(1, adapter.menuUpdates().size());
        assertEquals(Material.EMERALD, adapter.menuUpdates().get(0).contents().get(1).getType());
        assertEquals(player, adapter.menuUpdates().get(0).playerId());
    }

    @Test
    void aMenuIconWithoutAnActionDoesNothingAndPlaysNoSound() {
        adapter.addOnlinePlayer(player);
        var sound = ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", "text: test\n")
                .menu("claim", """
                        rows: 3
                        content_slots: ['1-8']
                        filler: {id: stone}
                        buttons:
                          claim_all:
                            slot: 0
                            item: {id: diamond}
                            sound: {key: "minecraft:ui.button.click", volume: 1.0, pitch: 1.0}
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
                }, WarningKinds.all()).claimMenu().buttons().get("claim_all").sound().orElseThrow();
        menus.open(player, new MenuView("Menu", 3, Map.of(0,
                new MenuIcon(new ItemStack(Material.DIAMOND), Optional.empty(), Optional.of(sound))))).join();

        menus.click(player, 0).join();

        assertEquals(0, adapter.sounds().size(), "a slot with no action is inert even with a sound");
    }

    @Test
    void aDeferredClickDoesNothingAfterItsMenuWasReplaced() {
        adapter.addOnlinePlayer(player);
        AtomicInteger oldClicks = new AtomicInteger();
        AtomicInteger newClicks = new AtomicInteger();
        menus.open(player, actionView("Old", Material.DIAMOND, oldClicks)).join();
        menus.clickLater(player, 0);
        menus.open(player, actionView("New", Material.EMERALD, newClicks)).join();

        adapter.runNextTickActions();

        assertEquals(0, oldClicks.get());
        assertEquals(0, newClicks.get(), "the deferred click does not move to the replacement");
        menus.click(player, 0).join();
        assertEquals(1, newClicks.get());
    }

    @Test
    void aDeferredClickDoesNothingAfterCloseAll() {
        adapter.addOnlinePlayer(player);
        AtomicInteger clicks = new AtomicInteger();
        menus.open(player, actionView("Menu", Material.DIAMOND, clicks)).join();
        menus.clickLater(player, 0);
        menus.closeAll().join();

        adapter.runNextTickActions();

        assertEquals(0, clicks.get());
    }

    @Test
    void closeRetiresOnlyThatPlayersMenu() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        adapter.addOnlinePlayer(player);
        adapter.addOnlinePlayer(other);
        menus.open(player, view("Mine", Material.DIAMOND)).join();
        menus.open(other, view("Theirs", Material.EMERALD)).join();

        menus.close(player).join();

        assertEquals(1, adapter.menuCloses().size());
        assertEquals(player, adapter.menuCloses().get(0).menu().playerId());
        menus.refresh(player, view("Mine", Material.STONE)).join();
        assertEquals(0, adapter.menuUpdates().size(), "the closed menu takes no refresh");
        menus.refresh(other, view("Theirs", Material.STONE)).join();
        assertEquals(1, adapter.menuUpdates().size(), "the other player's menu is untouched");
        assertEquals(other, adapter.menuUpdates().get(0).playerId());
    }

    @Test
    void closeCancelsAPendingReplacement() {
        adapter.addOnlinePlayer(player);
        AtomicInteger oldClicks = new AtomicInteger();
        AtomicInteger newClicks = new AtomicInteger();
        menus.open(player, actionView("Old", Material.DIAMOND, oldClicks)).join();
        adapter.holdMenuOpens();
        CompletableFuture<Void> replacing = menus.open(player, actionView("New", Material.EMERALD, newClicks));

        menus.close(player).join();
        assertEquals(2, adapter.menuCloses().size(), "the shown and pending menus are closed immediately");
        adapter.releaseMenuOpens();
        replacing.join();

        assertEquals(2, adapter.menuCloses().size(), "the late replacement is closed, not tracked");
        menus.click(player, 0).join();
        assertEquals(0, oldClicks.get());
        assertEquals(0, newClicks.get());
    }

    @Test
    void closeAllIsSafeWhenNothingIsOpen() {
        menus.closeAll().join();
        assertEquals(0, adapter.menuCloses().size());
    }
}
