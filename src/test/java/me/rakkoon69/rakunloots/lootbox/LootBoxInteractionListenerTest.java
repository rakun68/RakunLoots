package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.lootbox.listener.LootBoxInteractionListener;
import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuListener;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.MenuHolder;
import me.rakkoon69.rakunloots.server.PaperServerAdapter;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.Drop;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.InventoryOpen;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter.Message;
import me.rakkoon69.rakunloots.summon.FakeSharedInventory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.GROUND_Y;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interaction listener, registered on a MockBukkit server, carries clicks, breaks, explosions, chunk loads and
 * unloads, and shared inventory closes to a real lifecycle over the shipped config, and does what the lifecycle answers. Each
 * lifecycle call is seen by what it does through the fake server adapter: the player it drops for, opens for, or
 * messages, the position it reads or removes, and whether the lock let the player through. The MockBukkit world has
 * the fixture's world key, so a Bukkit block and the lifecycle's registry name the same position.
 */
class LootBoxInteractionListenerTest {

    /**
     * The shipped {@code still_locked} message, as the lifecycle renders it.
     */
    private static final String STILL_LOCKED =
            "§f[§bRakunLoots§f]§7 This loot is still §clocked §7and you are §cnot the owner §7!";

    @TempDir
    Path folder;

    private StreamableServerMock server;
    private WorldMock world;
    private LifecycleFixture fixture;
    private LootBoxStore store;
    private ConfigSnapshot config;
    private LootBoxLifecycle lifecycle;
    private Plugin plugin;
    private PlayerMock owner;
    private PlayerMock other;
    private PlayerMock staff;

