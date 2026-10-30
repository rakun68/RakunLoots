package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block list miss is a typo when no supported version knows the name, and a harmless mismatch when another version
 * does (phase 8e, ADR 0003 amendment). Every test loads through the config module over a fake block registry and a
 * small known-name list.
 */
class ConfigBlockTypoTest {

    /**
     * A server that knows every name in the shipped known-name list except the ones it lacks, so the shipped block
     * groups the loader also reads resolve without noise. It answers no old names. A tag has one member.
     */
    private record Registry(Set<String> lacked) implements NameCatalog {
        private static final Set<String> REAL = Set.copyOf(KnownBlockNames.shippedLines().get());

        @Override
        public Optional<String> currentId(String writtenId) {
            return REAL.contains(writtenId) && !lacked.contains(writtenId)
                    ? Optional.of(writtenId) : Optional.empty();
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            String name = "#" + namespacedTag;
            return REAL.contains(name) && !lacked.contains(name) ? Set.of("minecraft:stone") : Set.of();
        }
    }

    private static final Registry FULL = new Registry(Set.of());
    private static final Registry SERVER = new Registry(Set.of("minecraft:short_grass", "#minecraft:tall_flowers"));

    /**
     * What every supported version knows: this server's names, blocks only another version has, and tags.
     */
    private static final List<String> KNOWN = List.of(
            "minecraft:stone", "minecraft:stone_bricks", "minecraft:stove", "minecraft:stonecutter", "minecraft:dirt",
            "minecraft:short_grass", "minecraft:logs_block", "minecraft:stony", "minecraft:stonk", "minecraft:logz",
            "#minecraft:logs", "#minecraft:log", "#minecraft:logs_that_burn", "#minecraft:tall_flowers",
            "#minecraft:stones");

