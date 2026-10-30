package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.storage.SummonKind;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.BlockState;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.state.BlockStateMock;
import be.seeseemelk.mockbukkit.inventory.HopperInventoryMock;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.lootbox.listener.LootBoxGuardListener;
import me.rakkoon69.rakunloots.lootbox.listener.LootBoxPhysicsGuardListener;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wither;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.GROUND_Y;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard listeners, registered on a MockBukkit server, cancel each guarded event at a loot box the lifecycle
 * summoned, and leave the same event alone away from one. The MockBukkit world has the fixture's world key, so a
 * Bukkit block and the lifecycle's registry name the same position.
 */
class LootBoxGuardListenerTest {

    /**
     * Barrel on the ground, where no loot box is.
     */
    private static final BlockPosition PLAIN = new BlockPosition(6, GROUND_Y + 1, 6);

    @TempDir
    Path folder;

    private StreamableServerMock server;
    private WorldMock world;
    private LootBoxStore store;
    private Block box;
    private Block plain;

    @BeforeEach
    void setUp() throws Exception {
        server = StreamableServerMock.mock();
        world = new FixtureWorld();
        server.addWorld(world);
        LifecycleFixture fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        ConfigSnapshot config = shippedConfig();
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 57);
        lifecycle.start().join();
        SummonOutcome outcome = lifecycle.summon("airdrops", airSpot(2, 2), Optional.empty(), SummonKind.UNKNOWN).join();
        LootBox summoned = outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
        String placed = config.loot("airdrops").orElseThrow().block();

        box = block(summoned.position());
        box.setType(Material.matchMaterial(placed));
        plain = block(PLAIN);
        plain.setType(Material.matchMaterial(placed));

