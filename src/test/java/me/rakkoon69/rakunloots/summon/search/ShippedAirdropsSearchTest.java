package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.fake.FakeColumn;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A real summon search over the unedited shipped {@code loots/airdrops.yml}. Its replaceable blocks are plants and
 * flowers, with no air, so air at the top of the height range must still never count as ground.
 */
class ShippedAirdropsSearchTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;

    @Test
    void aTopSurfaceSearchOverStoneGrassAndAirFindsTheSpotAboveTheGrass() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                FakeColumn column = new FakeColumn(WORLD_KEY, chunkX, chunkZ, -64, 320);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = -64; y <= 62; y++) {
                            column.set(x, y, z, "minecraft:stone");
                        }
                        column.set(x, 63, z, "minecraft:grass_block");
                    }
                }
                adapter.putChunk(column.view(), true, true);
            }
        }
        SummonSearch search = new SummonSearch(adapter, config(), () -> 0L, new Random(7));
        search.start();
        try {
            SummonResult result = search.serverSummon(WORLD_KEY, "airdrops", FREE).join();

            Spot spot = result.spot().orElseThrow(() -> new AssertionError("miss " + result.missReason()));
            assertEquals(64, spot.y());
        } finally {
            search.stop();
        }
    }

    private static ConfigSnapshot config() {
        String worlds = resource("/worlds.yml")
                .replace("mode: smart", "mode: spawn")
                .replace("max: 800", "max: 20");
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(worlds)
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), written -> Optional.of(written), WarningKinds.all());
    }

    private static String resource(String path) {
        try (InputStream in = ShippedAirdropsSearchTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
