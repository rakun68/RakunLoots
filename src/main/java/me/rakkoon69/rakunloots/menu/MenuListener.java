package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.server.MenuHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Claims every click and drag in a menu, and tells the service when a menu closes.
 */
public final class MenuListener implements Listener {

    private final MenuService menus;

    public MenuListener(MenuService menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < top.getSize()) {
            menus.clickLater(event.getWhoClicked().getUniqueId(), slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            UUID playerId = event.getPlayer().getUniqueId();
            if (menus.isPhysicalOpen(playerId)) {
                menus.replacementClosed(playerId, holder.menuHandle());
            } else {
                menus.closed(playerId, holder.menuHandle());
            }
        }
    }
}
