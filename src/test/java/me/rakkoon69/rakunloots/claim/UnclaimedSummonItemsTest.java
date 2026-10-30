package me.rakkoon69.rakunloots.claim;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.SqliteStores;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import me.rakkoon69.rakunloots.summon.FakePlayerInventory;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim and the join message over the shipped config, a SQLite store in a temp folder, and the fake server
 * adapter. A test checks what the player sees: items in the inventory, messages sent, and rows left in the store.
 */
class UnclaimedSummonItemsTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a1");
    private static final String NETHER_TREASURE = """
            language: en
            display_name: 'Nether Treasure'
            open_mode: drop
            block: barrel
            roll:
              entries:
                one:
                  item:
                    id: diamond
            """;

    @TempDir
    Path folder;

    private StreamableServerMock server;
    private FakeServerAdapter adapter;
    private LootBoxStore store;
    private ConfigSnapshot config;
    private UnclaimedSummonItems claims;
    private final Map<String, Material> summonItems = new HashMap<>();
    private final List<String> failures = new ArrayList<>();
    private UUID player;

    @BeforeEach
    void setUp() throws Exception {
        server = StreamableServerMock.mock();
        adapter = new FakeServerAdapter(WORLD);
        store = SqliteStores.open(folder);
        config = shipped("");
        summonItems.put("airdrops", Material.NETHER_STAR);
        summonItems.put("nether_treasure", Material.DIAMOND_SWORD);
        claims = claims(() -> config);
        player = UUID.randomUUID();
        adapter.addOnlinePlayer(player);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    @Test
    void aClaimGivesAsManyAsFitKeepsTheRestAndSaysSo() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 200);
        adapter.inventoryOf(player).fillAllButSlots(new ItemStack(Material.STONE), 2);

        ClaimResult result = claims.claim(player).get();

        assertEquals(List.of(new ClaimResult.Taken("airdrops", 128, 72)), result.taken());
        assertEquals(128, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 72, false)), owed());
        adapter.globalScheduler().stepTicks(1);
        List<String> messages = adapter.messages().stream().map(FakeServerAdapter.Message::text).toList();
        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.get(0).startsWith("§f[§bRakunLoots§f]§7 You took §e128 §7summon item(s) of §r"),
                messages.get(0));
        assertTrue(messages.get(0).contains("Air Supply"), messages.get(0));
        assertTrue(messages.get(1).contains("§e72 §7summon item(s)"), messages.get(1));
        assertTrue(messages.get(1).contains("/claimsummon"), messages.get(1));
    }

    @Test
    void aClaimThatFitsEverythingDeletesTheRow() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 5);

        ClaimResult result = claims.claim(player).get();

        assertEquals(5, result.given());
        assertEquals(0, result.kept());
        assertEquals(5, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(), owed());
    }

    @Test
    void aClaimLowersTheStoredAmountBeforeItGivesTheItems() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 5);
        List<List<UnclaimedSummonItem>> storedWhenGiving = new ArrayList<>();
        adapter.onGive(() -> storedWhenGiving.add(owed()));

        claims.claim(player).get();

        assertEquals(List.of(List.of()), storedWhenGiving, "the row was already deleted when the give began");
        assertEquals(5, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
    }

    @Test
    void aCrashBetweenTheLowerAndTheGiveNeverGivesTheItemsTwice() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 5);
        adapter.onGive(() -> {
            throw new IllegalStateException("the server went down");
        });

        ExecutionException crash = assertThrows(ExecutionException.class, () -> claims.claim(player).get());

        assertEquals("the server went down", crash.getCause().getMessage());
        assertEquals(1, failures.size(), failures.toString());
        assertEquals(List.of(), owed(), "what was lowered is not owed again");
        assertEquals(0, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));

        adapter.onGive(() -> {
        });
        assertEquals(0, claims.claim(player).get().given(), "a second claim has nothing left to give");
        assertEquals(0, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
    }

    @Test
    void aGiveThatPutsInFewerThanAskedWritesTheRestBack() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 5);
        // The inventory fills between the room check and the give.
        adapter.onGive(() -> adapter.inventoryOf(player).fillWith(new ItemStack(Material.STONE)));

        ClaimResult result = claims.claim(player).get();

        assertEquals(List.of(new ClaimResult.Taken("airdrops", 0, 5)), result.taken());
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 5, false)), owed());
    }

    @Test
    void aClaimWithNoRoomKeepsEverythingAndGivesNothing() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 5);
        adapter.inventoryOf(player).fillWith(new ItemStack(Material.STONE));

        ClaimResult result = claims.claim(player).get();

        assertEquals(List.of(new ClaimResult.Taken("airdrops", 0, 5)), result.taken());
        assertEquals(List.of(), adapter.giveCalls());
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 5, false)), owed());
        adapter.globalScheduler().stepTicks(1);
        assertEquals(1, adapter.messages().size());
        assertTrue(adapter.messages().get(0).text().contains("Your inventory is full"));
    }

    @Test
    void itemsAreBuiltFromTheLootsCurrentSummonItemAtClaimTime() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 3);
        // The loot's summon item is edited after the items became owed.
        summonItems.put("airdrops", Material.EMERALD);

        claims.claim(player).get();

        assertEquals(3, adapter.inventoryOf(player).count(new ItemStack(Material.EMERALD)));
        assertEquals(0, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
    }

    @Test
    void anItemThatDoesNotStackIsGivenOnePerSlot() throws Exception {
        store.addUnclaimedSummonItems(player, "nether_treasure", 40);
        FakePlayerInventory inventory = adapter.inventoryOf(player);

        ClaimResult result = claims.claim(player).get();

        assertEquals(36, result.given(), "one per storage slot");
        assertEquals(4, result.kept());
        assertEquals(36, inventory.stackAmounts(new ItemStack(Material.DIAMOND_SWORD)).size());
        assertEquals(List.of(new UnclaimedSummonItem("nether_treasure", 4, false)), owed());
    }

    @Test
    void aRowWhoseLootIsMissingIsKeptFlaggedAndSkippedAndTheClaimSaysSo() throws Exception {
        store.addUnclaimedSummonItems(player, "deleted_loot", 3);
        store.addUnclaimedSummonItems(player, "airdrops", 2);

        ClaimResult result = claims.claim(player).get();

        assertEquals(2, result.given());
        assertEquals(List.of(new UnclaimedSummonItem("deleted_loot", 3, true)), result.skipped());
        assertEquals(List.of(new UnclaimedSummonItem("deleted_loot", 3, true)), owed());
        adapter.globalScheduler().stepTicks(1);
        List<String> messages = adapter.messages().stream().map(FakeServerAdapter.Message::text).toList();
        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.get(1).contains("§e3 §7summon item(s) of §cdeleted_loot §7stay waiting"),
                messages.get(1));
    }

    @Test
    void aClaimWithNothingOwedSaysSo() throws Exception {
        ClaimResult result = claims.claim(player).get();

        assertEquals(new ClaimResult(List.of(), List.of()), result);
        adapter.globalScheduler().stepTicks(1);
        assertEquals(List.of("§f[§bRakunLoots§f]§7 You have no summon items waiting."),
                adapter.messages().stream().map(FakeServerAdapter.Message::text).toList());
    }

    @Test
    void aClaimByAPlayerWhoIsOfflineKeepsEverything() throws Exception {
        UUID offline = UUID.randomUUID();
        store.addUnclaimedSummonItems(offline, "airdrops", 4);

        ClaimResult result = claims.claim(offline).get();

        assertEquals(0, result.given());
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 4, false)),
                store.unclaimedSummonItems(offline, config));
    }

    @Test
    void aStorageFailureFailsTheClaimAndTellsTheFailureSink() throws Exception {
        store.close();

        ExecutionException failure = assertThrows(ExecutionException.class, () -> claims.claim(player).get());

        assertFalse(failures.isEmpty());
        assertTrue(failure.getCause() != null);
        store = SqliteStores.open(folder);
    }

    @Test
    void aPlayerOwedItemsIsToldOnJoinAfterAnAsyncReadAndOnThePlayersThread() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 3);
        store.addUnclaimedSummonItems(player, "nether_treasure", 4);
        int asyncBefore = adapter.asyncScheduler().runCalls();

        claims.onJoin(player);

        assertEquals(asyncBefore + 1, adapter.asyncScheduler().runCalls(), "the read ran on the async scheduler");
        assertEquals(List.of(), adapter.messages(), "the message waits for the player's thread");

        adapter.globalScheduler().stepTicks(1);

        assertEquals(List.of(new FakeServerAdapter.Message(player,
                        "§f[§bRakunLoots§f]§7 You have §e7 §7summon item(s) waiting. Take them with §e/claimsummon§7.")),
                adapter.messages());
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 3, false),
                new UnclaimedSummonItem("nether_treasure", 4, false)), owed(), "telling changes nothing");
    }

    @Test
    void aClaimsMessagesAreSentOnThePlayersThread() throws Exception {
        store.addUnclaimedSummonItems(player, "airdrops", 5);

        claims.claim(player).get();

        assertEquals(List.of(), adapter.messages(), "the messages wait for the player's thread");

        adapter.globalScheduler().stepTicks(1);

        assertEquals(1, adapter.messages().size());
        assertTrue(adapter.messages().get(0).text().contains("summon item(s)"),
                adapter.messages().get(0).text());
    }

    @Test
    void aPlayerOwedNothingGetsNoJoinMessage() throws Exception {
        claims.onJoin(player);
        adapter.globalScheduler().stepTicks(1);

        assertEquals(List.of(), adapter.messages());
    }

    @Test
    void theJoinMessageNamesTheClaimCommandFromConfigYml() throws Exception {
        config = shipped("claim_command:\n  name: takeitems\n  aliases: [owed]\n");
        store.addUnclaimedSummonItems(player, "airdrops", 1);

        claims.onJoin(player);
        adapter.globalScheduler().stepTicks(1);

        assertEquals(1, adapter.messages().size());
        assertTrue(adapter.messages().get(0).text().endsWith("Take them with §e/takeitems§7."),
                adapter.messages().get(0).text());
    }

    @Test
    void aFailingReadOnJoinTellsTheFailureSinkAndSendsNothing() throws Exception {
        store.close();

        claims.onJoin(player);
        adapter.globalScheduler().stepTicks(1);

        assertEquals(1, failures.size());
        assertEquals(List.of(), adapter.messages());
        store = SqliteStores.open(folder);
    }

    @Test
    void theJoinListenerCarriesAJoinToTheJoinCheck() throws Exception {
        PlayerMock joined = server.addPlayer();
        adapter.addOnlinePlayer(joined.getUniqueId());
        store.addUnclaimedSummonItems(joined.getUniqueId(), "airdrops", 2);

        new ClaimJoinListener(claims).onJoin(new PlayerJoinEvent(joined, "joined"));
        adapter.globalScheduler().stepTicks(1);

        assertEquals(1, adapter.messages().size());
        assertEquals(joined.getUniqueId(), adapter.messages().get(0).playerId());
    }

    @Test
    void everyShippedLanguageHasTheClaimMessages() {
        ConfigSnapshot all = ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .language("vi", resource("/languages/vi.yml"))
                .language("zh", resource("/languages/zh.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), catalog(), WarningKinds.all());

        for (String language : List.of("en", "vi", "zh")) {
            assertTrue(all.languageKeys(language).containsAll(Set.of(UnclaimedSummonItems.KEY_WAITING,
                    UnclaimedSummonItems.KEY_NOTHING, UnclaimedSummonItems.KEY_GIVEN, UnclaimedSummonItems.KEY_KEPT,
                    UnclaimedSummonItems.KEY_LOOT_MISSING)), language);
        }
    }

    private UnclaimedSummonItems claims(java.util.function.Supplier<ConfigSnapshot> current) {
        return new UnclaimedSummonItems(adapter, current, store,
                loot -> new ItemStack(summonItems.get(loot.id())),
                (what, cause) -> failures.add(what));
    }

    private List<UnclaimedSummonItem> owed() {
        try {
            return store.unclaimedSummonItems(player, config);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * The shipped config.yml, worlds.yml, blocks.yml, en.yml, and loots/airdrops.yml, with the extra lines added to
     * config.yml, and a second loot.
     */
    private static ConfigSnapshot shipped(String extraGlobals) {
        return ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml") + "\n" + extraGlobals)
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .loot("nether_treasure", NETHER_TREASURE)
                .build(), catalog(), WarningKinds.all());
    }

    private static NameCatalog catalog() {
        return new NameCatalog() {
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
        };
    }

    private static String resource(String path) {
        try (InputStream in = UnclaimedSummonItemsTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