    @Test
    void aNameNoVersionKnowsWarnsHighWithItsCloseIds() {
        ConfigSnapshot snapshot = load("""
                groups:
                  g:
                    - minecraft:stone
                    - minecraft:dirtt
                """, KNOWN);

        ConfigWarning typo = only(snapshot, ConfigWarning.Kind.UNKNOWN_NAME);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, typo.severity());
        assertEquals("minecraft:dirtt", typo.writtenId().orElseThrow());
        assertEquals(List.of("minecraft:dirt"), typo.closeMatches());
        assertTrue(skipped(snapshot).isEmpty(), skipped(snapshot).toString());
    }

    @Test
    void stoenSuggestsStone() {
        ConfigSnapshot snapshot = load("""
                groups:
                  g:
                    - minecraft:stoen
                    - stoen
                """, KNOWN);

        List<ConfigWarning> typos = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME).toList();
        assertEquals(2, typos.size(), typos.toString());
        assertEquals("minecraft:stoen", typos.get(0).writtenId().orElseThrow());
        assertEquals("minecraft:stone", typos.get(0).closeMatches().get(0));
        assertEquals("stoen", typos.get(1).writtenId().orElseThrow());
        assertEquals("stone", typos.get(1).closeMatches().get(0));
    }

    @Test
    void atMostThreeCloseMatchesAreGivenAndEachEntryWarnsOnce() {
        ConfigSnapshot snapshot = load("""
                groups:
                  g:
                    - minecraft:ston
                    - minecraft:ston
                    - minecraft:stoen
                """, KNOWN);

        List<ConfigWarning> typos = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME).toList();
        assertEquals(2, typos.size(), typos.toString());
        assertEquals("minecraft:ston", typos.get(0).writtenId().orElseThrow());
        assertEquals(3, typos.get(0).closeMatches().size(), typos.get(0).closeMatches().toString());
        assertEquals("minecraft:stone", typos.get(0).closeMatches().get(0));
    }

    @Test
    void aNameAnotherVersionKnowsOnlyJoinsTheGroupedLowLineForPlainAndOptionalEntries() {
        ConfigSnapshot snapshot = ConfigLoader.load(sources("""
                groups:
                  g:
                    - minecraft:short_grass
                    - '?minecraft:logs_block'
                    - '#minecraft:tall_flowers'
                    - '?#minecraft:tall_flowers'
                    - minecraft:stone
                """), SERVER, () -> KNOWN, WarningKinds.all());

        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME
                || w.kind() == ConfigWarning.Kind.UNKNOWN_TAG), snapshot.warnings().toString());
        List<ConfigWarning> lines = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES).toList();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).entries().containsAll(List.of("minecraft:short_grass", "?minecraft:logs_block",
                "#minecraft:tall_flowers", "?#minecraft:tall_flowers")), lines.get(0).entries().toString());
    }

    @Test
    void anOptionalEntryNoVersionKnowsWarnsHighToo() {
        ConfigSnapshot snapshot = load("""
                groups:
                  g:
                    - '?minecraft:stoen'
                """, KNOWN);

        ConfigWarning typo = only(snapshot, ConfigWarning.Kind.UNKNOWN_NAME);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, typo.severity());
        assertEquals("minecraft:stoen", typo.writtenId().orElseThrow());
        assertEquals("minecraft:stone", typo.closeMatches().get(0));
        assertTrue(skipped(snapshot).isEmpty(), skipped(snapshot).toString());
    }

    @Test
    void aTagNoVersionKnowsWarnsHighWithCloseTagsFromTheTagPoolOnly() {
        ConfigSnapshot snapshot = load("""
                groups:
                  g:
                    - '#minecraft:logss'
                    - '?#minecraft:logss'
                """, KNOWN);

        List<ConfigWarning> typos = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_TAG).toList();
        assertEquals(2, typos.size(), typos.toString());
        for (ConfigWarning typo : typos) {
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, typo.severity());
            assertEquals("#minecraft:logs", typo.closeMatches().get(0));
            assertTrue(typo.closeMatches().stream().allMatch(name -> name.startsWith("#")),
                    typo.closeMatches().toString());
        }
    }

    @Test
    void anIdTypoIsNeverMatchedToATag() {
        ConfigSnapshot snapshot = load("""
                groups:
                  g:
                    - minecraft:logs
                """, KNOWN);

        ConfigWarning typo = only(snapshot, ConfigWarning.Kind.UNKNOWN_NAME);
        assertEquals(List.of("minecraft:logz"), typo.closeMatches());
    }

    @Test
    void theKnownListIsReadOnceOnTheFirstMissAndNeverWhenNothingMisses() {
        AtomicInteger reads = new AtomicInteger();

        ConfigLoader.load(sources("""
                groups:
                  g:
                    - minecraft:stone
                    - '?minecraft:dirt'
                    - '#minecraft:logs'
                """), FULL, () -> {
                    reads.incrementAndGet();
                    return KNOWN;
                }, WarningKinds.all());
        assertEquals(0, reads.get());

        ConfigLoader.load(sources("""
                groups:
                  g:
                    - minecraft:stoen
                    - '?minecraft:short_grass'
                    - '#minecraft:tall_flowers'
                    - minecraft:dirtt
                """), SERVER, () -> {
                    reads.incrementAndGet();
                    return KNOWN;
                }, WarningKinds.all());
        assertEquals(1, reads.get());
    }

    /**
     * The shipped list through the shipped default: a typo of a real block, and a block a newer version added.
     */
    @Test
    void shippedKnownListSuggestsStoneForStoenAndKeepsAnotherVersionsBlockQuiet() {
        Registry old = new Registry(Set.of("minecraft:short_grass"));
        ConfigSnapshot snapshot = ConfigLoader.load(sources("""
                groups:
                  g:
                    - minecraft:stoen
                    - minecraft:short_grass
                """), old, WarningKinds.all());

        ConfigWarning typo = only(snapshot, ConfigWarning.Kind.UNKNOWN_NAME);
        assertEquals("minecraft:stoen", typo.writtenId().orElseThrow());
        assertTrue(typo.closeMatches().contains("minecraft:stone"), typo.closeMatches().toString());
        assertTrue(skipped(snapshot).contains("minecraft:short_grass"), skipped(snapshot).toString());
    }

    @Test
    void theFirstTwentyTyposOfALoadWarnOnTheirOwnAndTheRestShareOneHighLine() {
        ConfigSnapshot snapshot = load(typoGroup(25), KNOWN);

        List<ConfigWarning> typos = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME).toList();
        assertEquals(20, typos.size(), typos.toString());
        for (ConfigWarning typo : typos) {
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, typo.severity());
            assertTrue(typo.closeMatches().contains("minecraft:stone"), typo + " " + typo.closeMatches());
        }
        ConfigWarning more = only(snapshot, ConfigWarning.Kind.MORE_TYPOS);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, more.severity());
        assertEquals(List.of("minecraft:stone20", "minecraft:stone21", "minecraft:stone22", "minecraft:stone23",
                "minecraft:stone24"), more.entries());
        assertTrue(more.count().isEmpty(), more.count().toString());
    }

    /**
     * Acceptance: thousands of typos give 21 lines, and the shared line names the first 10 and counts the rest.
     */
    @Test
    void theSharedTypoLineNamesTenAndCountsTheRest() {
        ConfigSnapshot snapshot = load(typoGroup(5000), KNOWN);

        assertEquals(20, snapshot.warnings().stream().filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME).count());
        ConfigWarning more = only(snapshot, ConfigWarning.Kind.MORE_TYPOS);
        assertEquals(10, more.entries().size(), more.entries().toString());
        assertEquals("minecraft:stone20", more.entries().get(0));
        assertEquals(4970, more.count().orElseThrow());
    }

    @Test
    void twentyTyposOrFewerAddNoSharedLine() {
        ConfigSnapshot snapshot = load(typoGroup(20), KNOWN);

        assertEquals(20, snapshot.warnings().stream().filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME).count());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.MORE_TYPOS),
                snapshot.warnings().toString());
    }

    @Test
    void theTypoCountStartsAgainOnEveryLoad() {
        String blocks = typoGroup(25);
        for (int load = 0; load < 2; load++) {
            ConfigSnapshot snapshot = load(blocks, KNOWN);
            assertEquals(20, snapshot.warnings().stream()
                    .filter(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_NAME).count());
            assertEquals(5, only(snapshot, ConfigWarning.Kind.MORE_TYPOS).entries().size());
        }
    }

    private static String typoGroup(int count) {
        StringBuilder yml = new StringBuilder("groups:\n  g:\n");
        for (int i = 0; i < count; i++) {
            yml.append("    - minecraft:stone").append(i).append('\n');
        }
        return yml.toString();
    }

    private static ConfigSnapshot load(String blocksYml, List<String> known) {
        return ConfigLoader.load(sources(blocksYml), FULL, () -> known, WarningKinds.all());
    }

    private static ConfigSources sources(String blocksYml) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .blocks(blocksYml)
                .build();
    }

    private static ConfigWarning only(ConfigSnapshot snapshot, ConfigWarning.Kind kind) {
        List<ConfigWarning> found = snapshot.warnings().stream().filter(w -> w.kind() == kind).toList();
        assertEquals(1, found.size(), snapshot.warnings().stream().map(w -> w.kind() + " " + w.writtenId() + " " + w.entries()).toList().toString());
        return found.get(0);
    }

    private static List<String> skipped(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES)
                .flatMap(w -> w.entries().stream())
                .toList();
    }
}
