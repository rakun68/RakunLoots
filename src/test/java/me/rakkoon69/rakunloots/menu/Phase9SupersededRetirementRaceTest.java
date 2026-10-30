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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase9SupersededRetirementRaceTest {

    private final UUID player = UUID.fromString("00000000-0000-0000-0000-0000000000c5");
    private final Scripted script = new Scripted();

    @BeforeEach
    void online() {
        script.delegate.addOnlinePlayer(player);
    }

    private static MenuView view(String title, Material material) {
        return new MenuView(title, 3, Map.of(0, new MenuIcon(new ItemStack(material), Optional.empty(),
                Optional.empty())));
    }

    @Test
    void aSupersededAttemptWaitsForALaterPendingReplacementAndMovesTheSubscriptionToIt() {
        long first = script.menus.openTracked(player, view("Menu", Material.DIAMOND)).join().orElseThrow();
        AtomicInteger retired = new AtomicInteger();
        assertTrue(script.menus.onRetired(player, first, retired::incrementAndGet));

        CompletableFuture<Optional<Long>> superseded =
                script.menus.replaceIf(player, first, view("First", Material.EMERALD));
        script.menus.replacementClosed(player, script.firstMenu.get());
        assertEquals(0, retired.get());

        CompletableFuture<Optional<Long>> later =
                script.menus.replaceIf(player, first, view("Second", Material.GOLD_INGOT));
        script.heldSecond.complete(Optional.of(script.secondMenu.get()));
        assertTrue(superseded.join().isEmpty(), "the first replacement lost to the later pending one");
        assertEquals(0, retired.get(), "the later replacement is still pending, so it is not terminal yet");

        script.heldThird.complete(Optional.of(script.thirdMenu.get()));
        long installed = later.join().orElseThrow();
        assertEquals(0, retired.get(), "the successful later replacement keeps one logical session");
        assertFalse(script.menus.onRetired(player, first, () -> {}));

        AtomicInteger moved = new AtomicInteger();
        assertTrue(script.menus.onRetired(player, installed, moved::incrementAndGet));
        script.menus.closeAll().join();
        assertEquals(1, retired.get());
        assertEquals(1, moved.get());
    }

    @Test
    void aFailedSupersededAttemptDoesNotRetireWhileALaterReplacementIsPending() {
        long first = script.menus.openTracked(player, view("Menu", Material.DIAMOND)).join().orElseThrow();
        AtomicInteger retired = new AtomicInteger();
        assertTrue(script.menus.onRetired(player, first, retired::incrementAndGet));

        CompletableFuture<Optional<Long>> superseded =
                script.menus.replaceIf(player, first, view("First", Material.EMERALD));
        script.menus.replacementClosed(player, script.firstMenu.get());
        CompletableFuture<Optional<Long>> later =
                script.menus.replaceIf(player, first, view("Second", Material.GOLD_INGOT));

        script.heldSecond.completeExceptionally(new IllegalStateException("first replacement failed"));
        assertThrows(CompletionException.class, superseded::join);
        assertEquals(0, retired.get(), "the later replacement is pending and still owns the session");

        script.heldThird.complete(Optional.of(script.thirdMenu.get()));
        long installed = later.join().orElseThrow();
        assertEquals(0, retired.get(), "the successful later replacement keeps the session");

        AtomicInteger moved = new AtomicInteger();
        assertTrue(script.menus.onRetired(player, installed, moved::incrementAndGet));
        script.menus.closeAll().join();
        assertEquals(1, retired.get());
        assertEquals(1, moved.get());
    }

    @Test
    void aLaterReplacementThatFailsAfterTheSupersededAttemptLandsRetiresTheClosedOldMenuOnce() {
        long first = script.menus.openTracked(player, view("Menu", Material.DIAMOND)).join().orElseThrow();
        AtomicInteger retired = new AtomicInteger();
        assertTrue(script.menus.onRetired(player, first, retired::incrementAndGet));

        CompletableFuture<Optional<Long>> superseded =
                script.menus.replaceIf(player, first, view("First", Material.EMERALD));
        script.menus.replacementClosed(player, script.firstMenu.get());
        CompletableFuture<Optional<Long>> later =
                script.menus.replaceIf(player, first, view("Second", Material.GOLD_INGOT));

        script.heldSecond.complete(Optional.of(script.secondMenu.get()));
        assertTrue(superseded.join().isEmpty());
        assertEquals(0, retired.get(), "the later replacement is pending");

        script.heldThird.completeExceptionally(new IllegalStateException("later replacement failed"));
        assertThrows(CompletionException.class, later::join);
        assertEquals(1, retired.get(), "the closed old menu retires when its last replacement fails");
        assertFalse(script.menus.onRetired(player, first, () -> {}));

        script.menus.closeAll().join();
        assertEquals(1, retired.get(), "retirement is delivered exactly once");
    }

    private static final class Scripted {

        private final FakeServerAdapter delegate = new FakeServerAdapter(UUID.randomUUID());
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicReference<MenuHandle> firstMenu = new AtomicReference<>();
        private final AtomicReference<MenuHandle> secondMenu = new AtomicReference<>();
        private final AtomicReference<MenuHandle> thirdMenu = new AtomicReference<>();
        private final CompletableFuture<Optional<MenuHandle>> heldSecond = new CompletableFuture<>();
        private final CompletableFuture<Optional<MenuHandle>> heldThird = new CompletableFuture<>();
        private final MenuService menus;

        private Scripted() {
            ServerAdapter adapter = (ServerAdapter) Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                    new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                        if (method.getName().equals("openMenu")) {
                            int number = opens.incrementAndGet();
                            if (number == 1) {
                                firstMenu.set((MenuHandle) args[1]);
                            } else if (number == 2) {
                                secondMenu.set((MenuHandle) args[1]);
                                return heldSecond;
                            } else if (number == 3) {
                                thirdMenu.set((MenuHandle) args[1]);
                                return heldThird;
                            }
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
            menus = new MenuService(adapter);
        }
    }
}
