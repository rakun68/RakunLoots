package me.rakkoon69.rakunloots.hologram.decent;

import eu.decentsoftware.holograms.api.utils.Common;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * DecentHolograms in memory. It refuses what DHAPI 2.10.1 refuses: a name outside {@link Common#NAME_REGEX}, read from
 * the real jar, and a name it already has, both with {@link IllegalArgumentException}, and null lines on an update. A
 * hologram created with save to file on is written to its files, as DecentHolograms writes
 * {@code holograms/<name>.yml}.
 */
public final class FakeDecentHolograms implements DecentHologramsCalls {

    /**
     * A hologram it draws now.
     */
    public record Drawn(HologramPosition position, List<String> lines, boolean downOrigin, int displayRange) {
    }

    private final Map<String, Drawn> holograms = new LinkedHashMap<>();
    private final List<NewHologram> created = new ArrayList<>();
    private final Set<String> files = new LinkedHashSet<>();

    /**
     * The real provider over these probes and this fake.
     */
    public static DecentHologramsProvider provider(HologramServer server, FakeDecentHolograms plugin) {
        return new DecentHologramsProvider(server, plugin);
    }

    @Override
    public synchronized void create(NewHologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        String name = hologram.name();
        if (!name.matches(Common.NAME_REGEX)) {
            throw new IllegalArgumentException(String.format(
                    "Hologram name can only contain alphanumeric characters, underscores and dashes! (%s)", name));
        }
        if (holograms.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Hologram with that name already exists! (%s)", name));
        }
        holograms.put(name, new Drawn(hologram.position(), hologram.lines(), hologram.downOrigin(),
                hologram.displayRange()));
        created.add(hologram);
        if (hologram.saveToFile()) {
            files.add("holograms/" + name + ".yml");
        }
    }

    @Override
    public synchronized boolean setLines(String name, List<String> lines) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lines, "lines");
        Drawn drawn = holograms.get(name);
        if (drawn == null) {
            return false;
        }
        holograms.put(name, new Drawn(drawn.position(), List.copyOf(lines), drawn.downOrigin(), drawn.displayRange()));
        return true;
    }

    @Override
    public synchronized void remove(String name) {
        Objects.requireNonNull(name, "name");
        holograms.remove(name);
    }

    /**
     * A hologram some other plugin or an admin made, created the way {@code /dh create} makes one.
     */
    public synchronized void createForeign(String name, HologramPosition position) {
        if (holograms.putIfAbsent(name, new Drawn(position, List.of("someone else's"), false, 48)) != null) {
            throw new IllegalArgumentException(String.format("Hologram with that name already exists! (%s)", name));
        }
        files.add("holograms/" + name + ".yml");
    }

    /**
     * What {@code /dh reload} does to holograms that were never saved: they are gone.
     */
    public synchronized void reload() {
        holograms.keySet().removeIf(name -> !files.contains("holograms/" + name + ".yml"));
    }

    public synchronized Map<String, Drawn> holograms() {
        return Map.copyOf(holograms);
    }

    /**
     * Every create call that went through, in order.
     */
    public synchronized List<NewHologram> created() {
        return List.copyOf(created);
    }

    /**
     * The hologram files it wrote.
     */
    public synchronized Set<String> files() {
        return Set.copyOf(files);
    }
}
