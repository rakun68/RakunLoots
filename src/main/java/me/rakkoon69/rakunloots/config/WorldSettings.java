package me.rakkoon69.rakunloots.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Summon rules for one world: the world timer, limits, which loots can appear, the location broadcast, and how a
 * server summon searches. Keys match the existing worlds.yml vocabulary.
 */
public final class WorldSettings {

    public static final int DEFAULT_TIME_BETWEEN_LOOT_SPAWN = 0;
    public static final int DEFAULT_LOOT_SPAWN_LIMIT = 0;
    public static final int DEFAULT_MINIMUM_PLAYER_TO_SPAWN_LOOT = 1;
    public static final int DEFAULT_PUBLIC_LOCATION_OFFSET = 0;
    /**
     * The world timer never runs more often than this, in seconds, whatever {@code time_between_loot_spawn} says.
     */
    public static final int MINIMUM_TIMER_SECONDS = 5;

    private final String worldName;
    private final int timeBetweenLootSpawn;
    private final int lootSpawnLimit;
    private final Map<String, Integer> loots;
    private final SummonSearchSettings summonSearch;
    private final int minimumPlayerToSpawnLoot;
    private final List<String> alsoCountPlayersFrom;
    private final int publicLocationHorizontalOffset;
    private final int publicLocationVerticalOffset;
    private final boolean broadcastRemove;

    WorldSettings(
            String worldName,
            int timeBetweenLootSpawn,
            int lootSpawnLimit,
            Map<String, Integer> loots,
            SummonSearchSettings summonSearch,
            int minimumPlayerToSpawnLoot,
            List<String> alsoCountPlayersFrom,
            int publicLocationHorizontalOffset,
            int publicLocationVerticalOffset,
            boolean broadcastRemove
    ) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.timeBetweenLootSpawn = timeBetweenLootSpawn;
        this.lootSpawnLimit = lootSpawnLimit;
        this.loots = Collections.unmodifiableMap(new LinkedHashMap<>(loots));
        this.summonSearch = Objects.requireNonNull(summonSearch, "summonSearch");
        this.minimumPlayerToSpawnLoot = minimumPlayerToSpawnLoot;
        this.alsoCountPlayersFrom = List.copyOf(Objects.requireNonNull(alsoCountPlayersFrom, "alsoCountPlayersFrom"));
        this.publicLocationHorizontalOffset = publicLocationHorizontalOffset;
        this.publicLocationVerticalOffset = publicLocationVerticalOffset;
        this.broadcastRemove = broadcastRemove;
    }

    public String worldName() {
        return worldName;
    }

    /**
     * Seconds between timer summons as written, which may be under {@link #MINIMUM_TIMER_SECONDS}. The world timer
     * runs on {@link #timerSeconds()}.
     */
    public int timeBetweenLootSpawn() {
        return timeBetweenLootSpawn;
    }

    /**
     * Seconds between timer summons, never under {@link #MINIMUM_TIMER_SECONDS}.
     */
    public int timerSeconds() {
        return Math.max(MINIMUM_TIMER_SECONDS, timeBetweenLootSpawn);
    }

    /**
     * How many live loot boxes and pending summons the world may hold before the timer stops summoning. Timer
     * summons only.
     */
    public int lootSpawnLimit() {
        return lootSpawnLimit;
    }

    /**
     * Players the world, plus the worlds in {@link #alsoCountPlayersFrom()}, must hold for a timer summon.
     */
    public int minimumPlayerToSpawnLoot() {
        return minimumPlayerToSpawnLoot;
    }

    /**
     * Names of the worlds whose players count toward {@link #minimumPlayerToSpawnLoot()}, beside this world's own.
     */
    public List<String> alsoCountPlayersFrom() {
        return alsoCountPlayersFrom;
    }

    /**
     * Blocks, each way, the broadcast location may sit from the real spot. Never negative.
     */
    public int publicLocationHorizontalOffset() {
        return publicLocationHorizontalOffset;
    }

    /**
     * Blocks up and down the broadcast location may sit from the real spot. Never negative.
     */
    public int publicLocationVerticalOffset() {
        return publicLocationVerticalOffset;
    }

    /**
     * Whether a loot box ending in this world is announced to the server.
     */
    public boolean broadcastRemove() {
        return broadcastRemove;
    }

    public Map<String, Integer> loots() {
        return loots;
    }

    public Optional<Integer> lootChance(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return Optional.ofNullable(loots.get(lootId));
    }

    public SummonSearchSettings summonSearch() {
        return summonSearch;
    }
}
