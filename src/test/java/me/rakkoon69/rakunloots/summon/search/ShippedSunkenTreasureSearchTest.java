package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.WarningKinds;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.server.ChunkView;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real summon search over the unedited shipped {@code loots/sunken_treasure.yml}: a player on plains uses the summon
 * item beside a pond. The plains are stone to 59, dirt to 62, and grass at 63. The pond is 5 by 5 at x and z 10 to 14,
 * water from 60 to 62 over dirt, so 25 columns of the 41 by 41 player ring hold a spot, each at y 60.
 */
class ShippedSunkenTreasureSearchTest {

    private static final UUID WORLD_KEY = SummonTestWorlds.WORLD_KEY;
    private static final OccupiedCheck FREE = (w, x, y, z) -> false;
    private static final int SEARCHES = 40;

    /**
     * A search that tried one column per attempt hit the pond with 25 / 1681 per attempt, so its 80 attempts missed
     * with (1 - 25/1681)^80 = 0.30, about 12 of 40 searches. A search that scans the chunk it read finds the pond once
     * any attempt lands in chunk 0, 0, which holds 256 ring columns: it misses with (1 - 256/1681)^80 = 2e-6.
     */
    @Test
    void everySummonItemSearchBesideASmallPondFindsThePond() {
        FakeServerAdapter adapter = plainsWithPond();
        ConfigSnapshot config = config();
        List<Integer> missed = new ArrayList<>();
        for (int seed = 0; seed < SEARCHES; seed++) {
            SummonSearch search = new SummonSearch(adapter, config, () -> 0L, new Random(seed));
            SummonResult result = search.aroundCenter(WORLD_KEY, "sunken_treasure", 0, 64, 0, FREE).join();
            if (result.spot().isEmpty()) {
                missed.add(seed);
                continue;
            }
            Spot spot = result.spot().get();
            assertEquals(60, spot.y(), "seed " + seed);
            assertTrue(inPond(spot.x()) && inPond(spot.z()), "seed " + seed + " spot " + spot);
        }

        assertEquals(List.of(), missed, "seeds that missed the pond");
    }

    private static boolean inPond(int coordinate) {
        return coordinate >= 10 && coordinate <= 14;
    }

    private static FakeServerAdapter plainsWithPond() {
        FakeServerAdapter adapter = new FakeServerAdapter(WORLD_KEY);
        adapter.setSpawn(0, 64, 0);
        for (int chunkX = -3; chunkX <= 2; chunkX++) {
            for (int chunkZ = -3; chunkZ <= 2; chunkZ++) {
                int baseX = chunkX * 16;
                int baseZ = chunkZ * 16;
                adapter.putChunk(new ChunkView(WORLD_KEY, chunkX, chunkZ, -64, 320, (x, y, z) -> {
                    boolean pond = inPond(baseX + x) && inPond(baseZ + z);
                    if (y <= 59) {
                        return y == 59 ? "minecraft:dirt" : "minecraft:stone";
                    }
                    if (y <= 62) {
                        return pond ? "minecraft:water" : "minecraft:dirt";
                    }
                    if (y == 63) {
                        return pond ? "minecraft:air" : "minecraft:grass_block";
                    }
                    return "minecraft:air";
                }), true, true);
            }
        }
        return adapter;
    }

    private static ConfigSnapshot config() {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("sunken_treasure", resource("/loots/sunken_treasure.yml"))
                .build(), written -> Optional.of(written), WarningKinds.all());
    }

    private static String resource(String path) {
        try (InputStream in = ShippedSunkenTreasureSearchTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
