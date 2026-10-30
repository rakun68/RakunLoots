package me.rakkoon69.rakunloots.hologram.holographic;

import me.rakkoon69.rakunloots.hologram.HologramPosition;

import java.util.List;
import java.util.UUID;

/**
 * The HolographicDisplays calls the provider makes, by box id. {@link HolographicDisplaysApiCalls} makes them through
 * HolographicDisplays' own API; a test fakes them. Like HolographicDisplays' API, every call works only on the main
 * thread.
 */
interface HolographicDisplaysCalls {

    /**
     * Creates a hologram with its lowest line at the position and shows it to the players in range.
     *
     * @param lines top line first
     * @throws IllegalStateException    off the main thread, as HolographicDisplays refuses it, or when the position's
     *                                  world is not loaded
     * @throws IllegalArgumentException when it already draws a hologram for that id
     */
    void create(UUID hologramId, HologramPosition position, List<String> lines);

    /**
     * Replaces the lines of the hologram for that id, keeping its lowest line where it was. Nothing happens when it
     * draws none.
     *
     * @throws IllegalStateException off the main thread
     */
    void setLines(UUID hologramId, List<String> lines);

    /**
     * Deletes the hologram for that id. Nothing happens when it draws none.
     *
     * @throws IllegalStateException off the main thread
     */
    void remove(UUID hologramId);
}
