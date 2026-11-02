package me.rakkoon69.rakunloots.server.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loads for plain Spigot, which calls no Paper-only method. Spigot loads chunks on the main thread only, so loads wait
 * in a queue, and at most one of them runs per tick.
 */
final class SpigotChunkLoader implements ChunkLoader {

    private final Plugin plugin;

    /**
     * Loads waiting to run, oldest first. Touched on the main thread only.
     */
    private final Queue<PendingLoad> pendingLoads = new ArrayDeque<>();

    /**
     * Whether a main-thread task that runs the next pending load is scheduled. Touched on the main thread only.
     */
    private boolean loadScheduled;

    SpigotChunkLoader(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Joins the load queue. One queued load runs per tick, in the order they joined, each in a main-thread task of
     * its own that looks the world up again and loads the chunk as asked. The future completes in that task.
     */
    @Override
    public CompletableFuture<Optional<Chunk>> load(World world, int chunkX, int chunkZ, boolean generate) {
        PendingLoad load = new PendingLoad(world.getUID(), chunkX, chunkZ, generate);
        pendingLoads.add(load);
        scheduleNextLoad();
        return load.result;
    }

    /**
     * Schedules the task that runs the next pending load on a later tick, unless it is scheduled already or nothing
     * is pending. When the task cannot be scheduled, as for a disabled plugin, every pending load fails.
     */
    private void scheduleNextLoad() {
        if (loadScheduled || pendingLoads.isEmpty()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, this::runNextLoad);
            loadScheduled = true;
        } catch (RuntimeException notScheduled) {
            for (PendingLoad load = pendingLoads.poll(); load != null; load = pendingLoads.poll()) {
                load.result.completeExceptionally(notScheduled);
            }
        }
    }

    private void runNextLoad() {
        loadScheduled = false;
        PendingLoad load = pendingLoads.poll();
        if (load != null) {
            try {
                load.result.complete(loadOnMainThread(load));
            } catch (Throwable failure) {
                load.result.completeExceptionally(failure);
            }
        }
        scheduleNextLoad();
    }

    private static Optional<Chunk> loadOnMainThread(PendingLoad load) {
        World world = Bukkit.getWorld(load.worldKey);
        if (world == null) {
            return Optional.empty();
        }
        if (!world.isChunkLoaded(load.chunkX, load.chunkZ)
                && !loadAsAsked(world, load.chunkX, load.chunkZ, load.generate)) {
            return Optional.empty();
        }
        return Optional.of(world.getChunkAt(load.chunkX, load.chunkZ));
    }

    /**
     * Loads an unloaded chunk, generating it only when asked.
     *
     * @return whether the chunk is loaded now
     */
    private static boolean loadAsAsked(World world, int chunkX, int chunkZ, boolean generate) {
        return generate
                ? world.loadChunk(chunkX, chunkZ, true)
                : world.isChunkGenerated(chunkX, chunkZ) && world.loadChunk(chunkX, chunkZ, false);
    }

    /**
     * One chunk load waiting its turn. A plain holder rather than a record, which would add equality and text that
     * nothing here uses to the shipped jar.
     */
    private static final class PendingLoad {

        final UUID worldKey;
        final int chunkX;
        final int chunkZ;
        final boolean generate;
        final CompletableFuture<Optional<Chunk>> result = new CompletableFuture<>();

        PendingLoad(UUID worldKey, int chunkX, int chunkZ, boolean generate) {
            this.worldKey = worldKey;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.generate = generate;
        }
    }
}
