package me.rakkoon69.rakunloots;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped {@code known_blocks.txt} against the {@code probe dump blocks} dumps of every RakunProbe test server
 * (tools/mergeblocks.py builds the first from the second).
 */
class ShippedKnownBlocksTest {

    private static List<String> shipped() throws IOException {
        try (InputStream in = ShippedKnownBlocksTest.class.getClassLoader().getResourceAsStream("known_blocks.txt")) {
            assertNotNull(in, "known_blocks.txt is shipped");
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n", -1));
        }
    }

    private static File[] dumps() throws URISyntaxException {
        var dir = ShippedKnownBlocksTest.class.getClassLoader().getResource("block-dumps");
        assertNotNull(dir, "block-dumps test resources");
        File[] files = new File(dir.toURI()).listFiles((d, n) -> n.endsWith(".txt"));
        assertNotNull(files);
        return files;
    }

    @Test
    void theShippedListHoldsEveryIdAndTagOfEveryTestServerDump() throws Exception {
        Set<String> known = new HashSet<>(shipped());
        File[] dumps = dumps();
        assertEquals(12, dumps.length, "Paper 1.18.2 to 26.2 with 1.21.5, Spigot 26.2, and Folia 26.2");
        for (File dump : dumps) {
            int names = 0;
            for (String line : Files.readAllLines(dump.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("# ")) {
                    continue;
                }
                names++;
                assertTrue(known.contains(line), dump.getName() + ": " + line + " is missing from known_blocks.txt");
            }
            assertTrue(names > 1000, dump.getName() + " has " + names + " names");
        }
        assertTrue(known.contains("minecraft:grass"), "an id only old servers know");
        assertTrue(known.contains("minecraft:short_grass"), "an id only new servers know");
        assertTrue(known.contains("#minecraft:tall_flowers"), "a tag keeps its leading #");
    }

    @Test
    void theShippedListIsOneSortedNamePerLineIdsThenTagsWithNoDuplicates() throws IOException {
        List<String> lines = new ArrayList<>(shipped());
        assertEquals("", lines.remove(lines.size() - 1), "ends with one newline");
        List<String> ids = lines.stream().filter(l -> !l.startsWith("#")).toList();
        List<String> tags = lines.stream().filter(l -> l.startsWith("#")).toList();
        List<String> idsThenTags = new ArrayList<>(ids);
        idsThenTags.addAll(tags);
        assertEquals(lines, idsThenTags, "ids first, then tags");
        assertEquals(ids.stream().sorted().distinct().toList(), ids);
        assertEquals(tags.stream().sorted().distinct().toList(), tags);
        assertTrue(ids.stream().allMatch(l -> l.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")), "an id is namespace:path");
        assertTrue(tags.stream().allMatch(l -> l.matches("#[a-z0-9_.-]+:[a-z0-9_./-]+")), "a tag is #namespace:path");
    }
}