    @BeforeEach
    void setUp() throws Exception {
        server = StreamableServerMock.mock();
        world = new FixtureWorld();
        server.addWorld(world);
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        config = shippedConfig(Map.of(
                "chest_crate", crateLoot(),
                "fragile", fragileLoot()));
        lifecycle = fixture.lifecycle(config, store, 58);
        lifecycle.start().join();
        plugin = MockBukkit.createMockPlugin();
        owner = player();
        other = player();
        staff = player();
        staff.addAttachment(plugin, LootBoxLifecycle.BYPASS_LOCK_PERMISSION, true);

        server.getPluginManager().registerEvents(new LootBoxInteractionListener(lifecycle), plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance: a right-click reaches the lifecycle with the right player, position, and bypass flag. The shipped
     * airdrop is drop open and locked for 10 seconds: a player the lock refuses gets {@code still_locked} and nothing
     * drops; the owner, and a player with {@code rakunloots.bypass.lock}, spill the box they clicked and no other.
     */
    @Test
    void aRightClickReachesTheLifecycleWithThePlayerThePositionAndTheBypassFlag() {
        LootBox ownersBox = summon("airdrops", 2, 2, Optional.of(owner.getUniqueId()));
        LootBox staffsBox = summon("airdrops", 4, 4, Optional.of(owner.getUniqueId()));

        click(other, ownersBox, EquipmentSlot.HAND);

        assertEquals(List.of(new Message(other.getUniqueId(), STILL_LOCKED)), fixture.adapter.messages(),
                "the player who clicked is told");
        assertEquals(List.of(), fixture.adapter.drops());

        click(owner, ownersBox, EquipmentSlot.HAND);

        assertEquals(dropsOf(ownersBox), fixture.adapter.drops(), "the owner spills the box clicked");
        assertTrue(lifecycle.boxAt(WORLD, staffsBox.position()).isPresent(), "the other box is untouched");

        click(staff, staffsBox, EquipmentSlot.HAND);

        assertEquals(1, fixture.adapter.messages().size(), "bypass passes without a message");
        assertEquals(concat(dropsOf(ownersBox), dropsOf(staffsBox)), fixture.adapter.drops(),
                "bypass spills a box it does not own");
    }

    /**
     * Acceptance: sneaking never bypasses the lock. A sneaking player without the permission is refused like any
     * other, so nothing about sneaking reaches the lifecycle.
     */
    @Test
    void aSneakingPlayerWithoutBypassIsStillLockedOut() {
        LootBox box = summon("airdrops", 2, 2, Optional.of(owner.getUniqueId()));
        other.setSneaking(true);

        PlayerInteractEvent event = click(other, box, EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertEquals(List.of(new Message(other.getUniqueId(), STILL_LOCKED)), fixture.adapter.messages());
        assertEquals(List.of(), fixture.adapter.drops());
        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent());
    }

    /**
     * Acceptance: a break reaches the lifecycle with the right player, position, and bypass flag. A refused break is
     * cancelled and tells the player; the owner's and a bypass player's break is not cancelled and ends the box
     * broken.
     */
    @Test
    void aBreakReachesTheLifecycleWithThePlayerThePositionAndTheBypassFlag() {
        LootBox ownersBox = summon("airdrops", 2, 2, Optional.of(owner.getUniqueId()));
        LootBox staffsBox = summon("airdrops", 4, 4, Optional.of(owner.getUniqueId()));

        BlockBreakEvent refused = breakBlock(other, ownersBox);

        assertTrue(refused.isCancelled(), "a refused break is cancelled");
        assertEquals(List.of(new Message(other.getUniqueId(), STILL_LOCKED)), fixture.adapter.messages());
        assertEquals(List.of(), fixture.adapter.drops());

        BlockBreakEvent owned = breakBlock(owner, ownersBox);

        assertFalse(owned.isCancelled(), "an allowed break is not cancelled");
        assertEquals(dropsOf(ownersBox), fixture.adapter.drops(), "the owner breaks the box broken");
        assertTrue(lifecycle.boxAt(WORLD, staffsBox.position()).isPresent(), "the other box is untouched");

        BlockBreakEvent bypassed = breakBlock(staff, staffsBox);

        assertFalse(bypassed.isCancelled(), "a bypass break is not cancelled");
        assertEquals(1, fixture.adapter.messages().size());
        assertEquals(concat(dropsOf(ownersBox), dropsOf(staffsBox)), fixture.adapter.drops());
    }

    /**
     * Acceptance: an allowed break is not cancelled, so the server breaks the block; its own drops and experience are
     * turned off, since what drops is the lifecycle's: what was left, and the block item only when the loot says so.
     * The shipped airdrop is a barrel with {@code drop_block} off, so no barrel drops. A break away from a box keeps
     * its vanilla drops.
     */
    @Test
    void anAllowedBreakIsNotCancelledAndDropsNothingOfTheServersOwn() {
        LootBox box = summon("airdrops", 2, 2, Optional.of(owner.getUniqueId()));
        Block plain = world.getBlockAt(6, GROUND_Y + 1, 6);
        plain.setType(Material.BARREL);

        BlockBreakEvent allowed = breakBlock(owner, box);
        BlockBreakEvent away = new BlockBreakEvent(plain, owner);
        away.setExpToDrop(3);
        fire(away);

        assertFalse(allowed.isCancelled());
        assertFalse(allowed.isDropItems(), "no vanilla barrel item");
        assertEquals(0, allowed.getExpToDrop());
        assertEquals(dropsOf(box), fixture.adapter.drops(), "only what was left drops");
        assertFalse(away.isCancelled());
        assertTrue(away.isDropItems(), "a plain barrel keeps its drops");
        assertEquals(3, away.getExpToDrop());
    }

    /**
     * A block another plugin replaced ends the box as a changed block when a player breaks it. The break is not
     * cancelled and its drops and experience are left alone, so the block that replaced the box drops what it drops.
     */
    @Test
    void aBreakOfAReplacedBlockKeepsTheDropsOfTheBlockThatReplacedTheBox() {
        LootBox box = summon("airdrops", 2, 2, Optional.of(owner.getUniqueId()));
        fixture.adapter.putBlock(box.position(), "minecraft:stone");

        BlockBreakEvent event = new BlockBreakEvent(block(box.position()), owner);
        event.setExpToDrop(3);
        fire(event);

        assertFalse(event.isCancelled());
        assertTrue(event.isDropItems(), "the block that replaced the box keeps its drops");
        assertEquals(3, event.getExpToDrop());
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, box.position()), "the box ended as a changed block");
    }

    /**
     * Acceptance: an allowed click on a box is still cancelled, and a click where no box is is not.
     */
    @Test
    void aClickWhereNoBoxIsIsNotCancelled() {
        Block plain = world.getBlockAt(6, GROUND_Y + 1, 6);
        plain.setType(Material.BARREL);

        PlayerInteractEvent event = interact(owner, plain, EquipmentSlot.HAND);

        assertFalse(event.isCancelled());
        assertNotEquals(Event.Result.DENY, event.useInteractedBlock(), "the barrel's own inventory opens");
        assertEquals(List.of(), fixture.adapter.messages());
    }

