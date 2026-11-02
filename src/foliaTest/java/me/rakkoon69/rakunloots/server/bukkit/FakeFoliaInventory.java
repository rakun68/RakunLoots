package me.rakkoon69.rakunloots.server.bukkit;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * A hand fake of a custom chest inventory for the fake Folia server. It refuses a size that is not a chest size, as
 * the server does. Only the calls the adapter and the tests make work.
 */
final class FakeFoliaInventory {

    private FakeFoliaInventory() {
    }

    /**
     * A custom chest inventory, as {@code Server#createInventory(InventoryHolder, int, String)} makes. An empty slot
     * reads as null.
     */
    static Inventory chest(InventoryHolder holder, int size, String title) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException(
                    "Size for custom inventory must be a multiple of 9 between 9 and 54 slots (got " + size + ")");
        }
        ItemStack[] slots = new ItemStack[size];
        return FoliaFakes.fake(Inventory.class, "FakeFoliaInventory[" + title + "]", (proxy, method, args) ->
                switch (method.getName() + "/" + args.length) {
                    case "getSize/0" -> size;
                    case "getType/0" -> InventoryType.CHEST;
                    case "getHolder/0" -> holder;
                    case "getItem/1" -> {
                        ItemStack stack = slots[(int) args[0]];
                        yield stack == null || stack.getType().isAir() ? null : stack;
                    }
                    case "setItem/2" -> {
                        slots[(int) args[0]] = (ItemStack) args[1];
                        yield null;
                    }
                    case "getContents/0" -> Arrays.copyOf(slots, size);
                    default -> FoliaFakes.UNHANDLED;
                });
    }
}
