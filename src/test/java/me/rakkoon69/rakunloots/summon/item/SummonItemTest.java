package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.lootbox.LifecycleFixture;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import me.rakkoon69.rakunloots.summon.SummonService;
import me.rakkoon69.rakunloots.summon.SummonTarget;
import me.rakkoon69.rakunloots.summon.search.SearchMiss;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.summon.search.SpotFinder;
import me.rakkoon69.rakunloots.summon.search.SummonResult;
import me.rakkoon69.rakunloots.summon.search.SummonSearch;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.GROUND_Y;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.position;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.resource;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The summon item through the shipped {@code loots/airdrops.yml}, the listener on a MockBukkit server, the summon
 * service, and the lifecycle fixture. A test checks what the player sees: the stack in their hand, the box in the
 * world, the item in their inventory, and the unclaimed rows in the store. The server adapter's own inventory stands
 * for the player's inventory a give-back reaches; the MockBukkit player's inventory is the one the click reads.
 */
class SummonItemTest {

    private static final long DELAY = 60;

    @TempDir
    Path folder;

    private StreamableServerMock server;
    private WorldMock world;
    private LifecycleFixture fixture;
    private LootBoxStore store;
    private Plugin plugin;
    private final AtomicReference<ConfigSnapshot> config = new AtomicReference<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final Finder finder = new Finder();
    private SummonItemMaker maker;
    private LootBoxLifecycle lifecycle;
    private SummonService service;
    private SummonItemUse use;
    private PlayerMock player;
    private PlayerMock other;

