package me.rakkoon69.rakunloots.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One loot's hologram, from its {@code hologram:} section. Held on the config snapshot beside the loot, never on it.
 *
 * @param enabled   whether a box of this loot shows a hologram
 * @param height    blocks above the block
 * @param viewRange blocks; the loot's own {@code view_range}, or the global {@code hologram.view_range}
 * @param lines     the loot's own {@code lines}; empty uses the {@code hologram_lines} message
 */
public record HologramSettings(boolean enabled, double height, int viewRange, Optional<List<String>> lines) {

    public static final boolean DEFAULT_ENABLED = true;
    public static final double DEFAULT_HEIGHT = 1.5;
    public static final int DEFAULT_VIEW_RANGE = 48;

    public HologramSettings {
        Objects.requireNonNull(lines, "lines");
        lines = lines.map(List::copyOf);
    }
}
