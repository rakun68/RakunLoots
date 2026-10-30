package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.items.ItemReference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code menus/} files through the config load: the shipped layouts, the validation rules, and the whole-menu
 * fallback.
 */
class ConfigMenuFileTest {

    private static final String CLICK = "minecraft:ui.button.click";
    private static final Set<ConfigWarning.Kind> MENU_KINDS = Set.of(
            ConfigWarning.Kind.UNUSABLE_MENU_ROWS,
            ConfigWarning.Kind.REJECTED_MENU_SLOT,
            ConfigWarning.Kind.MENU_BUTTON_ON_CONTENT,
            ConfigWarning.Kind.DUPLICATE_MENU_BUTTON_SLOT,
            ConfigWarning.Kind.UNKNOWN_MENU_BUTTON,
            ConfigWarning.Kind.UNKNOWN_MENU_FILE,
            ConfigWarning.Kind.UNKNOWN_MENU_KEY,
            ConfigWarning.Kind.UNUSABLE_MENU_VOLUME,
            ConfigWarning.Kind.UNUSABLE_MENU_PITCH,
            ConfigWarning.Kind.UNKNOWN_MENU_SOUND,
            ConfigWarning.Kind.UNUSABLE_MENU_BUTTONS,
            ConfigWarning.Kind.MENU_FALLBACK);