    @BeforeEach
    void setUp() throws Exception {
        server = StreamableServerMock.mock();
        world = new FixtureWorld();
        server.addWorld(world);
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        plugin = MockBukkit.createMockPlugin();
        player = player();
        other = player();
        fixture.adapter.setPlayers(List.of(new PlayerPosition(player.getUniqueId(), 10.5, 70, -3.5),
                new PlayerPosition(other.getUniqueId(), 30.5, 70, 8.5)));
        start(shippedConfig(), finder);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    private PlayerMock player() {
        PlayerMock created = server.addPlayer();
        fixture.adapter.addOnlinePlayer(created.getUniqueId());
        return created;
    }

    /**
     * Builds the summon item parts over the given config and search, and registers the listener.
     */
    private void start(ConfigSnapshot snapshot, SpotFinder search) throws Exception {
        config.set(snapshot);
        maker = new SummonItemMaker(ItemSourceRegistry.create());
        SummonItemGiveBack giveBack = new SummonItemGiveBack(fixture.adapter, config::get, store, maker,
                (what, cause) -> failures.add(what));
        lifecycle = new LootBoxLifecycle(fixture.adapter, snapshot, ItemSourceRegistry.create(), store,
                new Random(1), fixture.warnings);
        service = new SummonService(fixture.adapter, snapshot, lifecycle, search, giveBack, fixture.warnings);
        use = new SummonItemUse(maker, config::get, service, giveBack, (what, cause) -> failures.add(what));
        HandlerList.unregisterAll(plugin);
        server.getPluginManager().registerEvents(new SummonItemListener(use), plugin);
    }

    private Loot airdrops() {
        return config.get().loot("airdrops").orElseThrow();
    }

    private ItemStack summonItem(int amount) {
        ItemStack item = maker.create(airdrops());
        item.setAmount(amount);
        return item;
    }

    private PlayerInteractEvent click(PlayerMock who, Action action, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.HAND ? who.getInventory().getItemInMainHand()
                : who.getInventory().getItemInOffHand();
        PlayerInteractEvent event = new PlayerInteractEvent(who, action, held, null, BlockFace.SELF, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerInteractEvent rightClick(PlayerMock who) {
        return click(who, Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND);
    }

    /**
     * Whether the listener took the click for the summon item. A click in the air counts as cancelled from the start,
     * so the answer is whether the item's use was denied.
     */
    private static boolean taken(PlayerInteractEvent event) {
        return event.useItemInHand() == Event.Result.DENY;
    }

    private void step(long ticks) {
        fixture.adapter.globalScheduler().stepTicks(ticks);
    }

    private int inInventory(PlayerMock who, Material material) {
        return fixture.adapter.inventoryOf(who.getUniqueId()).countOfType(material);
    }

    private List<UnclaimedSummonItem> owed(PlayerMock who) throws Exception {
        return store.unclaimedSummonItems(who.getUniqueId(), config.get());
    }

    /**
     * A click something cancelled before the listener, such as a click on a loot box or one a protection plugin
     * refused, never summons and keeps the item.
     */
    @Test
    void aClickThatWasCancelledNeverSummons() {
        player.getInventory().setItemInMainHand(summonItem(2));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(0, finder.searches);
    }

    /**
     * Acceptance: the item is marked with its loot id and a hash of the loot's resolved summon item, in persistent
     * data. The hash follows the item the loot resolves to, not the amount.
     */
    @Test
    void theSummonItemIsMarkedWithItsLootIdAndAHashOfTheResolvedItem() {
        ItemStack item = maker.create(airdrops());

        assertEquals(Material.NETHER_STAR, item.getType(), "the shipped summon item");
        assertEquals("airdrops", item.getItemMeta().getPersistentDataContainer()
                .get(SummonItemMaker.LOOT_ID_KEY, PersistentDataType.STRING));
        String hash = item.getItemMeta().getPersistentDataContainer()
                .get(SummonItemMaker.HASH_KEY, PersistentDataType.STRING);
        assertFalse(hash.isEmpty());
        assertEquals(Optional.of("airdrops"), maker.lootIdOf(item));
        assertEquals(Optional.empty(), maker.lootIdOf(new ItemStack(Material.NETHER_STAR)));

        ItemStack again = maker.create(airdrops());
        again.setAmount(7);
        assertEquals(hash, again.getItemMeta().getPersistentDataContainer()
                .get(SummonItemMaker.HASH_KEY, PersistentDataType.STRING), "the same summon item, the same hash");
        assertFalse(maker.isOutdated(again, airdrops()));

        Loot edited = edited("id: nether_star", "id: emerald").loot("airdrops").orElseThrow();
        assertTrue(maker.isOutdated(again, edited));
        assertNotEquals(hash, maker.create(edited).getItemMeta().getPersistentDataContainer()
                .get(SummonItemMaker.HASH_KEY, PersistentDataType.STRING));
        Loot renamed = edited("name: '&r<#fad778>&lAir Supply Beacon'", "name: Air Supply Flare")
                .loot("airdrops").orElseThrow();
        assertTrue(maker.isOutdated(again, renamed), "an addition changes the hash too");
    }

    /**
     * Acceptance: a right click with the main hand summons in the loot's player_ring around the player, who owns the
     * box. The shipped ring is 0 to 0, so the box lands in the player's own column. No cooldown, permission, or world
     * limit: a second click a moment later summons again.
     */
    @Test
    void aMainHandRightClickSummonsInThePlayerRingAroundThePlayerWhoOwnsTheBox() throws Exception {
        ConfigSnapshot shipped = shippedConfig();
        start(shipped, new SummonSearch(fixture.adapter, shipped, () -> 0L, new Random(1)));
        fixture.adapter.setPlayers(List.of(new PlayerPosition(player.getUniqueId(), 10.5, 70, -3.5)));
        player.getInventory().setItemInMainHand(summonItem(2));

        PlayerInteractEvent event = rightClick(player);

        assertTrue(taken(event), "the click is the summon item's");
        assertTrue(lifecycle.occupiedCheck().isOccupied(WORLD, 10, GROUND_Y + 1, -4), "held at once, in the ring");
        step(DELAY);
        LootBox box = lifecycle.boxAt(WORLD, position(airSpot(10, -4))).orElseThrow();
        assertEquals(Optional.of(player.getUniqueId()), box.owner(), "the player owns it, so the lock admits them");
        assertEquals("airdrops", box.lootId());
        assertEquals("minecraft:barrel", fixture.adapter.blockAt(position(airSpot(10, -4))));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());

        rightClick(player);
        assertEquals(Material.AIR, player.getInventory().getItemInMainHand().getType(), "no cooldown");
        assertEquals(List.of(), failures);
    }

    /**
     * Acceptance: the off hand is ignored. A summon item there does nothing and a main hand click of another item
     * does not reach it.
     */
    @Test
    void theOffHandIsIgnored() {
        player.getInventory().setItemInOffHand(summonItem(1));

        PlayerInteractEvent offHand = click(player, Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND);
        PlayerInteractEvent mainHandEmpty = rightClick(player);

        assertFalse(taken(offHand));
        assertFalse(taken(mainHandEmpty));
        assertEquals(1, player.getInventory().getItemInOffHand().getAmount());
        assertEquals(0, finder.searches);
        assertEquals(List.of(), fixture.adapter.messages());
    }

    /**
     * Acceptance: the item is taken at the click. Clicking during the delay never starts more summons than there are
     * items: three items, three clicks in the delay, three summons, and a fourth click has nothing to use.
     */
    @Test
    void theItemIsTakenAtTheClickSoClicksDuringTheDelayNeverStartMoreSummonsThanItems() {
        player.getInventory().setItemInMainHand(summonItem(3));
        finder.spots.add(airSpot(1, 1));
        finder.spots.add(airSpot(5, 5));
        finder.spots.add(airSpot(9, 9));

        rightClick(player);
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount(), "taken before the delay ends");
        assertTrue(fixture.adapter.blockWrites().isEmpty());
        rightClick(player);
        rightClick(player);
        PlayerInteractEvent none = rightClick(player);

        assertFalse(taken(none), "nothing left to use");
        assertEquals(Material.AIR, player.getInventory().getItemInMainHand().getType());
        assertEquals(3, service.pendingCount(WORLD));
        assertEquals(3, finder.searches);
        step(DELAY);
        assertEquals(3, fixture.adapter.blockWrites().size());
    }

    /**
     * Acceptance: a search that finds no spot gives the item back, to the inventory when the player is online and it
     * fits, with the default message.
     */
    @Test
    void aSearchThatFindsNoSpotGivesTheItemBackToTheInventory() throws Exception {
        player.getInventory().setItemInMainHand(summonItem(1));
        finder.misses = 1;

        rightClick(player);

        assertEquals(Material.AIR, player.getInventory().getItemInMainHand().getType());
        assertEquals(1, inInventory(player, Material.NETHER_STAR), "given back");
        ItemStack back = fixture.adapter.inventoryOf(player.getUniqueId()).firstOfType(Material.NETHER_STAR);
        assertEquals(Optional.of("airdrops"), maker.lootIdOf(back), "a marked summon item again");
        assertEquals(List.of(), owed(player));
        assertEquals(1, fixture.adapter.messages().size());
        assertTrue(fixture.adapter.messages().get(0).text().contains("no suitable location"));
    }

    /**
     * Acceptance: an abandoned summon gives it back. A block changed during the delay is one such abandon.
     */
    @Test
    void anAbandonedSummonGivesTheItemBack() throws Exception {
        player.getInventory().setItemInMainHand(summonItem(1));
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        rightClick(player);
        assertEquals(0, inInventory(player, Material.NETHER_STAR), "nothing yet, the summon is pending");

        fixture.adapter.putBlock(position(spot), "minecraft:stone");
        step(DELAY);

        assertEquals(1, inInventory(player, Material.NETHER_STAR));
        assertEquals(List.of(), owed(player));
        assertTrue(fixture.adapter.blockWrites().isEmpty());
    }

    /**
     * Acceptance: a full inventory gets an unclaimed summon item, never a drop.
     */
    @Test
    void aFullInventoryGetsAnUnclaimedSummonItem() throws Exception {
        player.getInventory().setItemInMainHand(summonItem(1));
        fixture.adapter.inventoryOf(player.getUniqueId()).fillWith(new ItemStack(Material.STONE));
        finder.misses = 1;

        rightClick(player);

        assertEquals(0, inInventory(player, Material.NETHER_STAR));
        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 1, false)), owed(player));
        assertEquals(List.of(), fixture.adapter.drops());
        assertEquals(List.of(), failures);
    }

    /**
     * Acceptance: a player who logged off during the summon gets an unclaimed summon item.
     */
    @Test
    void anOfflinePlayerGetsAnUnclaimedSummonItem() throws Exception {
        UUID gone = UUID.randomUUID();
        service.summon("airdrops", SummonTarget.world(WORLD), Optional.of(gone), SummonSource.summonItem(gone));

        assertEquals(List.of(new UnclaimedSummonItem("airdrops", 1, false)),
                store.unclaimedSummonItems(gone, config.get()));
        assertEquals(List.of(), failures);
    }

    /**
     * Acceptance: two summon item summons whose searches pick the same block place one box, and the second item comes
     * back to its player. Each player holds one item and clicks; the second click is refused at the reservation, before
     * the delay.
     */
    @Test
    void twoSummonItemSummonsOnOneBlockPlaceOneBoxAndTheSecondItemComesBack() throws Exception {
        Spot spot = airSpot(3, 4);
        finder.spots.add(spot);
        finder.spots.add(spot);
        player.getInventory().setItemInMainHand(summonItem(1));
        other.getInventory().setItemInMainHand(summonItem(1));

        rightClick(player);
        rightClick(other);

        assertEquals(1, inInventory(other, Material.NETHER_STAR), "the second item came back at once");
        assertEquals(0, inInventory(player, Material.NETHER_STAR));
        step(DELAY);
        assertEquals(1, fixture.adapter.blockWrites().size());
        assertEquals(Optional.of(player.getUniqueId()), lifecycle.boxAt(WORLD, position(spot)).orElseThrow().owner());
        assertEquals(0, inInventory(player, Material.NETHER_STAR), "the first summon placed its box");
        assertEquals(List.of(), owed(other));
    }

    /**
     * Acceptance: an outdated item is rebuilt from the loot's current summon item, at the same amount, on use. The
     * rest of the stack is the rebuilt item.
     */
    @Test
    void anOutdatedItemIsRebuiltAtTheSameAmountOnUse() throws Exception {
        player.getInventory().setItemInMainHand(summonItem(5));
        config.set(edited("id: nether_star", "id: emerald"));
        finder.misses = 1;

        rightClick(player);

        ItemStack rest = player.getInventory().getItemInMainHand();
        assertEquals(Material.EMERALD, rest.getType());
        assertEquals(4, rest.getAmount(), "five rebuilt as five, one taken");
        assertFalse(maker.isOutdated(rest, airdrops()));
        assertEquals(Optional.of("airdrops"), maker.lootIdOf(rest));
        assertEquals(1, inInventory(player, Material.EMERALD), "the one given back is the current item too");
    }

    /**
     * Acceptance: an outdated item is rebuilt at the same amount on join, wherever it sits in the inventory, and a
     * current one and other items are left alone.
     */
    @Test
    void anOutdatedItemIsRebuiltAtTheSameAmountOnJoin() {
        player.getInventory().setItem(3, summonItem(5));
        player.getInventory().setItem(20, summonItem(2));
        player.getInventory().setItem(21, new ItemStack(Material.NETHER_STAR, 9));
        player.getInventory().setItemInOffHand(summonItem(4));
        config.set(edited("id: nether_star", "id: emerald"));
        ItemStack current = maker.create(airdrops());
        current.setAmount(6);
        player.getInventory().setItem(22, current);

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, "joined"));

        assertEquals(Material.EMERALD, player.getInventory().getItem(3).getType());
        assertEquals(5, player.getInventory().getItem(3).getAmount());
        assertEquals(Material.EMERALD, player.getInventory().getItem(20).getType());
        assertEquals(2, player.getInventory().getItem(20).getAmount());
        assertEquals(Material.EMERALD, player.getInventory().getItemInOffHand().getType());
        assertEquals(4, player.getInventory().getItemInOffHand().getAmount());
        assertEquals(new ItemStack(Material.NETHER_STAR, 9), player.getInventory().getItem(21), "not a summon item");
        assertEquals(current, player.getInventory().getItem(22), "already current");
        assertEquals(List.of(), failures);
    }

    /**
     * An item of a loot that is gone is not the plugin's to use: the click is left alone and the item stays.
     */
    @Test
    void aSummonItemOfAMissingLootIsLeftAlone() {
        player.getInventory().setItemInMainHand(summonItem(2));
        config.set(ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .build(), LifecycleFixture.SERVER_LIKE, WarningKinds.all()));
        assertTrue(config.get().loot("airdrops").isEmpty());

        PlayerInteractEvent event = rightClick(player);

        assertFalse(taken(event));
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(0, finder.searches);
    }

    /**
     * The shipped config with the airdrops file's text changed.
     */
    private static ConfigSnapshot edited(String from, String to) {
        return shippedConfig(Map.of("airdrops", resource("/loots/airdrops.yml").replace(from, to)));
    }

    /**
     * Returns the spots it was given, one per search, then misses.
     */
    private static final class Finder implements SpotFinder {

        final List<Spot> spots = new CopyOnWriteArrayList<>();
        volatile int searches;
        volatile int misses;

        private CompletableFuture<SummonResult> next() {
            searches++;
            if (misses > 0 || spots.isEmpty()) {
                return CompletableFuture.completedFuture(SummonResult.miss(SearchMiss.ATTEMPTS_USED_UP));
            }
            return CompletableFuture.completedFuture(SummonResult.hit(spots.remove(0)));
        }

        @Override
        public CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied) {
            return next();
        }

        @Override
        public CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                            OccupiedCheck occupied) {
            return next();
        }
    }

    private static final class FixtureWorld extends WorldMock {

        FixtureWorld() {
            super(Material.GRASS_BLOCK, 0, 256, GROUND_Y);
        }

        @Override
        public UUID getUID() {
            return WORLD;
        }
    }
}
