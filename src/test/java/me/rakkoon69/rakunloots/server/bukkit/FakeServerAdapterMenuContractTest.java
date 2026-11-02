package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.server.MenuHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeServerAdapterMenuContractTest {

    private FakeServerAdapter adapter;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        adapter = new FakeServerAdapter(UUID.randomUUID());
        playerId = UUID.randomUUID();
        adapter.addOnlinePlayer(playerId);
    }

    @Test
    void guardedOpenAcceptsWhenExpectedOldIsCurrentVisibleMenu() {
        MenuHandle first = adapter.createMenu(playerId, "First", 27, Map.of());
        assertEquals(Optional.of(first), adapter.openMenu(playerId, first).join());

        MenuHandle second = adapter.createMenu(playerId, "Second", 27, Map.of());
        assertEquals(Optional.of(second), adapter.openMenu(playerId, second, first).join());
        assertEquals(Optional.of(second), adapter.visibleMenu(playerId).map(m -> (MenuHandle) m));
    }

    @Test
    void guardedOpenRejectsWhenExpectedOldIsClosedOrNoLongerVisible() {
        MenuHandle first = adapter.createMenu(playerId, "First", 27, Map.of());
        adapter.openMenu(playerId, first).join();
        MenuHandle second = adapter.createMenu(playerId, "Second", 27, Map.of());

        // 1. Rejects when player closed the view
        adapter.closePlayerView(playerId);
        assertEquals(Optional.empty(), adapter.openMenu(playerId, second, first).join());

        // 2. Rejects when first was closed via closeMenu
        adapter.openMenu(playerId, first).join();
        adapter.closeMenu(first).join();
        assertEquals(Optional.empty(), adapter.openMenu(playerId, second, first).join());

        // 3. Rejects when a different menu is now visible
        MenuHandle other = adapter.createMenu(playerId, "Other", 27, Map.of());
        adapter.openMenu(playerId, other).join();
        assertEquals(Optional.empty(), adapter.openMenu(playerId, second, first).join());
    }

    @Test
    void guardedOpenWhileHeldRechecksVisibilityAndClosedStateWhenReleased() {
        MenuHandle first = adapter.createMenu(playerId, "First", 27, Map.of());
        adapter.openMenu(playerId, first).join();
        MenuHandle second = adapter.createMenu(playerId, "Second", 27, Map.of());

        adapter.holdMenuOpens();
        CompletableFuture<Optional<MenuHandle>> replacement = adapter.openMenu(playerId, second, first);
        assertFalse(replacement.isDone());

        // Player view closed while held
        adapter.closePlayerView(playerId);
        adapter.releaseMenuOpens();

        assertEquals(Optional.empty(), replacement.join());
    }
}
