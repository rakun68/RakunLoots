package me.rakkoon69.rakunloots.menu;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.server.PaperServerAdapter;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuListenerTest {

    private ServerMock server;
    private PlayerMock player;
    private MenuService menus;
    private final AtomicInteger clicks = new AtomicInteger();

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        server.addWorld(new WorldMock(Material.GRASS_BLOCK, 0, 256, 63));
        JavaPlugin plugin = MockBukkit.loadWith(MenuTestPlugin.class,
                new PluginDescriptionFile("MenuTest", "1.0", MenuTestPlugin.class.getName()));
        menus = new MenuService(new PaperServerAdapter(plugin));
        server.getPluginManager().registerEvents(new MenuListener(menus), plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private void openMenu() {
        MenuView view = new MenuView("Menu", 3, Map.of(0, new MenuIcon(new ItemStack(Material.DIAMOND),
                Optional.of(clicks::incrementAndGet), Optional.empty())));
        CompletableFuture<Void> opened = menus.open(player.getUniqueId(), view);
        tickUntil(opened);
        opened.join();
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof me.rakkoon69.rakunloots.server.MenuHolder);
    }

    private void tickUntil(CompletableFuture<?> future) {
        for (int tick = 0; tick < 50 && !future.isDone(); tick++) {
            server.getScheduler().performOneTick();
        }
        assertTrue(future.isDone(), "the menu work ran on the main thread");
    }

    private InventoryClickEvent click(int rawSlot, ClickType type, InventoryAction action, InventoryType.SlotType slotType) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), slotType, rawSlot, type, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void everyClickInAMenuIsCancelledAndOnlyTopClicksReachTheMenu() {
        openMenu();

        assertTrue(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL, InventoryType.SlotType.CONTAINER).isCancelled());
        assertTrue(click(0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY,
                InventoryType.SlotType.CONTAINER).isCancelled());
        assertTrue(click(0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP,
                InventoryType.SlotType.CONTAINER).isCancelled());
        assertTrue(click(0, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR,
                InventoryType.SlotType.CONTAINER).isCancelled());
        assertTrue(click(0, ClickType.DROP, InventoryAction.DROP_ONE_SLOT,
                InventoryType.SlotType.CONTAINER).isCancelled());
        assertTrue(click(0, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP,
                InventoryType.SlotType.CONTAINER).isCancelled());
        assertTrue(click(36, ClickType.LEFT, InventoryAction.PICKUP_ALL,
                InventoryType.SlotType.CONTAINER).isCancelled());
        assertEquals(0, clicks.get(), "the actions wait for the next tick");
        server.getScheduler().performOneTick();
        assertEquals(6, clicks.get(), "the top slot dispatched every click; the bottom click did not");
    }

    @Test
    void aClickActionRunsOnTheNextTickNotInsideTheEvent() {
        AtomicInteger actions = new AtomicInteger();
        MenuView view = new MenuView("Menu", 3, Map.of(0, new MenuIcon(new ItemStack(Material.DIAMOND),
                Optional.of(() -> {
                    actions.incrementAndGet();
                    player.closeInventory();
                }), Optional.empty())));
        CompletableFuture<Void> opened = menus.open(player.getUniqueId(), view);
        tickUntil(opened);
        opened.join();

        assertTrue(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL, InventoryType.SlotType.CONTAINER).isCancelled());
        assertEquals(0, actions.get(), "the action waits for the next tick, when the event is over");
        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                instanceof me.rakkoon69.rakunloots.server.MenuHolder);

        server.getScheduler().performOneTick();
        assertEquals(1, actions.get());
    }

    @Test
    void aDeferredClickDoesNothingAfterTheMenuClosed() {
        AtomicInteger actions = new AtomicInteger();
        MenuView view = new MenuView("Menu", 3, Map.of(0, new MenuIcon(new ItemStack(Material.DIAMOND),
                Optional.of(actions::incrementAndGet), Optional.empty())));
        CompletableFuture<Void> opened = menus.open(player.getUniqueId(), view);
        tickUntil(opened);
        opened.join();

        click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL, InventoryType.SlotType.CONTAINER);
        menus.closeAll().join();
        server.getScheduler().performOneTick();

        assertEquals(0, actions.get(), "the click was for a menu that had gone");
    }

    @Test
    void everyDragOverAMenuIsCancelled() {
        openMenu();
        ItemStack cursor = new ItemStack(Material.DIAMOND);

        InventoryDragEvent top = new InventoryDragEvent(player.getOpenInventory(), cursor, cursor, true,
                Map.of(0, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(top);
        assertTrue(top.isCancelled());

        InventoryDragEvent bottom = new InventoryDragEvent(player.getOpenInventory(), cursor, cursor, true,
                Map.of(27, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(bottom);
        assertTrue(bottom.isCancelled(), "the menu is still open, so nothing in the player inventory moves");
    }

    @Test
    void aPlainInventoryIsNotClaimed() {
        Inventory plain = server.createInventory(null, 27);
        player.openInventory(plain);
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void aMenuCloseRetiresTheMenuThroughItsHolder() {
        openMenu();
        player.closeInventory();

        menus.click(player.getUniqueId(), 0).join();
        assertEquals(0, clicks.get(), "the closed menu no longer dispatches");
    }

    public static final class MenuTestPlugin extends JavaPlugin {
        public MenuTestPlugin(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
            super(loader, description, dataFolder, file);
        }
    }
}
