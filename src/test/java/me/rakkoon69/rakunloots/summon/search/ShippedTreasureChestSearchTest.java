package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.fake.FakeColumn;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.server.NameCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real summon search over the shipped {@code loots/treasure_chest.yml} (only y_range widened, as a server did) in a
 * superflat world: bedrock at -64, dirt at -63 and -62, grass at -61, air above. The catalog gives the block tags as
 * Paper 26.2 does: its {@code #minecraft:dirt} holds only dirt, coarse dirt, and rooted dirt, and grass, podzol, and
 * mycelium sit in {@code #minecraft:grass_blocks}.
 */
class ShippedTreasureChestSearchTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    private static final NameCatalog PAPER_26_2 = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            return Optional.of(writtenId.startsWith("minecraft:") ? writtenId : "minecraft:" + writtenId);
        }

        @Override
        public Set<String> tagMembers(String tag) {
            return switch (tag) {
                case "minecraft:dirt" -> Set.of("minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt");
                case "minecraft:sand" -> Set.of("minecraft:sand", "minecraft:red_sand", "minecraft:suspicious_sand");
                case "minecraft:grass_blocks" -> Set.of("minecraft:grass_block", "minecraft:podzol",
                        "minecraft:mycelium");
                default -> Set.of("minecraft:" + tag.substring(tag.indexOf(':') + 1));
            };
        }
    };

    @Test
    void aSummonItemSearchFromAPlayerOnSuperflatGrassFindsASpotAboveTheGrass() {
        FakeServerAdapter adapter = superflat();
        SummonSearch search = new SummonSearch(adapter, config(), () -> 0L, new Random(7));

        SummonResult result = search.aroundCenter(WORLD_KEY, "treasure_chest", -1, -60, -1, FREE).join();

        Spot spot = result.spot().orElseThrow(() -> new AssertionError("miss " + result.missReason()));
        assertEquals(-60, spot.y());
        int distance = RingMath.chebyshev(-1, -1, spot.x(), spot.z());
        assertTrue(distance >= 4 && distance <= 16, "distance=" + distance);
    }

    @Test
    void theNaturalGroundGroupHoldsEverySurfaceBlockOfThe26_2Grass() {
        Set<String> ground = config().group("natural_ground").orElseThrow();

        for (String id : Set.of("minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
                "minecraft:moss_block", "minecraft:dirt", "minecraft:coarse_dirt")) {
            assertTrue(ground.contains(id), id + " is not natural ground");
        }
    }

    private static FakeServerAdapter superflat() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 1; chunkZ++) {
                FakeColumn column = new FakeColumn(WORLD_KEY, chunkX, chunkZ, -64, 320);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        column.set(x, -64, z, "minecraft:bedrock");
                        column.set(x, -63, z, "minecraft:dirt");
                        column.set(x, -62, z, "minecraft:dirt");
                        column.set(x, -61, z, "minecraft:grass_block");
                    }
                }
                adapter.putChunk(column.view(), true, true);
            }
        }
        return adapter;
    }

    private static ConfigSnapshot config() {
        String chest = resource("/loots/treasure_chest.yml").replace("y_range: '0:200'", "y_range: '-64:320'");
        assertTrue(chest.contains("'-64:320'"), "the shipped y_range line changed");
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("treasure_chest", chest)
                .build(), PAPER_26_2, WarningKinds.all());
    }

    private static String resource(String path) {
        try (InputStream in = ShippedTreasureChestSearchTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
