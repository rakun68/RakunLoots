package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.config.MenuSound;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * One clickable slot of a menu: its item, what a click does, and the sound that click plays.
 */
public final class MenuIcon {

    private final ItemStack item;
    private final Optional<Runnable> action;
    private final Optional<MenuSound> sound;

    public MenuIcon(ItemStack item, Optional<Runnable> action, Optional<MenuSound> sound) {
        this.item = Objects.requireNonNull(item, "item").clone();
        this.action = Objects.requireNonNull(action, "action");
        this.sound = Objects.requireNonNull(sound, "sound");
    }

    public ItemStack item() {
        return item.clone();
    }

    public Optional<Runnable> action() {
        return action;
    }

    public Optional<MenuSound> sound() {
        return sound;
    }
}
