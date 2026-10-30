package me.rakkoon69.rakunloots.hologram.fancy;

import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import me.rakkoon69.rakunloots.hologram.HologramServer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * FancyHolograms 2.12.0's hologram manager in memory, over the real API's hologram data and hologram classes. It does
 * what {@code HologramManagerImpl} 2.12.0 does: names are kept in lower case; adding a hologram under a name it already
 * has replaces that one without a word; removing one also removes it from {@code holograms.yml}; saving writes only
 * the persistent holograms to {@code holograms.yml}; and reload unloads the persistent holograms and loads those saved.
 * Worlds are proxies keyed by UUID.
 */
public final class FakeFancyHolograms implements HologramManager {

    private final Map<String, Hologram> holograms = new LinkedHashMap<>();
    private final Map<String, Hologram> saved = new LinkedHashMap<>();
    private final List<HologramData> created = new ArrayList<>();
    private final Map<UUID, World> worlds = new LinkedHashMap<>();

    /**
     * The real provider over these probes and this fake, with the worlds this fake has loaded.
     */
    public static FancyHologramsProvider provider(HologramServer server, FakeFancyHolograms plugin) {
        return new FancyHologramsProvider(server, () -> plugin, plugin::world);
    }

    /**
     * Loads a world with that key.
     */
    public synchronized FakeFancyHolograms withWorld(UUID key) {
        worlds.put(key, world(key, "world_" + worlds.size()));
        return this;
    }

    synchronized World world(UUID key) {
        return worlds.get(key);
    }

    @Override
    public synchronized Optional<Hologram> getHologram(String name) {
        return Optional.ofNullable(holograms.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public synchronized Collection<Hologram> getPersistentHolograms() {
        return holograms.values().stream().filter(hologram -> hologram.getData().isPersistent()).toList();
    }

    @Override
    public synchronized Collection<Hologram> getHolograms() {
        return List.copyOf(holograms.values());
    }

    @Override
    public synchronized void addHologram(Hologram hologram) {
        holograms.put(hologram.getData().getName().toLowerCase(Locale.ROOT), hologram);
    }

    @Override
    public synchronized void removeHologram(Hologram hologram) {
        String name = hologram.getData().getName().toLowerCase(Locale.ROOT);
        if (holograms.remove(name) != null) {
            saved.remove(name);
        }
    }

    @Override
    public synchronized Hologram create(HologramData data) {
        Objects.requireNonNull(data, "data");
        created.add(data);
        FakeHologram hologram = new FakeHologram(data);
        hologram.createHologram();
        return hologram;
    }

    @Override
    public synchronized void loadHolograms() {
        saved.forEach(holograms::putIfAbsent);
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public synchronized void saveHolograms() {
        for (Hologram hologram : getPersistentHolograms()) {
            saved.put(hologram.getData().getName().toLowerCase(Locale.ROOT), hologram);
        }
    }

    @Override
    public synchronized void reloadHolograms() {
        holograms.values().removeIf(hologram -> hologram.getData().isPersistent());
        loadHolograms();
    }

    /**
     * A hologram an admin made with {@code /hologram create}: persistent, and saved.
     */
    public synchronized void createForeign(String name, UUID worldKey) {
        TextHologramData data = new TextHologramData(name, new Location(worlds.get(worldKey), 0.5, 70.0, 0.5));
        data.setText(new ArrayList<>(List.of("someone else's")));
        addHologram(new FakeHologram(data));
        saveHolograms();
    }

    /**
     * The data of the hologram of that name, or empty.
     */
    public synchronized Optional<HologramData> data(String name) {
        return getHologram(name).map(Hologram::getData);
    }

    public synchronized Set<String> names() {
        return new LinkedHashSet<>(holograms.keySet());
    }

    /**
     * The names of the holograms in {@code holograms.yml}.
     */
    public synchronized Set<String> savedNames() {
        return new LinkedHashSet<>(saved.keySet());
    }

    /**
     * The hologram data of every create call, in order.
     */
    public synchronized List<HologramData> created() {
        return List.copyOf(created);
    }

    private static World world(UUID key, String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getUID" -> key;
                    case "getName" -> name;
                    case "equals" -> self == args[0];
                    case "hashCode" -> key.hashCode();
                    case "toString" -> "World{" + name + "}";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * The real {@link Hologram} base class with no packets; FancyHolograms' own hologram sends them to players.
     */
    private static final class FakeHologram extends Hologram {

        FakeHologram(HologramData data) {
            super(data);
        }

        @Override
        public int getEntityId() {
            return 0;
        }

        @Override
        @SuppressWarnings("removal")
        public Display getDisplayEntity() {
            return null;
        }

        @Override
        protected void create() {
        }

        @Override
        protected void delete() {
        }

        @Override
        protected void update() {
        }

        @Override
        protected boolean show(Player player) {
            return false;
        }

        @Override
        protected boolean hide(Player player) {
            return false;
        }

        @Override
        protected void refresh(Player player) {
        }
    }
}
