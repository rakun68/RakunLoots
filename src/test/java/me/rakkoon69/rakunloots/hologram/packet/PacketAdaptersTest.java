package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.MinecraftVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Packet adapter selection by Minecraft version, over a revision table the test sets, loading adapters by class name
 * from the test class loader.
 */
class PacketAdaptersTest {

    private static final ClassLoader LOADER = PacketAdaptersTest.class.getClassLoader();
    private static final List<PacketRevision> TABLE = List.of(
            revision("v1_18_R1", "1.18", "1.18.1", FirstAdapter.class.getName()),
            revision("v1_18_R2", "1.18.2", "1.18.2", SecondAdapter.class.getName()));

    /**
     * Each revision is picked from its first release to its last, and nothing outside the table.
     */
    @ParameterizedTest(name = "{0} picks {1}")
    @CsvSource({
            "1.18, FirstAdapter",
            "1.18.1, FirstAdapter",
            "1.18.2, SecondAdapter",
            "1.17.1, -",
            "1.19, -",
    })
    void eachRevisionCoversItsReleasesAndNothingElse(String version, String expected) {
        PacketAdapterMatch match = PacketAdapters.select(MinecraftVersion.parse(version), TABLE, LOADER);

        if (expected.equals("-")) {
            assertEquals(PacketAdapterMatch.none("there is no packet adapter for Minecraft " + version), match);
        } else {
            assertEquals(expected, match.adapter().orElseThrow().getClass().getSimpleName());
            assertEquals(Optional.empty(), match.refusal());
        }
    }

    /**
     * The table this build ships picks nothing for a version below every adapter, naming the version.
     */
    @Test
    void theShippedTableNamesTheVersionItHasNoAdapterFor() {
        PacketAdapterMatch match = PacketAdapters.select(MinecraftVersion.parse("1.17.1"), PacketAdapters.REVISIONS,
                LOADER);

        assertEquals(Optional.of("there is no packet adapter for Minecraft 1.17.1"), match.refusal());
    }

    /**
     * Each shipped revision's adapter is the class {@code NmsPacketAdapter} in the package named after the revision,
     * the class its subproject builds.
     */
    @Test
    void eachShippedRevisionNamesItsSubprojectsAdapterClass() {
        assertEquals("v1_18_R1", PacketAdapters.REVISIONS.get(0).name());
        for (PacketRevision revision : PacketAdapters.REVISIONS) {
            assertEquals("me.rakkoon69.rakunloots.hologram.packet." + revision.name() + ".NmsPacketAdapter",
                    revision.className());
        }
    }

    /**
     * The shipped revisions are in release order and never overlap, so one release has one adapter.
     */
    @Test
    void theShippedRevisionsAreOrderedAndNeverOverlap() {
        for (int index = 1; index < PacketAdapters.REVISIONS.size(); index++) {
            PacketRevision before = PacketAdapters.REVISIONS.get(index - 1);
            PacketRevision after = PacketAdapters.REVISIONS.get(index);
            assertTrue(before.to().isBelow(after.from()), before.name() + " overlaps " + after.name());
        }
    }

    /**
     * A revision whose class is missing or is not an adapter is no match, naming the revision and the version.
     */
    @Test
    void aRevisionThatCannotLoadIsNoMatch() {
        List<PacketRevision> broken = List.of(
                revision("v1_18_R1", "1.18", "1.18.1", "me.rakkoon69.rakunloots.hologram.packet.v1_18_R1.Missing"),
                revision("v1_18_R2", "1.18.2", "1.18.2", String.class.getName()));

        String missing = PacketAdapters.select(MinecraftVersion.parse("1.18.1"), broken, LOADER).refusal()
                .orElseThrow();
        String notAdapter = PacketAdapters.select(MinecraftVersion.parse("1.18.2"), broken, LOADER).refusal()
                .orElseThrow();

        assertTrue(missing.startsWith("the packet adapter v1_18_R1 for Minecraft 1.18.1 cannot load: "), missing);
        assertTrue(missing.contains("ClassNotFoundException"), missing);
        assertTrue(notAdapter.startsWith("the packet adapter v1_18_R2 for Minecraft 1.18.2 cannot load: "),
                notAdapter);
    }

    @Test
    void aRevisionEndsNoEarlierThanItStartsAndAMatchIsOneOrTheOther() {
        assertThrows(IllegalArgumentException.class, () -> revision("v1_18_R1", "1.18.1", "1.18", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new PacketAdapterMatch(Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new PacketAdapterMatch(Optional.of(new FirstAdapter()), Optional.of("both")));
    }

    private static PacketRevision revision(String name, String from, String to, String className) {
        return new PacketRevision(name, MinecraftVersion.parse(from), MinecraftVersion.parse(to), className);
    }

    /**
     * Stands for a version adapter; selection only creates it.
     */
    public static class FirstAdapter implements PacketAdapter {

        @Override
        public int entityCount(int lineCount) {
            return 1;
        }

        @Override
        public int nextEntityId() {
            return 1;
        }

        @Override
        public Object spawn(List<Integer> entityIds, HologramPosition position, List<String> lines) {
            return "spawn";
        }

        @Override
        public Object meta(List<Integer> entityIds, List<String> lines) {
            return "meta";
        }

        @Override
        public Object destroy(List<Integer> entityIds) {
            return "destroy";
        }

        @Override
        public void send(UUID playerId, Object packet) {
        }
    }

    public static final class SecondAdapter extends FirstAdapter {
    }
}
