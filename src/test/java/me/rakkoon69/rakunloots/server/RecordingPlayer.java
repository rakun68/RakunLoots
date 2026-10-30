package me.rakkoon69.rakunloots.server;

import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A MockBukkit player that records the thread and recorded task each read of its location, each inventory open
 * and close, and each chat message ran in.
 */
final class RecordingPlayer extends PlayerMock {

    /**
     * One read of this player's location.
     */
    record LocationRead(boolean mainThread, OptionalInt task) {
    }

    /**
     * One inventory open or close on this player.
     */
    record InventoryCall(String method, boolean mainThread, OptionalInt task) {
    }

    /**
     * One chat message sent to this player.
     */
    record MessageCall(String message, boolean mainThread, OptionalInt task) {
    }

    private final List<LocationRead> locationReads = new CopyOnWriteArrayList<>();
    private final List<InventoryCall> inventoryCalls = new CopyOnWriteArrayList<>();
    private final List<MessageCall> messageCalls = new CopyOnWriteArrayList<>();
    private volatile boolean refuseOpens;

    RecordingPlayer(ServerMock server, String name) {
        super(server, name);
    }

    @Override
    public @NotNull Location getLocation() {
        // PlayerMock's constructor reads the location before this class's fields are set.
        if (locationReads != null) {
            locationReads.add(new LocationRead(Bukkit.isPrimaryThread(), RecordingScheduler.runningTask()));
        }
        return super.getLocation();
    }

    @Override
    public InventoryView openInventory(@NotNull Inventory inventory) {
        inventoryCalls.add(new InventoryCall("openInventory", Bukkit.isPrimaryThread(),
                RecordingScheduler.runningTask()));
        if (refuseOpens) {
            return null;
        }
        return super.openInventory(inventory);
    }

    /**
     * From now on every open is cancelled, as another plugin's cancelled InventoryOpenEvent does: nothing opens, and
     * Bukkit returns no view.
     */
    void refuseInventoryOpens() {
        refuseOpens = true;
    }

    @Override
    public void closeInventory() {
        // PlayerMock's constructor closes the inventory before this class's fields are set.
        if (inventoryCalls != null) {
            inventoryCalls.add(new InventoryCall("closeInventory", Bukkit.isPrimaryThread(),
                    RecordingScheduler.runningTask()));
        }
        super.closeInventory();
    }

    @Override
    public void sendMessage(@NotNull String message) {
        messageCalls.add(new MessageCall(message, Bukkit.isPrimaryThread(), RecordingScheduler.runningTask()));
        super.sendMessage(message);
    }

    List<LocationRead> locationReads() {
        return List.copyOf(locationReads);
    }

    List<InventoryCall> inventoryCalls() {
        return List.copyOf(inventoryCalls);
    }

    List<MessageCall> messageCalls() {
        return List.copyOf(messageCalls);
    }
}
