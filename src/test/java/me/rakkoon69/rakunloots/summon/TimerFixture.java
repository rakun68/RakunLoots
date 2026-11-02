package me.rakkoon69.rakunloots.summon;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.lootbox.LifecycleFixture;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter.Message;
import me.rakkoon69.rakunloots.summon.search.SearchMiss;
import me.rakkoon69.rakunloots.lootbox.OccupiedCheck;
import me.rakkoon69.rakunloots.lootbox.Spot;
import me.rakkoon69.rakunloots.summon.search.SpotFinder;
import me.rakkoon69.rakunloots.summon.search.SummonResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.resource;

/**
 * The world timer, the summon service, and the lifecycle over the fake world, with two more worlds the fake lists:
 * the nether and the end, whose players the test sets. Time moves only when the test steps the global scheduler.
 */
public final class TimerFixture {

    public static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-00000000ae01");
    public static final UUID END = UUID.fromString("00000000-0000-0000-0000-00000000ae02");
    public static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000ae03");

    /**
     * A loot that lands at once.
     */
    public static final String FAST = """
            language: en
            open_mode: drop
            block: barrel
            lock: 10
            life: 360
            roll:
              entries:
                one:
                  item:
                    id: diamond
            """;
    /**
     * A loot that waits 100 ticks before its block is written.
     */
    public static final String SLOW = FAST + "summon_delay: 100\n";

    public final LifecycleFixture base;
    public final Finder finder = new Finder();
    public final List<String> adapterCalls = new CopyOnWriteArrayList<>();
    public final Map<UUID, List<PlayerPosition>> otherWorldPlayers = new HashMap<>();
    public final ServerAdapter adapter;
    public final LootBoxLifecycle lifecycle;
    public final SummonService service;
    public final WorldBroadcasts broadcasts;
    public final WorldTimers timers;
    public final ConfigSnapshot config;
    private int forgotten;

    public TimerFixture(LootBoxStore store, LifecycleFixture base, ConfigSnapshot config, long seed) {
        this.base = base;
        this.config = config;
        this.adapter = multiWorld(base);
        this.lifecycle = new LootBoxLifecycle(adapter, config, ItemSourceRegistry.create(), store, new Random(seed),
                base.warnings);
        this.service = new SummonService(adapter, config, lifecycle, finder, (player, lootId, reason) -> {
        }, base.warnings);
        this.broadcasts = new WorldBroadcasts(adapter, config, base.warnings, new Random(seed));
        lifecycle.addObserver(broadcasts);
        this.timers = new WorldTimers(adapter, config, lifecycle, service, broadcasts, base.warnings,
                new Random(seed));
    }

    /**
     * The shipped config.yml, blocks.yml, en.yml, and airdrops loot, with the given worlds.yml and extra loots.
     */
    public static ConfigSnapshot config(String worlds, Map<String, String> extraLoots) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(worlds)
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .language("vi", resource("/languages/vi.yml"))
                .language("zh", resource("/languages/zh.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"));
        extraLoots.forEach(sources::loot);
        return ConfigLoader.load(sources.build(), LifecycleFixture.SERVER_LIKE, WarningKinds.all());
    }

    /**
     * A worlds.yml with one world and the given keys, indented under it.
     */
    public static String worlds(String keys) {
        StringBuilder out = new StringBuilder("worlds:\n  world:\n");
        for (String line : keys.split("\n")) {
            out.append("    ").append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * Puts a player in the fake's own world, online so messages reach them.
     */
    public void playerInWorld(UUID player) {
        List<PlayerPosition> players = new ArrayList<>(base.adapter.playerPositions(WORLD).join());
        players.add(new PlayerPosition(player, 0, 64, 0));
        base.adapter.setPlayers(players);
        base.adapter.addOnlinePlayer(player);
    }

    /**
     * Puts a player in another world, online so messages reach them.
     */
    public void playerIn(UUID worldKey, UUID player) {
        otherWorldPlayers.computeIfAbsent(worldKey, key -> new ArrayList<>())
                .add(new PlayerPosition(player, 0, 64, 0));
        base.adapter.addOnlinePlayer(player);
    }

    public void step(long ticks) {
        base.adapter.globalScheduler().stepTicks(ticks);
    }

    /**
     * Ignores every message sent so far, so a test reads only what comes next.
     */
    public void forgetMessages() {
        forgotten = base.adapter.messages().size();
    }

    public List<String> messagesTo(UUID player) {
        List<String> texts = new ArrayList<>();
        for (Message message : base.adapter.messages().subList(forgotten, base.adapter.messages().size())) {
            if (message.playerId().equals(player)) {
                texts.add(message.text());
            }
        }
        return texts;
    }

    /**
     * The fake world's adapter listing the nether and the end as loaded, answering their player lists, and noting
     * each scheduler it is asked for.
     */
    private ServerAdapter multiWorld(LifecycleFixture fixture) {
        return (ServerAdapter) Proxy.newProxyInstance(ServerAdapter.class.getClassLoader(),
                new Class<?>[]{ServerAdapter.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "global", "async" -> adapterCalls.add(method.getName());
                        case "loadedWorlds" -> {
                            if (fixture.adapter.loadedWorlds().join().isEmpty()) {
                                return CompletableFuture.completedFuture(List.<LoadedWorld>of());
                            }
                            return CompletableFuture.completedFuture(List.of(new LoadedWorld(WORLD, "world"),
                                    new LoadedWorld(NETHER, "world_nether"), new LoadedWorld(END, "world_the_end"),
                                    new LoadedWorld(OTHER, "world_other")));
                        }
                        case "playerPositions" -> {
                            if (!args[0].equals(WORLD)) {
                                return CompletableFuture.completedFuture(
                                        List.copyOf(otherWorldPlayers.getOrDefault((UUID) args[0], List.of())));
                            }
                        }
                        default -> {
                        }
                    }
                    try {
                        return method.invoke(fixture.adapter, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    /**
     * A spot finder that hands out the spots the test queues, one per search, and misses when none is left.
     */
    public static final class Finder implements SpotFinder {

        final List<Spot> spots = new CopyOnWriteArrayList<>();
        volatile int searches;

        private CompletableFuture<SummonResult> next() {
            searches++;
            if (spots.isEmpty()) {
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
}
