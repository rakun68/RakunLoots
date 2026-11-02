package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.SqliteStores;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMenuTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a1");

    @TempDir
    Path folder;

    private FakeServerAdapter adapter;
    private LootBoxStore store;
    private ConfigSnapshot config;
    private UnclaimedSummonItems claims;
    private ClaimMenu menu;
    private MenuService menus;
    private UUID player;
    private final List<String> failures = new ArrayList<>();
    private final SummonItemFactory summonItems = loot -> new ItemStack(Material.NETHER_STAR);

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        adapter = new FakeServerAdapter(WORLD);
        store = SqliteStores.open(folder);
        player = UUID.randomUUID();
        adapter.addOnlinePlayer(player);
        use(snapshot("alpha", "beta", "gamma"));
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void aRowShowsTheSummonItemCappedAt64WithTheFullAmountInItsLore() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 100);

        open();

        ItemStack row = contents().get(0);
        assertEquals(Material.NETHER_STAR, row.getType());
        assertEquals(64, row.getAmount());
        assertTrue(String.join(" ", row.getItemMeta().getLore()).contains("100"),
                row.getItemMeta().getLore().toString());
        assertTrue(adapter.menuOpens().get(0).title().contains("100"), adapter.menuOpens().get(0).title());
    }

    @Test
    void aClickTakesOnlyThatLootAndReopensWithTheRemainingTotal() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();

        menus.click(player, 0).join();

        assertEquals(5, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(new UnclaimedSummonItem("beta", 7, false)), owed());
        assertEquals(2, adapter.menuOpens().size());
        assertEquals(7, adapter.menuOpens().get(1).contents().get(0).getAmount());
        assertEquals(1, adapter.menuCloses().size());
    }

    @Test
    void aClaimRefreshReopensWithTheRemainingTotalInTheTitle() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();

        menus.click(player, 0).join();

        assertEquals(2, adapter.menuOpens().size(), "a changed title requires a replacement menu");
        assertTrue(adapter.menuOpens().get(1).title().contains("7"), adapter.menuOpens().get(1).title());
        assertEquals(1, adapter.menuCloses().size(), "the old titled menu is retired after replacement");
        assertEquals(0, adapter.menuUpdates().size(), "title changes use replacement rather than an in-place update");
    }

    @Test
    void aPageTurnWhileTheTitleReplacementWaitsIsAppliedToTheNewMenu() throws Exception {
        use(snapshot(91));
        for (int i = 0; i < 91; i++) {
            store.addUnclaimedSummonItems(player, id(i), 1);
        }
        open();
        adapter.holdMenuOpens();

        menus.click(player, 0).join();
        menus.click(player, 53).join();
        adapter.releaseMenuOpens();

        assertEquals(2, adapter.menuOpens().size());
        assertEquals(1, adapter.menuUpdates().size(), "the page turn is applied after replacement lands");
        assertTrue(adapter.menuUpdates().get(0).contents().get(0).getItemMeta().getDisplayName().contains("loot46"),
                adapter.menuUpdates().get(0).contents().get(0).getItemMeta().getDisplayName());
    }

    @Test
    void takingTheLastRowWhileTheTitleReplacementWaitsClosesTheReplacementToo() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();
        adapter.holdMenuOpens();

        menus.click(player, 0).join();
        menus.click(player, 1).join();
        adapter.releaseMenuOpens();

        assertEquals(List.of(), owed());
        assertEquals(2, adapter.menuOpens().size());
        assertEquals(2, adapter.menuCloses().size(), "both the old and the superseded replacement are closed");
    }

    @Test
    void theClaimAllButtonTakesEveryLoot() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();

        menus.click(player, 49).join();

        assertEquals(12, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(), owed());
        assertEquals(1, adapter.menuCloses().size());
    }

    @Test
    void theMenuClosesWhenItsLastRowIsTaken() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        open();

        menus.click(player, 0).join();

        assertEquals(List.of(), owed());
        assertEquals(1, adapter.menuCloses().size());
    }

    @Test
    void twoClicksBeforeTheFirstClaimCompletesTakeOnlyTheFirstLoot() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();
        adapter.holdGives();

        menus.click(player, 0).join();
        menus.click(player, 1).join();

        assertEquals(List.of(5), adapter.giveCalls());
        assertEquals(List.of(new UnclaimedSummonItem("beta", 7, false)), owed());

        adapter.releaseGives();

        assertEquals(5, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(new UnclaimedSummonItem("beta", 7, false)), owed());
        assertEquals(7, adapter.menuOpens().get(1).contents().get(0).getAmount());
    }

    @Test
    void aFailedClaimReleasesTheGuardAndRefreshesTheMenu() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();
        adapter.onGive(() -> {
            throw new IllegalStateException("the give failed");
        });

        menus.click(player, 0).join();

        assertEquals(1, failures.size());
        failures.clear();
        assertEquals(List.of(new UnclaimedSummonItem("beta", 7, false)), owed());

        adapter.onGive(null);
        menus.click(player, 0).join();

        assertEquals(7, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(), owed());
        assertEquals(2, adapter.menuCloses().size(), "the failed claim leaves beta, then the successful claim closes its replacement");
    }

    @Test
    void aMissingLootShowsABarrierAndAClickOnItChangesNothing() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 2);
        store.addUnclaimedSummonItems(player, "gone", 4);
        open();

        ItemStack barrier = contents().get(1);
        assertEquals(Material.BARRIER, barrier.getType());
        assertTrue(barrier.getItemMeta().getDisplayName().contains("gone"), barrier.getItemMeta().getDisplayName());
        assertTrue(String.join(" ", barrier.getItemMeta().getLore()).contains("4"),
                barrier.getItemMeta().getLore().toString());

        menus.click(player, 1).join();

        assertEquals(List.of(new UnclaimedSummonItem("alpha", 2, false), new UnclaimedSummonItem("gone", 4, true)),
                owed());
        assertEquals(0, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertTrue(adapter.menuUpdates().isEmpty());
    }

    @Test
    void anUnbuildableSummonItemShowsABarrierAndKeepsTheOtherRowUsable() throws Exception {
        useRealSummonItems(realSummonSnapshot());
        store.addUnclaimedSummonItems(player, "broken", 3);
        store.addUnclaimedSummonItems(player, "alpha", 2);

        open();

        Map<Integer, ItemStack> shown = contents();
        ItemStack barrier = shown.get(1);
        assertEquals(Material.BARRIER, barrier.getType());
        assertTrue(barrier.getItemMeta().getDisplayName().contains("broken"),
                barrier.getItemMeta().getDisplayName());
        assertTrue(String.join(" ", barrier.getItemMeta().getLore()).contains("3"),
                barrier.getItemMeta().getLore().toString());
        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("broken"), failures.toString());
        failures.clear();

        menus.click(player, 1).join();
        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(0, adapter.inventoryOf(player).countOfType(Material.DIAMOND));
        assertEquals(List.of(new UnclaimedSummonItem("alpha", 2, false),
                new UnclaimedSummonItem("broken", 3, false)), owed());

        menus.click(player, 0).join();
        assertEquals(2, adapter.inventoryOf(player).countOfType(Material.DIAMOND));
        assertEquals(List.of(new UnclaimedSummonItem("broken", 3, false)), owed());
        assertEquals(1, failures.size(), failures.toString());
        failures.clear();
    }

    @Test
    void aRefreshCannotDispatchItsStaleViewAfterANewerClaimDuringRendering() throws Exception {
        AtomicBoolean arm = new AtomicBoolean();
        SummonItemFactory factory = loot -> {
            if (arm.compareAndSet(true, false)) {
                menus.click(player, 1).join();
            }
            return new ItemStack(Material.NETHER_STAR);
        };
        use(snapshot("alpha", "beta", "gamma"), () -> config, factory);
        store.addUnclaimedSummonItems(player, "alpha", 1);
        store.addUnclaimedSummonItems(player, "beta", 1);
        store.addUnclaimedSummonItems(player, "gamma", 1);
        open();
        adapter.holdGives();

        menus.click(player, 0).join();
        arm.set(true);
        adapter.releaseGives();

        assertEquals(2, adapter.menuOpens().size(), "the newest titled view replaces the stale refresh");
        Map<Integer, ItemStack> refreshed = adapter.menuOpens().get(1).contents();
        assertTrue(refreshed.get(0).getItemMeta().getDisplayName().contains("gamma"),
                refreshed.get(0).getItemMeta().getDisplayName());
        assertFalse(refreshed.containsKey(1), refreshed.keySet().toString());
        assertEquals(List.of(new UnclaimedSummonItem("gamma", 1, false)), owed());
    }

    @Test
    void rowsPageAt45WithPreviousAndNextButtons() throws Exception {
        use(snapshot(46));
        for (int i = 0; i < 46; i++) {
            store.addUnclaimedSummonItems(player, id(i), 1);
        }

        open();

        Map<Integer, ItemStack> first = contents();
        assertEquals(Material.NETHER_STAR, first.get(0).getType());
        assertEquals(Material.NETHER_STAR, first.get(44).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, first.get(45).getType());
        assertEquals(Material.ARROW, first.get(53).getType());
        assertTrue(first.get(53).getItemMeta().getDisplayName().contains("Next"),
                first.get(53).getItemMeta().getDisplayName());

        menus.click(player, 53).join();

        Map<Integer, ItemStack> second = adapter.menuUpdates().get(0).contents();
        assertEquals(Material.NETHER_STAR, second.get(0).getType());
        assertFalse(second.containsKey(1), second.keySet().toString());
        assertEquals(Material.ARROW, second.get(45).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, second.get(53).getType());

        menus.click(player, 45).join();

        assertEquals(Material.NETHER_STAR, adapter.menuUpdates().get(1).contents().get(0).getType());
    }

    @Test
    void aPartialGiveKeepsTheRestOwedAndRefreshesTheRemainingAmount() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 200);
        adapter.inventoryOf(player).fillAllButSlots(new ItemStack(Material.STONE), 2);

        open();
        menus.click(player, 0).join();

        assertEquals(128, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(new UnclaimedSummonItem("alpha", 72, false)), owed());
        assertEquals(2, adapter.menuOpens().size());
        ItemStack row = adapter.menuOpens().get(1).contents().get(0);
        assertEquals(64, row.getAmount());
        assertTrue(String.join(" ", row.getItemMeta().getLore()).contains("72"),
                row.getItemMeta().getLore().toString());
        assertEquals(1, adapter.menuCloses().size());
    }

    @Test
    void theClaimGuardSurvivesPaging() throws Exception {
        use(snapshot(46));
        for (int i = 0; i < 46; i++) {
            store.addUnclaimedSummonItems(player, id(i), 1);
        }
        open();
        adapter.holdGives();

        menus.click(player, 0).join();
        menus.click(player, 53).join();
        menus.click(player, 0).join();

        assertEquals(List.of(1), adapter.giveCalls());
        adapter.releaseGives();
        assertEquals(45, owed().size());
        assertTrue(owed().stream().noneMatch(row -> row.lootId().equals(id(0))));
    }

    @Test
    void theClaimGuardSurvivesReopeningAndThenReleases() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();
        adapter.holdGives();

        menus.click(player, 0).join();
        menu.open(player, () -> {
        });
        menus.click(player, 0).join();

        assertEquals(2, adapter.menuOpens().size());
        assertEquals(List.of(5), adapter.giveCalls());
        adapter.releaseGives();
        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(1, adapter.menuCloses().size());
        assertEquals(List.of(new UnclaimedSummonItem("beta", 7, false)), owed());

        menus.click(player, 0).join();
        assertEquals(List.of(5, 7), adapter.giveCalls());
    }

    @Test
    void takingTheOnlyRowOnPage2ClampsBackToPage1() throws Exception {
        use(snapshot(46));
        for (int i = 0; i < 46; i++) {
            store.addUnclaimedSummonItems(player, id(i), 1);
        }
        open();
        menus.click(player, 53).join();

        menus.click(player, 0).join();

        assertEquals(1, adapter.menuCloses().size());
        Map<Integer, ItemStack> refreshed = adapter.menuOpens().get(1).contents();
        assertEquals(Material.NETHER_STAR, refreshed.get(0).getType());
        assertEquals(Material.NETHER_STAR, refreshed.get(44).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, refreshed.get(45).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, refreshed.get(53).getType());
        assertEquals(45, owed().size());
    }

    @Test
    void aClaimCompletionAfterCloseDoesNotRefreshOrReopen() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();
        adapter.holdGives();

        menus.click(player, 0).join();
        menus.close(player).join();
        menu.open(player, () -> {
        });
        adapter.releaseGives();

        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(2, adapter.menuOpens().size());
        assertEquals(1, adapter.menuCloses().size());
    }

    @Test
    void aStaleCompletionCannotCloseANewerMenu() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        open();
        adapter.holdGives();
        AtomicInteger clicks = new AtomicInteger();

        menus.click(player, 0).join();
        menus.close(player).join();
        MenuView other = new MenuView("Other", 1, Map.of(0,
                new MenuIcon(new ItemStack(Material.DIAMOND), Optional.of(clicks::incrementAndGet), Optional.empty())));
        menus.open(player, other).join();
        adapter.releaseGives();

        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(1, adapter.menuCloses().size());
        menus.click(player, 0).join();
        assertEquals(1, clicks.get());
    }

    @Test
    void aClaimCompletionAfterReloadDoesNotRefreshOrReopen() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();
        adapter.holdGives();

        menus.click(player, 0).join();
        menus.reload(config);
        menu.open(player, () -> {
        });
        adapter.releaseGives();

        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(2, adapter.menuOpens().size());
        assertEquals(1, adapter.menuCloses().size());
    }

    @Test
    void aClaimCompletionAfterReplacementLeavesTheNewerMenuAlone() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 5);
        store.addUnclaimedSummonItems(player, "beta", 7);
        open();
        adapter.holdGives();
        AtomicInteger clicks = new AtomicInteger();

        menus.click(player, 0).join();
        MenuView other = new MenuView("Other", 1, Map.of(0,
                new MenuIcon(new ItemStack(Material.DIAMOND), Optional.of(clicks::incrementAndGet), Optional.empty())));
        menus.open(player, other).join();
        adapter.releaseGives();

        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(1, adapter.menuCloses().size());
        menus.click(player, 0).join();
        assertEquals(1, clicks.get());
    }

    @Test
    void aReplacementWhileTheOwedReadIsPendingWins() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 2);
        adapter.holdAsync();
        AtomicInteger clicks = new AtomicInteger();

        menu.open(player, () -> {
        });
        MenuView other = new MenuView("Other", 1, Map.of(0,
                new MenuIcon(new ItemStack(Material.DIAMOND), Optional.of(clicks::incrementAndGet), Optional.empty())));
        menus.open(player, other).join();
        assertEquals(1, adapter.menuOpens().size());

        adapter.releaseAsync();

        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(1, adapter.menuOpens().size());
        assertEquals(0, adapter.menuCloses().size(), "the pending read had no physical menu to close");
        assertEquals(List.of(new UnclaimedSummonItem("alpha", 2, false)), owed());
        menus.click(player, 0).join();
        assertEquals(1, clicks.get());
    }

    @Test
    void aReloadWhileTheOwedReadIsPendingDoesNotInstallAStaleMenu() throws Exception {
        store.addUnclaimedSummonItems(player, "alpha", 2);
        adapter.holdAsync();

        menu.open(player, () -> {
        });
        menus.reload(config);
        adapter.releaseAsync();

        assertTrue(adapter.menuUpdates().isEmpty());
        assertEquals(0, adapter.menuOpens().size());
        assertEquals(0, adapter.menuCloses().size());
        menus.click(player, 0).join();
        assertEquals(0, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(new UnclaimedSummonItem("alpha", 2, false)), owed());
    }

    @Test
    void aReloadBetweenTheMenuStateAndConfigReadCannotInstallAStaleMenu() throws Exception {
        AtomicInteger reloads = new AtomicInteger();
        use(snapshot("alpha"), () -> {
            if (reloads.compareAndSet(0, 1)) {
                menus.reload(config);
            }
            return config;
        });
        store.addUnclaimedSummonItems(player, "alpha", 2);

        menu.open(player, () -> {
        });

        assertEquals(0, adapter.menuOpens().size());
        assertEquals(0, adapter.menuCloses().size());
        assertTrue(adapter.menuUpdates().isEmpty());
        menus.click(player, 0).join();
        assertEquals(0, adapter.inventoryOf(player).count(new ItemStack(Material.NETHER_STAR)));
        assertEquals(List.of(new UnclaimedSummonItem("alpha", 2, false)), owed());
    }

    @Test
    void theMenuRendersTheSameSnapshotItRead() throws Exception {
        ConfigSnapshot first = snapshot("alpha");
        ConfigSnapshot second = snapshot("beta");
        AtomicInteger reads = new AtomicInteger();
        use(first, () -> reads.getAndIncrement() == 0 ? first : second);
        store.addUnclaimedSummonItems(player, "alpha", 2);

        open();

        assertEquals(Material.NETHER_STAR, contents().get(0).getType());
        assertEquals(1, reads.get());
    }

    @Test
    void openingTheMenuReadsTheConfigOnce() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        use(snapshot("alpha"), () -> {
            reads.incrementAndGet();
            return config;
        });
        store.addUnclaimedSummonItems(player, "alpha", 2);

        open();

        assertEquals(1, reads.get());
    }

    private void open() {
        AtomicInteger nothing = new AtomicInteger();
        menu.open(player, nothing::incrementAndGet);
        assertEquals(0, nothing.get());
        assertEquals(1, adapter.menuOpens().size(), "the menu did not open");
    }

    private Map<Integer, ItemStack> contents() {
        return adapter.menuOpens().get(0).contents();
    }

    private List<UnclaimedSummonItem> owed() {
        try {
            return store.unclaimedSummonItems(player, config);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void useRealSummonItems(ConfigSnapshot snapshot) {
        config = snapshot;
        ItemSourceRegistry sources = ItemSourceRegistry.create();
        SummonItemMaker maker = new SummonItemMaker(sources);
        MenuRenderer renderer = new MenuRenderer(sources, key -> config.message("", key), Warnings.ignore());
        menus = new MenuService(adapter, renderer);
        claims = new UnclaimedSummonItems(adapter, () -> config, store, maker,
                (what, cause) -> failures.add(what));
        menu = new ClaimMenu(adapter, () -> config, claims, maker, menus, renderer,
                (what, cause) -> failures.add(what));
    }

    private void use(ConfigSnapshot snapshot) {
        use(snapshot, () -> config);
    }

    private void use(ConfigSnapshot snapshot, Supplier<ConfigSnapshot> supplier) {
        use(snapshot, supplier, summonItems);
    }

    private void use(ConfigSnapshot snapshot, Supplier<ConfigSnapshot> supplier, SummonItemFactory factory) {
        config = snapshot;
        ItemSourceRegistry sources = ItemSourceRegistry.create();
        MenuRenderer renderer = new MenuRenderer(sources, key -> config.message("", key), Warnings.ignore());
        menus = new MenuService(adapter, renderer);
        claims = new UnclaimedSummonItems(adapter, supplier, store, factory,
                (what, cause) -> failures.add(what));
        menu = new ClaimMenu(adapter, supplier, claims, factory, menus, renderer,
                (what, cause) -> failures.add(what));
    }

    private static ConfigSnapshot snapshot(int loots) {
        String[] ids = new String[loots];
        for (int i = 0; i < loots; i++) {
            ids[i] = id(i);
        }
        return snapshot(ids);
    }

    private static String id(int index) {
        return String.format("loot%02d", index);
    }

    private static ConfigSnapshot realSummonSnapshot() {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", resource("/languages/en.yml"))
                .menu("claim", resource("/menus/claim.yml"));
        sources.loot("broken", "display_name: 'broken'\nopen_mode: drop\nblock: barrel\nroll:\n"
                + "  entries:\n    a:\n      item:\n        id: diamond\n");
        sources.loot("alpha", "display_name: 'alpha'\nopen_mode: drop\nblock: barrel\n"
                + "summon_item:\n  id: diamond\nroll:\n  entries:\n    a:\n      item:\n        id: diamond\n");
        return ConfigLoader.load(sources.build(), catalog(), WarningKinds.all());
    }

    private static ConfigSnapshot snapshot(String... lootIds) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals("language: en\n")
                .worlds("{}\n")
                .language("en", resource("/languages/en.yml"))
                .menu("claim", resource("/menus/claim.yml"));
        for (String lootId : lootIds) {
            sources.loot(lootId, "display_name: '" + lootId + "'\nopen_mode: drop\nblock: barrel\nroll:\n"
                    + "  entries:\n    a:\n      item:\n        id: diamond\n");
        }
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

    private static String resource(String path) {
        try (InputStream in = ClaimMenuTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
