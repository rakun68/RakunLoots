package me.rakkoon69.rakunloots.config.menu;

import me.rakkoon69.rakunloots.items.ItemReference;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One page of a menu file: its rows, its ordered content slots, its filler, and its buttons by name.
 */
public final class MenuPage {

    private final int rows;
    private final List<Integer> contentSlots;
    private final ItemReference filler;
    private final Map<String, MenuButton> buttons;

    MenuPage(int rows, Collection<Integer> contentSlots, ItemReference filler, Map<String, MenuButton> buttons) {
        this.rows = rows;
        this.contentSlots = List.copyOf(contentSlots);
        this.filler = Objects.requireNonNull(filler, "filler");
        this.buttons = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(buttons, "buttons")));
    }

    public int rows() {
        return rows;
    }

    /**
     * The slots the menu's entries fill, in the order written, with ranges expanded. A button's slot is not here.
     */
    public List<Integer> contentSlots() {
        return contentSlots;
    }

    public ItemReference filler() {
        return filler;
    }

    public Map<String, MenuButton> buttons() {
        return buttons;
    }

    /**
     * A copy of this page with the given buttons. The content slots, filler, and rows stay the same; this page is not
     * changed.
     */
    public MenuPage withButtons(Map<String, MenuButton> replacement) {
        return new MenuPage(rows, contentSlots, filler, replacement);
    }
}
