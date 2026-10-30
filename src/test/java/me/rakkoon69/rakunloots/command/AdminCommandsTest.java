package me.rakkoon69.rakunloots.command;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.animation.AnimationRegistry;
import me.rakkoon69.rakunloots.animation.AnimationService;
import me.rakkoon69.rakunloots.animation.AnimationWarning;
import me.rakkoon69.rakunloots.claim.ClaimMenu;
import me.rakkoon69.rakunloots.claim.UnclaimedSummonItems;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.visuals.BoxVisualsService;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary;
import me.rakkoon69.rakunloots.visuals.FakeLaserLibrary;
import me.rakkoon69.rakunloots.visuals.VisualsWarning;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.lootbox.LifecycleWarning;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.lootbox.SummonService;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.lootbox.SummonTarget;
import me.rakkoon69.rakunloots.lootbox.WorldBroadcasts;
import me.rakkoon69.rakunloots.lootbox.WorldTimers;
import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import me.rakkoon69.rakunloots.roll.RollWarning;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.LootBoxWarning;
import me.rakkoon69.rakunloots.storage.SqliteStores;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.summon.MissReason;
import me.rakkoon69.rakunloots.summon.OccupiedCheck;
import me.rakkoon69.rakunloots.summon.Spot;
import me.rakkoon69.rakunloots.summon.SpotFinder;
import me.rakkoon69.rakunloots.summon.SummonResult;
import me.rakkoon69.rakunloots.summonitem.SummonItemGiveBack;
import me.rakkoon69.rakunloots.summonitem.SummonItemMaker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin commands and the claim command through the command registry, over the real summon service, lifecycle,
 * world timers, summon item give back, claim, and SQLite store, the fake server adapter, and the shipped config,
 * messages, and permissions. Each test types a line as a fake sender and checks what the world, the inventory, the
 * store, and the sender see.
 */
class AdminCommandsTest {

    private static final UUID WORLD = FakeCommandSources.WORLD_KEY;
    private static final UUID STEVE = FakeCommandSources.STEVE_ID;
    private static final UUID STEVEN = FakeCommandSources.STEVEN_ID;
    private static final int GROUND_Y = 63;
    private static final String PREFIX = "&f[&bRakunLoots&f]&7";
    private static final String BASE = "rakunloots.command";
    private static final String[] ALL = {BASE + ".help", BASE + ".reload", BASE + ".list", BASE + ".give",
            BASE + ".summon", BASE + ".timer", BASE + ".remove", BASE + ".remove.all", BASE + ".remove.world",
            BASE + ".remove.loot", BASE + ".remove.near", BASE + ".warnings"};
    private static final long DELAY = 60;
    private static final long PERIOD = 420 * 20L;

    /**
     * A crate that lands at once.
     */
    private static final String CRATE = """
            language: en
            open_mode: drop
            block: barrel
            lock: 10
            life: 360
            roll:
              entries:
                one:
                  item:
                    id: diamond
            """;

    /**
     * One world with no player minimum and room for one box, so the spawn limit is easy to reach.
     */
    private static final String LIMITED_WORLDS = """
            worlds:
              world:
                time_between_loot_spawn: 420
                minimum_player_to_spawn_loot: 0
                loot_spawn_limit: 1
                loots:
                  airdrops: 100
            """;

    @TempDir
    Path folder;

