package me.rakkoon69.rakunloots.wiki;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiMenuTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a1");
    private static final String HEAD_DATA = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJjIn19fQ==";

    private static final String LANGUAGE = """
            prefix: ""
            menu_wiki_list_title: "&8Loots wiki"
            menu_wiki_list_entry_name: "&e{loot}"
            menu_wiki_list_entry_lore:
              - "&7{description}"
            menu_wiki_list_timer_chance: "&7Chance: &e{chance:2}%"
            menu_wiki_list_not_on_timer: "&7No timer"
            menu_wiki_list_click: "&eClick"
            menu_wiki_loot_title: "&8Loot {loot}"
            menu_wiki_content_chance: "&7Chance: &e{chance:2}%"
            menu_wiki_content_always: "&7Always included"
            menu_wiki_content_calculating: "&7Chance: calculating"
            menu_wiki_content_amount: "&7Amount: &e{amount}"
            menu_wiki_content_missing_name: "&cMissing {item}"
            menu_wiki_content_missing_lore:
              - "&7That item is not loaded."
            menu_wiki_info_name: "&6{loot} info"
            menu_wiki_info_open_mode: "&7Open mode: &e{open_mode}"
            menu_wiki_open_mode_drop: "&edrop"
            menu_wiki_open_mode_inventory: "&einventory"
            menu_wiki_info_lock: "&7Lock: &e{lock}"
            menu_wiki_info_no_lock: "&7Lock: &aNo lock"
            menu_wiki_info_life: "&7Life: &e{life}"
            menu_wiki_info_unending: "&7Life: &aUnending"
            menu_wiki_info_items: "&7Items: &e{min}&7 to &e{max}"
            menu_wiki_info_worlds: "&7Worlds:"
            menu_wiki_info_world: "&8- &e{world}&7: &e{chance:2}%"
            menu_wiki_info_no_worlds: "&7No world timer summons this loot."
            menu_button_back_name: "&a[Back]"
            menu_button_back_lore:
              - "&7Back to the list"
            menu_button_previous_page_name: "&a[Prev]"
            menu_button_previous_page_lore: "&7{page}/{pages}"
            menu_button_next_page_name: "&a[Next]"
            menu_button_next_page_lore: "&7{page}/{pages}"
            """;

    private static final String WIKI_MENU = """
            list:
              rows: 2
              content_slots: ["0-8"]
              filler:
                id: stone
              buttons:
                previous_page:
                  slot: 9
                  item:
                    id: arrow
                next_page:
                  slot: 17
                  item:
                    id: arrow
            loot:
              rows: 2
              content_slots: ["0-8"]
              filler:
                id: stone
              buttons:
                back:
                  slot: 9
                  item:
                    id: oak_door
                info:
                  slot: 10
                  item:
                    id: book
            """;

    private static final String ROWS_MISMATCH_MENU = """
            list:
              rows: 2
              content_slots: ["0-8"]
              filler:
                id: stone
              buttons:
                previous_page:
                  slot: 9
                  item:
                    id: arrow
                next_page:
                  slot: 17
                  item:
                    id: arrow
            loot:
              rows: 6
              content_slots: ["0-44"]
              filler:
                id: stone
              buttons:
                back:
                  slot: 48
                  item:
                    id: oak_door
                info:
                  slot: 49
                  item:
                    id: book
            """;

    private static final List<String> SHIPPED_LOOTS = List.of(
            "airdrops", "buried_stash", "deep_vault", "head_trophy", "sky_lantern", "sunken_treasure",
            "treasure_chest");

    private FakeServerAdapter adapter;
    private ConfigSnapshot config;
    private WikiMenu menu;
    private MenuService menus;
    private UUID player;
    private final List<String> failures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        StreamableServerMock.mock();
        adapter = new FakeServerAdapter(WORLD);
        player = UUID.randomUUID();
        adapter.addOnlinePlayer(player);
        use(snapshot("{}", new LinkedHashMap<>()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void rowsSortByTimerChanceThenIdAndHiddenLootsStayInTheDenominator() {
        Map<String, String> loots = new LinkedHashMap<>();
        loots.put("delta", loot("delta", "barrel", "Delta description", ""));
        loots.put("hidden", loot("hidden", "barrel", "Hidden description", "wiki: false\n"));
        loots.put("gamma", loot("gamma", "barrel", "Gamma description", ""));
        loots.put("alpha", loot("alpha", "barrel", "Alpha description", ""));
        loots.put("beta", loot("beta", "barrel", "Beta description", ""));
        use(snapshot("""
                worlds:
                  world:
                    loots:
                      alpha: 10
                      beta: 30
                      gamma: 30
                      hidden: 50
                """, loots));

        open(new SenderLocation(WORLD, 0, 64, 0));

        Map<Integer, ItemStack> shown = contents(0);
        assertEquals("alpha", named(shown.get(0)));
        assertEquals("beta", named(shown.get(1)));
        assertEquals("gamma", named(shown.get(2)));
        assertEquals("delta", named(shown.get(3)));
        assertFalse(shown.values().stream().anyMatch(item -> named(item).contains("hidden")));
        assertTrue(lore(shown.get(0)).contains("Alpha description"), lore(shown.get(0)));
        assertTrue(lore(shown.get(0)).contains("8.33"), lore(shown.get(0)));
        assertTrue(lore(shown.get(1)).contains("25.00"), lore(shown.get(1)));
        assertTrue(lore(shown.get(3)).contains("No timer"), lore(shown.get(3)));
        assertTrue(lore(shown.get(3)).contains("Click"), lore(shown.get(3)));
    }

    @Test
    void aConfiguredIconReplacesTheBlockAndAHeadKeepsItsData() {
        Map<String, String> loots = new LinkedHashMap<>();
        loots.put("alpha", loot("alpha", "barrel", null, "wiki:\n  icon:\n    id: emerald\n"));
        loots.put("beta", loot("beta", "player_head", null, "head_data: '" + HEAD_DATA + "'\n"));
        use(snapshot("{}", loots));

        open(new SenderLocation(WORLD, 0, 64, 0));

        assertEquals(Material.EMERALD, contents(0).get(0).getType());
        assertEquals(Material.PLAYER_HEAD, contents(0).get(1).getType());
        assertEquals(List.of(new FakeServerAdapter.BlockItemCall("minecraft:player_head", Optional.of(HEAD_DATA))),
                adapter.blockItemCalls());
    }

    @Test
    void anUnresolvableIconAndABlockWithoutAnItemDrawBarriers() {
        Map<String, String> loots = new LinkedHashMap<>();
        loots.put("alpha", loot("alpha", "barrel", null, "wiki:\n  icon:\n    id: not_an_item\n"));
        loots.put("beta", loot("beta", "water", null, ""));
        use(snapshot("{}", loots));

        open(new SenderLocation(WORLD, 0, 64, 0));

        assertEquals(Material.BARRIER, contents(0).get(0).getType());
        assertEquals(Material.BARRIER, contents(0).get(1).getType());
    }

    @Test
    void aLootPageShowsItsContentsAndBackKeepsTheListPage() {
        Map<String, String> loots = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            String id = String.format("loot%02d", index);
            loots.put(id, loot(id, "barrel", null, ""));
        }
        use(snapshot("{}", loots));
        open(new SenderLocation(WORLD, 0, 64, 0));
        assertEquals("loot00", named(contents(0).get(0)));

        menus.click(player, 17).join();
        assertEquals("loot09", named(contents(1).get(0)));

        menus.click(player, 0).join();
        assertEquals(2, adapter.menuOpens().size(), "the loot page did not replace the list page");
        FakeServerAdapter.MenuOpen loot = adapter.menuOpens().get(1);
        Map<Integer, ItemStack> stub = loot.contents();
        assertEquals("§8Loot loot09", loot.title());
        assertEquals(18, loot.size());
        assertEquals(Material.OAK_DOOR, stub.get(9).getType());
        assertEquals("§a[Back]", stub.get(9).getItemMeta().getDisplayName());
        assertTrue(String.join(" ", stub.get(9).getItemMeta().getLore()).contains("Back to the list"));
        assertEquals(Material.DIAMOND, stub.get(0).getType());
        assertTrue(lore(stub.get(0)).contains("Chance"), lore(stub.get(0)));
        for (int slot = 1; slot <= 8; slot++) {
            assertFalse(stub.containsKey(slot), "unused content slot " + slot + " is empty");
        }
        assertEquals(Material.BOOK, stub.get(10).getType());
        assertEquals("loot09 info", named(stub.get(10)));
        for (int slot = 11; slot <= 17; slot++) {
            assertEquals(Material.STONE, stub.get(slot).getType(), "slot " + slot);
        }

        int opens = adapter.menuOpens().size();
        int updates = adapter.menuUpdates().size();
        menus.click(player, 10).join();
        assertEquals(opens, adapter.menuOpens().size(), "the info icon is read-only");
        assertEquals(updates, adapter.menuUpdates().size(), "the info icon is read-only");

        menus.click(player, 9).join();
        assertEquals(3, adapter.menuOpens().size(), "the list page did not replace the loot page");
        FakeServerAdapter.MenuOpen back = adapter.menuOpens().get(2);
        assertEquals("§8Loots wiki", back.title());
        assertEquals(18, back.size());
        assertEquals("loot09", named(back.contents().get(0)));
    }

    @Test
    void aLootPageWithDifferentRowsReplacesTheListInventory() {
        Map<String, String> loots = new LinkedHashMap<>();
        loots.put("alpha", loot("alpha", "barrel", null, ""));
        use(snapshot("{}", loots, ROWS_MISMATCH_MENU));

        open(new SenderLocation(WORLD, 0, 64, 0));
        assertEquals(18, adapter.menuOpens().get(0).size());

        menus.click(player, 0).join();
        assertEquals(2, adapter.menuOpens().size(), "the loot page did not replace the list page");
        FakeServerAdapter.MenuOpen loot = adapter.menuOpens().get(1);
        assertEquals("§8Loot alpha", loot.title());
        assertEquals(54, loot.size());
        assertTrue(loot.contents().containsKey(48), "the back button is at the loot page's slot 48");

        menus.click(player, 48).join();
        assertEquals(3, adapter.menuOpens().size(), "the list page did not replace the loot page");
        FakeServerAdapter.MenuOpen list = adapter.menuOpens().get(2);
        assertEquals("§8Loots wiki", list.title());
        assertEquals(18, list.size());
        assertEquals("alpha", named(list.contents().get(0)));
    }

    @Test
    void aViewerInAnotherWorldGetsNoTimerChanceAndLootsSortById() {
        Map<String, String> loots = new LinkedHashMap<>();
        loots.put("beta", loot("beta", "barrel", null, ""));
        loots.put("alpha", loot("alpha", "barrel", null, ""));
        use(snapshot("""
                worlds:
                  world:
                    loots:
                      alpha: 10
                      beta: 90
                """, loots));

        open(new SenderLocation(UUID.randomUUID(), 0, 64, 0));

        assertEquals("alpha", named(contents(0).get(0)));
        assertEquals("beta", named(contents(0).get(1)));
        assertTrue(lore(contents(0).get(0)).contains("No timer"), lore(contents(0).get(0)));
        assertTrue(lore(contents(0).get(1)).contains("No timer"), lore(contents(0).get(1)));
    }

    @Test
    void shippedWorldsAndLootsPinTheListOrder() throws IOException {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .language("en", resource("/languages/en.yml"))
                .menu("wiki", resource("/menus/wiki.yml"));
        for (String id : SHIPPED_LOOTS) {
            sources.loot(id, resource("/loots/" + id + ".yml"));
        }
        use(ConfigLoader.load(sources.build(), catalog(), WarningKinds.all()));

        open(new SenderLocation(WORLD, 0, 64, 0));

        Map<Integer, ItemStack> shown = contents(0);
        List<String> names = new ArrayList<>();
        for (int slot = 0; slot < SHIPPED_LOOTS.size(); slot++) {
            names.add(named(shown.get(slot)));
        }
        assertEquals(List.of("Deep Vault", "Head Trophy", "Buried Stash", "Treasure Chest", "Air Supply",
                "Sky Lantern", "Sunken Treasure"), names);
    }

    private void open(SenderLocation location) {
        menu.open(player, location);
        assertEquals(1, adapter.menuOpens().size(), "the menu did not open");
    }

    private Map<Integer, ItemStack> contents(int update) {
        return update == 0 ? adapter.menuOpens().get(0).contents() : adapter.menuUpdates().get(update - 1).contents();
    }

    private static String named(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getDisplayName() == null) {
            return "";
        }
        return ChatColor.stripColor(item.getItemMeta().getDisplayName());
    }

    private static String lore(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getLore() == null) {
            return "";
        }
        return String.join(" ", item.getItemMeta().getLore());
    }

    private void use(ConfigSnapshot snapshot) {
        config = snapshot;
        ItemSourceRegistry items = ItemSourceRegistry.create();
        MenuRenderer renderer = new MenuRenderer(items, key -> config.message("", key), Warnings.ignore());
        menus = new MenuService(adapter, renderer);
        LootChanceCache chances = new LootChanceCache(adapter.async());
        menu = new WikiMenu(adapter, () -> config, items, menus, renderer, chances,
                (what, cause) -> failures.add(what));
        chances.load(snapshot);
    }

    private static ConfigSnapshot snapshot(String worlds, Map<String, String> loots) {
        return snapshot(worlds, loots, WIKI_MENU);
    }

    private static ConfigSnapshot snapshot(String worlds, Map<String, String> loots, String menu) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\nbanned_loot_blocks: []\n")
                .worlds(worlds)
                .language("en", LANGUAGE)
                .menu("wiki", menu);
        loots.forEach(sources::loot);
        return ConfigLoader.load(sources.build(), catalog(), WarningKinds.all());
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = WikiMenuTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String loot(String id, String block, String description, String extra) {
        return "display_name: '" + id + "'\n"
                + (description == null ? "" : "description:\n  - '" + description + "'\n")
                + "open_mode: drop\n"
                + "block: " + block + "\n"
                + extra
                + "roll:\n"
                + "  entries:\n"
                + "    a:\n"
                + "      item:\n"
                + "        id: diamond\n";
    }

    private static NameCatalog catalog() {
        return new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                return Optional.of(writtenId.contains(":") ? writtenId : "minecraft:" + writtenId);
            }

            @Override
            public boolean soundExists(String key) {
                return true;
            }
        };
    }
}
