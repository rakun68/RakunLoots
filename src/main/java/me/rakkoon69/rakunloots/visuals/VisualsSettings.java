package me.rakkoon69.rakunloots.visuals;

import java.util.Objects;
import java.util.Optional;

/**
 * One loot's glow and laser, from its {@code glow:} and {@code laser:} sections. Held on the config snapshot beside the
 * loot, never on it.
 *
 * @param glowEnabled       whether a box of this loot glows
 * @param glowColor         the colour it glows in
 * @param laser             the beam it shows to the sky; empty for none
 * @param laserViewDistance blocks within which players see the beam
 * @param laserStartOffset  blocks above the bottom of the box's block where the beam's entity sits
 */
public record VisualsSettings(boolean glowEnabled, GlowColor glowColor, Optional<LaserType> laser,
                              int laserViewDistance, int laserStartOffset) {

    public static final boolean DEFAULT_GLOW_ENABLED = false;
    public static final GlowColor DEFAULT_GLOW_COLOR = GlowColor.WHITE;
    public static final int DEFAULT_LASER_VIEW_DISTANCE = 50;
    /**
     * The bottom of the box's block. A player cannot pick a guardian laser's entities, so it can start there.
     */
    public static final int DEFAULT_LASER_START_OFFSET = 0;
    public static final int MIN_LASER_START_OFFSET = 0;
    public static final int MAX_LASER_START_OFFSET = 64;

    /**
     * A loot that says nothing: no glow and no laser.
     */
    public static final VisualsSettings NONE = new VisualsSettings(DEFAULT_GLOW_ENABLED, DEFAULT_GLOW_COLOR,
            Optional.empty(), DEFAULT_LASER_VIEW_DISTANCE, DEFAULT_LASER_START_OFFSET);

    public VisualsSettings {
        Objects.requireNonNull(glowColor, "glowColor");
        Objects.requireNonNull(laser, "laser");
    }
}
