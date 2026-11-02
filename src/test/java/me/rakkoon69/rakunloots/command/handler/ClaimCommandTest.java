package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.command.CommandRegistry;
import me.rakkoon69.rakunloots.command.FakeCommandSender;
import me.rakkoon69.rakunloots.command.FakeCommandSources;
import me.rakkoon69.rakunloots.summon.item.ClaimMenu;
import me.rakkoon69.rakunloots.summon.item.UnclaimedSummonItems;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.SqliteStores;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.summon.item.SummonItemMaker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCommandTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a1");

    @TempDir
    Path folder;

    private FakeServerAdapter adapter;
    private LootBoxStore store;
    private ConfigSnapshot config;
    private UnclaimedSummonItems claims;
    private CommandRegistry registry;
    private UUID player;
    private FakeCommandSender steve;
    private final List<String> failures = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        adapter = new FakeServerAdapter(WORLD);
        store = SqliteStores.open(folder);
        config = shipped();
        player = FakeCommandSources.STEVE_ID;
        adapter.addOnlinePlayer(player);
        steve = new FakeCommandSender("Steve", ClaimCommand.PERMISSION).asPlayer(player);
        SummonItemMaker maker = new SummonItemMaker(ItemSourceRegistry.create());
        claims = new UnclaimedSummonItems(adapter, () -> config, store, maker, (what, cause) -> failures.add(what));
        MenuRenderer renderer = new MenuRenderer(ItemSourceRegistry.create(), key -> config.message("", key),
                Warnings.ignore());
        MenuService menus = new MenuService(adapter, renderer);
        ClaimMenu menu = new ClaimMenu(adapter, () -> config, claims, maker, menus, renderer,
                (what, cause) -> failures.add(what));
        registry = new CommandRegistry(new FakeCommandSources(), key -> config.message("", key), line -> {
        });
        registry.register(ClaimCommand.node(config.claimCommand(), claims, menu));
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
        assertTrue(failures.isEmpty(), failures.toString());
    }

    /**
     * Acceptance: a player owed nothing gets claim_nothing and no menu.
     */
    @Test
    void aPlayerOwedNothingGetsClaimNothingAndNoMenu() {
        registry.dispatch(steve, "claimsummon");
        adapter.globalScheduler().stepTicks(1);

        assertEquals(List.of(message("claim_nothing")), steve.messages());
        assertTrue(adapter.menuOpenCalls().isEmpty());
    }

    /**
     * Acceptance: the claim command opens the menu when something is owed.
     */
    @Test
    void aPlayerOwedSomethingGetsTheMenu() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 2);

        registry.dispatch(steve, "claimsummon");

        assertEquals(1, adapter.menuOpens().size());
        assertTrue(adapter.menuOpens().get(0).title().contains("2"), adapter.menuOpens().get(0).title());
        assertEquals(List.of(), steve.messages());
        assertEquals(0, adapter.inventoryOf(player).countOfType(Material.NETHER_STAR));
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 2, false)), owed());
    }

    /**
     * Acceptance: claim all keeps the direct claim the command had before.
     */
    @Test
    void theAllChildGivesEverythingAtOnce() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 2);

        registry.dispatch(steve, "claimsummon all");

        assertEquals(2, adapter.inventoryOf(player).countOfType(Material.NETHER_STAR));
        assertEquals(List.of(), owed());
        assertTrue(adapter.menuOpenCalls().isEmpty());
    }

    /**
     * Acceptance: the root and all both refuse the console.
     */
    @Test
    void theRootAndAllRefuseTheConsole() {
        FakeCommandSender console = new FakeCommandSender("Console", ClaimCommand.PERMISSION);

        registry.dispatch(console, "claimsummon");
        registry.dispatch(console, "claimsummon all");

        assertEquals(List.of(message("cmd_claim_needs_player"), message("cmd_claim_needs_player")),
                console.messages());
    }

    @Test
    void aPlayerWithoutThePermissionRunsNeither() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 2);
        FakeCommandSender alex = new FakeCommandSender("Steve").asPlayer(player);

        registry.dispatch(alex, "claimsummon");
        registry.dispatch(alex, "claimsummon all");

        assertEquals(List.of(message("cmd_unknown"), message("cmd_unknown")), alex.messages());
        assertTrue(adapter.menuOpenCalls().isEmpty());
        assertEquals(0, adapter.inventoryOf(player).countOfType(Material.NETHER_STAR));
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 2, false)), owed());
    }

    private List<UnclaimedSummonItem> owed() {
        try {
            return store.unclaimedSummonItems(player, config);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String message(String key) {
        return config.message("", key).replace("{prefix}", config.message("", "prefix"));
    }

    private static ConfigSnapshot shipped() {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .menu("claim", resource("/menus/claim.yml"))
                .build(), catalog(), WarningKinds.all());
    }

    private static NameCatalog catalog() {
        return new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                return Optional.of(writtenId.contains(":") ? writtenId : "minecraft:" + writtenId);
            }

            @Override
            public Set<String> tagMembers(String namespacedTag) {
                return Set.of("minecraft:tag_" + namespacedTag.replace(':', '_'));
            }

            @Override
            public boolean soundExists(String key) {
                return true;
            }
        };
    }

    private static String resource(String path) {
        try (InputStream in = ClaimCommandTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
