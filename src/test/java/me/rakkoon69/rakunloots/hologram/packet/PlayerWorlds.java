package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.SharedInventory;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A server adapter with only what the viewer tracker uses: player position snapshots of several worlds, set by the
 * test, and a global scheduler the test steps by hand. Every other call throws, since the tracker never makes it. The
 * scheduler refuses a delay or period below 1, as the real ones do. Snapshots can be held back and released later, to
 * stand for Folia gathering positions on each player's thread while events go on.
 */
final class PlayerWorlds implements ServerAdapter {

    private final Map<UUID, Map<UUID, PlayerPosition>> worlds = new HashMap<>();
    private final List<UUID> snapshotsAsked = new ArrayList<>();
    private final List<Runnable> held = new ArrayList<>();
    private final SteppedScheduler global = new SteppedScheduler();
    private boolean holdSnapshots;

    /**
     * Puts a player in a world at a position, taking them out of any other world.
     */
    void stand(UUID playerId, UUID worldKey, double x, double y, double z) {
        leave(playerId);
        worlds.computeIfAbsent(worldKey, key -> new HashMap<>()).put(playerId, new PlayerPosition(playerId, x, y, z));
    }

    void leave(UUID playerId) {
        worlds.values().forEach(players -> players.remove(playerId));
    }

    /**
     * Each world a snapshot was asked for, in order.
     */
    List<UUID> snapshotsAsked() {
        return List.copyOf(snapshotsAsked);
    }

    /**
     * Takes each later snapshot when asked, but completes it only on {@link #releaseSnapshots}.
     */
    void holdSnapshots() {
        holdSnapshots = true;
    }

    void releaseSnapshots() {
        holdSnapshots = false;
        List<Runnable> release = List.copyOf(held);
        held.clear();
        release.forEach(Runnable::run);
    }

    SteppedScheduler globalScheduler() {
        return global;
    }

    @Override
    public CompletableFuture<List<PlayerPosition>> playerPositions(UUID worldKey) {
        snapshotsAsked.add(worldKey);
        List<PlayerPosition> snapshot = List.copyOf(worlds.getOrDefault(worldKey, Map.of()).values());
        if (!holdSnapshots) {
            return CompletableFuture.completedFuture(snapshot);
        }
        CompletableFuture<List<PlayerPosition>> later = new CompletableFuture<>();
        held.add(() -> later.complete(snapshot));
        return later;
    }

    @Override
    public TaskScheduler global() {
        return global;
    }

    @Override
    public CompletableFuture<Optional<ChunkView>> readChunk(UUID worldKey, int chunkX, int chunkZ,
                                                            UnloadedChunkPolicy policy) {
        throw unused();
    }

    @Override
    public CompletableFuture<Set<ChunkPosition>> loadedChunks(UUID worldKey) {
        throw unused();
    }

    @Override
    public CompletableFuture<Optional<BlockPosition>> worldSpawn(UUID worldKey) {
        throw unused();
    }

