package me.rakkoon69.rakunloots.server;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A menu backed by one Bukkit custom inventory. Its holder is a {@link MenuHolder}, never a shared inventory, so the
 * menu listener can claim it and the loot box close path never sees it. Once closed, it is never shown again, so an
 * open that was still on its way does nothing.
 */
final class BukkitMenuInventory implements MenuHandle, MenuHolder {

    private final UUID playerId;
    private final String title;
    private final Inventory inventory;
    private volatile boolean closed;

    @SuppressWarnings("deprecation") // Paper deprecates the String title for components; Spigot has only this one.
    private BukkitMenuInventory(Server server, UUID playerId, String title, int size) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.title = Objects.requireNonNull(title, "title");
        this.inventory = server.createInventory(this, size, title);
    }

    /**
     * Makes the menu and fills it with copies of the stacks.
     *
     * @throws IllegalArgumentException when the size is not a chest size, or a slot is outside it
     */
    static BukkitMenuInventory create(Server server, UUID playerId, String title, int size,
                                      Map<Integer, ItemStack> contents) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(contents, "contents");
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("size must be a multiple of 9 from 9 to 54, not " + size);
        }
        contents.forEach((slot, stack) -> {
            Objects.requireNonNull(stack, "stack");
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("slot " + slot + " is outside a size of " + size);
            }
        });
        BukkitMenuInventory menu = new BukkitMenuInventory(server, playerId, title, size);
        menu.replace(contents);
        return menu;
    }

    /**
     * The menu as one a server adapter made.
     *
     * @throws IllegalArgumentException when no server adapter made it
     */
    static BukkitMenuInventory of(MenuHandle menu) {
        Objects.requireNonNull(menu, "menu");
        if (!(menu instanceof BukkitMenuInventory made)) {
            throw new IllegalArgumentException("not made by a server adapter: " + menu);
        }
        return made;
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    String title() {
        return title;
    }

    int size() {
        return inventory.getSize();
    }

    @Override
    public MenuHandle menuHandle() {
        return this;
    }

    /**
     * The Bukkit inventory, so a close event can find its menu through the holder.
     */
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Replaces every slot with copies of the given stacks, clearing the ones left out. Call it on the thread that
     * owns the viewer.
     *
     * @throws IllegalArgumentException when a slot is outside the inventory, or a stack is null
     */
    void replace(Map<Integer, ItemStack> contents) {
        Objects.requireNonNull(contents, "contents");
        contents.forEach((slot, stack) -> {
            Objects.requireNonNull(stack, "stack");
            if (slot < 0 || slot >= inventory.getSize()) {
                throw new IllegalArgumentException("slot " + slot + " is outside a size of " + inventory.getSize());
            }
        });
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = contents.get(slot);
            inventory.setItem(slot, stack == null ? null : stack.clone());
        }
    }

    private static final ThreadLocal<UUID> PHYSICAL_OPENING = new ThreadLocal<>();

    static boolean isPhysicalOpen(UUID playerId) {
        return Objects.equals(PHYSICAL_OPENING.get(), playerId);
    }

    /**
     * Shows the menu to the player. Call it on the thread that owns the player.
     *
     * @return whether the player sees it; false when the menu was closed before this ran, or another plugin
     * cancelled the open
     */
    boolean open(Player player) {
        return open(player, null);
    }

    /**
     * Shows the menu to the player as a replacement for expectedOld. Call it on the thread that owns the player.
     */
    boolean open(Player player, MenuHandle expectedOld) {
        if (closed) {
            return false;
        }
        if (expectedOld != null) {
            var openView = player.getOpenInventory();
            Inventory currentTop = openView == null ? null : openView.getTopInventory();
            Inventory expectedInv = BukkitMenuInventory.of(expectedOld).getInventory();
            if (currentTop != expectedInv) {
                return false;
            }
        }
        PHYSICAL_OPENING.set(player.getUniqueId());
        try {
            player.openInventory(inventory);
        } finally {
            PHYSICAL_OPENING.remove();
        }
        return player.getOpenInventory().getTopInventory() == inventory;
    }

    /**
     * Closes the menu for good, so an open that has not run yet does nothing. Safe on any thread.
     */
    void markClosed() {
        closed = true;
    }

    /**
     * Closes the player's view while it still shows this menu. Call it on the thread that owns the player.
     */
    void close(Player player) {
        closed = true;
        if (player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
    }

    /**
     * Shows the menu to an online player. Call it on the main thread, which owns every player on Paper and Spigot.
     *
     * @return whether the player sees it; false when the player is not online
     */
    boolean openOnMainThread(Server server, UUID playerId) {
        return openOnMainThread(server, playerId, null);
    }

    /**
     * Shows the menu to an online player as a replacement for expectedOld. Call it on the main thread.
     */
    boolean openOnMainThread(Server server, UUID playerId, MenuHandle expectedOld) {
        Player player = server.getPlayer(playerId);
        return player != null && open(player, expectedOld);
    }

    /**
     * Closes the viewer's view while it still shows this menu. Call it on the main thread.
     */
    void closeOnMainThread(Server server, UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            close(player);
        }
    }

    /**
     * A copy of each stack by slot, for the slots that hold one now.
     */
    Map<Integer, ItemStack> contents() {
        Map<Integer, ItemStack> contents = new TreeMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                contents.put(slot, stack.clone());
            }
        }
        return contents;
    }
}
