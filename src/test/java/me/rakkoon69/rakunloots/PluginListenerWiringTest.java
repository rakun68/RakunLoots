package me.rakkoon69.rakunloots;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.lootbox.AbandonReason;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.lootbox.BoxObserver;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.lootbox.SummonOutcome;
import me.rakkoon69.rakunloots.summon.SummonReport;
import me.rakkoon69.rakunloots.summon.SummonService;
import me.rakkoon69.rakunloots.summon.SummonTarget;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.summon.item.SummonItemMaker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The listeners as the real enabled plugin registers them, fired with the real Bukkit events: a click on a loot box
 * with a summon item in hand opens the box and neither summons nor takes the item, and a chunk unload abandons the
 * pending summon in that chunk.
 */
class PluginListenerWiringTest {

    private ServerMock server;
    private WorldMock world;
    private RakunLootsPlugin plugin;
    private LootBoxLifecycle lifecycle;
    private SummonService summons;
    private PlayerMock player;

    @BeforeEach
    void start() throws Exception {
        server = StreamableServerMock.mock();
        world = new WorldMock(Material.GRASS_BLOCK, 0, 256, 63);
        server.addWorld(world);
        for (int chunkX = -1; chunkX <= 4; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 4; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ).load();
            }
        }
        plugin = MockBukkit.loadWith(TestPlugin.class, new PluginDescriptionFile("RakunLoots", "1.0.0-SNAPSHOT",
                RakunLootsPlugin.class.getName()));
        assertTrue(plugin.isEnabled());
        lifecycle = (LootBoxLifecycle) field("lifecycle");
        summons = (SummonService) field("summons");
        player = server.addPlayer();
        player.teleport(new Location(world, 40.5, 64, 40.5));
    }

    @AfterEach
    void stop() {
        // The plugin stops its summons and timers first, and the scheduler runs what they left, so a test that failed
        // midway does not leave the mock scheduler waiting for an async task.
        server.getPluginManager().disablePlugin(plugin);
        await(30, () -> false);
        MockBukkit.unmock();
    }

    /**
     * Ticks the server until the condition holds, as the summon search runs on an async thread that reads chunks
     * through the server's ticks. Gives up after about 5 seconds, and the caller's assertion then reports it.
     */
    private void await(java.util.function.BooleanSupplier condition) {
        await(500, condition);
    }

    private void await(int attempts, java.util.function.BooleanSupplier condition) {
        for (int attempt = 0; attempt < attempts && !condition.getAsBoolean(); attempt++) {
            server.getScheduler().performOneTick();
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Acceptance: right clicking a loot box while holding a summon item opens the box and does not also summon or
     * take the item. The shipped airdrop is drop open and its owner passes the lock, so the click ends the box. Its
     * summon item is in the main hand, and the loot box listener must run first, since both handle the click at the
     * same priority in the order they were registered.
     */
    @Test
    void aClickOnALootBoxWithASummonItemInHandOpensTheBoxAndDoesNotSummonOrTakeTheItem() {
        me.rakkoon69.rakunloots.server.BlockPosition at = new me.rakkoon69.rakunloots.server.BlockPosition(2, 64, 2);
        SummonOutcome outcome = join(lifecycle.summon("airdrops", new Spot(world.getUID(), 2, 64, 2, "minecraft:air"),
                Optional.of(player.getUniqueId()), SummonKind.SUMMON_ITEM));
        LootBox box = outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
        world.getBlockAt(2, 64, 2).setType(Material.matchMaterial(box.placedBlockId()));
        ItemStack summonItem = new SummonItemMaker(plugin.itemSources()).create(plugin.config().loot("airdrops")
                .orElseThrow());
        summonItem.setAmount(3);
        player.getInventory().setItemInMainHand(summonItem);
        SummonWatcher watcher = new SummonWatcher();
        lifecycle.addObserver(watcher);

        Block block = world.getBlockAt(2, 64, 2);
        PlayerInteractEvent click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(click);

        assertEquals(Optional.empty(), lifecycle.boxAt(world.getUID(), at), "the click opened the box");
        assertEquals(0, summons.pendingCount(world.getUID()), "and did not summon");
        assertEquals(3, player.getInventory().getItemInMainHand().getAmount(), "and did not take the item");
        assertTrue(click.isCancelled());
        assertEquals(List.of(), watcher.pending, "no summon began");
        assertEquals(List.of(), watcher.abandoned);
    }

    /**
     * Acceptance: a chunk unload reaches the summon service, which abandons the pending summon in that chunk and not
     * one in another chunk. The shipped airdrop waits 60 ticks before it writes its block.
     */
    @Test
    void aChunkUnloadAbandonsThePendingSummonInThatChunk() {
        SummonWatcher watcher = new SummonWatcher();
        lifecycle.addObserver(watcher);
        CompletableFuture<SummonReport> summon = summons.adminSummon("airdrops",
                SummonTarget.player(world.getUID(), player.getUniqueId()), Optional.empty());
        await(() -> summons.pendingCount(world.getUID()) == 1);
        assertEquals(1, summons.pendingCount(world.getUID()), "the summon holds a spot in its delay");

        // Not the chunk of the held spot: nothing happens.
        Spot held = watcher.pending.get(0);
        int chunkX = held.x() >> 4;
        int chunkZ = held.z() >> 4;
        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(chunkX + 3, chunkZ)));
        assertEquals(1, summons.pendingCount(world.getUID()));

        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(chunkX, chunkZ)));

        assertEquals(0, summons.pendingCount(world.getUID()));
        assertEquals(List.of(AbandonReason.CHUNK_UNLOADED), watcher.abandoned);
        assertTrue(summon.isDone());
    }

    /**
     * Records the summon events and nothing else.
     */
    private static final class SummonWatcher implements BoxObserver {

        final List<Spot> pending = new ArrayList<>();
        final List<AbandonReason> abandoned = new ArrayList<>();

        @Override
        public void shown(BoxEvent box) {
        }

        @Override
        public void ticked(BoxEvent box) {
        }

        @Override
        public void outOfView(BoxEvent box) {
        }

        @Override
        public void ended(BoxEvent box) {
        }

        @Override
        public void summonPending(Spot spot, String lootId, int delayTicks) {
            pending.add(spot);
        }

        @Override
        public void summonAbandoned(Spot spot, String lootId, AbandonReason reason) {
            abandoned.add(reason);
        }
    }

    /**
     * Runs the server's ticks until the future completes, as the scheduler hands the work back.
     */
    private <T> T join(CompletableFuture<T> future) {
        for (int tick = 0; tick < 200 && !future.isDone(); tick++) {
            server.getScheduler().performOneTick();
        }
        return future.getNow(null);
    }

    private Object field(String name) throws ReflectiveOperationException {
        Field field = RakunLootsPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }

    public static final class TestPlugin extends RakunLootsPlugin {

        public TestPlugin(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
            super(loader, description, dataFolder, file);
        }
    }

}