    /**
     * A catalog that knows the shipped click sound and only that sound.
     */
    private static final NameCatalog CLICK_CATALOG = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            return Optional.of(writtenId);
        }

        @Override
        public boolean soundExists(String key) {
            return CLICK.equals(key);
        }
    };

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigMenuFileTest.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ConfigSnapshot load(String claim, String wiki) {
        return load(CLICK_CATALOG, claim, wiki);
    }

    private static ConfigSnapshot load(NameCatalog catalog, String claim, String wiki) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", "text: test\n");
        if (claim != null) {
            sources.menu("claim", claim);
        }
        if (wiki != null) {
            sources.menu("wiki", wiki);
        }
        return ConfigLoader.load(sources.build(), catalog, WarningKinds.all());
    }

    private static long count(ConfigSnapshot snapshot, ConfigWarning.Kind kind) {
        return snapshot.warnings().stream().filter(w -> w.kind() == kind).count();
    }

    private static List<ConfigWarning> menuWarnings(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream().filter(warning -> MENU_KINDS.contains(warning.kind())).toList();
    }

    private static List<ConfigWarning.Kind> kinds(ConfigSnapshot snapshot) {
        return menuWarnings(snapshot).stream().map(ConfigWarning::kind).toList();
    }

    private static ConfigWarning single(ConfigSnapshot snapshot, ConfigWarning.Kind kind) {
        List<ConfigWarning> found = snapshot.warnings().stream().filter(w -> w.kind() == kind).toList();
        assertEquals(1, found.size(), found.toString());
        return found.get(0);
    }

    private static void assertShippedClaim(ConfigSnapshot snapshot) {
        MenuPage claim = snapshot.claimMenu();
        assertEquals(6, claim.rows());
        assertEquals(45, claim.contentSlots().size());
        assertEquals("gray_stained_glass_pane", claim.filler().id());
        assertEquals(45, claim.buttons().get("previous_page").slot());
        assertEquals(49, claim.buttons().get("claim_all").slot());
        assertEquals(53, claim.buttons().get("next_page").slot());
    }

    private static void assertShippedWikiLoot(ConfigSnapshot snapshot) {
        MenuPage loot = snapshot.wikiLootMenu();
        assertEquals(6, loot.rows());
        assertEquals(45, loot.contentSlots().size());
        assertEquals(48, loot.buttons().get("back").slot());
        assertEquals(49, loot.buttons().get("info").slot());
        assertTrue(loot.buttons().get("info").sound().isEmpty());
    }

    @Test
    void theShippedMenuFilesGiveTheSpecifiedLayoutsQuietly() throws IOException {
        ConfigSnapshot snapshot = load(resource("menus/claim.yml"), resource("menus/wiki.yml"));

        assertEquals(List.of(), kinds(snapshot));
        assertShippedClaim(snapshot);
        MenuPage list = snapshot.wikiListMenu();
        assertEquals(6, list.rows());
        assertEquals(45, list.contentSlots().size());
        assertEquals("gray_stained_glass_pane", list.filler().id());
        assertEquals(45, list.buttons().get("previous_page").slot());
        assertEquals(53, list.buttons().get("next_page").slot());
        assertShippedWikiLoot(snapshot);
        assertEquals("arrow", snapshot.claimMenu().buttons().get("previous_page").item().id());
        assertEquals("chest_minecart", snapshot.claimMenu().buttons().get("claim_all").item().id());
        assertEquals("oak_door", snapshot.wikiLootMenu().buttons().get("back").item().id());
        assertEquals("book", snapshot.wikiLootMenu().buttons().get("info").item().id());
        assertEquals(CLICK, snapshot.claimMenu().buttons().get("claim_all").sound().orElseThrow().key());
        assertEquals(1.0, snapshot.claimMenu().buttons().get("claim_all").sound().orElseThrow().volume());
        assertEquals(1.0, snapshot.claimMenu().buttons().get("claim_all").sound().orElseThrow().pitch());
    }

    @Test
    void aMissingMenuFileUsesTheShippedLayoutSilentlyEvenWithAnEmptyCatalog() {
        ConfigSnapshot snapshot = load(NameCatalog.empty(), null, null);

        assertEquals(List.of(), kinds(snapshot));
        assertShippedClaim(snapshot);
        assertEquals(45, snapshot.wikiListMenu().contentSlots().size());
        assertShippedWikiLoot(snapshot);
    }

    @Test
    void rowsOutsideOneToSixWarnsHighAndUsesTheShippedValue() {
        for (String rows : List.of("0", "7", "'six'", "[3]")) {
            ConfigSnapshot snapshot = load("rows: " + rows + "\n", null);

            assertEquals(6, snapshot.claimMenu().rows(), rows);
            ConfigWarning warning = single(snapshot, ConfigWarning.Kind.UNUSABLE_MENU_ROWS);
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity(), rows);
        }
    }

    @Test
    void defaultContentSlotsFollowAShrunkPageWithoutAWarning() {
        ConfigSnapshot snapshot = load("rows: 3\n", null);

        assertEquals(3, snapshot.claimMenu().rows());
        assertEquals(27, snapshot.claimMenu().contentSlots().size());
        assertEquals(26, snapshot.claimMenu().contentSlots().get(26));
        assertEquals(0, count(snapshot, ConfigWarning.Kind.UNUSABLE_MENU_ROWS));
        assertEquals(0, count(snapshot, ConfigWarning.Kind.REJECTED_MENU_SLOT));
    }

    @Test
    void anExplicitSlotOutsideThePageWarnsHighAndIsDropped() {
        ConfigSnapshot snapshot = load("""
                rows: 3
                content_slots: [0, 27]
                filler: {id: gray_stained_glass_pane}
                """, null);

        assertEquals(List.of(0), new ArrayList<>(snapshot.claimMenu().contentSlots()));
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.REJECTED_MENU_SLOT).severity());
    }

    @Test
    void contentRangesExpandInOrderAndKeepTheirOrder() {
        ConfigSnapshot snapshot = load("""
                rows: 3
                content_slots: ['0-2', 1, '4-5']
                filler: {id: gray_stained_glass_pane}
                """, null);

        assertEquals(List.of(0, 1, 2, 4, 5), new ArrayList<>(snapshot.claimMenu().contentSlots()));
        assertEquals(List.of(), kinds(snapshot));
    }

    @Test
    void aButtonOnAContentSlotWarnsHighAndTakesTheSlotOut() {
        ConfigSnapshot snapshot = load("""
                rows: 2
                content_slots: [0, 1, 2]
                filler: {id: gray_stained_glass_pane}
                buttons:
                  claim_all:
                    slot: 1
                    item: {id: chest_minecart}
                """, null);

        assertEquals(List.of(0, 2), new ArrayList<>(snapshot.claimMenu().contentSlots()));
        assertEquals(1, snapshot.claimMenu().buttons().get("claim_all").slot());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.MENU_BUTTON_ON_CONTENT).severity());
    }

    @Test
    void twoButtonsOnOneSlotWarnHighAndTheLaterOneIsDropped() {
        ConfigSnapshot snapshot = load("""
                rows: 6
                content_slots: ['0-44']
                filler: {id: gray_stained_glass_pane}
                buttons:
                  claim_all:
                    slot: 49
                    item: {id: chest_minecart}
                  previous_page:
                    slot: 49
                    item: {id: arrow}
                """, null);

        assertTrue(snapshot.claimMenu().buttons().containsKey("claim_all"));
        assertFalse(snapshot.claimMenu().buttons().containsKey("previous_page"));
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.DUPLICATE_MENU_BUTTON_SLOT).severity());
    }

    @Test
    void anUnknownButtonNameWarnsHighAndTheRestOfThePageStays() {
        ConfigSnapshot snapshot = load("""
                buttons:
                  mystery:
                    slot: 49
                    item: {id: stone}
                """, null);

        assertEquals(List.of(), new ArrayList<>(snapshot.claimMenu().buttons().keySet()));
        assertEquals(45, snapshot.claimMenu().contentSlots().size());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.UNKNOWN_MENU_BUTTON).severity());
    }

    @Test
    void aKnownButtonInheritsAnOmittedSlotAndItemSilently() {
        ConfigSnapshot snapshot = load("buttons:\n  claim_all: {}\n", null);

        MenuButton button = snapshot.claimMenu().buttons().get("claim_all");
        assertEquals(49, button.slot());
        assertEquals("chest_minecart", button.item().id());
        assertTrue(button.sound().isEmpty());
        assertEquals(List.of(), kinds(snapshot));
    }

    @Test
    void aKnownButtonWithAnInvalidSlotWarnsHighAndIsDropped() {
        ConfigSnapshot snapshot = load("buttons:\n  claim_all:\n    slot: 99\n", null);

        assertFalse(snapshot.claimMenu().buttons().containsKey("claim_all"));
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.REJECTED_MENU_SLOT).severity());
    }

    @Test
    void anInheritedButtonSlotOutsideAShrunkPageWarnsHighAndIsDropped() {
        ConfigSnapshot snapshot = load(
                "rows: 1\ncontent_slots: [0, 1]\nbuttons:\n  claim_all: {}\n", null);

        assertEquals(1, snapshot.claimMenu().rows());
        assertEquals(List.of(0, 1), snapshot.claimMenu().contentSlots());
        assertFalse(snapshot.claimMenu().buttons().containsKey("claim_all"));
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.REJECTED_MENU_SLOT).severity());
    }

    @Test
    void aKnownButtonWithAnUnusableItemWarnsAndUsesTheShippedItem() {
        ConfigSnapshot snapshot = load("buttons:\n  claim_all:\n    item: stone\n", null);

        assertEquals("chest_minecart", snapshot.claimMenu().buttons().get("claim_all").item().id());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY).severity());
    }

    @Test
    void aValidItemThatTheCatalogDoesNotKnowStaysForTheRendererToBarrier() {
        NameCatalog rejecting = new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                return writtenId.equals("gray_stained_glass_pane") ? Optional.of(writtenId) : Optional.empty();
            }

            @Override
            public boolean soundExists(String key) {
                return CLICK.equals(key);
            }
        };
        ConfigSnapshot snapshot = load(rejecting,
                "buttons:\n  claim_all:\n    item: {id: not_installed}\n", null);

        assertEquals("not_installed", snapshot.claimMenu().buttons().get("claim_all").item().id());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.UNKNOWN_NAME).severity());
    }

    @Test
    void anItemReferenceWithVanillaAdditionsParsesAsInALootFile() {
        ConfigSnapshot snapshot = load("""
                filler:
                  id: gray_stained_glass_pane
                  name: '&7Pane'
                  custom_model_data: 7
                  unbreakable: true
                """, null);

        ItemReference filler = snapshot.claimMenu().filler();
        assertEquals("vanilla", filler.source());
        assertEquals("gray_stained_glass_pane", filler.id());
        assertEquals(Optional.of("&7Pane"), filler.additions().name());
        assertEquals(Optional.of(7), filler.additions().customModelData());
        assertEquals(Optional.of(true), filler.additions().unbreakable());
    }

    @Test
    void anUnknownKeyAnywhereWarnsHighAndChangesNothing() {
        ConfigSnapshot root = load("mystery: true\n", null);
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(root, ConfigWarning.Kind.UNKNOWN_MENU_KEY).severity());
        assertEquals(6, root.claimMenu().rows());
        assertTrue(root.claimMenu().buttons().isEmpty());

        for (String claim : List.of(
                "buttons:\n  claim_all:\n    slot: 49\n    item: {id: chest_minecart}\n    mystery: true\n",
                "buttons:\n  claim_all:\n    slot: 49\n    item: {id: chest_minecart}\n"
                        + "    sound:\n      key: minecraft:ui.button.click\n      mystery: true\n")) {
            ConfigSnapshot snapshot = load(claim, null);

            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                    single(snapshot, ConfigWarning.Kind.UNKNOWN_MENU_KEY).severity(), claim);
            assertEquals(49, snapshot.claimMenu().buttons().get("claim_all").slot(), claim);
        }
    }

    @Test
    void anUnknownMenuFileWarnsHighAndIsIgnored() {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", "text: test\n")
                .menu("minigame", "rows: 1\n")
                .build();

        ConfigSnapshot snapshot = ConfigLoader.load(sources, CLICK_CATALOG, WarningKinds.all());

        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.UNKNOWN_MENU_FILE).severity());
        assertShippedClaim(snapshot);
    }

    @Test
    void aBadVolumeOrPitchWarnsHighAndUsesOne() {
        ConfigSnapshot snapshot = load("""
                buttons:
                  claim_all:
                    slot: 49
                    item: {id: chest_minecart}
                    sound:
                      key: minecraft:ui.button.click
                      volume: -1
                      pitch: 2.5
                """, null);

        MenuSound sound = snapshot.claimMenu().buttons().get("claim_all").sound().orElseThrow();
        assertEquals(1.0, sound.volume());
        assertEquals(1.0, sound.pitch());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.UNUSABLE_MENU_VOLUME).severity());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.UNUSABLE_MENU_PITCH).severity());
    }

    @Test
    void aGoodSoundKeepsItsVolumeAndPitch() {
        ConfigSnapshot snapshot = load("""
                buttons:
                  claim_all:
                    slot: 49
                    item: {id: chest_minecart}
                    sound:
                      key: minecraft:ui.button.click
                      volume: 0
                      pitch: 0.5
                """, null);

        MenuSound sound = snapshot.claimMenu().buttons().get("claim_all").sound().orElseThrow();
        assertEquals(0.0, sound.volume());
        assertEquals(0.5, sound.pitch());
        assertEquals(List.of(), kinds(snapshot));
    }

    @Test
    void anUnknownSoundWarnsHighAndLeavesTheButtonSilent() {
        ConfigSnapshot snapshot = load("""
                buttons:
                  claim_all:
                    slot: 49
                    item: {id: chest_minecart}
                    sound:
                      key: minecraft:no_such_sound
                """, null);

        assertTrue(snapshot.claimMenu().buttons().get("claim_all").sound().isEmpty());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                single(snapshot, ConfigWarning.Kind.UNKNOWN_MENU_SOUND).severity());
    }

    @Test
    void anOmittedSoundIsSilentWithoutAWarning() {
        ConfigSnapshot snapshot = load("""
                buttons:
                  claim_all:
                    slot: 49
                    item: {id: chest_minecart}
                """, null);

        assertTrue(snapshot.claimMenu().buttons().get("claim_all").sound().isEmpty());
        assertEquals(List.of(), kinds(snapshot));
    }

    @Test
    void aPresentSoundWithoutAUsableKeyWarnsHighAndLeavesTheButtonSilent() {
        for (String sound : List.of("{}", "{key: 5}", "{key: '  '}")) {
            ConfigSnapshot snapshot = load("""
                    buttons:
                      claim_all:
                        slot: 49
                        item: {id: chest_minecart}
                        sound: %s
                    """.formatted(sound), null);

            assertTrue(snapshot.claimMenu().buttons().get("claim_all").sound().isEmpty(), sound);
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH,
                    single(snapshot, ConfigWarning.Kind.UNKNOWN_MENU_SOUND).severity(), sound);
        }
    }

    @Test
    void aMissingWikiPageUsesItsShippedPageAndKeepsTheOtherCustomised() {
        ConfigSnapshot snapshot = load(null, """
                list:
                  rows: 3
                  content_slots: [0, 1, 2]
                  filler: {id: gray_stained_glass_pane}
                """);

        assertEquals(3, snapshot.wikiListMenu().rows());
        assertEquals(List.of(0, 1, 2), new ArrayList<>(snapshot.wikiListMenu().contentSlots()));
        assertEquals(6, snapshot.wikiLootMenu().rows());
        assertEquals(45, snapshot.wikiLootMenu().contentSlots().size());
        assertEquals(49, snapshot.wikiLootMenu().buttons().get("info").slot());
        assertEquals(List.of(), kinds(snapshot));
    }

    @Test
    void aWikiPageThatEndsWithoutContentFallsBackTheWholeWiki() {
        ConfigSnapshot snapshot = load(null, """
                list:
                  rows: 1
                  content_slots: [99]
                loot:
                  rows: 1
                  content_slots: [99]
                """);

        assertEquals(6, snapshot.wikiListMenu().rows());
        assertEquals(45, snapshot.wikiListMenu().contentSlots().size());
        assertShippedWikiLoot(snapshot);
        assertEquals(1, count(snapshot, ConfigWarning.Kind.MENU_FALLBACK));
        assertEquals(1, menuWarnings(snapshot).size(), menuWarnings(snapshot).toString());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.SEVERE,
                single(snapshot, ConfigWarning.Kind.MENU_FALLBACK).severity());
    }

    @Test
    void anUnreadableRootOrAPageWithNoContentFallsBackToTheShippedClaimLayoutWithOneSevereWarning() {
        for (String broken : List.of(
                "rows: [\n",
                "- invalid-root\n",
                "rows: 1\ncontent_slots: [99]\nfiller: {id: stone}\n")) {
            ConfigSnapshot snapshot = load(broken, null);

            assertShippedClaim(snapshot);
            assertEquals(1, menuWarnings(snapshot).size(), broken + " " + menuWarnings(snapshot));
            ConfigWarning warning = single(snapshot, ConfigWarning.Kind.MENU_FALLBACK);
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.SEVERE, warning.severity(), broken);
        }
    }
}