    @Override
    public CompletableFuture<List<LoadedWorld>> loadedWorlds() {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> setBlock(UUID worldKey, BlockPosition position, String blockId,
                                               Optional<String> headData) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> replaceBlock(UUID worldKey, BlockPosition position, String expectedBlockId,
                                                   String blockId, Optional<String> headData) {
        throw unused();
    }

    @Override
    public CompletableFuture<Optional<String>> readBlock(UUID worldKey, BlockPosition position) {
        throw unused();
    }

    @Override
    public Optional<String> readBlockHere(UUID worldKey, BlockPosition position) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> removeBlock(UUID worldKey, BlockPosition position, String blockId) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> removeBlockLoadingChunk(UUID worldKey, BlockPosition position, String blockId) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> holdChunk(UUID worldKey, int chunkX, int chunkZ) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> releaseChunk(UUID worldKey, int chunkX, int chunkZ) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> dropStacks(UUID worldKey, BlockPosition position, List<ItemStack> stacks) {
        throw unused();
    }

    @Override
    public Optional<ItemStack> blockItem(String blockId, Optional<String> headData) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> drawParticles(UUID worldKey, BlockPosition owner,
                                                    List<me.rakkoon69.rakunloots.server.ParticleDraw> draws) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> sendMessage(UUID playerId, String message) {
        throw unused();
    }

    @Override
    public CompletableFuture<Integer> inventoryRoom(UUID playerId, ItemStack item) {
        throw unused();
    }

    @Override
    public CompletableFuture<Integer> giveItems(UUID playerId, ItemStack item, int amount) {
        throw unused();
    }

    @Override
    public SharedInventory createSharedInventory(String title, int size, Map<Integer, ItemStack> contents) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> openSharedInventory(UUID playerId, SharedInventory inventory) {
        throw unused();
    }

    @Override
    public CompletableFuture<Void> closeSharedInventory(SharedInventory inventory, Set<UUID> viewers) {
        throw unused();
    }

    @Override
    public me.rakkoon69.rakunloots.server.MenuHandle createMenu(UUID playerId, String title, int size,
                                                               Map<Integer, ItemStack> contents) {
        throw unused();
    }

    @Override
    public CompletableFuture<Optional<me.rakkoon69.rakunloots.server.MenuHandle>> openMenu(
            UUID playerId, me.rakkoon69.rakunloots.server.MenuHandle menu,
            me.rakkoon69.rakunloots.server.MenuHandle expectedOld) {
        throw unused();
    }

    @Override
    public boolean isPhysicalOpenInProgress(UUID playerId) {
        return false;
    }

    @Override
    public CompletableFuture<Void> updateMenu(me.rakkoon69.rakunloots.server.MenuHandle menu,
                                              Map<Integer, ItemStack> contents) {
        throw unused();
    }

    @Override
    public CompletableFuture<Void> closeMenu(me.rakkoon69.rakunloots.server.MenuHandle menu) {
        throw unused();
    }

    @Override
    public CompletableFuture<Void> runOnPlayerNextTick(UUID playerId, Runnable action) {
        throw unused();
    }

    @Override
    public CompletableFuture<Boolean> playSound(UUID playerId, String key, double volume, double pitch) {
        throw unused();
    }

    @Override
    public TaskScheduler atLocation(UUID worldKey, int blockX, int blockZ) {
        throw unused();
    }

    @Override
    public TaskScheduler forPlayer(UUID playerId) {
        throw unused();
    }

    @Override
    public TaskScheduler async() {
        throw unused();
    }

    private static UnsupportedOperationException unused() {
        return new UnsupportedOperationException("the viewer tracker never calls this");
    }

    /**
     * Runs tasks on the test thread when the test steps ticks.
     */
    static final class SteppedScheduler implements TaskScheduler {

        private final List<Scheduled> pending = new ArrayList<>();
        private long tick;

        @Override
        public TaskHandle run(Runnable task) {
            return schedule(task, 1, 0);
        }

        @Override
        public TaskHandle runLater(Runnable task, long delayTicks) {
            atLeastOne(delayTicks);
            return schedule(task, delayTicks, 0);
        }

        @Override
        public TaskHandle repeat(Runnable task, long delayTicks, long periodTicks) {
            atLeastOne(delayTicks);
            atLeastOne(periodTicks);
            return schedule(task, delayTicks, periodTicks);
        }

        /**
         * Tasks waiting to run, repeating ones included.
         */
        int pending() {
            return (int) pending.stream().filter(scheduled -> !scheduled.cancelled).count();
        }

        void stepTicks(long ticks) {
            for (long step = 0; step < ticks; step++) {
                tick++;
                for (Scheduled scheduled : List.copyOf(pending)) {
                    if (scheduled.cancelled || scheduled.nextDue > tick) {
                        continue;
                    }
                    scheduled.task.run();
                    if (scheduled.period > 0) {
                        scheduled.nextDue = tick + scheduled.period;
                    } else {
                        scheduled.cancelled = true;
                    }
                }
                pending.removeIf(scheduled -> scheduled.cancelled);
            }
        }

        private TaskHandle schedule(Runnable task, long delay, long period) {
            Scheduled scheduled = new Scheduled(task, tick + delay, period);
            pending.add(scheduled);
            return () -> scheduled.cancelled = true;
        }

        private static void atLeastOne(long ticks) {
            if (ticks < 1) {
                throw new IllegalArgumentException("ticks must be at least 1, got " + ticks);
            }
        }

        private static final class Scheduled {
            private final Runnable task;
            private final long period;
            private long nextDue;
            private boolean cancelled;

            private Scheduled(Runnable task, long nextDue, long period) {
                this.task = task;
                this.nextDue = nextDue;
                this.period = period;
            }
        }
    }
}
