package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.items.ItemReference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSummonItemTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(
            written.contains(":") ? written : "minecraft:" + written);

    /**
     * Acceptance: the loot file's summon item comes back as an item reference with vanilla additions.
     */
    @Test
    void theSummonItemIsAnItemReferenceWithVanillaAdditions() {
        ConfigSnapshot snapshot = ConfigLoader.load(loot("""
                summon_item:
                  id: nether_star
                  name: '&bBeacon'
                  lore:
                    - '&7Right click.'
                  custom_model_data: 7
                """), ACCEPTING, WarningKinds.all());

        ItemReference item = snapshot.loot("demo").orElseThrow().summonItem().orElseThrow();
        assertEquals("vanilla", item.source());
        assertEquals("minecraft:nether_star", item.id());
        assertEquals(Optional.of("&bBeacon"), item.additions().name());
        assertEquals(Optional.of(List.of("&7Right click.")), item.additions().lore());
        assertEquals(Optional.of(7), item.additions().customModelData());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNKNOWN_KEY));
    }

    @Test
    void aSummonItemFromAnotherSourceKeepsItsSource() {
        ConfigSnapshot snapshot = ConfigLoader.load(loot("""
                summon_item:
                  source: mmoitems
                  id: SWORD:KEY
                """), ACCEPTING, WarningKinds.all());

        ItemReference item = snapshot.loot("demo").orElseThrow().summonItem().orElseThrow();
        assertEquals("mmoitems", item.source());
        assertEquals("SWORD:KEY", item.id());
    }

    @Test
    void aLootWithoutASummonItemHasNone() {
        ConfigSnapshot snapshot = ConfigLoader.load(loot(""), ACCEPTING, WarningKinds.all());

        assertEquals(Optional.empty(), snapshot.loot("demo").orElseThrow().summonItem());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_SUMMON_ITEM));
    }

    @Test
    void anUnusableSummonItemWarnsHighAndLeavesTheLootWithoutOne() {
        for (String bad : new String[]{"summon_item: nether_star\n", "summon_item:\n  name: 'no id'\n",
                "summon_item:\n  id: ''\n"}) {
            ConfigSnapshot snapshot = ConfigLoader.load(loot(bad), ACCEPTING, WarningKinds.all());

            assertTrue(snapshot.loot("demo").isPresent(), bad);
            assertEquals(Optional.empty(), snapshot.loot("demo").orElseThrow().summonItem(), bad);
            assertTrue(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.UNUSABLE_SUMMON_ITEM
                    && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                    && w.lootId().equals(Optional.of("demo"))), bad);
        }
    }

    /**
     * Acceptance: the shipped loots/airdrops.yml has the summon item.
     */
    @Test
    void theShippedAirdropsFileHasItsSummonItemBack() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                if (writtenId.startsWith("@") || writtenId.startsWith("#")) {
                    return Optional.empty();
                }
                return Optional.of(writtenId.contains(":") ? writtenId : "minecraft:" + writtenId);
            }

            @Override
            public Set<String> tagMembers(String namespacedTag) {
                return Set.of("minecraft:tag_" + namespacedTag.replace(':', '_'));
            }
        }, WarningKinds.all());

        ItemReference item = snapshot.loot("airdrops").orElseThrow().summonItem().orElseThrow();
        assertEquals("vanilla", item.source());
        assertEquals("minecraft:nether_star", item.id());
        assertEquals(Optional.of("&r<#fad778>&lAir Supply Beacon"), item.additions().name(),
                "a named nether star, so a player can tell it from a vanilla one");
        assertTrue(!item.additions().lore().orElseThrow().isEmpty());
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.lootId().equals(Optional.of("airdrops"))),
                "the shipped loot reads without a warning");
    }

    private static ConfigSources loot(String extra) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n" + extra + """
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .build();
    }

    private static String resource(String path) {
        try (InputStream in = ConfigSummonItemTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
