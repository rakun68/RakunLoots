package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.config.loot.ContentEntry;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.config.loot.LootEndRules;
import me.rakkoon69.rakunloots.config.loot.OpenMode;
import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.items.VanillaAdditions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLootLoadTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void loadReturnsDropAndInventoryLootsKeyedByIdInSourceOrder() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("drop_loot", """
                        language: en
                        open_mode: drop
                        block: barrel
                        roll:
                          entries:
                            one:
                              item:
                                id: diamond
                        """)
                .loot("inv_loot", """
                        language: en
                        open_mode: inventory
                        block: chest
                        roll:
                          entries:
                            one:
                              item:
                                id: emerald
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());

        assertEquals(List.of("drop_loot", "inv_loot"), List.copyOf(snapshot.loots().keySet()));
        assertEquals(OpenMode.DROP, snapshot.loot("drop_loot").orElseThrow().openMode());
        assertEquals(OpenMode.INVENTORY, snapshot.loot("inv_loot").orElseThrow().openMode());
    }

    @Test
    void explicitLockLifeLootEndPresentationAndEntryRoundTrip() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("supply", """
                        language: en
                        open_mode: inventory
                        lock: 10
                        life: 120
                        loot_end:
                          allow_explosion: true
                          drop_block: true
                          timeout_spill: true
                          empty_removal: true
                        display_name: '&rFancy'
                        description:
                          - first
                          - second
                        block: player_head
                        head_data: head-texture
                        roll:
                          min: 0
                          max: 1
                          entries:
                            gem:
                              amount: 3
                              weight: 5
                              slot: 4
                              item:
                                source: vanilla
                                id: Diamond
                                name: '&fGem'
                                lore:
                                  - '&7line'
                                enchants:
                                  - sharpness:1
                                flags:
                                  - HIDE_ENCHANTS
                                custom_model_data: 9
                                unbreakable: true
                                damage: 2
                                nbt:
                                  demo: 1
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        Loot loot = snapshot.loot("supply").orElseThrow();

        assertEquals(OpenMode.INVENTORY, loot.openMode());
        assertEquals(10, loot.lockSeconds());
        assertEquals(OptionalInt.of(120), loot.lifeSeconds());
        assertTrue(loot.lootEnd().allowExplosion());
        assertTrue(loot.lootEnd().dropBlock());
        assertTrue(loot.lootEnd().timeoutSpill());
        assertTrue(loot.lootEnd().emptyRemoval());
        assertEquals("&rFancy", loot.displayName());
        assertEquals(List.of("first", "second"), loot.description());
        assertEquals("player_head", loot.block());
        assertEquals(Optional.of("head-texture"), loot.headData());
        assertEquals(0, loot.roll().min());
        assertEquals(1, loot.roll().max());
        assertEquals(1, loot.roll().entries().size());

        ContentEntry entry = loot.roll().entries().get(0);
        assertEquals("gem", entry.id());
        assertEquals(3, entry.amount());
        assertEquals(5, entry.weight());
        assertEquals(OptionalInt.of(4), entry.slot());
        ItemReference ref = entry.item();
        assertEquals("vanilla", ref.source());
        assertEquals("Diamond", ref.id());
        VanillaAdditions additions = ref.additions();
        assertEquals(Optional.of("&fGem"), additions.name());
        assertEquals(Optional.of(List.of("&7line")), additions.lore());
        assertEquals(Optional.of(List.of("sharpness:1")), additions.enchants());
        assertEquals(Optional.of(List.of("HIDE_ENCHANTS")), additions.flags());
        assertEquals(Optional.of(9), additions.customModelData());
        assertEquals(Optional.of(true), additions.unbreakable());
        assertEquals(Optional.of(2), additions.damage());
        assertEquals(Optional.of(Map.of("demo", 1)), additions.nbt());
    }

    @Test
    void blankSourceIsVanillaTrimmedLowercaseAndIdKeepsCase() {
        ConfigSources sources = minimalLoot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    blank:
                      item:
                        source: "  "
                        id: "  DiAmOnD  "
                    spaced:
                      item:
                        source: "  VaNiLlA  "
                        id: emerald
                """);

        Loot loot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow();
        assertEquals("vanilla", loot.roll().entries().get(0).item().source());
        assertEquals("DiAmOnD", loot.roll().entries().get(0).item().id());
        assertEquals("vanilla", loot.roll().entries().get(1).item().source());
        assertEquals("emerald", loot.roll().entries().get(1).item().id());
    }

    @Test
    void mmoItemsIdStaysOneString() {
        ConfigSources sources = minimalLoot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    sword:
                      item:
                        source: mmoitems
                        id: SWORD:CUTLASS
                """);

        ItemReference ref = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow()
                .roll().entries().get(0).item();
        assertEquals("mmoitems", ref.source());
        assertEquals("SWORD:CUTLASS", ref.id());
    }

    @Test
    void omittedLockAndLockZeroMeanNoLockWithoutWarning() {
        ConfigSources omitted = minimalLoot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources zero = minimalLoot("""
                open_mode: drop
                lock: 0
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot omittedSnap = ConfigLoader.load(omitted, ACCEPTING, WarningKinds.all());
        ConfigSnapshot zeroSnap = ConfigLoader.load(zero, ACCEPTING, WarningKinds.all());

        assertEquals(0, omittedSnap.loot("demo").orElseThrow().lockSeconds());
        assertEquals(0, zeroSnap.loot("demo").orElseThrow().lockSeconds());
        assertTrue(omittedSnap.warnings().stream().noneMatch(w -> w.lootId().equals(Optional.of("demo"))
                && w.kind() != ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                && w.kind() != ConfigWarning.Kind.LOOT_LANGUAGE_MISSING));
        assertTrue(zeroSnap.warnings().stream().noneMatch(w -> w.lootId().equals(Optional.of("demo"))
                && w.kind() != ConfigWarning.Kind.LOOT_LANGUAGE_CLASH
                && w.kind() != ConfigWarning.Kind.LOOT_LANGUAGE_MISSING));
    }

    @Test
    void omittedLifeIs360WithoutWarning() {
        ConfigSources sources = minimalLoot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ConfigSnapshot snapshot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all());
        assertEquals(OptionalInt.of(360), snapshot.loot("demo").orElseThrow().lifeSeconds());
        assertTrue(snapshot.warnings().stream().noneMatch(w ->
                w.kind() == ConfigWarning.Kind.UNUSABLE_LIFE));
    }

    @Test
    void omittedLootEndRulesAreAllOffIncludingEmptyRemovalOnDrop() {
        ConfigSources sources = minimalLoot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        LootEndRules end = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().lootEnd();
        assertFalse(end.allowExplosion());
        assertFalse(end.dropBlock());
        assertFalse(end.timeoutSpill());
        assertFalse(end.emptyRemoval());
    }

    @Test
    void omittedDisplayNameIsLootIdBlankStaysBlankOmittedDescriptionEmpty() {
        ConfigSources omitted = minimalLoot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);
        ConfigSources blank = minimalLoot("""
                open_mode: drop
                display_name: ""
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        assertEquals("demo", ConfigLoader.load(omitted, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().displayName());
        assertEquals("", ConfigLoader.load(blank, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().displayName());
        assertEquals(List.of(), ConfigLoader.load(omitted, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow().description());
    }

    @Test
    void displayNameAndDescriptionStoredAsWritten() {
        ConfigSources sources = minimalLoot("""
                open_mode: drop
                display_name: '&c<#00FF00>Name'
                description:
                  - '&7Line <#112233>two'
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        Loot loot = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow();
        assertEquals("&c<#00FF00>Name", loot.displayName());
        assertEquals(List.of("&7Line <#112233>two"), loot.description());
    }

    @Test
    void omittedAmountWeightAndSlotUseDefaults() {
        ConfigSources sources = minimalLoot("""
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """);

        ContentEntry entry = ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow()
                .roll().entries().get(0);
        assertEquals(1, entry.amount());
        assertEquals(1, entry.weight());
        assertTrue(entry.slot().isEmpty());
    }

    @Test
    void oldKeysStillLoadMessagesLanguageWorldsAndNamesWithoutImmutableLoot() {
        NameCatalog catalog = written -> {
            if ("GRASS".equals(written)) {
                return Optional.of("SHORT_GRASS");
            }
            return Optional.of(written);
        };
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: English\n")
                .worlds("""
                        worlds:
                          world:
                            time_between_loot_spawn: 5
                            loot_spawn_limit: 2
                            loots:
                              demo: 10
                        """)
                .loot("demo", """
                        language: en
                        display_name: Old Box
                        messages:
                          still_locked: Override
                        loot_block_settings:
                          material: GRASS
                        """)
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, catalog, WarningKinds.all());

        assertTrue(snapshot.loot("demo").isEmpty());
        assertEquals(Optional.of("en"), snapshot.lootLanguage("demo"));
        assertEquals("Override", snapshot.message("demo", "still_locked"));
        assertEquals(Optional.of(5), snapshot.world("world").map(WorldSettings::timeBetweenLootSpawn));
        assertEquals(Optional.of("SHORT_GRASS"), snapshot.resolvedName("GRASS"));
    }

    private static ConfigSources minimalLoot(String body) {
        return ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\n" + body)
                .build();
    }
}
