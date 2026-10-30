package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuRendererTest {

    private static final List<String> MISSING_LAYOUT_KEYS = List.of(
            "menu_layout_item_missing_name", "menu_layout_item_missing_lore");

    @BeforeEach
    void start() {
        MockBukkit.mock();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private static final String MENU = """
            rows: 3
            content_slots: ['0-9']
            filler: {id: gray_stained_glass_pane}
            buttons:
              previous_page:
                slot: 18
                item: {id: arrow}
              next_page:
                slot: 26
                item: {id: arrow}
            """;

    private static final NameCatalog CATALOG = new NameCatalog() {
        @Override
        public Optional<String> currentId(String id) {
            return Optional.of(id);
        }

        @Override
        public boolean soundExists(String key) {
            return "minecraft:ui.button.click".equals(key);
        }
    };

    private static String resource(String path) throws IOException {
        try (InputStream in = MenuRendererTest.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ConfigSnapshot config(String menu) throws IOException {
        return ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", resource("languages/en.yml"))
                .menu("claim", menu)
                .build(), CATALOG, WarningKinds.all());
    }

    private static MenuRenderer renderer(ConfigSnapshot config) {
        return new MenuRenderer(ItemSourceRegistry.create(), key -> config.message("", key), me.rakkoon69.rakunloots.warning.Warnings.ignore());
    }

    private static List<MenuIcon> entries(int count) {
        List<MenuIcon> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            entries.add(new MenuIcon(new ItemStack(Material.PAPER), Optional.empty(), Optional.empty()));
        }
        return entries;
    }

    private static MenuView page(MenuRenderer renderer, ConfigSnapshot config, int count, int requested) {
        return page(renderer, config, count, requested, page -> {
        });
    }

    private static MenuView page(MenuRenderer renderer, ConfigSnapshot config, int count, int requested,
                                 java.util.function.IntConsumer goToPage) {
        return renderer.render("claim", "Claims", config.claimMenu(), entries(count), requested, goToPage);
    }

    private static void assertFiller(MenuView view, int slot) {
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, view.icons().get(slot).item().getType(), "slot " + slot);
    }

    private static void assertEmpty(MenuView view, int slot) {
        assertNull(view.icons().get(slot), "slot " + slot);
    }

    private static void assertArrow(MenuView view, int slot) {
        assertEquals(Material.ARROW, view.icons().get(slot).item().getType(), "slot " + slot);
    }

    @Test
    void zeroEntriesIsOneEmptyPage() {
        ConfigSnapshot config = assertDoesNotThrowLoad(MENU);
        MenuRenderer renderer = renderer(config);
        MenuView view = page(renderer, config, 0, 0);

        assertEquals(1, renderer.pageCount(config.claimMenu(), 0));
        for (int slot = 0; slot < 10; slot++) {
            assertEmpty(view, slot);
        }
        assertFiller(view, 18);
        assertFiller(view, 26);
        assertEquals(27, view.size());
    }

    @Test
    void tenEntriesFitOnOnePageWithNoPageButtons() {
        ConfigSnapshot config = assertDoesNotThrowLoad(MENU);
        MenuRenderer renderer = renderer(config);
        MenuView view = page(renderer, config, 10, 0);

        assertEquals(1, renderer.pageCount(config.claimMenu(), 10));
        for (int slot = 0; slot < 10; slot++) {
            assertEquals(Material.PAPER, view.icons().get(slot).item().getType(), "slot " + slot);
        }
        assertFiller(view, 18);
        assertFiller(view, 26);
    }

    @Test
    void elevenEntriesNeedTwoPages() {
        ConfigSnapshot config = assertDoesNotThrowLoad(MENU);
        MenuRenderer renderer = renderer(config);
        MenuView first = page(renderer, config, 11, 0);

        assertEquals(2, renderer.pageCount(config.claimMenu(), 11));
        assertEquals(Material.PAPER, first.icons().get(9).item().getType());
        assertFiller(first, 18);
        assertArrow(first, 26);
        assertEquals("§a[Next page >]", first.icons().get(26).item().getItemMeta().getDisplayName());
        assertEquals("§7Page 2 of 2", first.icons().get(26).item().getItemMeta().getLore().get(0));

        MenuView second = page(renderer, config, 11, 1);
        assertEquals(Material.PAPER, second.icons().get(0).item().getType());
        for (int slot = 1; slot < 10; slot++) {
            assertEmpty(second, slot);
        }
        assertArrow(second, 18);
        assertEquals("§a[< Previous page]", second.icons().get(18).item().getItemMeta().getDisplayName());
        assertEquals("§7Page 1 of 2", second.icons().get(18).item().getItemMeta().getLore().get(0));
        assertFiller(second, 26);
    }

    @Test
    void twentyFiveEntriesHaveThreePagesWithTrailingEmptyContentSlots() {
        ConfigSnapshot config = assertDoesNotThrowLoad(MENU);
        MenuRenderer renderer = renderer(config);

        assertEquals(3, renderer.pageCount(config.claimMenu(), 25));
        MenuView middle = page(renderer, config, 25, 1);
        for (int slot = 0; slot < 10; slot++) {
            assertEquals(Material.PAPER, middle.icons().get(slot).item().getType(), "slot " + slot);
        }
        assertArrow(middle, 18);
        assertArrow(middle, 26);

        MenuView last = page(renderer, config, 25, 2);
        for (int slot = 0; slot < 5; slot++) {
            assertEquals(Material.PAPER, last.icons().get(slot).item().getType(), "slot " + slot);
        }
        for (int slot = 5; slot < 10; slot++) {
            assertEmpty(last, slot);
        }
        assertArrow(last, 18);
        assertFiller(last, 26);
    }

    @Test
    void outOfRangeAndNegativePagesClampToTheLastPage() {
        ConfigSnapshot config = assertDoesNotThrowLoad(MENU);
        MenuRenderer renderer = renderer(config);

        for (int requested : List.of(-1, 3, 99)) {
            MenuView view = page(renderer, config, 25, requested);
            assertEquals(Material.PAPER, view.icons().get(4).item().getType(), "requested " + requested);
            assertEmpty(view, 5);
            assertArrow(view, 18);
            assertFiller(view, 26);
        }
    }

    @Test
    void pageButtonRunsTheCallbackWithTheDestinationPage() {
        ConfigSnapshot config = assertDoesNotThrowLoad(MENU);
        MenuRenderer renderer = renderer(config);
        AtomicInteger destination = new AtomicInteger(-1);

        MenuView first = page(renderer, config, 11, 0, destination::set);
        first.icons().get(26).action().orElseThrow().run();
        assertEquals(1, destination.get());

        MenuView second = page(renderer, config, 11, 1, destination::set);
        second.icons().get(18).action().orElseThrow().run();
        assertEquals(0, destination.get());
    }

    @Test
    void aMissingLayoutItemDrawsABarrierAndWarnsOncePerReload() {
        ConfigSnapshot config = assertDoesNotThrowLoad("""
                rows: 3
                content_slots: ['0-9']
                filler: {source: ghost, id: diamond}
                buttons:
                  next_page:
                    slot: 26
                    item: {source: ghost, id: arrow}
                """);
        List<MenuWarning> warnings = new ArrayList<>();
        FakeServerAdapter adapter = new FakeServerAdapter(java.util.UUID.randomUUID());
        MenuRenderer renderer = new MenuRenderer(ItemSourceRegistry.create(), key -> config.message("", key),
                w -> warnings.add((MenuWarning) w));
        MenuService menus = new MenuService(adapter, renderer);

        MenuView first = page(renderer, config, 11, 0);
        MenuView second = page(renderer, config, 11, 0);
        assertEquals(Material.BARRIER, first.icons().get(10).item().getType());
        assertEquals(Material.BARRIER, second.icons().get(26).item().getType());
        assertEquals(2, warnings.size(), warnings.toString());
        assertEquals(List.of("filler", "button:next_page"),
                warnings.stream().map(MenuWarning::entry).toList());

        menus.reload(config);
        page(renderer, config, 11, 0);
        assertEquals(4, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().allMatch(warning -> warning.kind() == MenuWarning.Kind.MENU_LAYOUT_ITEM_MISSING));
        assertFalse(warnings.get(0).item().id().isBlank());
    }

    @Test
    void theShippedClaimLayoutRendersItsRealItemsAndButtons() throws IOException {
        ConfigSnapshot config = ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", resource("languages/en.yml"))
                .menu("claim", resource("menus/claim.yml"))
                .menu("wiki", resource("menus/wiki.yml"))
                .build(), CATALOG, WarningKinds.all());
        MenuRenderer renderer = renderer(config);
        MenuView view = renderer.render("claim", "Claims", config.claimMenu(), entries(46), 0, page -> {
        });

        assertEquals(Material.PAPER, view.icons().get(0).item().getType());
        assertEquals(Material.PAPER, view.icons().get(44).item().getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, view.icons().get(45).item().getType());
        assertEquals(Material.CHEST_MINECART, view.icons().get(49).item().getType());
        assertEquals(Material.ARROW, view.icons().get(53).item().getType());
        assertEquals("§a[Next page >]", view.icons().get(53).item().getItemMeta().getDisplayName());
    }

    @Test
    void pagingLanguageTextIsTranslatedAndColored() throws IOException {
        ConfigSnapshot config = ConfigLoader.load(ConfigSources.builder()
                .globals("language: vi\n")
                .worlds("{}\n")
                .language("vi", resource("languages/vi.yml"))
                .menu("claim", MENU)
                .build(), CATALOG, WarningKinds.all());
        MenuRenderer renderer = renderer(config);
        MenuView view = page(renderer, config, 11, 0);

        assertEquals("§a[Trang sau >]", view.icons().get(26).item().getItemMeta().getDisplayName());
        assertEquals("§7Trang 2/2", view.icons().get(26).item().getItemMeta().getLore().get(0));
    }

    @Test
    void missingLayoutItemUsesTheCapturedLanguageAndAStructuralMarker() throws IOException {
        ConfigSnapshot english = ConfigLoader.load(ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", resource("languages/en.yml"))
                .language("vi", resource("languages/vi.yml"))
                .menu("claim", """
                        rows: 1
                        content_slots: ['0']
                        filler: {source: ghost, id: missing}
                        """)
                .build(), CATALOG, WarningKinds.all());
        ConfigSnapshot vietnamese = ConfigLoader.load(ConfigSources.builder()
                .globals("language: vi\n")
                .worlds("{}\n")
                .language("en", resource("languages/en.yml"))
                .language("vi", resource("languages/vi.yml"))
                .menu("claim", """
                        rows: 1
                        content_slots: ['0']
                        filler: {source: ghost, id: missing}
                        """)
                .build(), CATALOG, WarningKinds.all());
        MenuRenderer renderer = renderer(english);

        MenuView view = renderer.render("claim", "Claims", english.claimMenu(), List.of(), 0, page -> {
        }, key -> vietnamese.message("", key));
        ItemStack missing = view.icons().get(1).item();

        assertEquals("§cThieu ghost:missing", missing.getItemMeta().getDisplayName());
        assertEquals("§7Muc menu nay khong the tai.", missing.getItemMeta().getLore().get(0));
        assertTrue(MenuRenderer.isMissingIcon(missing));

        ItemStack legitimateBarrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = legitimateBarrier.getItemMeta();
        meta.setLore(missing.getItemMeta().getLore());
        legitimateBarrier.setItemMeta(meta);
        assertFalse(MenuRenderer.isMissingIcon(legitimateBarrier));

        ItemStack oldEnglishBarrier = new ItemStack(Material.BARRIER);
        ItemMeta oldEnglishMeta = oldEnglishBarrier.getItemMeta();
        oldEnglishMeta.setLore(List.of("§7This menu item could not be loaded."));
        oldEnglishBarrier.setItemMeta(oldEnglishMeta);
        assertFalse(MenuRenderer.isMissingIcon(oldEnglishBarrier));
    }

    @Test
    void everyShippedLanguageHasTheMissingLayoutMessages() throws IOException {
        List<ConfigSnapshot> snapshots = new ArrayList<>();
        for (String language : List.of("en", "vi", "zh")) {
            ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                    .globals("language: " + language + "\n")
                    .worlds("{}\n")
                    .language("en", resource("languages/en.yml"))
                    .language("vi", resource("languages/vi.yml"))
                    .language("zh", resource("languages/zh.yml"))
                    .build(), CATALOG, WarningKinds.all());
            snapshots.add(snapshot);
            assertTrue(snapshot.languageKeys(language).containsAll(MISSING_LAYOUT_KEYS), language);
            for (String key : MISSING_LAYOUT_KEYS) {
                assertFalse(snapshot.message("", key).isBlank(), language + " " + key);
            }
        }
        for (String key : MISSING_LAYOUT_KEYS) {
            int distinct = Set.copyOf(snapshots.stream().map(snapshot -> snapshot.message("", key)).toList()).size();
            assertEquals(3, distinct, key);
        }
    }

    private static ConfigSnapshot assertDoesNotThrowLoad(String menu) {
        try {
            return config(menu);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
