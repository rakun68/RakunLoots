package me.rakkoon69.rakunloots.config.menu;

import me.rakkoon69.rakunloots.items.ItemReference;

import java.util.Objects;
import java.util.Optional;

/**
 * One button of a menu page: its slot, its item reference, and the sound it plays when clicked.
 */
public final class MenuButton {

    private final int slot;
    private final ItemReference item;
    private final Optional<MenuSound> sound;

    MenuButton(int slot, ItemReference item, Optional<MenuSound> sound) {
        this.slot = slot;
        this.item = Objects.requireNonNull(item, "item");
        this.sound = Objects.requireNonNull(sound, "sound");
    }

    public int slot() {
        return slot;
    }

    public ItemReference item() {
        return item;
    }

    public Optional<MenuSound> sound() {
        return sound;
    }
}
