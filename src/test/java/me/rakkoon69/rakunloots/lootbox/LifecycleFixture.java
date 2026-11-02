package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.roll.RollWarning;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.storage.LootBoxRecord;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.LootBoxWarning;
import me.rakkoon69.rakunloots.storage.SqliteStores;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A lifecycle over the shipped config, a SQLite store in a temp folder, and the fake server adapter with a flat
 * world: grass at y 63 and air above, in loaded chunks -1 to 1.
 */
public final class LifecycleFixture {

    public static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
    public static final int GROUND_Y = 63;

    /**
     * Resolves written ids to namespaced lower case, as the server catalog does. Every tag holds one stand-in block.
     */
    public static final NameCatalog SERVER_LIKE = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            if (writtenId.startsWith("@") || writtenId.startsWith("#")) {
                return Optional.empty();
            }
            String lower = writtenId.toLowerCase(Locale.ROOT);
            return Optional.of(lower.contains(":") ? lower : "minecraft:" + lower);
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            return Set.of("minecraft:tag_" + namespacedTag.replace(':', '_'));
        }
    };

    public final Path folder;
    public final FakeServerAdapter adapter = new FakeServerAdapter(WORLD);
    public final Warnings warnings = new Warnings();

    public LifecycleFixture(Path folder) {
        this.folder = folder;
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                adapter.putChunk(flatChunk(chunkX, chunkZ), true, true);
            }
        }
    }

    public static ChunkView flatChunk(int chunkX, int chunkZ) {
        return new ChunkView(WORLD, chunkX, chunkZ, 0, 256,
                (x, y, z) -> y == GROUND_Y ? "minecraft:grass_block" : "minecraft:air");
    }

    /**
     * An air spot on the ground.
     */
    public static Spot airSpot(int x, int z) {
        return new Spot(WORLD, x, GROUND_Y + 1, z, "minecraft:air");
    }

    public static BlockPosition position(Spot spot) {
        return new BlockPosition(spot.x(), spot.y(), spot.z());
    }

    /**
     * The shipped config.yml, worlds.yml, blocks.yml, en.yml, and loots/airdrops.yml, plus any extra loot files.
     */
    public static ConfigSnapshot shippedConfig(Map<String, String> extraLoots) {
        return shippedConfig(extraLoots, "load");
    }

    /**
     * The shipped config with the shipped world's {@code unloaded_chunks} set to {@code load}, {@code generate}, or
     * {@code skip}. Smart summon cannot skip, so {@code skip} also sets the shipped world's mode to {@code spawn}.
     */
    public static ConfigSnapshot shippedConfig(Map<String, String> extraLoots, String unloadedChunks) {
        String worlds = resource("/worlds.yml");
        if (!worlds.contains("unloaded_chunks: generate")) {
            throw new IllegalStateException("the shipped worlds.yml no longer sets unloaded_chunks: generate");
        }
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(worlds.replace("unloaded_chunks: generate", "unloaded_chunks: " + unloadedChunks)
                        .replace("mode: smart", unloadedChunks.equals("skip") ? "mode: spawn" : "mode: smart"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"));
        extraLoots.forEach(sources::loot);
        return ConfigLoader.load(sources.build(), SERVER_LIKE, WarningKinds.all());
    }

    public static ConfigSnapshot shippedConfig() {
        return shippedConfig(Map.of());
    }

    public LootBoxStore openStore() throws Exception {
        return SqliteStores.open(folder);
    }

    public LootBoxLifecycle lifecycle(ConfigSnapshot config, LootBoxStore store, long seed) {
        return new LootBoxLifecycle(adapter, config, ItemSourceRegistry.create(), store, new Random(seed), warnings);
    }

    /**
     * Every box the store file holds, read through a second connection, by box id.
     */
    public Map<UUID, LootBoxRecord> storedBoxes(ConfigSnapshot config) throws Exception {
        try (LootBoxStore reader = openStore()) {
            return reader.loadAll(config, world -> true).boxes().stream()
                    .map(loaded -> loaded.box())
                    .collect(Collectors.toMap(LootBoxRecord::boxId, Function.identity()));
        }
    }

    public static String resource(String path) {
        try (InputStream in = LifecycleFixture.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Records every warning, from any thread.
     */
    public static final class Warnings implements me.rakkoon69.rakunloots.warning.Warnings {

        public final List<LifecycleWarning> lifecycle = new CopyOnWriteArrayList<>();
        public final List<RollWarning> roll = new CopyOnWriteArrayList<>();
        public final List<LootBoxWarning> store = new CopyOnWriteArrayList<>();
        public final List<String> debug = new CopyOnWriteArrayList<>();
        public volatile boolean debugOn;

        @Override
        public boolean debugEnabled() {
            return debugOn;
        }

        @Override
        public void debug(String line) {
            if (debugOn) {
                debug.add(line);
            }
        }

        @Override
        public void warn(Warning warning) {
            if (warning instanceof LifecycleWarning lw) {
                lifecycle.add(lw);
            } else if (warning instanceof RollWarning rw) {
                roll.add(rw);
            } else if (warning instanceof LootBoxWarning sw) {
                store.add(sw);
            }
        }

        public void warn(LifecycleWarning warning) {
            lifecycle.add(warning);
        }

        public void warn(RollWarning warning) {
            roll.add(warning);
        }

        public void warn(LootBoxWarning warning) {
            store.add(warning);
        }

        public List<LifecycleWarning.Kind> lifecycleKinds() {
            return lifecycle.stream().map(LifecycleWarning::kind).toList();
        }
    }
}
