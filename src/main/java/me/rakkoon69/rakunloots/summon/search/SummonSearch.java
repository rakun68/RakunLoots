package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.summon.SummonSearchSettings;
import me.rakkoon69.rakunloots.config.WorldSettings;
import me.rakkoon69.rakunloots.config.summon.HeightRange;
import me.rakkoon69.rakunloots.config.summon.SummonRing;
import me.rakkoon69.rakunloots.config.summon.SummonRules;
import me.rakkoon69.rakunloots.lootbox.ChunkHolds;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Finds a summon spot by searching columns according to world and loot rules.
 */
public final class SummonSearch implements SpotFinder {

    private final ServerAdapter adapter;
    private volatile ConfigSnapshot config;
    private final SearchClock clock;
    private final Random random;
    private final IdleTracker idleTracker;

    public SummonSearch(ServerAdapter adapter, ConfigSnapshot config, SearchClock clock, Random random) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.idleTracker = new IdleTracker(adapter, clock, config);
    }

    public void start() {
        idleTracker.start();
    }

    public void stop() {
        idleTracker.stop();
    }

    /**
     * Switches this search to a reloaded snapshot and applies its idle settings on the next sample.
     */
    public void reload(ConfigSnapshot config) {
        this.config = Objects.requireNonNull(config, "config");
        idleTracker.reload(config);
    }

    public IdleTracker idleTracker() {
        return idleTracker;
    }

    @Override
    public CompletableFuture<SummonResult> serverSummon(
            UUID worldKey,
            String lootId,
            OccupiedCheck occupied
    ) {
        return serverSummon(worldKey, lootId, occupied, ChunkHolds.NONE);
    }

    @Override
    public CompletableFuture<SummonResult> serverSummon(
            UUID worldKey,
            String lootId,
            OccupiedCheck occupied,
            ChunkHolds holds
    ) {
        Objects.requireNonNull(holds, "holds");
        return schedule(() -> {
            ResolvedSearch resolved = resolve(worldKey, lootId);
            if (resolved.miss != null) {
                return SummonResult.miss(resolved.miss);
            }
            idleTracker.trackWorld(worldKey, resolved.settings);
            Optional<BlockPosition> spawn = adapter.worldSpawn(worldKey).join();
            if (spawn.isEmpty()) {
                return SummonResult.miss(SearchMiss.WORLD_NOT_LOADED);
            }
            BlockPosition center = spawn.get();
            if (resolved.settings.mode() == SummonSearchSettings.Mode.SMART) {
                return smartSummon(worldKey, resolved, center.x(), center.z(), occupied, holds);
            }
            return columnSearch(
                    worldKey,
                    resolved,
                    center.x(),
                    center.z(),
                    resolved.settings.spawnRing(),
                    occupied,
                    holds
            );
        });
    }

    @Override
    public CompletableFuture<SummonResult> aroundCenter(
            UUID worldKey,
            String lootId,
            int x,
            int y,
            int z,
            OccupiedCheck occupied
    ) {
        return aroundCenter(worldKey, lootId, x, y, z, occupied, ChunkHolds.NONE);
    }

    @Override
    public CompletableFuture<SummonResult> aroundCenter(
            UUID worldKey,
            String lootId,
            int x,
            int y,
            int z,
            OccupiedCheck occupied,
            ChunkHolds holds
    ) {
        Objects.requireNonNull(holds, "holds");
        return schedule(() -> {
            ResolvedSearch resolved = resolve(worldKey, lootId);
            if (resolved.miss != null) {
                return SummonResult.miss(resolved.miss);
            }
            idleTracker.trackWorld(worldKey, resolved.settings);
            Optional<SummonRules> rules = clipVerticalOffset(resolved.rules, y);
            if (rules.isEmpty()) {
                return SummonResult.miss(SearchMiss.EMPTY_HEIGHT_RANGE);
            }
            ResolvedSearch clipped = new ResolvedSearch(resolved.settings, rules.get(), null);
            return columnSearch(worldKey, clipped, x, z, rules.get().playerRing(), occupied, holds);
        });
    }

    private SummonResult smartSummon(
            UUID worldKey,
            ResolvedSearch resolved,
            int spawnX,
            int spawnZ,
            OccupiedCheck occupied,
            ChunkHolds holds
    ) {
        List<PlayerPosition> players = adapter.playerPositions(worldKey).join();
        if (players.isEmpty()) {
            return switch (resolved.settings.whenAllAbsent()) {
                case SPAWN -> columnSearch(
                        worldKey,
                        resolved,
                        spawnX,
                        spawnZ,
                        resolved.settings.spawnRing(),
                        occupied,
                        holds
                );
                case NONE -> SummonResult.miss(SearchMiss.NO_PLAYERS);
            };
        }
        List<PlayerPosition> pickPool = idleTracker.smartCandidates(worldKey, players);
        double medianX = RingMath.medianX(pickPool);
        double medianZ = RingMath.medianZ(pickPool);
        SummonRing ring = resolved.settings.smartRing();
        return tryAttempts(
                worldKey,
                resolved.rules,
                resolved.settings.unloadedChunks(),
                ring,
                occupied,
                holds,
                () -> {
                    PlayerPosition player = RingMath.pickWeightedPlayer(
                            pickPool, medianX, medianZ, resolved.settings.pickBias(), random);
                    int centerX = (int) Math.floor(player.x());
                    int centerZ = (int) Math.floor(player.z());
                    double angle = RingMath.sampleSmartAngle(
                            player, medianX, medianZ, resolved.settings.medianPull(), random);
                    return new Sample(centerX, centerZ, RingMath.sampleColumn(centerX, centerZ, ring, angle, random));
                }
        );
    }

    private SummonResult columnSearch(
            UUID worldKey,
            ResolvedSearch resolved,
            int centerX,
            int centerZ,
            SummonRing ring,
            OccupiedCheck occupied,
            ChunkHolds holds
    ) {
        UnloadedChunkPolicy policy = resolved.settings.unloadedChunks();
        if (policy == UnloadedChunkPolicy.SKIP) {
            return skipSearch(worldKey, resolved, centerX, centerZ, ring, occupied);
        }
        return tryAttempts(
                worldKey,
                resolved.rules,
                policy,
                ring,
                occupied,
                holds,
                () -> new Sample(centerX, centerZ, RingMath.sampleColumn(centerX, centerZ, ring, random))
        );
    }

    /**
     * One attempt is one chunk read. The attempt tries its sampled column first, so where most columns hold a spot the
     * spot lands where it was sampled. When that column holds none, it scans every column of the chunk inside the ring
     * and picks one at random: the chunk is already read, and a sparse spot such as a pond is found once any attempt
     * lands in its chunk. A chunk scanned empty for one center is not scanned again in this search.
     */
    private SummonResult tryAttempts(
            UUID worldKey,
            SummonRules rules,
            UnloadedChunkPolicy policy,
            SummonRing ring,
            OccupiedCheck occupied,
            ChunkHolds holds,
            Supplier<Sample> nextSample
    ) {
        HeldReads reads = new HeldReads(worldKey, policy, holds);
        Set<ScannedChunk> scannedEmpty = new HashSet<>();
        try {
            int attempts = rules.attempts();
            for (int attempt = 0; attempt < attempts; attempt++) {
                Sample sample = nextSample.get();
                int chunkX = Math.floorDiv(sample.column().x(), 16);
                int chunkZ = Math.floorDiv(sample.column().z(), 16);
                ScannedChunk scanned = new ScannedChunk(chunkX, chunkZ, sample.centerX(), sample.centerZ());
                if (scannedEmpty.contains(scanned)) {
                    continue;
                }
                Optional<ChunkView> view = reads.read(chunkX, chunkZ);
                if (view.isEmpty()) {
                    scannedEmpty.add(scanned);
                    continue;
                }
                Optional<Spot> spot = ColumnCheck.find(rules, view.get(), Math.floorMod(sample.column().x(), 16),
                        Math.floorMod(sample.column().z(), 16), occupied, random);
                if (spot.isEmpty()) {
                    List<Spot> spots = scanChunkInRing(
                            rules, view.get(), sample.centerX(), sample.centerZ(), ring, occupied);
                    if (!spots.isEmpty()) {
                        spot = Optional.of(spots.get(random.nextInt(spots.size())));
                    }
                }
                if (spot.isPresent()) {
                    return SummonResult.hit(spot.get(), reads.keepOnly(spot.get()));
                }
                scannedEmpty.add(scanned);
            }
            return SummonResult.miss(SearchMiss.ATTEMPTS_USED_UP);
        } finally {
            reads.giveBack();
        }
    }

    private SummonResult skipSearch(
            UUID worldKey,
            ResolvedSearch resolved,
            int centerX,
            int centerZ,
            SummonRing ring,
            OccupiedCheck occupied
    ) {
        Set<ChunkPosition> loaded = adapter.loadedChunks(worldKey).join();
        if (loaded.isEmpty()) {
            return SummonResult.miss(SearchMiss.NO_LOADED_CHUNKS_IN_RING);
        }
        List<ChunkPosition> inRing = new ArrayList<>();
        for (ChunkPosition chunk : loaded) {
            if (RingMath.chunkOverlapsRing(centerX, centerZ, ring, chunk)) {
                inRing.add(chunk);
            }
        }
        if (inRing.isEmpty()) {
            return SummonResult.miss(SearchMiss.NO_LOADED_CHUNKS_IN_RING);
        }
        Collections.shuffle(inRing, random);
        int attempts = Math.min(resolved.rules.attempts(), inRing.size());
        Map<Long, Optional<ChunkView>> cache = new HashMap<>();
        for (int attempt = 0; attempt < attempts; attempt++) {
            ChunkPosition chunk = inRing.get(attempt);
            Optional<ChunkView> view = readChunk(worldKey, chunk.chunkX(), chunk.chunkZ(), UnloadedChunkPolicy.SKIP, cache);
            if (view.isEmpty()) {
                continue;
            }
            List<Spot> spots = scanChunkInRing(
                    resolved.rules, view.get(), centerX, centerZ, ring, occupied);
            if (!spots.isEmpty()) {
                Spot spot = spots.get(random.nextInt(spots.size()));
                return SummonResult.hit(spot);
            }
        }
        return SummonResult.miss(SearchMiss.ATTEMPTS_USED_UP);
    }

    private List<Spot> scanChunkInRing(
            SummonRules rules,
            ChunkView view,
            int centerX,
            int centerZ,
            SummonRing ring,
            OccupiedCheck occupied
    ) {
        List<Spot> spots = new ArrayList<>();
        int baseX = view.chunkX() * 16;
        int baseZ = view.chunkZ() * 16;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                if (!RingMath.inRing(centerX, centerZ, worldX, worldZ, ring)) {
                    continue;
                }
                ColumnCheck.find(rules, view, localX, localZ, occupied, random).ifPresent(spots::add);
            }
        }
        return spots;
    }

    private Optional<ChunkView> readChunk(
            UUID worldKey,
            int chunkX,
            int chunkZ,
            UnloadedChunkPolicy policy,
            Map<Long, Optional<ChunkView>> cache
    ) {
        long key = chunkKey(chunkX, chunkZ);
        Optional<ChunkView> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<ChunkView> view = adapter.readChunk(worldKey, chunkX, chunkZ, policy).join();
        cache.put(key, view);
        return view;
    }

    /**
     * The chunk reads of one search under {@code load} or {@code generate}. Each chunk the search reads is held through
     * the lifecycle's count, so it cannot unload between the read and the summon that places a box there (ADR 0015).
     * The hold is asked for before the read, so a generated chunk is loaded and kept by it; a chunk that is not
     * generated yet is held right after the read that generated it. A chunk with no view gives its hold back at once.
     * When the search ends, {@link #keepOnly} leaves the returned spot's hold with the caller, and
     * {@link #giveBack} returns every other hold, whatever the outcome.
     */
    private final class HeldReads {

        private final UUID worldKey;
        private final UnloadedChunkPolicy policy;
        private final ChunkHolds holds;
        private final Map<Long, Optional<ChunkView>> cache = new HashMap<>();
        private final Set<ChunkPosition> held = new HashSet<>();

        private HeldReads(UUID worldKey, UnloadedChunkPolicy policy, ChunkHolds holds) {
            this.worldKey = worldKey;
            this.policy = policy;
            this.holds = holds;
        }

        private Optional<ChunkView> read(int chunkX, int chunkZ) {
            long key = chunkKey(chunkX, chunkZ);
            Optional<ChunkView> cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            Optional<ChunkView> view;
            if (policy == UnloadedChunkPolicy.SKIP) {
                view = adapter.readChunk(worldKey, chunkX, chunkZ, policy).join();
            } else {
                boolean holding = holds.take(worldKey, chunkX, chunkZ).join();
                view = adapter.readChunk(worldKey, chunkX, chunkZ, policy).join();
                if (view.isPresent() && !holding) {
                    holding = holds.take(worldKey, chunkX, chunkZ).join();
                }
                if (holding && view.isPresent()) {
                    held.add(new ChunkPosition(chunkX, chunkZ));
                } else if (holding) {
                    holds.give(worldKey, chunkX, chunkZ);
                }
            }
            cache.put(key, view);
            return view;
        }

        /**
         * Takes the spot's chunk out of what this search gives back.
         *
         * @return whether the caller now owns a hold on that chunk
         */
        private boolean keepOnly(Spot spot) {
            return held.remove(new ChunkPosition(spot.x() >> 4, spot.z() >> 4));
        }

        private void giveBack() {
            for (ChunkPosition chunk : held) {
                holds.give(worldKey, chunk.chunkX(), chunk.chunkZ());
            }
            held.clear();
        }
    }

    private Optional<SummonRules> clipVerticalOffset(SummonRules rules, int centerY) {
        if (rules.verticalOffset().isEmpty()) {
            return Optional.of(rules);
        }
        int offset = rules.verticalOffset().getAsInt();
        HeightRange range = rules.heightRange();
        int min = Math.max(range.min(), centerY - offset);
        int max = Math.min(range.max(), centerY + offset);
        if (min > max) {
            return Optional.empty();
        }
        return Optional.of(rules.withHeightRange(new HeightRange(min, max)));
    }

    private ResolvedSearch resolve(UUID worldKey, String lootId) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(lootId, "lootId");
        Optional<String> worldName = worldName(worldKey);
        if (worldName.isEmpty()) {
            return ResolvedSearch.miss(SearchMiss.WORLD_NOT_LOADED);
        }
        // One read, so a reload between lookups cannot mix two snapshots.
        ConfigSnapshot snapshot = config;
        Optional<SummonRules> rules = snapshot.summonRules(lootId);
        if (snapshot.loot(lootId).isEmpty() || rules.isEmpty()) {
            return ResolvedSearch.miss(SearchMiss.UNKNOWN_LOOT);
        }
        SummonSearchSettings settings = snapshot.world(worldName.get())
                .map(WorldSettings::summonSearch)
                .orElse(SummonSearchSettings.defaults());
        return new ResolvedSearch(settings, rules.get(), null);
    }

    private Optional<String> worldName(UUID worldKey) {
        for (LoadedWorld world : adapter.loadedWorlds().join()) {
            if (world.key().equals(worldKey)) {
                return Optional.of(world.name());
            }
        }
        return Optional.empty();
    }

    private CompletableFuture<SummonResult> schedule(Callable<SummonResult> work) {
        CompletableFuture<SummonResult> future = new CompletableFuture<>();
        adapter.async().run(() -> {
            try {
                future.complete(work.call());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
    }

    /**
     * One sampled column and the ring center it was sampled around.
     */
    private record Sample(int centerX, int centerZ, RingMath.SampledColumn column) {
    }

    private record ScannedChunk(int chunkX, int chunkZ, int centerX, int centerZ) {
    }

    private record ResolvedSearch(SummonSearchSettings settings, SummonRules rules, SearchMiss miss) {

        private static ResolvedSearch miss(SearchMiss reason) {
            return new ResolvedSearch(null, null, reason);
        }
    }
}
