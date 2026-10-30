package me.rakkoon69.rakunloots.menu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A menu in plugin types: its title, its rows, and the icon on each slot.
 */
public final class MenuView {

    private final String title;
    private final int rows;
    private final Map<Integer, MenuIcon> icons;

    public MenuView(String title, int rows, Map<Integer, MenuIcon> icons) {
        this.title = Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be from 1 to 6, not " + rows);
        }
        this.rows = rows;
        Map<Integer, MenuIcon> copy = new LinkedHashMap<>(Objects.requireNonNull(icons, "icons"));
        copy.forEach((slot, icon) -> {
            Objects.requireNonNull(icon, "icon");
            if (slot < 0 || slot >= rows * 9) {
                throw new IllegalArgumentException("slot " + slot + " is outside " + (rows * 9) + " slots");
            }
        });
        this.icons = Collections.unmodifiableMap(copy);
    }

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    public Map<Integer, MenuIcon> icons() {
        return icons;
    }
}
