package me.rakkoon69.rakunloots.config;

import java.util.Locale;
import java.util.Optional;

/**
 * The sixteen colours a block can glow in, named as Minecraft's chat colours are.
 */
public enum GlowColor {
    BLACK(0x000000), DARK_BLUE(0x0000AA), DARK_GREEN(0x00AA00), DARK_AQUA(0x00AAAA), DARK_RED(0xAA0000),
    DARK_PURPLE(0xAA00AA), GOLD(0xFFAA00), GRAY(0xAAAAAA), DARK_GRAY(0x555555), BLUE(0x5555FF), GREEN(0x55FF55),
    AQUA(0x55FFFF), RED(0xFF5555), LIGHT_PURPLE(0xFF55FF), YELLOW(0xFFFF55), WHITE(0xFFFFFF);

    private final int rgb;

    GlowColor(int rgb) {
        this.rgb = rgb;
    }

    /**
     * The colour as Minecraft draws the chat colour of this name, as 0xRRGGBB, which a block display's glow colour
     * override takes.
     */
    public int rgb() {
        return rgb;
    }

    /**
     * The colour a written name means, ignoring case and surrounding spaces, or empty when it names none.
     */
    public static Optional<GlowColor> parse(String written) {
        String name = written.trim().toUpperCase(Locale.ROOT);
        for (GlowColor color : values()) {
            if (color.name().equals(name)) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }
}
