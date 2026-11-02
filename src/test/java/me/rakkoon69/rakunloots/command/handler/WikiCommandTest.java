package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.command.CommandRegistry;
import me.rakkoon69.rakunloots.command.FakeCommandSender;
import me.rakkoon69.rakunloots.command.FakeCommandSources;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.wiki.LootChanceCache;
import me.rakkoon69.rakunloots.wiki.WikiMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiCommandTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a1");

    private static final String LANGUAGE = """
            prefix: ""
            cmd_unknown: "&cUnknown command."
            cmd_wiki_needs_player: "&cOnly players can open the loots wiki."
            menu_wiki_list_title: "&8Loots wiki"
            menu_wiki_list_entry_name: "&e{loot}"
            menu_wiki_list_entry_lore:
              - "&7{description}"
            menu_wiki_list_timer_chance: "&7Chance: &e{chance:2}%"
            menu_wiki_list_not_on_timer: "&7No timer"
            menu_wiki_list_click: "&eClick"
            menu_wiki_loot_title: "&8Loot {loot}"
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

    private FakeServerAdapter adapter;
    private ConfigSnapshot config;
    private CommandRegistry registry;
    private UUID player;
    private FakeCommandSender steve;
    private final List<String> failures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        StreamableServerMock.mock();
        adapter = new FakeServerAdapter(WORLD);
        player = UUID.randomUUID();
        adapter.addOnlinePlayer(player);
        config = snapshot();
        steve = new FakeCommandSender("Steve", WikiCommand.PERMISSION)
                .asPlayer(player)
                .at(new SenderLocation(WORLD, 0, 64, 0));
        ItemSourceRegistry items = ItemSourceRegistry.create();
        MenuRenderer renderer = new MenuRenderer(items, key -> config.message("", key), Warnings.ignore());
        MenuService menus = new MenuService(adapter, renderer);
        LootChanceCache chances = new LootChanceCache(adapter.async());
        WikiMenu menu = new WikiMenu(adapter, () -> config, items, menus, renderer, chances,
                (what, cause) -> failures.add(what));
        chances.load(config);
        registry = new CommandRegistry(new FakeCommandSources(), key -> config.message("", key), line -> {
        });
        registry.register(WikiCommand.node(config.wikiCommand(), menu));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void theRootAndItsAliasOpenTheWikiForAPlayer() {
        registry.dispatch(steve, "loots");
        registry.dispatch(steve, "rklloots");

        assertEquals(2, adapter.menuOpens().size());
        assertEquals("§8Loots wiki", adapter.menuOpens().get(0).title());
        assertEquals(List.of(), steve.messages());
    }

    @Test
    void theConsoleIsRefused() {
        FakeCommandSender console = new FakeCommandSender("Console", WikiCommand.PERMISSION);

        registry.dispatch(console, "loots");

        assertEquals(List.of(message("cmd_wiki_needs_player")), console.messages());
        assertTrue(adapter.menuOpenCalls().isEmpty());
    }

    @Test
    void aPlayerWithoutThePermissionGetsTheUnknownCommandReply() {
        FakeCommandSender alex = new FakeCommandSender("Alex")
                .asPlayer(player)
                .at(new SenderLocation(WORLD, 0, 64, 0));

        registry.dispatch(alex, "loots");

        assertEquals(List.of(message("cmd_unknown")), alex.messages());
        assertTrue(adapter.menuOpenCalls().isEmpty());
    }

    private String message(String key) {
        return config.message("", key).replace("{prefix}", config.message("", "prefix"));
    }

    private static ConfigSnapshot snapshot() {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", LANGUAGE)
                .menu("wiki", WIKI_MENU);
        sources.loot("demo", "display_name: 'demo'\nopen_mode: drop\nblock: barrel\n"
                + "roll:\n  entries:\n    a:\n      item:\n        id: diamond\n");
        return ConfigLoader.load(sources.build(), catalog(), WarningKinds.all());
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
