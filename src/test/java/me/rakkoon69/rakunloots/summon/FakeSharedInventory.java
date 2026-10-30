package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.server.SharedInventory;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A shared inventory the fake server adapter made. Like the real one, it refuses a size that is not a chest size and
 * a slot outside it, copies the stacks it is given and hands out copies, and every viewer sees the same slots, so a
 * stack one viewer takes is gone for every other. Like the real one, it is the holder of a Bukkit inventory, so a close
 * event can find it.
 */
public final class FakeSharedInventory implements SharedInventory, InventoryHolder {

    private final String title;
    private final int size;
    private final Map<Integer, ItemStack> slots = new TreeMap<>();
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private Inventory inventory;
    private volatile boolean closed;

    FakeSharedInventory(String title, int size, Map<Integer, ItemStack> contents) {
        this.title = Objects.requireNonNull(title, "title");
        Objects.requireNonNull(contents, "contents");
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("size must be a multiple of 9 from 9 to 54, not " + size);
        }
        this.size = size;
        contents.forEach((slot, stack) -> {
            Objects.requireNonNull(stack, "stack");
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("slot " + slot + " is outside a size of " + size);
            }
            slots.put(slot, stack.clone());
        });
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public synchronized Map<Integer, ItemStack> contents() {
        Map<Integer, ItemStack> copy = new TreeMap<>();
        slots.forEach((slot, stack) -> copy.put(slot, stack.clone()));
        return copy;
    }

    /**
     * A viewer takes the whole stack in a slot, as a click in the open inventory would.
     *
     * @return the stack taken
     * @throws IllegalStateException when the viewer does not see this inventory or the slot is empty
     */
    public synchronized ItemStack take(UUID viewer, int slot) {
        if (!viewers.contains(viewer)) {
            throw new IllegalStateException(viewer + " does not see this inventory");
        }
        ItemStack taken = slots.remove(slot);
        if (taken == null) {
            throw new IllegalStateException("slot " + slot + " is empty");
        }
        return taken;
    }

    /**
     * The players who see this inventory now.
     */
    public Set<UUID> viewers() {
        return Set.copyOf(viewers);
    }

    void open(UUID viewer) {
        viewers.add(viewer);
    }

    void closeFor(Set<UUID> players) {
        closed = true;
        viewers.removeAll(players);
    }

    boolean closed() {
        return closed;
    }

    /**
     * A viewer closes their own view, as the player pressing escape would.
     */
    public void close(UUID viewer) {
        viewers.remove(viewer);
    }

    /**
     * A Bukkit inventory held by this one, made on first use on the running MockBukkit server, for a test that shows
     * it to a MockBukkit player. Its slots are not this inventory's: {@link #take} and {@link #contents()} do not see
     * it.
     */
    @Override
    public synchronized @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size, title);
        }
        return inventory;
    }
}