    /**
     * Acceptance: a click on a container loot box cancels the vanilla open, whether the lock refuses it or it opens
     * the shared inventory. The admitted player sees the shared inventory instead.
     */
    @Test
    void aClickOnAContainerLootBoxCancelsTheVanillaOpen() {
        LootBox box = summon("chest_crate", 2, 2, Optional.of(owner.getUniqueId()));

        PlayerInteractEvent refused = click(other, box, EquipmentSlot.HAND);
        PlayerInteractEvent opened = click(owner, box, EquipmentSlot.HAND);

        assertTrue(refused.isCancelled());
        assertEquals(Event.Result.DENY, refused.useInteractedBlock(), "the refused player's chest stays shut");
        assertTrue(opened.isCancelled());
        assertEquals(Event.Result.DENY, opened.useInteractedBlock(), "the chest's own inventory never opens");
        List<InventoryOpen> opens = fixture.adapter.inventoryOpens();
        assertEquals(List.of(owner.getUniqueId()), opens.stream().map(InventoryOpen::playerId).toList(),
                "the owner sees the shared inventory");
    }

    /**
     * Acceptance: an off-hand click makes no call. The owner's off-hand click on an unlocked drop open box spills
     * nothing and leaves the box; the main hand click that follows spills it.
     */
    @Test
    void anOffHandClickMakesNoCall() {
        LootBox box = summon("airdrops", 2, 2, Optional.of(owner.getUniqueId()));
        LootBox locked = summon("airdrops", 4, 4, Optional.of(owner.getUniqueId()));

        PlayerInteractEvent offHand = click(owner, box, EquipmentSlot.OFF_HAND);
        click(other, locked, EquipmentSlot.OFF_HAND);

        assertFalse(offHand.isCancelled());
        assertEquals(List.of(), fixture.adapter.drops());
        assertEquals(List.of(), fixture.adapter.messages(), "not even the lock answers");
        assertEquals(List.of(), fixture.adapter.blockReads(), "the lifecycle never looked at either block");
        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent());

        click(owner, box, EquipmentSlot.HAND);

