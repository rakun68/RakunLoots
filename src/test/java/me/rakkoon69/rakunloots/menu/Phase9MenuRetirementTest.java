package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.server.MenuHandle;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase9MenuRetirementTest {

    private final UUID player = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private final UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000c4");
    private final FakeServerAdapter delegate = new FakeServerAdapter(UUID.randomUUID());
    private final List<MenuHandle> handles = new CopyOnWriteArrayList<>();
    private final ServerAdapter adapter = recording(delegate);
    private final MenuService menus = new MenuService(adapter);

    @BeforeEach
    void online() {
        delegate.addOnlinePlayer(player);
        delegate.addOnlinePlayer(other);
    }

    private static MenuView view(String title, Material material) {
        return new MenuView(title, 3, Map.of(0, new MenuIcon(new ItemStack(material), Optional.empty(),
                Optional.empty())));
    }

    private ServerAdapter recording(FakeServerAdapter delegate) {
        return (ServerAdapter) Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(delegate, args);
                        if (method.getName().equals("openMenu") && result instanceof CompletableFuture<?> future) {
                            future.thenAccept(value -> {
                                if (value instanceof Optional<?> optional
                                        && optional.orElse(null) instanceof MenuHandle handle) {
                                    handles.add(handle);
                                }
                            });
                        }
                        return result;
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private long open(UUID playerId) {
        return menus.openTracked(playerId, view("Menu", Material.DIAMOND)).join().orElseThrow();
    }

    @Test
    void anOrdinaryCloseRetiresTheInstalledEpochOnceAndALateRegistrationCannotResurrectIt() {
        long epoch = open(player);
        AtomicInteger retired = new AtomicInteger();
        assertTrue(menus.onRetired(player, epoch, retired::incrementAndGet));

        menus.closed(player, handles.get(0));
        menus.closed(player, handles.get(0));

        assertEquals(1, retired.get());
        AtomicInteger late = new AtomicInteger();
        assertFalse(menus.onRetired(player, epoch, late::incrementAndGet));
        assertEquals(1, late.get(), "a registration after the close is disposed at once");
    }

    @Test
    void explicitClosesRetireOnlyTheEpochTheyActuallyClose() {
        long first = open(player);
        AtomicInteger firstRetired = new AtomicInteger();
        assertTrue(menus.onRetired(player, first, firstRetired::incrementAndGet));

        menus.close(player).join();
        assertEquals(1, firstRetired.get(), "an explicit close is terminal");

        long second = open(player);
        AtomicInteger secondRetired = new AtomicInteger();
        assertTrue(menus.onRetired(player, second, secondRetired::incrementAndGet));

        menus.closeIf(player, first).join();
        assertEquals(0, secondRetired.get(), "a stale close cannot drop the newer menu");
        menus.closeIf(player, second).join();
        assertEquals(1, secondRetired.get());
    }

    @Test
    void closeAllRetiresEveryOpenSessionAndOnlyOnce() {
        long mine = open(player);
        long theirs = open(other);
        AtomicInteger mineRetired = new AtomicInteger();
        AtomicInteger theirsRetired = new AtomicInteger();
        assertTrue(menus.onRetired(player, mine, mineRetired::incrementAndGet));
        assertTrue(menus.onRetired(other, theirs, theirsRetired::incrementAndGet));

        menus.closeAll().join();
        menus.closeAll().join();

        assertEquals(1, mineRetired.get());
        assertEquals(1, theirsRetired.get());
    }

    @Test
    void aFreshOpenRetiresTheEpochItSupersedes() {
        long old = open(player);
        AtomicInteger oldRetired = new AtomicInteger();
        assertTrue(menus.onRetired(player, old, oldRetired::incrementAndGet));

        long replacement = open(player);

        assertEquals(1, oldRetired.get(), "a fresh menu supersedes the old logical session");
        AtomicInteger replacementRetired = new AtomicInteger();
        assertTrue(menus.onRetired(player, replacement, replacementRetired::incrementAndGet));
        menus.closeAll().join();
        assertEquals(1, replacementRetired.get());
    }

    @Test
    void replaceIfMovesTheSubscriptionToTheEpochItInstallsAndTheOldCloseIsNotTerminal() {
        long first = open(player);
        AtomicInteger retired = new AtomicInteger();
        assertTrue(menus.onRetired(player, first, retired::incrementAndGet));

        long second = menus.replaceIf(player, first, view("Page", Material.EMERALD)).join().orElseThrow();

        assertEquals(0, retired.get(), "navigation keeps the logical session");
        menus.closed(player, handles.get(0));
        assertEquals(0, retired.get(), "the old physical close during navigation is not a terminal close");

        AtomicInteger moved = new AtomicInteger();
        assertTrue(menus.onRetired(player, second, moved::incrementAndGet));
        menus.closeAll().join();
        assertEquals(1, retired.get(), "the transferred subscription retires exactly once");
        assertEquals(1, moved.get());
    }

    @Test
    void aFailedReplacementRetiresTheOldEpochWhenItsMenuWasPhysicallyClosed() {
        long first = open(player);
        AtomicInteger retired = new AtomicInteger();
        assertTrue(menus.onRetired(player, first, retired::incrementAndGet));
        delegate.holdMenuOpens();
        CompletableFuture<Optional<Long>> replacing = menus.replaceIf(player, first, view("Page", Material.EMERALD));
        assertFalse(replacing.isDone());
        menus.replacementClosed(player, handles.get(0));
        assertEquals(0, retired.get(), "a pending replacement is not terminal yet");

        delegate.refuseMenuOpens();
        delegate.releaseMenuOpens();
        assertTrue(replacing.join().isEmpty());
        assertEquals(1, retired.get(), "the failed replacement left no menu, so the session retires");
    }

    @Test
    void aFailedReplacementThatLeavesTheOldMenuOpenKeepsTheSubscription() {
        long first = open(player);
        AtomicInteger retired = new AtomicInteger();
        assertTrue(menus.onRetired(player, first, retired::incrementAndGet));
        delegate.failMenuOpens();

        assertThrows(CompletionException.class,
                () -> menus.replaceIf(player, first, view("Page", Material.EMERALD)).join());
        assertEquals(0, retired.get(), "the old menu is still open and still owns the session");

        menus.close(player).join();
        assertEquals(1, retired.get());
    }

    @Test
    void anUnqualifiedClosedCallWhileReplacementWaitsIsTerminalAndCancelsPendingReplacement() {
        long first = open(player);
        AtomicInteger retired = new AtomicInteger();
        assertTrue(menus.onRetired(player, first, retired::incrementAndGet));
        delegate.holdMenuOpens();
        CompletableFuture<Optional<Long>> replacing = menus.replaceIf(player, first, view("Page", Material.EMERALD));
        assertFalse(replacing.isDone());
        menus.closed(player, handles.get(0));
        assertEquals(1, retired.get(), "unqualified closed is terminal and retires the session immediately");

        delegate.releaseMenuOpens();
        assertTrue(replacing.join().isEmpty(), "pending replacement was cancelled");
    }
}
