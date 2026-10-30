package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.server.FoliaFakes;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block display glow over hand fakes of the Bukkit world, since MockBukkit 1.18 has no block display. A fake
 * display records each setter it was called with, and a fake world rejects a spawn of anything but a display class, as
 * a server does. The server adapter is the real fake of the summon tests, whose scheduler runs a task only when the test
 * steps a tick, so a call that does its work on the calling thread instead of on the block's thread shows.
 */
class BlockDisplayGlowTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
    private static final BlockPosition BOX = new BlockPosition(18, 64, -35);

    private final FakeServerAdapter adapter = new FakeServerAdapter(WORLD);
    private final List<String> atLocationCalls = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<Display> spawned = new ArrayList<>();
    private final Map<Display, Map<String, Object>> setters = new HashMap<>();
    private final Map<Display, Set<String>> tags = new HashMap<>();
    private final Set<Display> removed = new HashSet<>();
    private final BlockData barrel = FoliaFakes.fake(BlockData.class, "barrel data",
            (proxy, method, args) -> FoliaFakes.UNHANDLED);
    private Material boxMaterial = Material.BARREL;
    private boolean refuseScheduling;
    private World world;
    private Plugin plugin;
    private ServerAdapter server;
    private Chunk loadedChunk;
    private final List<Entity> chunkEntities = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = FoliaFakes.fake(World.class, "world", this::worldCall);
        Server bukkit = FoliaFakes.fake(Server.class, "server", (proxy, method, args) -> switch (method.getName()) {
            case "getWorld" -> WORLD.equals(args[0]) ? world : null;
            case "getWorlds" -> List.of(world);
            case "getPluginManager" -> FoliaFakes.fake(PluginManager.class, "plugin manager",
                    (manager, call, callArgs) -> {
                        if (call.getName().equals("registerEvents")) {
                            listeners.add((Listener) callArgs[0]);
                            return null;
                        }
                        return FoliaFakes.UNHANDLED;
                    });
            default -> FoliaFakes.UNHANDLED;
        });
        plugin = FoliaFakes.fake(Plugin.class, "plugin", (proxy, method, args) ->
                method.getName().equals("getServer") ? bukkit : FoliaFakes.UNHANDLED);
        server = (ServerAdapter) java.lang.reflect.Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("atLocation")) {
                        atLocationCalls.add(args[0] + " " + args[1] + " " + args[2]);
                        if (refuseScheduling) {
                            throw new IllegalStateException("Plugin attempted to register task while disabled");
                        }
                    }
                    try {
                        return method.invoke(adapter, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private BlockDisplayGlow glow() {
        return new BlockDisplayGlow(plugin, server);
    }

    private void tick() {
        adapter.globalScheduler().stepTicks(1);
    }

    private Object worldCall(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getUID":
                return WORLD;
            case "getBlockAt":
                return block(boxMaterial, 0);
            case "isChunkLoaded":
                return loadedChunk != null;
            case "getChunkAt":
                return loadedChunk;
            case "spawn": {
                // World#spawn(Location, Class): a server refuses a class it cannot spawn.
                Class<?> type = (Class<?>) args[1];
                if (!Display.class.isAssignableFrom(type)) {
                    throw new IllegalArgumentException("cannot spawn " + type);
                }
                BlockDisplay display = display(new Location(world, ((Location) args[0]).getX(),
                        ((Location) args[0]).getY(), ((Location) args[0]).getZ()));
                spawned.add(display);
                return display;
            }
            default:
                return FoliaFakes.UNHANDLED;
        }
    }

    /**
     * A block of the given material whose neighbours all have the given light, so the display's brightness is checked.
     */
    private Block block(Material material, int neighbourBlockLight) {
        return FoliaFakes.fake(Block.class, "block", (proxy, method, args) -> switch (method.getName()) {
            case "getType" -> material;
            case "getBlockData" -> barrel;
            case "getRelative" -> neighbour((BlockFace) args[0], neighbourBlockLight);
            case "getLightFromBlocks", "getLightFromSky" -> (byte) 0;
            default -> FoliaFakes.UNHANDLED;
        });
    }

    private Block neighbour(BlockFace face, int blockLight) {
        // Only the block above sees the sky, and only the block east holds a torch.
        return FoliaFakes.fake(Block.class, "neighbour " + face, (proxy, method, args) -> switch (method.getName()) {
            case "getLightFromBlocks" -> (byte) (face == BlockFace.EAST ? 9 : blockLight);
            case "getLightFromSky" -> (byte) (face == BlockFace.UP ? 15 : 0);
            default -> FoliaFakes.UNHANDLED;
        });
    }

    private BlockDisplay display(Location location) {
        BlockDisplay[] self = new BlockDisplay[1];
        Map<String, Object> calls = new HashMap<>();
        Set<String> displayTags = new HashSet<>();
        self[0] = FoliaFakes.fake(BlockDisplay.class, "display", (proxy, method, args) -> {
            switch (method.getName()) {
                case "setBlock", "setGlowing", "setGlowColorOverride", "setPersistent", "setBrightness",
                     "setTransformation", "setInvulnerable", "setGravity":
                    calls.put(method.getName(), args[0]);
                    return null;
                case "addScoreboardTag":
                    displayTags.add((String) args[0]);
                    return true;
                case "getScoreboardTags":
                    return displayTags;
                case "getLocation":
                    return location;
                case "remove":
                    removed.add(self[0]);
                    return null;
                case "isValid":
                    return !removed.contains(self[0]);
                default:
                    return FoliaFakes.UNHANDLED;
            }
        });
        setters.put(self[0], calls);
        tags.put(self[0], displayTags);
        return self[0];
    }

    /**
     * Acceptance: glow on 1.19.4 and newer is a real block display at the box's block, showing the box's placed block
     * data, glowing with the loot's glow colour, never saved with the chunk, and tagged so a leftover is found. It is
     * made on the thread that owns the block, not on the calling thread.
     */
    @Test
    void aBlockDisplayIsSpawnedGlowingWithTheColourAndTheBoxsBlockDataOnTheBlocksThread() {
        BlockDisplayGlow glow = glow();
        assertTrue(glow.sharedByAll());

        var done = glow.showToAll(WORLD, BOX, GlowColor.GOLD);

        assertEquals(List.of(), spawned, "nothing is spawned before the block's thread runs");
        tick();
        done.join();
        assertEquals(List.of(WORLD + " 18 -35"), atLocationCalls.subList(0, 1));
        Display display = spawned.get(0);
        Map<String, Object> set = setters.get(display);
        assertEquals(1, spawned.size());
        assertSame(barrel, set.get("setBlock"));
        assertEquals(true, set.get("setGlowing"));
        assertEquals(Color.fromRGB(0xFFAA00), set.get("setGlowColorOverride"));
        assertEquals(false, set.get("setPersistent"), "a display is never saved with the chunk");
        assertEquals(Set.of(BlockDisplayGlow.TAG), tags.get(display));
        assertEquals(new Location(world, 18, 64, -35).toVector(), display.getLocation().toVector(),
                "at the block's own corner, with no offset");
    }

    /**
     * Acceptance: the display matches the block, so it does not z-fight visibly: it is a hair larger than the block
     * around the block's centre, so its faces always win the depth test, and it is lit by the light around the block,
     * since light inside a solid block is dark.
     */
    @Test
    void theDisplayIsAHairLargerThanTheBlockAndLitByTheLightAroundIt() {
        BlockDisplayGlow glow = glow();

        glow.showToAll(WORLD, BOX, GlowColor.RED);
        tick();

        Map<String, Object> set = setters.get(spawned.get(0));
        Transformation transformation = (Transformation) set.get("setTransformation");
        assertEquals(1.002f, transformation.getScale().x(), 1e-6);
        assertEquals(1.002f, transformation.getScale().y(), 1e-6);
        assertEquals(1.002f, transformation.getScale().z(), 1e-6);
        assertEquals(-0.001f, transformation.getTranslation().x(), 1e-6);
        assertEquals(-0.001f, transformation.getTranslation().y(), 1e-6);
        assertEquals(-0.001f, transformation.getTranslation().z(), 1e-6);
        Display.Brightness brightness = (Display.Brightness) set.get("setBrightness");
        assertEquals(9, brightness.getBlockLight(), "the brightest block light around the block");
        assertEquals(15, brightness.getSkyLight(), "the brightest sky light around the block");
    }

    /**
     * Acceptance: the display is removed when the glow stops, on the block's thread, and a block shown again gets a
     * new one.
     */
    @Test
    void hidingTheGlowRemovesTheDisplayOnTheBlocksThread() {
        BlockDisplayGlow glow = glow();
        glow.showToAll(WORLD, BOX, GlowColor.GOLD);
        tick();
        Display first = spawned.get(0);

        var done = glow.hideFromAll(WORLD, BOX);

        assertFalse(removed.contains(first), "removed on the block's thread, not the calling one");
        tick();
        assertTrue(done.isDone());
        assertTrue(removed.contains(first));

        glow.showToAll(WORLD, BOX, GlowColor.GOLD);
        tick();
        assertEquals(2, spawned.size());
        assertFalse(removed.contains(spawned.get(1)));
    }

    /**
     * A hide queued behind a show that has not run yet still removes what the show makes, since both run in order on the
     * block's thread.
     */
    @Test
    void aHideRightAfterAShowLeavesNoDisplay() {
        BlockDisplayGlow glow = glow();

        glow.showToAll(WORLD, BOX, GlowColor.GOLD);
        glow.hideFromAll(WORLD, BOX);
        tick();

        assertEquals(1, spawned.size());
        assertTrue(removed.contains(spawned.get(0)));
    }

    /**
     * Acceptance: close, at plugin stop, removes every display that is left.
     */
    @Test
    void closeRemovesEveryDisplayThatIsLeft() {
        BlockDisplayGlow glow = glow();
        glow.showToAll(WORLD, BOX, GlowColor.GOLD);
        glow.showToAll(WORLD, new BlockPosition(1, 70, 1), GlowColor.GREEN);
        tick();

        glow.close();

        assertEquals(new HashSet<>(spawned), removed);
    }

    /**
     * At plugin stop the plugin may no longer schedule, so a hide that cannot be scheduled removes the display where it
     * stands, and does not fail.
     */
    @Test
    void aHideThatCannotBeScheduledBecauseThePluginIsStoppingRemovesTheDisplayAtOnce() {
        BlockDisplayGlow glow = glow();
        glow.showToAll(WORLD, BOX, GlowColor.GOLD);
        tick();
        refuseScheduling = true;

        var done = glow.hideFromAll(WORLD, BOX);

        assertTrue(done.isDone());
        assertFalse(done.isCompletedExceptionally());
        assertTrue(removed.contains(spawned.get(0)));
    }

    /**
     * A display that cannot be made fails the future, so the service warns once and the box goes on without glow.
     */
    @Test
    void aShowThatCannotSpawnFailsItsFuture() {
        boxMaterial = Material.BARREL;
        world = FoliaFakes.fake(World.class, "world", (proxy, method, args) -> switch (method.getName()) {
            case "getUID" -> WORLD;
            case "getBlockAt" -> block(Material.BARREL, 0);
            case "spawn" -> throw new IllegalStateException("not on this region's thread");
            default -> FoliaFakes.UNHANDLED;
        });
        Server bukkit = FoliaFakes.fake(Server.class, "server", (proxy, method, args) -> switch (method.getName()) {
            case "getWorld" -> world;
            case "getWorlds" -> List.of();
            case "getPluginManager" -> FoliaFakes.fake(PluginManager.class, "plugin manager",
                    (manager, call, callArgs) -> call.getName().equals("registerEvents") ? null : FoliaFakes.UNHANDLED);
            default -> FoliaFakes.UNHANDLED;
        });
        plugin = FoliaFakes.fake(Plugin.class, "plugin", (proxy, method, args) ->
                method.getName().equals("getServer") ? bukkit : FoliaFakes.UNHANDLED);
        BlockDisplayGlow glow = new BlockDisplayGlow(plugin, server);

        var done = glow.showToAll(WORLD, BOX, GlowColor.GOLD);
        tick();

        CompletionException failure = assertThrows(CompletionException.class, done::join);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    /**
     * A box whose block is gone, such as air, glows nothing and it is not a failure.
     */
    @Test
    void aBoxWhoseBlockIsGoneGlowsNothingWithoutFailing() {
        boxMaterial = Material.AIR;
        BlockDisplayGlow glow = glow();

        var done = glow.showToAll(WORLD, BOX, GlowColor.GOLD);
        tick();

        assertTrue(done.isDone());
        assertFalse(done.isCompletedExceptionally());
        assertEquals(List.of(), spawned);
    }

    /**
     * The per player calls are not supported, since a display is one entity every player sees.
     */
    @Test
    void theCallsForOnePlayerAreRefused() {
        BlockDisplayGlow glow = glow();

        assertThrows(UnsupportedOperationException.class,
                () -> glow.show(WORLD, BOX, UUID.randomUUID(), GlowColor.GOLD));
        assertThrows(UnsupportedOperationException.class, () -> glow.hide(WORLD, BOX, UUID.randomUUID()));
    }

    /**
     * Acceptance: a display left behind by a crash is removed when its chunk's entities load: one tagged as the plugin's
     * and a display. A display of another plugin, and a tagged entity that is no display, stay.
     */
    @Test
    void aLeftoverDisplayIsRemovedWhenItsChunkLoads() {
        glow();
        BlockDisplay leftover = display(new Location(world, 5, 65, 5));
        tags.get(leftover).add(BlockDisplayGlow.TAG);
        BlockDisplay other = display(new Location(world, 6, 65, 5));
        tags.get(other).add("someone_elses");
        Entity notDisplay = FoliaFakes.fake(Entity.class, "tagged entity", (proxy, method, args) ->
                method.getName().equals("getScoreboardTags") ? Set.of(BlockDisplayGlow.TAG) : FoliaFakes.UNHANDLED);
        Chunk chunk = FoliaFakes.fake(Chunk.class, "chunk", (proxy, method, args) ->
                method.getName().equals("getWorld") ? world : FoliaFakes.UNHANDLED);

        assertEquals(1, listeners.size());
        invokeLoadHandler(listeners.get(0), new EntitiesLoadEvent(chunk, List.of(leftover, other, notDisplay)));

        assertEquals(Set.of(leftover), removed);
    }

    /**
     * Acceptance: a display left in a chunk that is already loaded when the plugin enables is removed too, on the
     * thread that owns the chunk.
     */
    @Test
    void aLeftoverDisplayInAChunkLoadedAtEnableIsRemovedOnTheChunksThread() {
        BlockDisplay leftover = display(new Location(world, 40, 65, 40));
        tags.get(leftover).add(BlockDisplayGlow.TAG);
        BlockDisplay other = display(new Location(world, 41, 65, 40));
        chunkEntities.add(leftover);
        chunkEntities.add(other);
        loadedChunk = FoliaFakes.fake(Chunk.class, "chunk", (proxy, method, args) ->
                method.getName().equals("getEntities") ? chunkEntities.toArray(new Entity[0]) : FoliaFakes.UNHANDLED);
        adapter.putChunk(new ChunkView(WORLD, 2, 2, 0, 256, (x, y, z) -> "minecraft:air"), true, true);

        glow();

        assertEquals(Set.of(), removed, "the sweep runs on the chunk's thread");
        tick();
        assertEquals(Set.of(leftover), removed);
        assertTrue(atLocationCalls.contains(WORLD + " 32 32"), "scheduled at the chunk's own block");
    }

    private static void invokeLoadHandler(Listener listener, EntitiesLoadEvent event) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(org.bukkit.event.EventHandler.class)
                    && method.getParameterTypes()[0] == EntitiesLoadEvent.class) {
                try {
                    method.setAccessible(true);
                    method.invoke(listener, event);
                    return;
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
        }
        throw new AssertionError("no handler for EntitiesLoadEvent");
    }
}