        assertEquals(dropsOf(box), fixture.adapter.drops());
    }

    /**
     * Acceptance: an entity explosion's block list loses exactly the positions the lifecycle keeps: a locked box, and
     * an unlocked box whose loot allows explosions, which the lifecycle ends itself. The ground around them, and a
     * box whose block another plugin replaced, stay in the list.
     */
    @Test
    void anEntityExplosionLosesExactlyTheBlocksTheLifecycleKeeps() {
        Blast blast = blast();
        EntityExplodeEvent event = new EntityExplodeEvent(creeper(), blast.center(), blast.blocks(), 1f);

        fire(event);

        blast.assertKept(event.blockList());
    }

    /**
     * Acceptance: a block explosion's block list loses exactly the positions the lifecycle keeps.
     */
    @Test
    void aBlockExplosionLosesExactlyTheBlocksTheLifecycleKeeps() {
        Blast blast = blast();
        BlockExplodeEvent event = new BlockExplodeEvent(blast.center().getBlock(), blast.blocks(), 1f);

        fire(event);

        blast.assertKept(event.blockList());
    }

    /**
     * Acceptance: a chunk load reaches the lifecycle with its world and chunk. The lifecycle reads every box in that
     * chunk and ends one whose block another plugin replaced while nobody looked; a box in another chunk is not read.
     */
    @Test
    void aChunkLoadReachesTheLifecycleWithItsChunk() {
        LootBox changed = summon("airdrops", 2, 2, Optional.empty());
        LootBox elsewhere = summon("airdrops", 20, 2, Optional.empty());
        fixture.adapter.putBlock(changed.position(), "minecraft:stone");

        fire(new ChunkLoadEvent(world.getChunkAt(1, 0), false));

        assertEquals(List.of(elsewhere.position()), fixture.adapter.blockReads(), "only the loaded chunk's box");
        assertTrue(lifecycle.boxAt(WORLD, changed.position()).isPresent());

        fire(new ChunkLoadEvent(world.getChunkAt(0, 0), false));

        assertEquals(List.of(elsewhere.position(), changed.position()), fixture.adapter.blockReads());
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, changed.position()), "the changed block ended");
        assertTrue(fixture.warnings.lifecycleKinds().contains(LifecycleWarning.Kind.CHANGED_BLOCK));
        assertTrue(lifecycle.boxAt(WORLD, elsewhere.position()).isPresent());
    }

    /**
     * Acceptance: a chunk unload event reaches the lifecycle's chunk unloaded call with its world and chunk. The
     * observer hears out of view for the box in that chunk only, and the box still lives.
     */
    @Test
    void aChunkUnloadReachesTheLifecycleWithItsChunk() {
        LootBox inChunk = summon("airdrops", 2, 2, Optional.empty());
        LootBox elsewhere = summon("airdrops", 20, 2, Optional.empty());
        List<BoxEvent> outOfView = new ArrayList<>();
        lifecycle.addObserver(new BoxObserver() {
            @Override
            public void shown(BoxEvent box) {
            }

            @Override
            public void ticked(BoxEvent box) {
            }

            @Override
            public void outOfView(BoxEvent box) {
                outOfView.add(box);
            }

            @Override
            public void ended(BoxEvent box) {
            }
        });

        fire(new ChunkUnloadEvent(world.getChunkAt(0, 0)));

        assertEquals(List.of(BoxEvent.of(inChunk)), outOfView);
        assertTrue(lifecycle.boxAt(WORLD, inChunk.position()).isPresent());

        fire(new ChunkUnloadEvent(world.getChunkAt(1, 0)));

        assertEquals(List.of(BoxEvent.of(inChunk), BoxEvent.of(elsewhere)), outOfView);
    }

    /**
     * Acceptance: a shared inventory close reaches the lifecycle with that inventory and the player who closed it.
     * The crate is inventory open with {@code empty_removal} on: after its owner, the one viewer, empties it, a close
     * of it by a player who never opened it changes nothing, and the viewer's own close ends the box and removes its
     * chest. A close of any other inventory is ignored.
     */
    @Test
    void anInventoryCloseReachesTheLifecycleWithTheInventoryAndThePlayer() {
        LootBox box = summon("chest_crate", 2, 2, Optional.of(other.getUniqueId()));
        click(other, box, EquipmentSlot.HAND);
        FakeSharedInventory shared = fixture.adapter.inventoryOpens().get(0).inventory();
        shared.contents().keySet().forEach(slot -> shared.take(other.getUniqueId(), slot));

        owner.openInventory(server.createInventory(null, 9, "plain"));
        owner.closeInventory();
        owner.openInventory(shared.getInventory());
        owner.closeInventory();

        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent(), "not a viewer of that inventory");
        assertEquals("minecraft:chest", fixture.adapter.blockAt(box.position()));

        other.openInventory(shared.getInventory());
        other.closeInventory();

        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, box.position()), "the last viewer closed it empty");
        assertEquals("minecraft:air", fixture.adapter.blockAt(box.position()));
    }

    /**
     * A menu close passes through both listeners while a loot box shared inventory is open for the same player. The
     * menu listener claims the menu, and the loot box listener leaves it alone: the lifecycle is never told the shared
     * inventory closed, so an emptied box would have ended if it had been.
     */
    @Test
    void aMenuClosePassesBothListenersWithoutClosingTheLootBoxInventory() {
        LootBox box = summon("chest_crate", 2, 2, Optional.of(owner.getUniqueId()));
        click(owner, box, EquipmentSlot.HAND);
        FakeSharedInventory shared = fixture.adapter.inventoryOpens().get(0).inventory();
        shared.contents().keySet().forEach(slot -> shared.take(owner.getUniqueId(), slot));

        MenuService menus = new MenuService(new PaperServerAdapter(plugin));
        server.getPluginManager().registerEvents(new MenuListener(menus), plugin);
        MenuView view = new MenuView("Menu", 3, Map.of(0, new MenuIcon(new ItemStack(Material.DIAMOND),
                Optional.empty(), Optional.empty())));
        CompletableFuture<Void> opened = menus.open(owner.getUniqueId(), view);
        for (int tick = 0; tick < 50 && !opened.isDone(); tick++) {
            server.getScheduler().performOneTick();
        }
        assertTrue(opened.isDone(), "the menu opened on the main thread");
        opened.join();
        assertTrue(owner.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder);

        owner.closeInventory();

        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent(),
                "the menu close did not reach the loot box lifecycle");
        assertEquals("minecraft:chest", fixture.adapter.blockAt(box.position()),
                "the emptied box stayed until its shared inventory closes");
    }

    /**
     * Two boxes in the blast, a locked airdrop and an unlocked fragile box, plus a box whose block another plugin
     * replaced and the ground under all three.
     */
    private Blast blast() {
        LootBox locked = summon("airdrops", 2, 2, Optional.of(owner.getUniqueId()));
        LootBox fragile = summon("fragile", 3, 2, Optional.empty());
        LootBox changed = summon("airdrops", 4, 2, Optional.empty());
        fixture.adapter.putBlock(changed.position(), "minecraft:stone");
        block(changed.position()).setType(Material.STONE);
        List<Block> blocks = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            blocks.add(world.getBlockAt(x, GROUND_Y, 2));
            blocks.add(world.getBlockAt(x, GROUND_Y + 1, 2));
        }
        return new Blast(block(fragile.position()).getLocation(), blocks, locked, fragile, changed);
    }

    private final class Blast {

        private final Location center;
        private final List<Block> blocks;
        private final LootBox locked;
        private final LootBox fragile;
        private final LootBox changed;

        Blast(Location center, List<Block> blocks, LootBox locked, LootBox fragile, LootBox changed) {
            this.center = center;
            this.blocks = blocks;
            this.locked = locked;
            this.fragile = fragile;
            this.changed = changed;
        }

        Location center() {
            return center;
        }

        List<Block> blocks() {
            return new ArrayList<>(blocks);
        }

        void assertKept(List<Block> left) {
            List<Block> expected = new ArrayList<>(blocks);
            expected.remove(block(locked.position()));
            expected.remove(block(fragile.position()));
            assertEquals(expected, left, "only the two boxes are taken out");
            assertTrue(left.contains(block(changed.position())), "the replaced block is not a loot box");
            assertTrue(lifecycle.boxAt(WORLD, locked.position()).isPresent(), "the locked box stays");
            assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, fragile.position()), "the fragile box ended");
            assertEquals("minecraft:air", fixture.adapter.blockAt(fragile.position()),
                    "the lifecycle removed the fragile box's block");
            assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, changed.position()), "the changed box ended");
        }
    }

    private PlayerInteractEvent click(PlayerMock player, LootBox box, EquipmentSlot hand) {
        return interact(player, block(box.position()), hand);
    }

    /**
     * A right-click on a block, as the server fires it for one hand.
     */
    private PlayerInteractEvent interact(PlayerMock player, Block block, EquipmentSlot hand) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.AIR), block, BlockFace.UP, hand);
        fire(event);
        return event;
    }

    private BlockBreakEvent breakBlock(PlayerMock player, LootBox box) {
        BlockBreakEvent event = new BlockBreakEvent(block(box.position()), player);
        fire(event);
        return event;
    }

    private void fire(Event event) {
        server.getPluginManager().callEvent(event);
    }

    /**
     * Summons a loot and sets the MockBukkit block to the placed block too.
     */
    private LootBox summon(String lootId, int x, int z, Optional<UUID> boxOwner) {
        SummonOutcome outcome = lifecycle.summon(lootId, airSpot(x, z), boxOwner, SummonKind.UNKNOWN).join();
        LootBox box = outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
        block(box.position()).setType(Material.matchMaterial(box.placedBlockId()));
        return box;
    }

    /**
     * What spills from a box nobody took from: each rolled stack, at the box.
     */
    private static List<Drop> dropsOf(LootBox box) {
        List<Drop> drops = box.contents().stacks().stream()
                .map(stack -> new Drop(box.position(), stack.item().getType().getKey().toString(),
                        stack.item().getAmount(), Optional.empty()))
                .toList();
        assertNotEquals(List.of(), drops, "the roll gave the box something");
        return drops;
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private Block block(BlockPosition position) {
        return world.getBlockAt(position.x(), position.y(), position.z());
    }

    private PlayerMock player() {
        PlayerMock player = server.addPlayer();
        fixture.adapter.addOnlinePlayer(player.getUniqueId());
        return player;
    }

    /**
     * A creeper that answers its type and nothing else, so the listener provably reads the explosion, not the
     * entity. MockBukkit 1.18 has no creeper.
     */
    private static Creeper creeper() {
        return (Creeper) Proxy.newProxyInstance(Creeper.class.getClassLoader(), new Class<?>[]{Creeper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> org.bukkit.entity.EntityType.CREEPER;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "CREEPER";
                    default -> throw new UnsupportedOperationException("Creeper." + method.getName());
                });
    }

    /**
     * Inventory open on a chest, locked for 10 seconds, removed once empty and closed.
     */
    private static String crateLoot() {
        return """
                language: en
                display_name: '&r<#fad778>Air Supply'
                open_mode: inventory
                lock: 10
                life: 360
                block: chest
                loot_end:
                  empty_removal: true
                roll:
                  entries:
                    gold:
                      weight: -1
                      amount: 7
                      item:
                        id: gold_ingot
                """;
    }

    /**
     * Drop open on a barrel, no lock, and an explosion ends it.
     */
    private static String fragileLoot() {
        return """
                language: en
                display_name: 'Fragile'
                open_mode: drop
                lock: 0
                life: 360
                block: barrel
                loot_end:
                  allow_explosion: true
                roll:
                  entries:
                    gold:
                      weight: -1
                      amount: 2
                      item:
                        id: gold_ingot
                """;
    }

    /**
     * A flat MockBukkit world with the fixture's world key.
     */
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
