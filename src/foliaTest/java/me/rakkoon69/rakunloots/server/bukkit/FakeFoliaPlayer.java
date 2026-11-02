package me.rakkoon69.rakunloots.server.bukkit;

import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A hand fake of an online Folia player standing at a position in a fake world, with an entity scheduler of its
 * own. It records each read of its world state, each inventory open and close, and each chat message, with the owner
 * whose task it ran in.
 */
final class FakeFoliaPlayer {

    /**
     * One read of the player's world state.
     *
     * @param owner the owner whose task the read ran in, if any
     */
    record StateRead(String method, Optional<FakeFoliaTicks.Owner> owner, String thread) {
    }

    /**
     * One chat message sent to the player.
     *
     * @param owner the owner whose task sent it, if any
     */
    record Message(String text, Optional<FakeFoliaTicks.Owner> owner) {
    }

    /**
     * One sound played to the player, with the owner whose task played it.
     */
    record Sound(String key, float volume, float pitch, Optional<FakeFoliaTicks.Owner> owner) {
    }

    private final UUID id = UUID.randomUUID();
    private final FakeEntityScheduler scheduler;
    private final List<StateRead> reads = new CopyOnWriteArrayList<>();
    private final List<StateRead> inventoryCalls = new CopyOnWriteArrayList<>();
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final List<Sound> sounds = new CopyOnWriteArrayList<>();
    private volatile Inventory top;
    private final List<Optional<FakeFoliaTicks.Owner>> inventoryOwners = new CopyOnWriteArrayList<>();
    private final ItemStack[] storage = new ItemStack[36];
    private final PlayerInventory inventory = FoliaFakes.fake(PlayerInventory.class, "FakeFoliaPlayerInventory",
            (proxy, method, args) -> switch (method.getName() + "/" + args.length) {
                case "getStorageContents/0" -> {
                    inventoryOwners.add(FakeFoliaTicks.currentOwner());
                    ItemStack[] copy = new ItemStack[storage.length];
                    for (int slot = 0; slot < storage.length; slot++) {
                        copy[slot] = storage[slot] == null ? null : storage[slot].clone();
                    }
                    yield copy;
                }
                case "setItem/2" -> {
                    inventoryOwners.add(FakeFoliaTicks.currentOwner());
                    int slot = (int) args[0];
                    ItemStack written = (ItemStack) args[1];
                    if (slot < 0 || slot >= storage.length) {
                        throw new IndexOutOfBoundsException("only the storage slots are faked: " + slot);
                    }
                    storage[slot] = written == null || written.getType().isAir() ? null : written.clone();
                    yield null;
                }
                default -> FoliaFakes.UNHANDLED;
            });
    private final Player player;
    private volatile FakeFoliaWorld world;
    private final double x;
    private final double y;
    private final double z;

    FakeFoliaPlayer(FakeFoliaTicks ticks, FakeFoliaWorld world, double x, double y, double z) {
        this.scheduler = new FakeEntityScheduler(ticks, id);
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.player = FoliaFakes.fake(Player.class, "FakeFoliaPlayer[" + id + "]", (proxy, method, args) ->
                switch (method.getName() + "/" + args.length) {
                    case "getUniqueId/0" -> id;
                    case "getInventory/0" -> inventory;
                    case "getScheduler/0" -> scheduler;
                    case "getLocation/0" -> {
                        record("getLocation");
                        yield new Location(this.world.world(), this.x, this.y, this.z);
                    }
                    case "getWorld/0" -> {
                        record("getWorld");
                        yield this.world.world();
                    }
                    case "openInventory/1" -> {
                        if (!(args[0] instanceof Inventory inventory)) {
                            yield FoliaFakes.UNHANDLED;
                        }
                        inventoryCalls.add(new StateRead("openInventory", FakeFoliaTicks.currentOwner(),
                                Thread.currentThread().getName()));
                        top = inventory;
                        yield view();
                    }
                    case "getOpenInventory/0" -> view();
                    case "closeInventory/0" -> {
                        inventoryCalls.add(new StateRead("closeInventory", FakeFoliaTicks.currentOwner(),
                                Thread.currentThread().getName()));
                        top = null;
                        yield null;
                    }
                    case "sendMessage/1" -> {
                        if (!(args[0] instanceof String text)) {
                            yield FoliaFakes.UNHANDLED;
                        }
                        messages.add(new Message(text, FakeFoliaTicks.currentOwner()));
                        yield null;
                    }
                    case "playSound/4" -> {
                        if (!(args[1] instanceof String key) || !(args[2] instanceof Float volume)
                                || !(args[3] instanceof Float pitch)) {
                            yield FoliaFakes.UNHANDLED;
                        }
                        sounds.add(new Sound(key, volume, pitch, FakeFoliaTicks.currentOwner()));
                        yield null;
                    }
                    default -> FoliaFakes.UNHANDLED;
                });
    }

    /**
     * Fills every storage slot with a full stack of the filler, leaving the first slots empty.
     */
    void fillStorage(ItemStack filler, int emptySlots) {
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = filler.clone();
            stack.setAmount(stack.getMaxStackSize());
            storage[slot] = slot < emptySlots ? null : stack;
        }
    }

    /**
     * How many of the item the storage slots hold.
     */
    int stored(ItemStack item) {
        int total = 0;
        for (ItemStack stack : storage) {
            if (stack != null && stack.isSimilar(item)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * The owner whose task ran each read or fill of the player's inventory.
     */
    List<Optional<FakeFoliaTicks.Owner>> inventoryOwners() {
        return List.copyOf(inventoryOwners);
    }

    /**
     * The player's open view. Only its top inventory is faked; with none open, the player sees their own
     * inventory, and the top is null.
     */
    private InventoryView view() {
        Inventory shown = top;
        return new InventoryView() {
            @Override
            public @NotNull Inventory getTopInventory() {
                return shown;
            }

            @Override
            public @NotNull Inventory getBottomInventory() {
                throw new UnsupportedOperationException("not faked");
            }

            @Override
            public @NotNull HumanEntity getPlayer() {
                return player;
            }

            @Override
            public @NotNull InventoryType getType() {
                return shown == null ? InventoryType.CRAFTING : shown.getType();
            }

            @Override
            public @NotNull String getTitle() {
                throw new UnsupportedOperationException("not faked");
            }

            @Override
            public @NotNull String getOriginalTitle() {
                throw new UnsupportedOperationException("not faked");
            }

            @Override
            public void setTitle(@NotNull String title) {
                throw new UnsupportedOperationException("not faked");
            }
        };
    }

    /**
     * The top inventory of the player's open view, or null when they see only their own inventory.
     */
    Inventory topInventory() {
        return top;
    }

    /**
     * Each inventory open and close, with the owner whose task it ran in.
     */
    List<StateRead> inventoryCalls() {
        return List.copyOf(inventoryCalls);
    }

    /**
     * Each chat message, with the owner whose task sent it.
     */
    List<Message> messages() {
        return List.copyOf(messages);
    }

    /**
     * Each sound played, with the owner whose task played it.
     */
    List<Sound> sounds() {
        return List.copyOf(sounds);
    }

    UUID id() {
        return id;
    }

    Player player() {
        return player;
    }

    FakeFoliaWorld world() {
        return world;
    }

    FakeEntityScheduler scheduler() {
        return scheduler;
    }

    void moveTo(FakeFoliaWorld world) {
        this.world = world;
    }

    List<StateRead> reads() {
        return List.copyOf(reads);
    }

    private void record(String method) {
        reads.add(new StateRead(method, FakeFoliaTicks.currentOwner(), Thread.currentThread().getName()));
    }
}
