package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An optional entry, written {@code ?minecraft:x} or {@code ?#minecraft:tag}, is used only when the server knows that
 * exact name (ADR 0003 amendment). Every test loads through the config module over a fake block registry.
 */
class ConfigOptionalBlockEntriesTest {

    /**
     * A server's block registry. Like the real catalog, it knows a block only by its current name, answers an old name
     * with the current id, and knows a tag only when the version has it. It knows every other {@code minecraft:} block
     * and tag, so the shipped groups the loader also reads resolve without noise.
     */
    private record Registry(Set<String> lackedBlocks, Map<String, String> renames, Set<String> lackedTags)
            implements NameCatalog {

        @Override
        public Optional<String> currentId(String writtenId) {
            if (!writtenId.startsWith("minecraft:")) {
                return Optional.empty();
            }
            if (!lackedBlocks.contains(writtenId)) {
                return Optional.of(writtenId);
            }
            return Optional.ofNullable(renames.get(writtenId)).filter(current -> !lackedBlocks.contains(current));
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            return namespacedTag.startsWith("minecraft:") && !lackedTags.contains(namespacedTag)
                    ? Set.of("minecraft:tag_" + namespacedTag.substring("minecraft:".length())) : Set.of();
        }
    }

    /**
     * 26.2: {@code grass} is {@code short_grass}, and {@code tall_flowers} is not a block tag.
     */
    private static final Registry V26_2 = new Registry(
            Set.of("minecraft:grass", "minecraft:nope", "minecraft:also_nope"),
            Map.of("minecraft:grass", "minecraft:short_grass"),
            Set.of("minecraft:tall_flowers"));

    /**
     * 1.19: {@code grass} is the block's name, and {@code tall_flowers} is a block tag.
     */
    private static final Registry V1_19 = new Registry(
            Set.of("minecraft:short_grass", "minecraft:nope", "minecraft:also_nope"), Map.of(), Set.of());

    /**
     * Names some other version knows, which this server's registry lacks.
     */
    private static final List<String> OTHER_VERSIONS = List.of(
            "minecraft:grass", "minecraft:short_grass", "minecraft:also_nope", "#minecraft:tall_flowers");

    @Test
    void optionalOldNameDoesNotResolveAndOptionalCurrentNameDoes() {
        ConfigSnapshot snapshot = ConfigLoader.load(blocks("""
                groups:
                  grasses:
                    - '?minecraft:grass'
                    - '?minecraft:short_grass'
                    - minecraft:fern
                """), V26_2, WarningKinds.all());

        assertEquals(Set.of("minecraft:short_grass", "minecraft:fern"), snapshot.group("grasses").orElseThrow());
        assertTrue(skipped(snapshot).contains("?minecraft:grass"));
        assertFalse(skipped(snapshot).contains("?minecraft:short_grass"));
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.OLD_NAME),
                snapshot.warnings().toString());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME),
                snapshot.warnings().toString());
    }

    @Test
    void optionalEntryTheOtherWayRoundOnAnOlderServer() {
        ConfigSnapshot snapshot = ConfigLoader.load(blocks("""
                groups:
                  grasses:
                    - '?minecraft:grass'
                    - '?minecraft:short_grass'
                """), V1_19, WarningKinds.all());

        assertEquals(Set.of("minecraft:grass"), snapshot.group("grasses").orElseThrow());
        assertTrue(skipped(snapshot).contains("?minecraft:short_grass"));
        assertFalse(skipped(snapshot).contains("?minecraft:grass"));
    }

    @Test
    void optionalTagIsUsedWhenTheServerHasItAndSkippedWhenItDoesNot() {
        String groups = """
                groups:
                  tall:
                    - '?#minecraft:tall_flowers'
                    - minecraft:stone
                """;

        ConfigSnapshot old = ConfigLoader.load(blocks(groups), V1_19, WarningKinds.all());
        ConfigSnapshot current = ConfigLoader.load(blocks(groups), V26_2, WarningKinds.all());

        assertEquals(Set.of("minecraft:tag_tall_flowers", "minecraft:stone"), old.group("tall").orElseThrow());
        assertFalse(skipped(old).contains("?#minecraft:tall_flowers"));
        assertEquals(Set.of("minecraft:stone"), current.group("tall").orElseThrow());
        assertTrue(skipped(current).contains("?#minecraft:tall_flowers"));
        assertTrue(current.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_TAG),
                current.warnings().toString());
    }

    @Test
    void oneLowWarningPerLoadListsEverySkippedEntryOnceAcrossGroupsAndLists() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\nbanned_loot_blocks:\n  - '?minecraft:also_nope'\n  - '@tall'\n")
                .language("en", "still_locked: locked\n")
                .blocks("""
                        groups:
                          tall:
                            - '?#minecraft:tall_flowers'
                            - '?minecraft:also_nope'
                          again:
                            - '?minecraft:also_nope'
                            - '?minecraft:nope'
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, V26_2, () -> List.of("minecraft:nope", "minecraft:also_nope", "minecraft:grass", "minecraft:short_grass", "#minecraft:tall_flowers"), WarningKinds.all());

        List<ConfigWarning> kind = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES).toList();
        assertEquals(1, kind.size(), kind.toString());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, kind.get(0).severity());
        List<String> entries = kind.get(0).entries();
        assertTrue(entries.containsAll(List.of(
                "?#minecraft:tall_flowers", "?minecraft:also_nope", "?minecraft:nope", "?minecraft:grass")), entries.toString());
        assertEquals(entries.size(), entries.stream().distinct().count(), entries.toString());
    }

    @Test
    void noSkippedEntryMeansNoWarning() {
        Registry knowsAll = new Registry(Set.of(), Map.of(), Set.of());

        ConfigSnapshot snapshot = ConfigLoader.load(blocks("""
                groups:
                  grasses:
                    - '?minecraft:short_grass'
                    - '?#minecraft:small_flowers'
                """), knowsAll, WarningKinds.all());

        assertEquals(Set.of("minecraft:short_grass", "minecraft:tag_small_flowers"),
                snapshot.group("grasses").orElseThrow());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES),
                snapshot.warnings().toString());
    }

    @Test
    void plainEntryKeepsItsOldNameWarningAndWarnsHighOnlyForANameNoVersionKnows() {
        ConfigSnapshot snapshot = ConfigLoader.load(blocks("""
                groups:
                  mixed:
                    - 'minecraft:grass'
                    - 'minecraft:nope'
                    - 'minecraft:also_nope'
                    - '#minecraft:tall_flowers'
                    - '?minecraft:also_nope'
                """), V26_2, () -> OTHER_VERSIONS, WarningKinds.all());

        assertEquals(Set.of("minecraft:short_grass"), snapshot.group("mixed").orElseThrow());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.OLD_NAME
                && "minecraft:grass".equals(w.writtenId().orElse(null))));
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
                && "minecraft:nope".equals(w.writtenId().orElse(null))));
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_TAG),
                snapshot.warnings().toString());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> "minecraft:also_nope".equals(w.writtenId().orElse(null))));
        assertTrue(skipped(snapshot).contains("?minecraft:also_nope"));
        assertTrue(skipped(snapshot).contains("minecraft:also_nope"));
        assertTrue(skipped(snapshot).contains("#minecraft:tall_flowers"));
    }

    /**
     * The shipped blocks.yml over each version's registry gives no high warning and no old-name, unknown-name, or
     * unknown-tag warning, and the one low warning lists what that version lacks. The registries follow the RakunProbe
     * servers' data: 1.19.4 has grass and no short_grass, 1.20.6 and later have short_grass and no grass, and the
     * tall_flowers tag is gone from 1.21.4.
     */
    @Test
    void shippedBlocksYmlIsQuietOnEveryVersionAndListsWhatItSkipped() throws IOException {
        String shipped = resource("/blocks.yml");
        Registry v119 = new Registry(Set.of("minecraft:short_grass"), Map.of(), Set.of());
        Registry v206 = new Registry(Set.of("minecraft:grass"), Map.of("minecraft:grass", "minecraft:short_grass"),
                Set.of());
        Registry v262 = new Registry(Set.of("minecraft:grass"), Map.of("minecraft:grass", "minecraft:short_grass"),
                Set.of("minecraft:tall_flowers"));

        ConfigSnapshot on119 = ConfigLoader.load(shippedFiles(shipped), v119, WarningKinds.all());
        ConfigSnapshot on206 = ConfigLoader.load(shippedFiles(shipped), v206, WarningKinds.all());
        ConfigSnapshot on262 = ConfigLoader.load(shippedFiles(shipped), v262, WarningKinds.all());

        for (ConfigSnapshot snapshot : List.of(on119, on206, on262)) {
            assertTrue(snapshot.warnings().stream().noneMatch(w -> w.severity() != me.rakkoon69.rakunloots.warning.Severity.LOW),
                    snapshot.warnings().toString());
            assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.OLD_NAME
                    || w.kind() == ConfigWarning.Kind.UNKNOWN_NAME || w.kind() == ConfigWarning.Kind.UNKNOWN_TAG),
                    snapshot.warnings().toString());
        }
        assertEquals(List.of("?minecraft:short_grass"), skipped(on119));
        assertEquals(List.of("?minecraft:grass"), skipped(on206));
        assertEquals(List.of("?#minecraft:tall_flowers", "?minecraft:grass"), skipped(on262));
        assertTrue(on119.group("plants").orElseThrow().contains("minecraft:grass"));
        assertTrue(on206.group("plants").orElseThrow().contains("minecraft:short_grass"));
        assertTrue(on262.group("plants").orElseThrow().contains("minecraft:short_grass"));
    }

    /**
     * The shipped globals, worlds, and English messages, so only the block lists can warn.
     */
    private static ConfigSources shippedFiles(String blocksYml) throws IOException {
        return ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .language("en", resource("/languages/en.yml"))
                .blocks(blocksYml)
                .build();
    }

    private static List<String> skipped(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES)
                .flatMap(w -> w.entries().stream())
                .toList();
    }

    private static ConfigSources blocks(String blocksYml) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .blocks(blocksYml)
                .build();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigOptionalBlockEntriesTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
