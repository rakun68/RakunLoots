package me.rakkoon69.rakunloots.hologram.holographic;

import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI;
import me.filoghost.holographicdisplays.api.hologram.Hologram;
import me.filoghost.holographicdisplays.api.hologram.HologramLines;
import me.filoghost.holographicdisplays.api.hologram.PlaceholderSetting;
import me.filoghost.holographicdisplays.api.hologram.line.TextHologramLine;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The HolographicDisplays calls made through its API on the running plugin, on the main thread only, as
 * HolographicDisplays requires. Only its methods name HolographicDisplays' classes, so building it loads none of them.
 *
 * <p>HolographicDisplays puts the top of the first line at a hologram's position and stacks the lines down. Each
 * hologram here is lifted by its lines' height, as HolographicDisplays measures it with the server owner's space between
 * lines, so its lowest line ends at the given position, and it moves again when the number of lines changes.
 */
final class HolographicDisplaysApiCalls implements HolographicDisplaysCalls {

    private final Plugin plugin;
    private final Map<UUID, Placed> holograms = new HashMap<>();

    HolographicDisplaysApiCalls(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Creates an API hologram, which HolographicDisplays keeps in memory only, with placeholders off, then adds its
     * lines and lifts it, all in this one main thread call, before HolographicDisplays shows it on its next tick.
     */
    @Override
    public void create(UUID hologramId, HologramPosition position, List<String> lines) {
        if (holograms.containsKey(hologramId)) {
            throw new IllegalArgumentException("a hologram for " + hologramId + " is already drawn");
        }
        World world = plugin.getServer().getWorld(position.worldKey());
        if (world == null) {
            throw new IllegalStateException("world " + position.worldKey() + " is not loaded");
        }
        Hologram hologram = HolographicDisplaysAPI.get(plugin)
                .createHologram(new Location(world, position.x(), position.y(), position.z()));
        Placed placed = new Placed(hologram, world, position);
        holograms.put(hologramId, placed);
        try {
            hologram.setPlaceholderSetting(PlaceholderSetting.DISABLE_ALL);
            lines.forEach(hologram.getLines()::appendText);
            placed.lift();
        } catch (RuntimeException failure) {
            holograms.remove(hologramId);
            hologram.delete();
            throw failure;
        }
    }

    /**
     * Sets the text of the lines it has, adds or removes lines at the bottom, and lifts it again when their number
     * changed.
     */
    @Override
    public void setLines(UUID hologramId, List<String> lines) {
        Placed placed = holograms.get(hologramId);
        if (placed == null) {
            return;
        }
        HologramLines current = placed.hologram().getLines();
        int before = current.size();
        for (int index = 0; index < lines.size(); index++) {
            if (index < before) {
                ((TextHologramLine) current.get(index)).setText(lines.get(index));
            } else {
                current.appendText(lines.get(index));
            }
        }
        for (int index = before - 1; index >= lines.size(); index--) {
            current.remove(index);
        }
        if (before != lines.size()) {
            placed.lift();
        }
    }

    @Override
    public void remove(UUID hologramId) {
        Placed placed = holograms.get(hologramId);
        if (placed == null) {
            return;
        }
        placed.hologram().delete();
        holograms.remove(hologramId);
    }

    /**
     * A drawn hologram and where its lowest line ends.
     */
    private record Placed(Hologram hologram, World world, HologramPosition bottom) {

        void lift() {
            hologram.setPosition(world, bottom.x(), bottom.y() + hologram.getLines().getHeight(), bottom.z());
        }
    }
}
