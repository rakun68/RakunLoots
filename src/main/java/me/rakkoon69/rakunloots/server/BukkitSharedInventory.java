package me.rakkoon69.rakunloots.server;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A shared inventory backed by one Bukkit custom inventory, which it holds, so every viewer sees the same slots. Once
 * closed, it is never shown again, so an open that was still on its way does nothing.
 */
final class BukkitSharedInventory implements SharedInventory, InventoryHolder {

    private final String title;
    private final Inventory inventory;
    private volatile boolean closed;

    @SuppressWarnings("deprecation") // Paper deprecates the String title for components; Spigot has only this one.
    private BukkitSharedInventory(Server server, String title, int size) {
        this.title = title;
        this.inventory = server.createInventory(this, size, title);
    }

    /**
     * Makes the inventory and fills it with copies of the stacks.
     *
     * @throws IllegalArgumentException when the size is not a chest size, or a slot is outside it
     */
    static BukkitSharedInventory create(Server server, String title, int size, Map<Integer, ItemStack> contents) {
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
        BukkitSharedInventory shared = new BukkitSharedInventory(server, title, size);
        contents.forEach((slot, stack) -> shared.inventory.setItem(slot, stack.clone()));
        return shared;
    }

    /**
     * The inventory as one a server adapter made.
     *
     * @throws IllegalArgumentException when no server adapter made it
     */
    static BukkitSharedInventory of(SharedInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        if (!(inventory instanceof BukkitSharedInventory shared)) {
            throw new IllegalArgumentException("not made by a server adapter: " + inventory);
        }
        return shared;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int size() {
        return inventory.getSize();
    }

    @Override
    public Map<Integer, ItemStack> contents() {
        Map<Integer, ItemStack> contents = new TreeMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                contents.put(slot, stack.clone());
            }
        }
        return contents;
    }

    /**
     * The Bukkit inventory, so a close event can find its shared inventory through the holder.
     */
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Shows the inventory to the player. Call it on the thread that owns the player.
     *
     * @return whether the player sees it; false when the inventory was closed before this ran, or another plugin
     * cancelled the open
     */
    boolean open(Player player) {
        if (closed) {
            return false;
        }
        player.openInventory(inventory);
        return player.getOpenInventory().getTopInventory() == inventory;
    }

    /**
     * Closes the inventory for good, so an open that has not run yet does nothing. Safe on any thread.
     */
    void markClosed() {
        closed = true;
    }

    /**
     * Closes the player's view while it still shows this inventory. Call it on the thread that owns the player.
     */
    void close(Player player) {
        closed = true;
        if (player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
    }

    /**
     * Shows the inventory to an online player. Call it on the main thread, which owns every player on Paper and
     * Spigot.
     *
     * @return whether the player sees it; false when the player is not online
     */
    boolean openOnMainThread(Server server, UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        return open(player);
    }

    /**
     * Closes the views of the given players who are online. Call it on the main thread, which owns every player on
     * Paper and Spigot.
     */
    void closeOnMainThread(Server server, Set<UUID> viewers) {
        for (UUID playerId : viewers) {
            Player player = server.getPlayer(playerId);
            if (player != null) {
                close(player);
            }
        }
    }
}
