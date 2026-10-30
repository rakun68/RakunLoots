package me.rakkoon69.rakunloots.wiki;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.Loot;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.text.MessageText;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.roll.LootChance;
import me.rakkoon69.rakunloots.server.MenuHandle;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #112 through the real config, the recording server adapter, and the real chance calculation.
 */
class Phase9LootPageAcceptanceTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a2");
    private static final List<String> SHIPPED_LOOTS = List.of(
            "airdrops", "buried_stash", "deep_vault", "head_trophy", "sky_lantern", "sunken_treasure",
            "treasure_chest");
    private static final List<String> LOOT_PAGE_KEYS = List.of(
            "menu_wiki_content_chance", "menu_wiki_content_always", "menu_wiki_content_calculating",
            "menu_wiki_content_amount", "menu_wiki_content_missing_name", "menu_wiki_content_missing_lore",
            "menu_wiki_info_name", "menu_wiki_info_open_mode", "menu_wiki_open_mode_drop",
            "menu_wiki_open_mode_inventory", "menu_wiki_info_lock", "menu_wiki_info_no_lock",
            "menu_wiki_info_life", "menu_wiki_info_unending", "menu_wiki_info_items", "menu_wiki_info_worlds",
            "menu_wiki_info_world", "menu_wiki_info_no_worlds");

    private static final String LANGUAGE = """
            prefix: ""
            menu_layout_item_missing_name: "&cMissing {item}"
            menu_layout_item_missing_lore: "&7This menu item could not be loaded."
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

    private static final String SMALL_WIKI_MENU = """
            list:
              rows: 2
              content_slots: ["0-8"]
              filler:
                id: stone
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
                    id: not_an_item
            """;

    private static final String PAGED_WIKI_MENU = """
            list:
              rows: 2
              content_slots: ["0-8"]
              filler:
                id: stone
              buttons:
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

    private static final String WIKI_MENU = """
            list:
              rows: 6
              content_slots: ["0-44"]
              filler:
                id: stone
              buttons:
                previous_page:
                  slot: 45
                  item:
                    id: arrow
                next_page:
                  slot: 53
                  item:
                    id: arrow
            loot:
              rows: 6
              content_slots: ["0-44"]
              filler:
                id: stone
              buttons:
                previous_page:
                  slot: 45
                  item:
                    id: arrow
                back:
                  slot: 48
                  item:
                    id: oak_door
                info:
                  slot: 49
                  item:
                    id: book
                next_page:
                  slot: 53
                  item:
                    id: arrow
            """;

    private FakeServerAdapter adapter;
    private ConfigSnapshot config;
    private WikiMenu menu;
    private MenuService menus;
    private LootChanceCache cache;
    private UUID player;
    private final List<String> failures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        StreamableServerMock.mock();
        adapter = new FakeServerAdapter(WORLD);
        player = UUID.randomUUID();
        adapter.addOnlinePlayer(player);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void shippedTreasureChestShowsItsExactContentsChancesAmountsAndInfo() throws IOException {
        use(shippedTreasure());

        Map<Integer, ItemStack> page = openLoot("Treasure Chest");

        assertEquals(Material.STICK, page.get(0).getType());
        assertEquals(1, page.get(0).getAmount());
        assertEquals(Material.IRON_PICKAXE, page.get(1).getType());
        assertEquals(Material.IRON_SWORD, page.get(2).getType());
        assertEquals(Material.BREAD, page.get(3).getType());
        assertEquals(6, page.get(3).getAmount());
        assertEquals(Material.GOLD_INGOT, page.get(4).getType());
        assertEquals(8, page.get(4).getAmount());

        Loot loot = config.loot("treasure_chest").orElseThrow();
        Map<String, LootChance.EntryChance> chances = LootChance.calculate(loot.roll());
        assertEquals(MessageText.formatPercent(chances.get("tagged_stick").probability(), 2),
                chanceShown(page.get(0)), lore(page.get(0)).toString());
        assertEquals(MessageText.formatPercent(chances.get("chest_pickaxe").probability(), 2),
                chanceShown(page.get(1)), lore(page.get(1)).toString());
        assertEquals(MessageText.formatPercent(chances.get("chest_blade").probability(), 2),
                chanceShown(page.get(2)), lore(page.get(2)).toString());
        assertEquals(MessageText.formatPercent(chances.get("bread").probability(), 2),
                chanceShown(page.get(3)), lore(page.get(3)).toString());
        assertTrue(lore(page.get(4)).stream().anyMatch(line -> line.contains("Always")), lore(page.get(4)).toString());

        List<String> blade = lore(page.get(2));
        assertTrue(ChatColor.stripColor(blade.get(0)).contains("Found in a treasure chest."), blade.toString());
        assertTrue(ChatColor.stripColor(blade.get(1)).contains("%"), blade.toString());
        assertTrue(ChatColor.stripColor(blade.get(2)).contains("Amount: 1"), blade.toString());

        ItemStack info = page.get(49);
        assertEquals(Material.BOOK, info.getType());
        assertEquals("Treasure Chest", name(info));
        List<String> infoLore = lore(info);
        String joined = ChatColor.stripColor(String.join("\n", infoLore));
        assertTrue(joined.contains("A chest on solid ground"), joined);
        assertTrue(joined.contains("inventory"), joined);
        assertTrue(joined.contains("0:30"), joined);
        assertTrue(joined.contains("10:00"), joined);
        assertTrue(joined.contains("1 to 3"), joined);
        assertTrue(joined.contains("world"), joined);
        assertTrue(headerBeforeWorld(infoLore), infoLore.toString());
        assertTrue(joined.contains(MessageText.formatPercent(30.0 / 190.0, 2)), joined);

        int opens = adapter.menuOpens().size();
        int updates = adapter.menuUpdates().size();
        int sounds = adapter.sounds().size();
        menus.click(player, 0).join();
        menus.click(player, 49).join();
        assertEquals(opens, adapter.menuOpens().size(), "content and info have no action");
        assertEquals(updates, adapter.menuUpdates().size(), "content and info have no action");
        assertEquals(sounds, adapter.sounds().size(), "content and info have no sound");
    }

    @Test
    void weightZeroIsHiddenAndAMissingItemIsABarrierNamedSourceAndId() {
        use(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  min: 1
                  max: 4
                  entries:
                    hidden:
                      weight: 0
                      amount: 1
                      item:
                        id: not_an_item
                    vanilla_missing:
                      weight: 10
                      amount: 1
                      item:
                        id: not_an_item
                    namespaced_missing:
                      weight: 20
                      amount: 1
                      item:
                        id: 'minecraft:not_an_item'
                    custom_missing:
                      weight: 30
                      amount: 1
                      item:
                        source: mmoitems
                        id: premium
                    drawable:
                      weight: 40
                      amount: 1
                      item:
                        id: diamond
                    always:
                      weight: -1
                      amount: 4
                      item:
                        id: emerald
                """))));

        Map<Integer, ItemStack> page = openLoot("Demo");
        List<String> barriers = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = page.get(slot);
            if (item != null && item.getType() == Material.BARRIER) {
                barriers.add(name(item));
                assertTrue(String.join(" ", lore(item)).contains("not loaded"), lore(item).toString());
            }
        }

        assertEquals(List.of("Missing vanilla:not_an_item", "Missing vanilla:minecraft:not_an_item",
                "Missing mmoitems:premium"), barriers);
        assertEquals(5, filledContent(page), "the weight-zero entry is absent");
        assertEquals(Material.EMERALD, page.get(4).getType(), "always-included entries are last");
        assertEquals(4, page.get(4).getAmount());
        assertNull(page.get(5), "only five non-hidden entries are shown");
    }

    @Test
    void contentStacksCapAtTheItemMaximumButTheLoreKeepsTheConfiguredAmount() {
        use(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      amount: 99
                      item:
                        id: ender_pearl
                """))));

        ItemStack icon = openLoot("Demo").get(0);
        assertEquals(new ItemStack(Material.ENDER_PEARL).getMaxStackSize(), icon.getAmount());
        assertTrue(ChatColor.stripColor(String.join(" ", lore(icon))).contains("Amount: 99"),
                lore(icon).toString());
    }

    @Test
    void infoListsWorldTimersByNameWithTheirChances() {
        use(fixture("""
                worlds:
                  zeta:
                    loots:
                      demo: 30
                      other: 70
                  alpha:
                    loots:
                      demo: 70
                      other: 30
                  none:
                    loots:
                      other: 1
                """, Map.of(
                "demo", loot("Demo", """
                        roll:
                          entries:
                            a:
                              item:
                                id: diamond
                        """),
                "other", loot("Other", """
                        roll:
                          entries:
                            a:
                              item:
                                id: emerald
                        """))));

        List<String> lore = lore(openLoot("Demo").get(49));
        int header = indexContaining(lore, "Worlds:");
        int alpha = indexContaining(lore, "- alpha:");
        int zeta = indexContaining(lore, "- zeta:");
        assertTrue(header >= 0 && header < alpha && alpha < zeta, lore.toString());
        assertTrue(ChatColor.stripColor(lore.get(alpha)).contains("70.00"), lore.get(alpha));
        assertTrue(ChatColor.stripColor(lore.get(zeta)).contains("30.00"), lore.get(zeta));
        assertFalse(lore.stream().anyMatch(line -> ChatColor.stripColor(line).contains("- none")), lore.toString());
    }

    @Test
    void noLockAndUnendingShowTheirLinesAndTimesUseTheSharedFormatter() {
        Map<String, String> loots = new LinkedHashMap<>();
        loots.put("open", loot("No Lock", """
                lock: 0
                life: -1
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """));
        loots.put("timed", loot("Timed", """
                lock: 3725
                life: 600
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """));
        use(fixture("{}", loots));

        String open = ChatColor.stripColor(String.join("\n", lore(openLoot("No Lock").get(49))));
        assertTrue(open.contains("No lock"), open);
        assertTrue(open.contains("Unending"), open);
        assertTrue(open.contains("No world timer summons this loot."), open);

        menus.click(player, 48).join();
        String timed = ChatColor.stripColor(String.join("\n", lore(openLoot("Timed").get(49))));
        assertTrue(timed.contains("1:02:05"), timed);
        assertTrue(timed.contains("10:00"), timed);
    }

    @Test
    void aPageOpenedBeforeChancesAreReadyCalculatesThenRefreshesInPlaceWhenTheyArrive() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """))));
        assertEquals(1, adapter.asyncScheduler().runCalls(), "one chance job after the load");

        Map<Integer, ItemStack> page = openLoot("Demo");
        assertEquals(1, adapter.asyncScheduler().runCalls(), "opening a page schedules no chance work");
        assertTrue(String.join(" ", lore(page.get(0))).contains("calculating"), lore(page.get(0)).toString());

        adapter.releaseAsync();

        assertEquals(1, adapter.asyncScheduler().runCalls(), "the held job was the only one");
        assertEquals(1, adapter.menuUpdates().size(), "the open page refreshes in place");
        assertEquals(2, adapter.menuOpens().size(), "the loot page is not reopened");
        ItemStack refreshed = adapter.menuUpdates().get(0).contents().get(0);
        String refreshedLore = String.join(" ", lore(refreshed));
        assertFalse(refreshedLore.contains("calculating"), refreshedLore);
        assertTrue(refreshedLore.contains("%"), refreshedLore);
    }

    @Test
    void anAlwaysIncludedEntrySaysSoBeforeChancesArrive() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    drawable:
                      item:
                        id: diamond
                    always:
                      weight: -1
                      amount: 4
                      item:
                        id: emerald
                """))));

        Map<Integer, ItemStack> page = openLoot("Demo");

        assertTrue(String.join(" ", lore(page.get(0))).contains("calculating"), lore(page.get(0)).toString());
        assertTrue(String.join(" ", lore(page.get(1))).contains("Always included"), lore(page.get(1)).toString());
        assertFalse(String.join(" ", lore(page.get(1))).contains("calculating"), lore(page.get(1)).toString());
    }

    @Test
    void aMissingInfoLayoutItemKeepsTheMenuBarrierNameAndLore() {
        use(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """)), SMALL_WIKI_MENU));

        ItemStack info = openLoot("Demo").get(10);

        assertEquals(Material.BARRIER, info.getType());
        assertEquals("Missing vanilla:not_an_item", name(info));
        List<String> lore = lore(info);
        assertTrue(ChatColor.stripColor(String.join(" ", lore)).contains("This menu item could not be loaded."),
                lore.toString());
        assertFalse(ChatColor.stripColor(String.join(" ", lore)).contains("Demo info"), lore.toString());

        int opens = adapter.menuOpens().size();
        int updates = adapter.menuUpdates().size();
        int sounds = adapter.sounds().size();
        menus.click(player, 10).join();
        assertEquals(opens, adapter.menuOpens().size());
        assertEquals(updates, adapter.menuUpdates().size());
        assertEquals(sounds, adapter.sounds().size());
    }

    @Test
    void aMissingBackLayoutItemKeepsItsBarrierNameAndStillReturnsToList() {
        use(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """)), SMALL_WIKI_MENU.replace("id: oak_door", "id: not_an_item")));

        int before = adapter.menuOpens().size();
        menu.open(player, new SenderLocation(WORLD, 0, 64, 0));
        assertEquals(before + 1, adapter.menuOpens().size(), "the list did not open");
        menus.click(player, 0).join();
        assertEquals(before + 2, adapter.menuOpens().size(), "the loot page did not open");

        ItemStack back = adapter.menuOpens().get(before + 1).contents().get(9);
        assertEquals(Material.BARRIER, back.getType());
        assertEquals("Missing vanilla:not_an_item", name(back));
        assertTrue(ChatColor.stripColor(String.join(" ", lore(back))).contains("This menu item could not be loaded."),
                lore(back).toString());

        menus.click(player, 9).join();
        assertEquals(before + 3, adapter.menuOpens().size(), "the missing back barrier still returns to the list");
        assertEquals("§8Loots wiki", adapter.menuOpens().get(before + 2).title());
    }

    @Test
    void anExceptionalLootPageOpenDoesNotLeaveTheSessionOnALootPage() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """))));
        int before = adapter.menuOpens().size();
        menu.open(player, new SenderLocation(WORLD, 0, 64, 0));
        assertEquals(before + 1, adapter.menuOpens().size(), "the list did not open");
        int slot = slotNamed(adapter.menuOpens().get(before).contents(), "Demo");

        adapter.failMenuOpens();
        menus.click(player, slot).join();
        assertFalse(failures.isEmpty(), "the failed open is reported");
        failures.clear();
        int updates = adapter.menuUpdates().size();

        adapter.releaseAsync();

        assertEquals(updates, adapter.menuUpdates().size(), "the still-open list is not refreshed");
    }

    @Test
    void pagingTheListDuringAFailedLootOpenDoesNotLeaveALootPageBehind() {
        Map<String, String> loots = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            loots.put("demo" + index, loot("Demo" + index, """
                    roll:
                      entries:
                        a:
                          item:
                            id: diamond
                    """));
        }
        useHeld(fixture("{}", loots, PAGED_WIKI_MENU));
        int before = adapter.menuOpens().size();
        menu.open(player, new SenderLocation(WORLD, 0, 64, 0));
        assertEquals(before + 1, adapter.menuOpens().size(), "the list did not open");

        adapter.holdMenuOpens();
        menus.click(player, 0).join();
        menus.click(player, 17).join();
        adapter.refuseMenuOpens();
        adapter.releaseMenuOpens();
        int updates = adapter.menuUpdates().size();

        adapter.releaseAsync();

        assertEquals(updates, adapter.menuUpdates().size(), "the list stays on its new page");
        assertEquals("Demo9", name(adapter.menuUpdates().get(0).contents().get(0)));
    }

    @Test
    void aLootPageThatFailsToOpenDoesNotRefreshOverTheStillOpenList() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """))));
        int before = adapter.menuOpens().size();
        menu.open(player, new SenderLocation(WORLD, 0, 64, 0));
        assertEquals(before + 1, adapter.menuOpens().size(), "the list did not open");
        Map<Integer, ItemStack> list = adapter.menuOpens().get(before).contents();
        int slot = slotNamed(list, "Demo");

        adapter.refuseMenuOpens();
        menus.click(player, slot).join();
        assertEquals(before + 1, adapter.menuOpens().size(), "the loot page open was refused");
        int updates = adapter.menuUpdates().size();

        adapter.releaseAsync();

        assertEquals(updates, adapter.menuUpdates().size(), "the still-open list is not refreshed");
        assertEquals("Demo", name(adapter.menuOpens().get(before).contents().get(slot)));
    }

    @Test
    void aReadyChanceRefreshSurvivesARefusedBackReplacement() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """))));
        openLoot("Demo");
        assertTrue(String.join(" ", lore(adapter.menuOpens().get(1).contents().get(0))).contains("calculating"));

        adapter.holdMenuOpens();
        menus.click(player, 48).join();
        adapter.releaseAsync();
        assertTrue(cache.chances(config, "demo").isPresent());

        adapter.refuseMenuOpens();
        adapter.releaseMenuOpens();
        assertEquals(1, adapter.menuUpdates().size(),
                "the still-visible loot page receives its ready refresh after Back is refused");
        assertFalse(String.join(" ", lore(adapter.menuUpdates().get(0).contents().get(0))).contains("calculating"));
    }

    @Test
    void aReadyChanceRefreshSurvivesAnExceptionalBackReplacement() {
        ConfigSnapshot snapshot = fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """)));
        AtomicInteger opens = new AtomicInteger();
        CompletableFuture<Optional<MenuHandle>> failedBack = new CompletableFuture<>();
        ServerAdapter server = (ServerAdapter) Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("openMenu") && opens.incrementAndGet() == 3) {
                        return failedBack;
                    }
                    try {
                        return method.invoke(adapter, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        config = snapshot;
        ItemSourceRegistry items = ItemSourceRegistry.create();
        cache = new LootChanceCache(server.async());
        MenuRenderer renderer = new MenuRenderer(items, key -> config.message("", key), Warnings.ignore());
        menus = new MenuService(server, renderer);
        menu = new WikiMenu(server, () -> config, items, menus, renderer, cache,
                (what, cause) -> failures.add(what + ": " + cause));
        adapter.holdAsync();
        cache.load(snapshot);

        openLoot("Demo");
        adapter.holdMenuOpens();
        menus.click(player, 48).join();
        adapter.releaseAsync();
        assertTrue(cache.chances(snapshot, "demo").isPresent());

        failedBack.completeExceptionally(new IllegalStateException("replacement failed"));
        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.stream().anyMatch(failure -> failure.startsWith("could not replace the loots wiki")),
                failures.toString());
        failures.clear();
        assertEquals(1, adapter.menuUpdates().size(),
                "the still-visible loot page receives its ready refresh after Back fails");
        assertFalse(String.join(" ", lore(adapter.menuUpdates().get(0).contents().get(0))).contains("calculating"));
    }

    @Test
    void twoLootPageOpensThatFailDoNotLeaveTheSessionOnALootPage() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """))));
        int before = adapter.menuOpens().size();
        menu.open(player, new SenderLocation(WORLD, 0, 64, 0));
        assertEquals(before + 1, adapter.menuOpens().size(), "the list did not open");
        Map<Integer, ItemStack> list = adapter.menuOpens().get(before).contents();
        int slot = slotNamed(list, "Demo");

        adapter.holdMenuOpens();
        menus.click(player, slot).join();
        menus.click(player, slot).join();
        adapter.refuseMenuOpens();
        adapter.releaseMenuOpens();
        assertEquals(before + 1, adapter.menuOpens().size(), "both loot page opens were refused");
        int updates = adapter.menuUpdates().size();

        adapter.releaseAsync();

        assertEquals(updates, adapter.menuUpdates().size(), "the still-open list is not refreshed");
        assertEquals("Demo", name(adapter.menuOpens().get(before).contents().get(slot)));
    }

    @Test
    void aPageClosedBeforeChancesArriveIsNotRefreshed() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """))));
        openLoot("Demo");
        menus.close(player).join();
        int opens = adapter.menuOpens().size();
        int updates = adapter.menuUpdates().size();

        adapter.releaseAsync();

        assertEquals(opens, adapter.menuOpens().size(), "a closed page is not reopened");
        assertEquals(updates, adapter.menuUpdates().size(), "a closed page is not refreshed");
    }

    @Test
    void aPageBackOnTheListBeforeChancesArriveIsNotRefreshedBackToTheLootPage() {
        useHeld(fixture("{}", Map.of("demo", loot("Demo", """
                roll:
                  entries:
                    a:
                      item:
                        id: diamond
                """))));
        openLoot("Demo");
        menus.click(player, 48).join();
        int opens = adapter.menuOpens().size();
        int updates = adapter.menuUpdates().size();

        adapter.releaseAsync();

        assertEquals(opens, adapter.menuOpens().size());
        assertEquals(updates, adapter.menuUpdates().size(), "the list page the player moved to is left alone");
    }

    @Test
    void fiftyEntriesPageOnTheShippedLayoutAndBackReturnsToTheList() {
        StringBuilder roll = new StringBuilder("roll:\n  min: 1\n  max: 1\n  entries:\n");
        for (int index = 0; index < 50; index++) {
            roll.append("    e").append(index).append(":\n")
                    .append("      weight: 1\n")
                    .append("      amount: ").append(index + 1).append("\n")
                    .append("      item:\n")
                    .append("        id: diamond\n");
        }
        use(fixture("{}", Map.of("demo", loot("Demo", roll.toString()))));

        Map<Integer, ItemStack> first = openLoot("Demo");
        assertEquals(1, first.get(0).getAmount());
        assertEquals(45, first.get(44).getAmount());
        assertEquals(Material.ARROW, first.get(53).getType(), "next page");
        assertEquals(Material.STONE, first.get(45).getType(), "no previous page on page one");

        menus.click(player, 53).join();
        Map<Integer, ItemStack> second = adapter.menuUpdates().get(0).contents();
        assertEquals(46, second.get(0).getAmount());
        assertEquals(50, second.get(4).getAmount());
        assertNull(second.get(5), "the last page holds only five entries");
        assertEquals(Material.ARROW, second.get(45).getType(), "previous page");

        menus.click(player, 45).join();
        assertEquals(1, adapter.menuUpdates().get(1).contents().get(0).getAmount());

        menus.click(player, 48).join();
        assertEquals(3, adapter.menuOpens().size(), "back replaces the loot page with the list");
        assertEquals("§8Loots wiki", adapter.menuOpens().get(2).title());
        assertEquals("Demo", name(adapter.menuOpens().get(2).contents().get(0)));
    }

    @Test
    void everyLootPageKeyIsTranslatedInEveryShippedLanguage() throws IOException {
        for (String language : List.of("en", "vi", "zh")) {
            Map<String, Object> messages = messages("/languages/" + language + ".yml");
            for (String key : LOOT_PAGE_KEYS) {
                Object value = messages.get(key);
                assertNotNull(value, language + " is missing " + key);
                if (value instanceof List<?> list) {
                    assertFalse(list.isEmpty(), language + " " + key);
                    assertTrue(list.stream().anyMatch(line -> !String.valueOf(line).isBlank()), language + " " + key);
                } else {
                    assertFalse(String.valueOf(value).isBlank(), language + " " + key);
                }
            }
        }
    }

    private void use(ConfigSnapshot snapshot) {
        build(snapshot);
        cache.load(snapshot);
    }

    private void useHeld(ConfigSnapshot snapshot) {
        build(snapshot);
        adapter.holdAsync();
        cache.load(snapshot);
    }

    private void build(ConfigSnapshot snapshot) {
        config = snapshot;
        ItemSourceRegistry items = ItemSourceRegistry.create();
        cache = new LootChanceCache(adapter.async());
        MenuRenderer renderer = new MenuRenderer(items, key -> config.message("", key), Warnings.ignore());
        menus = new MenuService(adapter, renderer);
        menu = new WikiMenu(adapter, () -> config, items, menus, renderer, cache,
                (what, cause) -> failures.add(what + ": " + cause));
    }

    private Map<Integer, ItemStack> openLoot(String name) {
        int before = adapter.menuOpens().size();
        menu.open(player, new SenderLocation(WORLD, 0, 64, 0));
        assertEquals(before + 1, adapter.menuOpens().size(), "the list did not open");
        int slot = slotNamed(adapter.menuOpens().get(before).contents(), name);
        menus.click(player, slot).join();
        assertEquals(before + 2, adapter.menuOpens().size(), "the loot page did not open");
        return adapter.menuOpens().get(before + 1).contents();
    }

    private static int slotNamed(Map<Integer, ItemStack> contents, String name) {
        for (Map.Entry<Integer, ItemStack> entry : contents.entrySet()) {
            if (name(entry.getValue()).equals(name)) {
                return entry.getKey();
            }
        }
        throw new AssertionError("no icon named " + name + " in " + contents);
    }

    private static int filledContent(Map<Integer, ItemStack> page) {
        int filled = 0;
        for (int slot = 0; slot < 45; slot++) {
            if (page.get(slot) != null) {
                filled++;
            }
        }
        return filled;
    }

    private static int indexContaining(List<String> lore, String text) {
        for (int index = 0; index < lore.size(); index++) {
            if (ChatColor.stripColor(lore.get(index)).contains(text)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean headerBeforeWorld(List<String> lore) {
        int header = -1;
        int world = -1;
        for (int index = 0; index < lore.size(); index++) {
            String line = ChatColor.stripColor(lore.get(index));
            if (line.equals("Worlds:")) {
                header = index;
            } else if (line.startsWith("- world:")) {
                world = index;
            }
        }
        return header >= 0 && world > header;
    }

    private static String name(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getDisplayName() == null) {
            return "";
        }
        return ChatColor.stripColor(item.getItemMeta().getDisplayName());
    }

    private static String chanceShown(ItemStack item) {
        for (String line : lore(item)) {
            String plain = ChatColor.stripColor(line);
            if (plain.startsWith("Chance:")) {
                return plain.substring("Chance:".length()).replace("%", "").trim();
            }
        }
        return "";
    }

    private static List<String> lore(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getLore() == null) {
            return List.of();
        }
        return item.getItemMeta().getLore();
    }

    private static ConfigSnapshot shippedTreasure() throws IOException {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .language("en", resource("/languages/en.yml"))
                .menu("wiki", resource("/menus/wiki.yml"));
        for (String id : SHIPPED_LOOTS) {
            sources.loot(id, resource("/loots/" + id + ".yml"));
        }
        return ConfigLoader.load(sources.build(), catalog(), WarningKinds.all());
    }

    private static ConfigSnapshot fixture(String worlds, Map<String, String> loots) {
        return fixture(worlds, loots, WIKI_MENU);
    }

    private static ConfigSnapshot fixture(String worlds, Map<String, String> loots, String menu) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\nbanned_loot_blocks: []\n")
                .worlds(worlds)
                .language("en", LANGUAGE)
                .menu("wiki", menu);
        loots.forEach(sources::loot);
        return ConfigLoader.load(sources.build(), catalog(), WarningKinds.all());
    }

    private static String loot(String displayName, String body) {
        return "display_name: '" + displayName + "'\nopen_mode: drop\nblock: barrel\n" + body;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = Phase9LootPageAcceptanceTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> messages(String path) throws IOException {
        return (Map<String, Object>) new Yaml().load(resource(path));
    }

    private static NameCatalog catalog() {
        return new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                if (writtenId.contains(":")) {
                    return Optional.of(writtenId);
                }
                if (writtenId.equals("not_an_item")) {
                    return Optional.empty();
                }
                return Optional.of("minecraft:" + writtenId);
            }

            @Override
            public boolean soundExists(String key) {
                return true;
            }
        };
    }
}