    private final FakeServerAdapter adapter = new FakeServerAdapter(WORLD);
    private final AtomicReference<ConfigSnapshot> config = new AtomicReference<>();
    private final AtomicReference<ConfigSnapshot> reloaded = new AtomicReference<>();
    private final AtomicReference<RuntimeException> reloadFailure = new AtomicReference<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final List<String> debugLines = new CopyOnWriteArrayList<>();
    private final List<String> loaderSaw = new CopyOnWriteArrayList<>();
    private final Finder finder = new Finder();
    private final RecordingProvider provider = new RecordingProvider();
    private LootBoxStore store;
    private LootBoxLifecycle lifecycle;
    private SummonService service;
    private WorldTimers timers;
    private HologramService holograms;
    private AnimationService animations;
    private BoxVisualsService visuals;
    private final List<AnimationWarning> animationWarnings = new CopyOnWriteArrayList<>();
    private final List<VisualsWarning> visualsWarnings = new CopyOnWriteArrayList<>();
    private final FakeGlowLibrary glow = new FakeGlowLibrary();
    private final List<ConfigSnapshot> searchSaw = new CopyOnWriteArrayList<>();
    private final List<ConfigSnapshot> menusSaw = new CopyOnWriteArrayList<>();
    private final List<String> reloadOrder = new CopyOnWriteArrayList<>();
    private MenuService realMenus;
    private final AtomicReference<RuntimeException> searchFailure = new AtomicReference<>();
    private SummonItemMaker maker;
    private CommandRegistry registry;
    private FakeCommandSender admin;
    private Sources sources;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                adapter.putChunk(new ChunkView(WORLD, chunkX, chunkZ, 0, 256,
                        (x, y, z) -> y == GROUND_Y ? "minecraft:grass_block" : "minecraft:air"), true, true);
            }
        }
        adapter.setPlayers(List.of(new PlayerPosition(STEVE, 10.5, 64, -3.5)));
        adapter.addOnlinePlayer(STEVE);
        store = SqliteStores.open(folder);
        start(ShippedHologramConfig.load("en", Map.of("crate", CRATE)));
    }

    @AfterEach
    void tearDown() throws Exception {
        timers.stop();
        lifecycle.stop();
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Builds every module over a config and registers the admin root and the claim command under the config's names.
     */
    private void start(ConfigSnapshot snapshot) {
        config.set(snapshot);
        Warnings warnings = warning -> {
            if (warning instanceof LifecycleWarning) {
                failures.add("lifecycle warning " + warning);
            }
        };
        maker = new SummonItemMaker(ItemSourceRegistry.create());
        SummonItemGiveBack giveBack = new SummonItemGiveBack(adapter, config::get, store, maker,
                (what, cause) -> failures.add(what));
        lifecycle = new LootBoxLifecycle(adapter, snapshot, ItemSourceRegistry.create(), store, new Random(7), warnings);
        service = new SummonService(adapter, snapshot, lifecycle, finder, giveBack, warnings);
        WorldBroadcasts broadcasts = new WorldBroadcasts(adapter, snapshot, warnings, new Random(7));
        lifecycle.addObserver(broadcasts);
        timers = new WorldTimers(adapter, snapshot, lifecycle, service, broadcasts, warnings, new Random(7));
        holograms = new HologramService(snapshot, provider, id -> Optional.empty());
        animations = new AnimationService(snapshot, AnimationRegistry.create(),
                (world, owner, draws) -> CompletableFuture.completedFuture(true), adapter.global(),
                w -> animationWarnings.add((AnimationWarning) w));
        visuals = new BoxVisualsService(snapshot, adapter, () -> glow, FakeLaserLibrary::new,
                w -> visualsWarnings.add((VisualsWarning) w));
        UnclaimedSummonItems claims = new UnclaimedSummonItems(adapter, config::get, store, maker,
                (what, cause) -> failures.add(what));
        sources = new Sources();
        CommandMessages messages = key -> config.get().message("", key);
        registry = new CommandRegistry(sources, messages, debugLines::add);
        MenuRenderer menuRenderer = new MenuRenderer(ItemSourceRegistry.create(), messages::text, Warnings.ignore());
        realMenus = new MenuService(adapter, menuRenderer);
        ClaimMenu claimMenu = new ClaimMenu(adapter, config::get, claims, maker, realMenus, menuRenderer,
                (what, cause) -> failures.add(what));
        registry.register(AdminCommands.root(snapshot.adminCommand(), new AdminCommands.Parts(
                () -> {
                    loaderSaw.add("pending " + service.pendingCount(WORLD));
                    if (reloadFailure.get() != null) {
                        throw reloadFailure.get();
                    }
                    return reloaded.get();
                },
                adapter, sources, lifecycle, service, timers, holograms, animations, visuals, next -> {
                    if (searchFailure.get() != null) {
                        throw searchFailure.get();
                    }
                    reloadOrder.add("search");
                    searchSaw.add(next);
                }, next -> {
                    reloadOrder.add("menus");
                    menusSaw.add(next);
                    realMenus.reload(next);
                }, giveBack::give, new me.rakkoon69.rakunloots.warning.WarningRecord())));
        registry.register(ClaimCommand.node(snapshot.claimCommand(), claims, claimMenu));
        admin = new FakeCommandSender("Admin", ALL);
    }

    /**
     * Acceptance: {@code list} lists loot ids.
     */
    @Test
    void listListsTheLootIdsInOrder() {
        assertEquals(List.of(PREFIX + " Loots (&e2&7): &fairdrops&7, &fcrate"), run(admin, "rkl list"));
    }

    /**
     * Acceptance: {@code give <loot> [player] [amount]} gives summon items. An online player with room gets them in
     * the inventory, as marked summon items, and nothing is owed.
     */
    @Test
    void giveGivesSummonItemsToAnOnlinePlayerWithRoom() throws Exception {
        List<String> shown = run(admin, "rkl give airdrops Steve 3");

        assertEquals(List.of(PREFIX + " Gave &e3 &7summon item(s) of &fairdrops &7to &fSteve&7. Any that do not fit "
                + "wait for the player to claim them."), shown);
        assertEquals(3, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));
        ItemStack given = adapter.inventoryOf(STEVE).firstOfType(Material.NETHER_STAR);
        assertEquals(Optional.of("airdrops"), maker.lootIdOf(given), "a marked summon item");
        assertEquals(List.of(), owed(STEVE));
        assertEquals(List.of(), failures);
    }

    /**
     * Acceptance: an offline player gets unclaimed summon items, for the whole amount.
     */
    @Test
    void giveToAnOfflinePlayerWritesUnclaimedSummonItems() throws Exception {
        run(admin, "rkl give airdrops Steven 2");

        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 2, false)), owed(STEVEN));
        assertEquals(0, adapter.inventoryOf(STEVEN).countOfType(Material.NETHER_STAR));
        assertEquals(List.of(), failures);
    }

    /**
     * Acceptance: a full inventory gets unclaimed summon items, and a nearly full one gets what fits with the rest
     * unclaimed.
     */
    @Test
    void giveIntoAFullInventoryWritesUnclaimedSummonItemsAndKeepsOnlyTheRestWhenSomeFit() throws Exception {
        adapter.inventoryOf(STEVE).fillWith(new ItemStack(Material.STONE));
        run(admin, "rkl give airdrops Steve 3");
        assertEquals(0, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 3, false)), owed(STEVE));

        adapter.inventoryOf(STEVE).fillAllButSlots(new ItemStack(Material.STONE), 1);
        run(admin, "rkl give airdrops Steve 70");

        assertEquals(64, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR), "one free slot holds a stack");
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 9, false)), owed(STEVE), "3 and the 6 that did not fit");
        assertEquals(List.of(), failures);
    }

    /**
     * Without a player the give goes to the sender, and the console must name one.
     */
    @Test
    void giveWithoutAPlayerGivesToTheSenderAndTheConsoleMustNameOne() throws Exception {
        FakeCommandSender steve = new FakeCommandSender("Steve", ALL).asPlayer(STEVE);

        run(steve, "rkl give airdrops");
        assertEquals(1, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));

        assertEquals(List.of(PREFIX + " &cThe console must name the player to give to."), run(admin, "rkl give airdrops"));
        assertEquals(1, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));
        assertEquals(List.of(), owed(STEVE));
    }

    /**
     * Acceptance: {@code summon <loot> world} uses the admin summon on that world: a world search, a spot held through
     * the summon delay, then a loot box with no owner.
     */
    @Test
    void summonWithAWorldRunsThatWorldsSearchAndPlacesAnOwnerlessBoxAfterTheDelay() {
        Spot spot = new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air");
        finder.spots.add(spot);

        List<String> shown = run(admin, "rkl summon airdrops world");

        assertEquals(List.of("world " + WORLD + " airdrops"), finder.searches);
        assertEquals(List.of(), shown, "the reply comes when the summon ends, after its delay");
        assertEquals(1, service.pendingCount(WORLD));
        adapter.globalScheduler().stepTicks(DELAY);

        assertEquals(List.of(PREFIX + " Summoned &fairdrops &7at &e3 64 4&7."), admin.messages());
        LootBox box = lifecycle.boxAt(WORLD, new BlockPosition(3, GROUND_Y + 1, 4)).orElseThrow();
        assertEquals(Optional.empty(), box.owner());
        assertEquals("airdrops", box.lootId());
    }

    /**
     * Acceptance (debug mode): the outcome of a summon, a give, a timer run, and a remove, which is otherwise only
     * sent to the sender, goes to the debug sink as well, with no color codes, who ran it, and the command.
     */
    @Test
    void everyAdminCommandOutcomeAlsoGoesToTheDebugSink() {
        finder.spots.add(new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air"));
        finder.spots.add(new Spot(WORLD, 8, GROUND_Y + 1, 8, "minecraft:air"));

        run(admin, "rkl give airdrops Steve 3");
        run(admin, "rkl summon airdrops world");
        assertEquals(1, debugLines.size(), "the summon's reply waits for its delay");
        adapter.globalScheduler().stepTicks(DELAY);
        run(admin, "rkl timer world now --force");
        adapter.globalScheduler().stepTicks(DELAY);
        run(admin, "rkl remove all");

        assertEquals(List.of(
                "command /rkl give by Admin: [RakunLoots] Gave 3 summon item(s) of airdrops to Steve. Any that do not "
                        + "fit wait for the player to claim them.",
                "command /rkl summon by Admin: [RakunLoots] Summoned airdrops at 3 64 4.",
                "command /rkl timer by Admin: [RakunLoots] Timer summon in world: summoned airdrops at 8 64 8.",
                "command /rkl remove all by Admin: [RakunLoots] Removed 2 loot box(es)."), debugLines);
    }

    /**
     * Acceptance: with a player the summon is in the loot's player ring around that player, with no owner.
     */
    @Test
    void summonWithAPlayerSearchesTheRingAroundThePlayerAndHasNoOwner() {
        finder.spots.add(new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air"));

        run(admin, "rkl summon airdrops steve");
        adapter.globalScheduler().stepTicks(DELAY);

        assertEquals(List.of("around " + WORLD + " airdrops 10 64 -4"), finder.searches, "the player's block");
        LootBox box = lifecycle.boxAt(WORLD, new BlockPosition(3, GROUND_Y + 1, 4)).orElseThrow();
        assertEquals(Optional.empty(), box.owner());
        assertEquals(List.of(PREFIX + " Summoned &fairdrops &7at &e3 64 4&7."), admin.messages());
    }

    /**
     * An abandoned admin summon says so with the reason's message, and an admin summon never gives a summon item back.
     */
    @Test
    void anAbandonedSummonTellsTheSenderWhyAndGivesNothingBack() throws Exception {
        List<String> shown = run(admin, "rkl summon airdrops world");

        assertEquals(1, shown.size());
        List<String> lines = List.of(shown.get(0).split("\r?\n", -1));
        assertEquals(2, lines.size(), "the reason is on its own chat line: " + shown.get(0));
        assertEquals(PREFIX + " &cThe summon of &fairdrops &cwas abandoned.", lines.get(0));
        assertEquals("§f[§bRakunLoots§f]§7 There is §cno suitable location §7for a loot to spawn !", lines.get(1));
        assertEquals(List.of(), owed(STEVE));
        assertEquals(0, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));
    }

    /**
     * An abandoned timer summon puts the reason on its own chat line, so the reason's prefix does not follow the
     * command's prefix on one line.
     */
    @Test
    void anAbandonedTimerSummonPutsTheReasonOnItsOwnChatLine() {
        run(admin, "rkl timer world now");
        adapter.globalScheduler().stepTicks(DELAY);

        String last = admin.messages().get(admin.messages().size() - 1);
        List<String> lines = List.of(last.split("\r?\n", -1));
        assertEquals(2, lines.size(), last);
        assertEquals(PREFIX + " &cThe timer summon in &fworld &cwas abandoned.", lines.get(0));
        assertEquals("§f[§bRakunLoots§f]§7 There is §cno suitable location §7for a loot to spawn !", lines.get(1));
    }

    /**
     * A player sender who names no target searches their own world, and the console must name one.
     */
    @Test
    void summonWithoutATargetSearchesTheSendersWorldAndTheConsoleMustNameOne() {
        FakeCommandSender steve = new FakeCommandSender("Steve", ALL).asPlayer(STEVE)
                .at(new SenderLocation(WORLD, 0.5, 64, 0.5));

        run(steve, "rkl summon airdrops");
        assertEquals(List.of("world " + WORLD + " airdrops"), finder.searches);

        assertEquals(List.of(PREFIX + " &cThe console must name a world or a player to summon in."),
                run(admin, "rkl summon airdrops"));
        assertEquals(1, finder.searches.size());
    }

    /**
     * A world that is named but not loaded says so, and the search never starts.
     */
    @Test
    void summonInAWorldThatIsNotLoadedSaysSo() {
        assertEquals(List.of(PREFIX + " &cThe world &fworld_nether &cis not loaded."),
                run(admin, "rkl summon airdrops world_nether"));
        assertEquals(List.of(), finder.searches);
    }

    /**
     * The summon target argument takes a world or an online player, refuses anything else with its reason and the
     * type it expected, offers close matches, and completes both kinds of name.
     */
    @Test
    void theSummonTargetTakesAWorldOrAnOnlinePlayerAndCompletesBoth() {
        assertEquals(List.of(
                        PREFIX + " &f/rkl summon airdrops &c&nwrld&r&f",
                        "&8> &cno world or online player is named &fwrld&c&7, expected &ea world name or the name of a "
                                + "player who is online&7. Did you mean &eworld&7?"),
                run(admin, "rkl summon airdrops wrld"));
        assertEquals(List.of("Steve", "world", "world_nether"), registry.complete(admin, "rkl summon airdrops "
                ).stream().filter(name -> !name.equals("Alex") && !name.equals("creative")
                        && !name.equals("world_the_end")).toList());
        assertEquals(List.of("Steve"), registry.complete(admin, "rkl summon airdrops ste"));
    }

    /**
     * Acceptance: {@code timer <world> now} refuses with its reason when the world holds too few players, and
     * {@code --force} summons anyway, skipping only the player count.
     */
    @Test
    void timerNowRefusesBelowTheMinimumPlayersAndForceSummonsAnyway() {
        adapter.setPlayers(List.of());

        assertEquals(List.of(PREFIX + " &cNot enough players for &fworld&c. Use &e--force &cto skip the minimum player "
                + "count."), run(admin, "rkl timer world now"));
        assertEquals(List.of(), finder.searches);

        finder.spots.add(new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air"));
        run(admin, "rkl timer world now --force");
        adapter.globalScheduler().stepTicks(DELAY);

        assertEquals(List.of("world " + WORLD + " airdrops"), finder.searches);
        assertTrue(lifecycle.isLootBox(WORLD, 3, GROUND_Y + 1, 4));
        assertEquals(PREFIX + " Timer summon in &fworld&7: summoned &fairdrops &7at &e3 64 4&7.",
                admin.messages().get(admin.messages().size() - 1));
    }

    /**
     * Acceptance: {@code timer <world> now} follows the usual checks and runs one timer summon with a player in the
     * world, and the loot is one of the world's list.
     */
    @Test
    void timerNowRunsOneTimerSummonWhenTheChecksPass() {
        finder.spots.add(new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air"));

        run(admin, "rkl timer world now");
        adapter.globalScheduler().stepTicks(DELAY);

        assertEquals(1, finder.searches.size());
        assertTrue(lifecycle.isLootBox(WORLD, 3, GROUND_Y + 1, 4));
        assertEquals(PREFIX + " Timer summon in &fworld&7: summoned &fairdrops &7at &e3 64 4&7.", admin.messages().get(0));
    }

    /**
     * Acceptance: the spawn limit refuses {@code timer <world> now}, and {@code --force} skips it.
     */
    @Test
    void timerNowRefusesAtTheSpawnLimitAndForceSkipsIt() throws Exception {
        start(ShippedHologramConfig.load("en", LIMITED_WORLDS, Map.of("crate", CRATE)));
        finder.spots.add(new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air"));
        service.adminSummon("crate", SummonTarget.world(WORLD), Optional.empty()).join();
        assertEquals(1, lifecycle.liveBoxCount(WORLD));

        assertEquals(List.of(PREFIX + " &cworld &calready holds its loot spawn limit of loot boxes and pending "
                + "summons. Use &e--force &cto skip the limit."), run(admin, "rkl timer world now"));
        assertEquals(1, finder.searches.size(), "only the crate was searched for");

        finder.spots.add(new Spot(WORLD, 9, GROUND_Y + 1, 9, "minecraft:air"));
        run(admin, "rkl timer world now --force");
        adapter.globalScheduler().stepTicks(DELAY);

        assertEquals(2, lifecycle.liveBoxCount(WORLD));
        assertTrue(lifecycle.isLootBox(WORLD, 9, GROUND_Y + 1, 9));
    }

    /**
     * Acceptance: {@code timer <world> now} resets that world's timer. A timer that has run most of its period starts
     * full again, so its own summon comes one whole period after the command.
     */
    @Test
    void timerNowResetsTheWorldsTimer() {
        timers.start();
        adapter.globalScheduler().stepTicks(PERIOD - 20);
        finder.spots.add(new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air"));

        run(admin, "rkl timer world now");
        assertEquals(1, finder.searches.size());

        adapter.globalScheduler().stepTicks(PERIOD - 1);
        assertEquals(1, finder.searches.size(), "the timer's own summon is a whole period away");
        adapter.globalScheduler().stepTicks(1);
        assertEquals(2, finder.searches.size());
    }

    /**
     * A world that is not in {@code worlds.yml} is refused with its reason, and nothing is searched.
     */
    @Test
    void timerNowOfAWorldWithoutATimerSaysSo() {
        assertEquals(List.of(PREFIX + " &cThe world &fcreative &chas no timer in worlds.yml."),
                run(admin, "rkl timer creative now"));
        assertEquals(List.of(), finder.searches);
    }

    /**
     * Acceptance: {@code reload} abandons pending summons first, which gives their summon items back, then reloads the
     * config, the lifecycle, the world timers, and the hologram service.
     */
    @Test
    void reloadAbandonsPendingSummonsThenReloadsEveryModule() throws Exception {
        ConfigSnapshot next = ShippedHologramConfig.load("zh", Map.of("crate", CRATE, "vote", CRATE));
        reloaded.set(next);
        timers.start();
        adapter.globalScheduler().stepTicks(PERIOD - 100);
        finder.spots.add(new Spot(WORLD, 3, GROUND_Y + 1, 4, "minecraft:air"));
        CompletableFuture<?> pending = service.summon("airdrops", SummonTarget.world(WORLD), Optional.of(STEVE),
                SummonSource.summonItem(STEVE));
        assertEquals(1, service.pendingCount(WORLD));
        finder.spots.add(new Spot(WORLD, 9, GROUND_Y + 1, 9, "minecraft:air"));

        List<String> shown = run(admin, "rkl reload");

        assertEquals(List.of("pending 0"), loaderSaw, "the config was read after the pending summon was abandoned");
        assertTrue(pending.isDone());
        assertEquals(1, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR), "the summon item came back");
        assertEquals(List.of(PREFIX + " Reloaded. &e3 &7loot(s) loaded, &e" + next.warnings().size() + " &7warning(s)."),
                shown);
        // The summon service took the new config: a loot only the new config has can be summoned.
        assertTrue(service.adminSummon("vote", SummonTarget.world(WORLD), Optional.empty()).join().box().isPresent());
        // The lifecycle took it: a box of the new loot lands.
        LootBox box = lifecycle.summon("vote", new Spot(WORLD, 5, GROUND_Y + 1, 5, "minecraft:air"), Optional.empty(), SummonKind.UNKNOWN)
                .join().box().orElseThrow();
        // The hologram service took it: its lines are the new config's language.
        holograms.shown(new BoxEvent(box.boxId(), "vote", WORLD, box.position(), Optional.empty(), 10,
                java.util.OptionalInt.of(360)));
        assertEquals("§7所有者：§e§7无所有者", provider.firstLine);
        // The timers started full: the timer that was 20 seconds from firing is a whole period away again.
        int searches = finder.searches.size();
        adapter.globalScheduler().stepTicks(PERIOD - 1);
        assertEquals(searches, finder.searches.size());
        adapter.globalScheduler().stepTicks(1);
        assertEquals(searches + 1, finder.searches.size());
    }

    /**
     * Acceptance: {@code reload} also reloads the animation service, the box visuals service, and the summon search.
     * A reload re-checks each animation's type, so an unknown type warns again; a reload re-arms the visuals' one
     * warning per reload, so a glow that throws warns again; and the search takes the new config.
     */
    @Test
    void reloadAlsoReloadsTheAnimationAndBoxVisualsServicesAndTheSearch() throws IOException {
        ConfigSnapshot next = ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .animations("BAD:\n  type: NOPE\n  variables:\n    loops: 1\n")
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), writtenId -> writtenId.startsWith("@") || writtenId.startsWith("#")
                ? Optional.empty() : Optional.of(writtenId), WarningKinds.all());
        reloaded.set(next);
        glow.throwOnCalls(new IllegalStateException("no packets"));
        adapter.setPlayers(List.of(new PlayerPosition(STEVE, 10.5, 64, -3.5)));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        visuals.shown(new BoxEvent(first, "airdrops", WORLD, new BlockPosition(1, 64, 1), Optional.empty(), 10,
                java.util.OptionalInt.of(360)));
        visuals.shown(new BoxEvent(second, "airdrops", WORLD, new BlockPosition(2, 64, 2), Optional.empty(), 10,
                java.util.OptionalInt.of(360)));
        visuals.ended(new BoxEvent(second, "airdrops", WORLD, new BlockPosition(2, 64, 2), Optional.empty(), 10,
                java.util.OptionalInt.of(360)));
        assertEquals(1, visualsWarnings.size(), "one warning until the next reload");
        assertEquals(List.of(), animationWarnings);

        run(admin, "rkl reload");

        assertEquals(1, animationWarnings.size(), animationWarnings.toString());
        assertEquals(AnimationWarning.Kind.UNKNOWN_TYPE, animationWarnings.get(0).kind());
        visuals.shown(new BoxEvent(second, "airdrops", WORLD, new BlockPosition(2, 64, 2), Optional.empty(), 10,
                java.util.OptionalInt.of(360)));
        assertEquals(2, visualsWarnings.size(), "the reload re-armed the one warning per reload");
        assertEquals(List.of(next), searchSaw);
    }

    /**
     * Acceptance: a successful reload hands the new config to the search before it retires the menus.
     */
    @Test
    void reloadClosesMenusAfterTheSearchTakesTheNewConfig() throws Exception {
        ConfigSnapshot next = ShippedHologramConfig.load("en", Map.of("crate", CRATE));
        reloaded.set(next);

        run(admin, "rkl reload");

        assertEquals(List.of(next), menusSaw);
        assertEquals(List.of("search", "menus"), reloadOrder);
    }

    /**
     * A reload through the command hands the new config to the search, then the real menu service closes every menu
     * that player opened. The menu service is the one the plugin wires, not a recorder.
     */
    @Test
    void reloadClosesEveryRealMenuAfterTheSearchTakesTheNewConfig() throws Exception {
        ConfigSnapshot next = ShippedHologramConfig.load("en", Map.of("crate", CRATE));
        reloaded.set(next);
        UUID alex = UUID.randomUUID();
        adapter.addOnlinePlayer(alex);
        MenuView view = new MenuView("Menu", 3, Map.of(0, new MenuIcon(new ItemStack(Material.DIAMOND),
                Optional.empty(), Optional.empty())));
        realMenus.open(STEVE, view).join();
        realMenus.open(alex, view).join();
        assertEquals(2, adapter.menuOpens().size());
        assertEquals(List.of(), adapter.menuCloses());

        run(admin, "rkl reload");

        assertEquals(List.of(next), searchSaw, "the search saw the new config");
        assertEquals(List.of(next), menusSaw, "the menu service saw the new config");
        assertEquals(List.of("search", "menus"), reloadOrder, "the menus closed after the search took it");
        assertEquals(Set.of(STEVE, alex), adapter.menuCloses().stream()
                .map(close -> close.playerId()).collect(java.util.stream.Collectors.toSet()),
                "every real open menu was closed");
        assertTrue(adapter.menuCloses().stream().allMatch(close ->
                close.menu() instanceof FakeServerAdapter.FakeMenu menu && menu.closed()));
    }

    /**
     * Acceptance: when a later module fails, the menu generation still turns over and every menu closes, so nothing
     * keeps drawing from the old layout.
     */
    @Test
    void aLaterReloadFailureStillClosesMenus() throws Exception {
        ConfigSnapshot next = ShippedHologramConfig.load("en", Map.of("crate", CRATE));
        reloaded.set(next);
        searchFailure.set(new IllegalStateException("search broke"));

        String shown = String.join("\n", run(admin, "rkl reload"));

        assertTrue(shown.contains("search broke"), shown);
        assertEquals(List.of(next), menusSaw);
        assertEquals("menus", reloadOrder.get(reloadOrder.size() - 1));
    }

    /**
     * Acceptance: the claim command registers under its {@code config.yml} name and aliases, and its default
     * permission is everyone's. A player takes what they are owed.
     */
    @Test
    void theClaimCommandRunsUnderItsConfigNameAndAliasesForEveryone() throws Exception {
        store.addUnclaimedSummonItems(STEVE, "airdrops", 2);
        FakeCommandSender everyone = new FakeCommandSender("Steve", ClaimCommand.PERMISSION).asPlayer(STEVE);

        assertEquals("claimsummon", config.get().claimCommand().name());
        registry.dispatch(everyone, "rklclaim all");

        assertEquals(2, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));
        assertEquals(List.of(), owed(STEVE));
        // The claim's messages go out on the player's thread.
        adapter.globalScheduler().stepTicks(1);
        assertTrue(adapter.messages().stream().anyMatch(message -> message.playerId().equals(STEVE)
                && message.text().contains("You took")), adapter.messages().toString());

        store.addUnclaimedSummonItems(STEVE, "airdrops", 1);
        registry.dispatch(everyone, "claimsummon all");
        assertEquals(3, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));
    }

    /**
     * Only a player can claim, because the items go into an inventory.
     */
    @Test
    void theClaimCommandNeedsAPlayer() {
        FakeCommandSender console = new FakeCommandSender("Console", ClaimCommand.PERMISSION);

        registry.dispatch(console, "claimsummon");
        registry.dispatch(console, "claimsummon all");

        assertEquals(List.of(
                PREFIX + " &cOnly a player can take summon items, because they go into an inventory.",
                PREFIX + " &cOnly a player can take summon items, because they go into an inventory."),
                console.messages());
    }

    /**
     * The admin root answers to the name and aliases of the {@code admin_command} block of the config.
     */
    @Test
    void theAdminRootRunsUnderItsConfigNameAndAliases() {
        assertEquals("rkl", config.get().adminCommand().name());
        assertEquals(List.of("rkloots"), config.get().adminCommand().aliases());

        assertEquals(run(admin, "rkl list"), run(admin, "rkloots list"));
    }

    /**
     * A sender without a node's permission neither sees nor runs it.
     */
    @Test
    void aSenderWithoutThePermissionOfACommandNeitherSeesNorRunsIt() {
        FakeCommandSender partial = new FakeCommandSender("Alex", BASE + ".list");

        run(partial, "rkl reload");
        run(partial, "rkl give airdrops Steve");

        assertEquals(0, adapter.inventoryOf(STEVE).countOfType(Material.NETHER_STAR));
        assertEquals(List.of("list"), registry.complete(partial, "rkl "));
        assertEquals(List.of(PREFIX + " Loots (&e2&7): &fairdrops&7, &fcrate"), run(partial, "rkl list"));
    }

    /**
     * A reload that fails on an unquoted placeholder in a language file shows the admin the file, the line, and the
     * hint to quote the value.
     */
    @Test
    void aReloadThatFailsOnAnUnquotedPlaceholderShowsTheHintToTheAdmin() {
        try {
            me.rakkoon69.rakunloots.config.ConfigLoader.load(me.rakkoon69.rakunloots.config.ConfigSources.builder()
                    .globals("language: en\n")
                    .language("en", "still_locked: {prefix} locked\n").build(), written -> Optional.of(written), WarningKinds.all());
            throw new AssertionError("the load did not fail");
        } catch (org.yaml.snakeyaml.error.YAMLException failure) {
            reloadFailure.set(failure);
        }

        String shown = String.join("\n", run(admin, "rkl reload"));

        assertTrue(shown.contains("languages/en.yml"), shown);
        assertTrue(shown.contains("Put the whole value in quotes"), shown);
        assertEquals(List.of(), menusSaw, "a config that never loaded leaves the open menus alone");
    }

    /**
     * Every new node's help keys have a text in every shipped language, and every permission is declared in
     * {@code plugin.yml} under {@code rakunloots.admin}. The claim command's permission is for everyone by default.
     */
    @Test
    void theShippedFilesHoldTheHelpAndPermissionsOfEveryNewCommand() throws IOException {
        CommandNode root = registry.root("rkl").orElseThrow();
        ConfigSnapshot snapshot = ShippedHologramConfig.load("en", Map.of());
        Map<String, Map<String, Object>> permissions = permissions();
        @SuppressWarnings("unchecked")
        Map<String, Boolean> children = (Map<String, Boolean>) permissions.get("rakunloots.admin").get("children");

        assertEquals(List.of("help", "reload", "list", "give", "summon", "remove", "timer",
                "warnings"),
                root.children().stream().map(CommandNode::name).toList());
        for (CommandNode node : root.children()) {
            List<String> keys = new ArrayList<>(List.of(node.helpKey("summary"), node.helpKey("description"),
                    node.helpKey("example")));
            node.arguments().forEach(argument -> keys.add(node.argumentHelpKey(argument)));
            for (String language : List.of("en", "vi", "zh")) {
                assertTrue(snapshot.languageKeys(language).containsAll(keys), language + " lacks help for " + node.path());
            }
            String permission = node.permission().orElseThrow();
            assertTrue(permissions.containsKey(permission), permission + " is not declared");
            assertEquals(Boolean.TRUE, children.get(permission), permission + " is not a child of rakunloots.admin");
        }
        assertEquals(true, permissions.get(ClaimCommand.PERMISSION).get("default"));
    }

    private List<String> run(FakeCommandSender sender, String line) {
        int before = sender.messages().size();
        registry.dispatch(sender, line);
        return sender.messages().subList(before, sender.messages().size());
    }

    private List<UnclaimedSummonItem> owed(UUID player) throws Exception {
        return store.unclaimedSummonItems(player, config.get());
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = AdminCommandsTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> permissions() throws IOException {
        try (InputStream in = AdminCommandsTest.class.getResourceAsStream("/plugin.yml")) {
            Map<String, Object> plugin = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return (Map<String, Map<String, Object>>) plugin.get("permissions");
        }
    }

    /**
     * Names read from the current config, with Steve online and Steven known.
     */
    private final class Sources implements CommandSources {

        @Override
        public Collection<String> lootIds() {
            return config.get().loots().keySet();
        }

        @Override
        public Collection<String> worldNames() {
            return List.of("world", "world_nether", "creative");
        }

        @Override
        public Optional<UUID> worldKey(String worldName) {
            return worldName.equals("world") ? Optional.of(WORLD) : Optional.empty();
        }

        @Override
        public Collection<String> playerNames() {
            return List.of("Steve", "Steven");
        }

        @Override
        public Collection<String> onlinePlayerNames() {
            return List.of("Steve");
        }

        @Override
        public Optional<UUID> playerId(String playerName) {
            return new FakeCommandSources().playerId(playerName);
        }
    }

    /**
     * Hands out the spots the test queues, one per search, misses when none is left, and notes each search.
     */
    private static final class Finder implements SpotFinder {

        final List<Spot> spots = new CopyOnWriteArrayList<>();
        final List<String> searches = new CopyOnWriteArrayList<>();

        private CompletableFuture<SummonResult> next() {
            if (spots.isEmpty()) {
                return CompletableFuture.completedFuture(SummonResult.miss(MissReason.ATTEMPTS_USED_UP));
            }
            return CompletableFuture.completedFuture(SummonResult.hit(spots.remove(0)));
        }

        @Override
        public CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied) {
            searches.add("world " + worldKey + " " + lootId);
            return next();
        }

        @Override
        public CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                            OccupiedCheck occupied) {
            searches.add("around " + worldKey + " " + lootId + " " + x + " " + y + " " + z);
            return next();
        }
    }

    /**
     * Notes the first line of the first hologram it is asked to draw.
     */
    private static final class RecordingProvider implements HologramProvider {

        volatile String firstLine;

        @Override
        public CanRun canRun() {
            return CanRun.YES;
        }

        @Override
        public void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
            if (firstLine == null) {
                firstLine = lines.get(1);
            }
        }

        @Override
        public void update(UUID hologramId, List<String> lines) {
        }

        @Override
        public void remove(UUID hologramId) {
        }

        @Override
        public void removeAll() {
        }
    }
}
