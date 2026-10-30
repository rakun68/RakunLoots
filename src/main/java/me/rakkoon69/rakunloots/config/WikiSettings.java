package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.items.ItemReference;

import java.util.Objects;
import java.util.Optional;

/**
 * A loot's {@code wiki} settings: whether the loots wiki lists it, and the icon it shows instead of the loot's block.
 * Held beside the loot, never on it.
 */
public final class WikiSettings {

    private static final WikiSettings SHOWN = new WikiSettings(true, Optional.empty());

    private final boolean show;
    private final Optional<ItemReference> icon;

    WikiSettings(boolean show, Optional<ItemReference> icon) {
        this.show = show;
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    /**
     * The defaults: listed, with the loot's block as the icon.
     */
    public static WikiSettings shown() {
        return SHOWN;
    }

    public boolean show() {
        return show;
    }

    /**
     * The configured icon, or empty to use the loot's block.
     */
    public Optional<ItemReference> icon() {
        return icon;
    }
}