        server.getPluginManager().registerEvents(new LootBoxGuardListener(lifecycle), MockBukkit.createMockPlugin());
        server.getPluginManager().registerEvents(new LootBoxPhysicsGuardListener(lifecycle),
                MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance: MockBukkit fires each listed event at a loot box and sees it cancelled.
     */
    @Test
    void eachGuardedEventAtALootBoxIsCancelled() {
        List<String> notCancelled = new ArrayList<>();
        for (Case each : cases()) {
            if (!fire(each.event().apply(box))) {
                notCancelled.add(each.name());
            }
        }

        assertEquals(List.of(), notCancelled);
    }

    /**
     * Acceptance: the same event at a position without a box is not cancelled.
     */
    @Test
    void theSameEventWithoutALootBoxIsNotCancelled() {
        List<String> cancelled = new ArrayList<>();
        for (Case each : cases()) {
            if (fire(each.event().apply(plain))) {
                cancelled.add(each.name());
            }
        }

        assertEquals(List.of(), cancelled);
    }

    /**
     * Acceptance: a piston that moves a box as its second or later block is cancelled.
     */
    @Test
    void aPistonThatMovesABoxAsItsSecondOrLaterBlockIsCancelled() {
        Block piston = box.getRelative(BlockFace.WEST, 3);
        piston.setType(Material.STICKY_PISTON);
        Block first = box.getRelative(BlockFace.WEST, 2);
        Block second = box.getRelative(BlockFace.WEST);
        first.setType(Material.SLIME_BLOCK);
        second.setType(Material.STONE);
        Block past = box.getRelative(BlockFace.EAST);

        assertTrue(fire(new BlockPistonExtendEvent(piston, List.of(second, box), BlockFace.EAST)), "second");
        assertTrue(fire(new BlockPistonExtendEvent(piston, List.of(first, second, box), BlockFace.EAST)), "third");
        assertTrue(fire(new BlockPistonRetractEvent(piston, List.of(first, second, box), BlockFace.EAST)),
                "pulled third");
        assertFalse(fire(new BlockPistonExtendEvent(piston, List.of(first, second), BlockFace.EAST)),
                "stops short of the box");
        assertFalse(fire(new BlockPistonExtendEvent(piston, List.of(first, past), BlockFace.EAST)),
                "moves the blocks around the box");
    }

    /**
     * Acceptance: a hopper minecart below a box is cancelled.
     */
    @Test
    void aHopperMinecartBelowABoxIsCancelled() {
        assertTrue(fire(new InventoryMoveItemEvent(containerAt(box), new ItemStack(Material.GOLD_INGOT),
                hopperMinecart(), false)), "the minecart pulls from the box");
        assertFalse(fire(new InventoryMoveItemEvent(containerAt(plain), new ItemStack(Material.GOLD_INGOT),
                hopperMinecart(), false)), "the minecart pulls from a plain barrel");
    }

    /**
     * A chest or trapped chest placed against a loot box would join it into a double chest, so it is cancelled. A
     * chest away from the box, on a diagonal, or a block that is not a chest is not.
     */
    @Test
    void aChestPlacedBesideALootBoxIsCancelled() {
        assertTrue(place(box.getRelative(BlockFace.WEST), Material.CHEST), "chest west");
        assertTrue(place(box.getRelative(BlockFace.NORTH), Material.TRAPPED_CHEST), "trapped chest north");
        assertTrue(place(box.getRelative(BlockFace.EAST), Material.CHEST), "chest east");
        assertTrue(place(box.getRelative(BlockFace.SOUTH), Material.CHEST), "chest south");
        assertFalse(place(box.getRelative(BlockFace.WEST).getRelative(BlockFace.NORTH), Material.CHEST), "diagonal");
        assertFalse(place(box.getRelative(BlockFace.WEST), Material.BARREL), "not a chest");
        assertFalse(place(plain.getRelative(BlockFace.WEST), Material.CHEST), "away from the box");
    }

    private boolean place(Block at, Material type) {
        BlockState replaced = at.getState();
        at.setType(type);
        return fire(new BlockPlaceEvent(at, replaced, at.getRelative(BlockFace.DOWN), new ItemStack(type),
                server.addPlayer(), true, EquipmentSlot.HAND));
    }

    /**
     * Each guarded event, built at a block.
     */
    private List<Case> cases() {
        return List.of(
                new Case("hopper below pulls", at -> new InventoryMoveItemEvent(containerAt(at),
                        new ItemStack(Material.GOLD_INGOT), hopperAt(at.getRelative(BlockFace.DOWN)), true)),
                new Case("hopper minecart below pulls", at -> new InventoryMoveItemEvent(containerAt(at),
                        new ItemStack(Material.GOLD_INGOT), hopperMinecart(), false)),
                new Case("hopper below pulls from a double chest half", at -> new InventoryMoveItemEvent(
                        doubleChest(chestAt(at.getRelative(BlockFace.NORTH)), containerAt(at)),
                        new ItemStack(Material.GOLD_INGOT), hopperAt(at.getRelative(BlockFace.DOWN)), true)),
                new Case("hopper above pushes", at -> new InventoryMoveItemEvent(hopperAt(at.getRelative(BlockFace.UP)),
                        new ItemStack(Material.GOLD_INGOT), containerAt(at), true)),
                new Case("piston pushes", at -> new BlockPistonExtendEvent(pistonWestOf(at), List.of(at),
                        BlockFace.EAST)),
                new Case("sticky piston pulls", at -> new BlockPistonRetractEvent(pistonWestOf(at), List.of(at),
                        BlockFace.EAST)),
                new Case("fire burns", at -> new BlockBurnEvent(at, fireAbove(at))),
                new Case("enderman takes", at -> new EntityChangeBlockEvent(entity(Enderman.class,
                        EntityType.ENDERMAN), at, server.createBlockData(Material.AIR))),
                new Case("wither breaks", at -> new EntityChangeBlockEvent(entity(Wither.class, EntityType.WITHER),
                        at, server.createBlockData(Material.AIR))),
                new Case("ravager breaks", at -> new EntityChangeBlockEvent(entity(Ravager.class,
                        EntityType.RAVAGER), at, server.createBlockData(Material.AIR))),
                new Case("falling block lands", at -> new EntityChangeBlockEvent(entity(FallingBlock.class,
                        EntityType.FALLING_BLOCK), at, server.createBlockData(Material.SAND))),
                new Case("fades", at -> new BlockFadeEvent(at, new BlockStateMock(Material.WATER))),
                new Case("forms", at -> new BlockFormEvent(at, new BlockStateMock(Material.WHITE_CONCRETE))),
                new Case("snow golem forms", at -> new EntityBlockFormEvent(entity(Snowman.class,
                        EntityType.SNOWMAN), at, new BlockStateMock(Material.SNOW))),
                new Case("spreads", at -> new BlockSpreadEvent(at, at.getRelative(BlockFace.NORTH),
                        new BlockStateMock(Material.GRASS_BLOCK))),
                new Case("liquid flows in", at -> new BlockFromToEvent(waterAbove(at), at)),
                new Case("physics drops a gravity block", at -> new BlockPhysicsEvent(at,
                        server.createBlockData(Material.SAND))),
                new Case("dragon egg teleports away", at -> new BlockFromToEvent(at, at.getRelative(BlockFace.UP, 4))),
                new Case("grows", at -> new BlockGrowEvent(at, new BlockStateMock(Material.PUMPKIN))),
                new Case("leaves decay", at -> new LeavesDecayEvent(at)),
                new Case("cauldron fills", at -> new CauldronLevelChangeEvent(at, null,
                        CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL, new BlockStateMock(
                        Material.WATER_CAULDRON))),
                new Case("sponge soaks", at -> new SpongeAbsorbEvent(at, List.of())),
                new Case("tree grows into", at -> new StructureGrowEvent(at.getRelative(BlockFace.DOWN, 3)
                        .getLocation(), TreeType.TREE, false, null, List.of(at.getRelative(BlockFace.DOWN, 3)
                        .getState(), at.getState()))),
                new Case("bone meal changes", at -> new BlockFertilizeEvent(at.getRelative(BlockFace.NORTH), null,
                        List.of(at.getState()))));
    }

    private record Case(String name, Function<Block, Event> event) {
    }

    /**
     * Calls the event through the plugin manager.
     *
     * @return whether a listener cancelled it
     */
    private boolean fire(Event event) {
        server.getPluginManager().callEvent(event);
        return ((Cancellable) event).isCancelled();
    }

    private Block block(BlockPosition position) {
        return world.getBlockAt(position.x(), position.y(), position.z());
    }

    private static Inventory containerAt(Block block) {
        return ((Container) block.getState()).getInventory();
    }

    private static Inventory hopperAt(Block block) {
        block.setType(Material.HOPPER);
        return ((Container) block.getState()).getInventory();
    }

    private static Inventory chestAt(Block block) {
        block.setType(Material.CHEST);
        return ((Container) block.getState()).getInventory();
    }

    /**
     * A double chest over two block inventories, as CraftBukkit's: its holder is a {@link DoubleChest}, whose sides
     * are the halves' holders. MockBukkit 1.18 has no double chest. It answers only what a double chest's holder
     * reads, and fails on anything else.
     */
    private static Inventory doubleChest(Inventory left, Inventory right) {
        DoubleChestInventory[] self = new DoubleChestInventory[1];
        self[0] = (DoubleChestInventory) Proxy.newProxyInstance(DoubleChestInventory.class.getClassLoader(),
                new Class<?>[]{DoubleChestInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getLeftSide" -> left;
                    case "getRightSide" -> right;
                    case "getHolder" -> new DoubleChest(self[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "double chest";
                    default -> throw new UnsupportedOperationException("DoubleChestInventory." + method.getName());
                });
        return self[0];
    }

    private static Block pistonWestOf(Block block) {
        Block piston = block.getRelative(BlockFace.WEST);
        piston.setType(Material.STICKY_PISTON);
        return piston;
    }

    private static Block fireAbove(Block block) {
        Block fire = block.getRelative(BlockFace.UP);
        fire.setType(Material.FIRE);
        return fire;
    }

    private static Block waterAbove(Block block) {
        Block water = block.getRelative(BlockFace.UP);
        water.setType(Material.WATER);
        return water;
    }

    /**
     * A hopper minecart's inventory. It belongs to the minecart, not to a block.
     */
    private static Inventory hopperMinecart() {
        return new HopperInventoryMock(entity(HopperMinecart.class, EntityType.MINECART_HOPPER));
    }

    /**
     * An entity that answers its type and nothing else. MockBukkit 1.18 has no enderman, wither, ravager, falling
     * block, snow golem, or hopper minecart. A listener that asked the entity anything but its type would fail here,
     * so the guard provably decides by the block alone.
     */
    private static <T extends Entity> T entity(Class<T> type, EntityType kind) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> kind;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> kind.name();
                    default -> throw new UnsupportedOperationException(kind + "." + method.getName());
                }));
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
