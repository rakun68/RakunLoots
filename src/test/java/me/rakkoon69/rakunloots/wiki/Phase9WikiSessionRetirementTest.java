package me.rakkoon69.rakunloots.wiki;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase9WikiSessionRetirementTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a2");

    private FakeServerAdapter adapter;
    private MenuService menus;
    private WikiMenu wiki;
    private UUID player;

    @BeforeEach
    void setUp() {
        StreamableServerMock.mock();
        adapter = new FakeServerAdapter(WORLD);
        player = UUID.randomUUID();
        adapter.addOnlinePlayer(player);
        var snapshot = config();
        var items = ItemSourceRegistry.create();
        var renderer = new MenuRenderer(items, key -> snapshot.message("", key), Warnings.ignore());
        menus = new MenuService(adapter, renderer);
        var chances = new LootChanceCache(adapter.async());
        wiki = new WikiMenu(adapter, () -> snapshot, items, menus, renderer, chances,
                (what, failure) -> {
                    throw new AssertionError(what, failure);
                });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void open() {
        wiki.open(player, new SenderLocation(WORLD, 0, 64, 0));
    }

    @Test
    void anExplicitCloseRetiresTheWikiSessionImmediately() throws Exception {
        open();
        assertEquals(1, sessions().size());

        menus.close(player).join();

        assertEquals(0, sessions().size(), "an explicit close is terminal without another chance publish");
    }

    @Test
    void aFreshWikiOpenSupersedesTheOldSessionAndAnOldHandleCloseCannotDropTheNewOne() throws Exception {
        open();
        Object first = sessions().get(player);
        open();
        Object second = sessions().get(player);

        assertNotSame(first, second, "a fresh open registers a new session");
        assertEquals(1, sessions().size());
        menus.closed(player, adapter.menuCloses().get(0).menu());
        assertSame(second, sessions().get(player), "the old handle close cannot retire its replacement");

        menus.closeAll().join();

        assertTrue(sessions().isEmpty());
    }

    @Test
    void navigationKeepsTheSameSessionAndASuccessfulReplacementTransfersRetirement() throws Exception {
        open();
        Object session = sessions().get(player);

        menus.click(player, 0).join();

        assertEquals(2, adapter.menuOpens().size(), "the loot page replaced the list page");
        assertSame(session, sessions().get(player), "navigation keeps the logical wiki session");
        menus.closed(player, adapter.menuCloses().get(0).menu());
        assertSame(session, sessions().get(player), "the old page close is not a terminal close");

        menus.closeAll().join();

        assertTrue(sessions().isEmpty(), "the transferred subscription retires the session");
    }

    private Map<?, ?> sessions() throws Exception {
        var field = WikiMenu.class.getDeclaredField("sessions");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(wiki);
    }

    private static me.rakkoon69.rakunloots.config.ConfigSnapshot config() {
        String english;
        try (var stream = Phase9WikiSessionRetirementTest.class.getResourceAsStream("/languages/en.yml")) {
            english = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        var snapshot = ConfigLoader.load(ConfigSources.builder().globals("language: en\n")
                .worlds("{}\n").language("en", english)
                .loot("demo", "open_mode: drop\nblock: barrel\nroll:\n  min: 1\n  max: 1\n  entries:\n"
                        + "    a:\n      weight: 1\n      item: {id: diamond}\n").build(),
                new NameCatalog() {
                    public Optional<String> currentId(String id) { return Optional.of(id); }
                    public boolean soundExists(String key) { return true; }
                }, WarningKinds.all());
        assertTrue(snapshot.loot("demo").isPresent());
        return snapshot;
    }
}
