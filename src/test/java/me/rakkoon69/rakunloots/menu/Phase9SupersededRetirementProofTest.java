package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.server.MenuHandle;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
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

import static org.junit.jupiter.api.Assertions.*;

class Phase9SupersededRetirementProofTest {
    @Test
    void aClosedOldMenuRetiresWhenItsReplacementLosesToAFailedOpen() {
        UUID player = UUID.randomUUID();
        FakeServerAdapter delegate = new FakeServerAdapter(UUID.randomUUID());
        delegate.addOnlinePlayer(player);
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<MenuHandle> original = new AtomicReference<>();
        AtomicReference<MenuHandle> pending = new AtomicReference<>();
        CompletableFuture<Optional<MenuHandle>> held = new CompletableFuture<>();
        ServerAdapter adapter = (ServerAdapter) Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("openMenu")) {
                        int number = opens.incrementAndGet();
                        if (number == 2) {
                            pending.set((MenuHandle) args[1]);
                            return held;
                        }
                        if (number == 3) {
                            return CompletableFuture.failedFuture(new IllegalStateException("superseding open failed"));
                        }
                        original.set((MenuHandle) args[1]);
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        MenuService menus = new MenuService(adapter);
        MenuView view = new MenuView("Menu", 3, Map.of());
        long epoch = menus.openTracked(player, view).join().orElseThrow();
        AtomicInteger retired = new AtomicInteger();
        assertTrue(menus.onRetired(player, epoch, retired::incrementAndGet));
        CompletableFuture<Optional<Long>> replacement = menus.replaceIf(player, epoch, view);
        menus.replacementClosed(player, original.get());
        assertEquals(0, retired.get());
        assertThrows(CompletionException.class, () -> menus.openTracked(player, view).join());
        held.complete(Optional.of(pending.get()));
        assertTrue(replacement.join().isEmpty());
        assertEquals(1, retired.get(), "a physically closed, superseded old menu must retire its session");
        assertFalse(menus.onRetired(player, epoch, () -> {}), "the old epoch is no longer active");
        menus.closeAll().join();
        assertEquals(1, retired.get(), "retirement is delivered exactly once");
    }
}
