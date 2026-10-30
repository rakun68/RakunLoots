package me.rakkoon69.rakunloots.hologram.decent;

import me.rakkoon69.rakunloots.hologram.HologramPosition;

import java.util.List;
import java.util.Objects;

/**
 * The DecentHolograms calls the provider makes, by hologram name. {@link DhapiCalls} makes them through
 * DecentHolograms' own API; a test fakes them.
 */
interface DecentHologramsCalls {

    /**
     * Creates and shows a hologram, as {@code DHAPI.createHologram} does, with its origin, display range, and lines set
     * before any player sees it.
     *
     * @throws IllegalArgumentException when the name breaks DecentHolograms' name rule (letters, digits, {@code _}, and
     *                                  {@code -}), or DecentHolograms already has a hologram of that name
     */
    void create(NewHologram hologram);

    /**
     * Replaces the lines of the hologram of that name, as {@code DHAPI.setHologramLines} does, and realigns them.
     *
     * @return false when DecentHolograms has no hologram of that name, such as after its own reload
     */
    boolean setLines(String name, List<String> lines);

    /**
     * Deletes the hologram of that name, as {@code DHAPI.removeHologram} does. Nothing happens when there is none.
     */
    void remove(String name);

    /**
     * A hologram to create.
     *
     * @param name         unique among DecentHolograms' holograms
     * @param position     where its origin sits
     * @param lines        top line first
     * @param saveToFile   whether DecentHolograms writes it to its {@code holograms} folder
     * @param downOrigin   whether the position is the hologram's bottom rather than its top
     * @param displayRange blocks within which players see it
     */
    record NewHologram(
            String name,
            HologramPosition position,
            List<String> lines,
            boolean saveToFile,
            boolean downOrigin,
            int displayRange
    ) {

        public NewHologram {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(position, "position");
            lines = List.copyOf(lines);
        }
    }
}
