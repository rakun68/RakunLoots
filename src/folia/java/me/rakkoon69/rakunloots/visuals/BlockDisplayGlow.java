package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.GlowColor;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glow as a real block display entity at the box's block, for Minecraft 1.19.4 and newer (ADR 0016). GlowingBlocks
 * draws a glow with an invisible fake shulker, whose hitbox is bigger than a player head and covers a chest, so a
 * player's right click and break hit the shulker and never reach the box. A display has no hitbox, so a click reaches
 * the block. It works on Spigot, Paper, and Folia, since it spawns an ordinary entity.
 *
 * <p>The display shows the box's placed block data and glows in the loot's colour through the glow colour override. It
 * is one entity every player sees, so this library is {@link #sharedByAll}. It is never persistent, so the server never
 * saves it with a chunk. It carries a scoreboard tag, and one left over in a chunk, such as by a crash that saved it
 * through another plugin, is removed when its chunk's entities load and in the chunks loaded at enable.
 *
 * <p>The display is matched to the block so it does not z-fight with it. It sits at the block's own corner, and is a
 * hair larger than the block around the block's centre: scale 1.002, translated by -0.001, so its faces are always in
 * front of the block's, never in the same plane. Light inside a solid block is dark, so it takes the brightest block
 * light and sky light around the block, taken once when it spawns.
 *
 * <p>Every display is spawned and removed in a task on the thread that owns the block, which on Folia is the region.
 * At plugin stop the plugin can no longer schedule, so a removal that cannot be scheduled removes the display where it
 * is called. A display that cannot be removed that way is not persistent, so it goes with the server.
 */
public final class BlockDisplayGlow implements GlowLibrary {

    /**
     * The scoreboard tag on every display this library spawns.
     */
    public static final String TAG = "rakunloots_glow";

    private static final float SCALE = 1.002f;
    private static final float OFFSET = -0.001f;
    private static final BlockFace[] AROUND = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST};

    private record Key(UUID worldKey, BlockPosition position) {
    }

    private final Plugin plugin;
    private final ServerAdapter server;
    private final Map<Key, BlockDisplay> displays = new ConcurrentHashMap<>();
    private final Listener cleanup = new Cleanup();

    /**
     * Registers the leftover cleanup and sweeps the chunks that are loaded now.
     */
    public BlockDisplayGlow(Plugin plugin, ServerAdapter server) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        plugin.getServer().getPluginManager().registerEvents(cleanup, plugin);
        sweepLoadedChunks();
    }

    @Override
    public boolean sharedByAll() {
        return true;
    }

    @Override
    public CompletableFuture<Void> show(UUID worldKey, BlockPosition position, UUID playerId, GlowColor color) {
        throw new UnsupportedOperationException("a block display glow is drawn for every player at once");
    }

    @Override
    public CompletableFuture<Void> hide(UUID worldKey, BlockPosition position, UUID playerId) {
        throw new UnsupportedOperationException("a block display glow is drawn for every player at once");
    }

    @Override
    public CompletableFuture<Void> showToAll(UUID worldKey, BlockPosition position, GlowColor color) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            server.atLocation(worldKey, position.x(), position.z()).run(() -> {
                try {
                    spawn(worldKey, position, color);
                    done.complete(null);
                } catch (Throwable failure) {
                    done.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            done.completeExceptionally(failure);
        }
        return done;
    }

    @Override
    public CompletableFuture<Void> hideFromAll(UUID worldKey, BlockPosition position) {
        Key key = new Key(worldKey, position);
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            server.atLocation(worldKey, position.x(), position.z()).run(() -> {
                try {
                    removeDisplay(key);
                    done.complete(null);
                } catch (Throwable failure) {
                    done.completeExceptionally(failure);
                }
            });
        } catch (Throwable cannotSchedule) {
            // The plugin is stopping: nothing can be scheduled, so remove it here.
            try {
                removeDisplay(key);
                done.complete(null);
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        }
        return done;
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(cleanup);
        for (Key key : List.copyOf(displays.keySet())) {
            try {
                removeDisplay(key);
            } catch (Throwable ignored) {
                // Off the owning thread on Folia the removal is refused, and the display is not persistent, so it
                // goes with the server.
            }
        }
    }

    private void spawn(UUID worldKey, BlockPosition position, GlowColor color) {
        World world = plugin.getServer().getWorld(worldKey);
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        if (block.getType().isAir()) {
            return;
        }
        // World#spawn without a consumer: the consumer overloads differ between server versions. The display is set up
        // in the same task it is added in, before any player's client is sent it.
        BlockDisplay display = world.spawn(new Location(world, position.x(), position.y(), position.z()),
                BlockDisplay.class);
        display.setBlock(block.getBlockData());
        display.setGlowing(true);
        display.setGlowColorOverride(Color.fromRGB(color.rgb()));
        display.setBrightness(brightnessAround(block));
        display.setTransformation(new Transformation(new Vector3f(OFFSET, OFFSET, OFFSET), new AxisAngle4f(),
                new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.addScoreboardTag(TAG);
        BlockDisplay replaced = displays.put(new Key(worldKey, position), display);
        if (replaced != null) {
            replaced.remove();
        }
    }

    private static Display.Brightness brightnessAround(Block block) {
        int blockLight = 0;
        int skyLight = 0;
        for (BlockFace face : AROUND) {
            Block next = block.getRelative(face);
            blockLight = Math.max(blockLight, next.getLightFromBlocks());
            skyLight = Math.max(skyLight, next.getLightFromSky());
        }
        return new Display.Brightness(blockLight, skyLight);
    }

    private void removeDisplay(Key key) {
        BlockDisplay display = displays.remove(key);
        if (display != null) {
            display.remove();
        }
    }

    private void sweepLoadedChunks() {
        try {
            for (World world : plugin.getServer().getWorlds()) {
                UUID worldKey = world.getUID();
                server.loadedChunks(worldKey).thenAccept(chunks -> {
                    for (ChunkPosition chunk : chunks) {
                        sweepChunk(worldKey, chunk);
                    }
                });
            }
        } catch (Throwable ignored) {
            // A sweep is best effort: a display is not persistent, so a leftover is rare, and the chunk load cleanup
            // still runs for every chunk that loads later.
        }
    }

    private void sweepChunk(UUID worldKey, ChunkPosition chunk) {
        try {
            server.atLocation(worldKey, chunk.chunkX() << 4, chunk.chunkZ() << 4).run(() -> {
                World world = plugin.getServer().getWorld(worldKey);
                if (world != null && world.isChunkLoaded(chunk.chunkX(), chunk.chunkZ())) {
                    removeLeftovers(List.of(world.getChunkAt(chunk.chunkX(), chunk.chunkZ()).getEntities()));
                }
            });
        } catch (Throwable ignored) {
            // As above.
        }
    }

    private static void removeLeftovers(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof BlockDisplay && entity.getScoreboardTags().contains(TAG)) {
                entity.remove();
            }
        }
    }

    /**
     * Removes the leftover displays of a chunk whose entities just loaded.
     */
    private static final class Cleanup implements Listener {

        @EventHandler
        public void onEntitiesLoad(EntitiesLoadEvent event) {
            removeLeftovers(event.getEntities());
        }
    }
}
