package me.rakkoon69.rakunloots.server.bukkit;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitScheduler;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A hand fake of a Folia server: fake worlds, players, custom inventories, and profiles, the four Folia schedulers
 * running on one tick clock, and a
 * Bukkit scheduler that throws, as Folia's does, and records each time it was asked for or called. Tasks run only when
 * the test ticks the server. Stop it with {@link #shutdown()}. It is never set as the static Bukkit server, so a
 * static Bukkit call fails the test too.
 */
final class FakeFoliaServer {

    private final Map<UUID, FakeFoliaWorld> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, FakeFoliaPlayer> players = new ConcurrentHashMap<>();
    private final FakeFoliaTicks ticks = new FakeFoliaTicks();
    private final FakeRegionScheduler regionScheduler = new FakeRegionScheduler(ticks);
    private final FakeGlobalRegionScheduler globalRegionScheduler = new FakeGlobalRegionScheduler(ticks);
    private final FakeAsyncScheduler asyncScheduler = new FakeAsyncScheduler(ticks);
    private final List<String> bukkitSchedulerCalls = new CopyOnWriteArrayList<>();
    private final List<LogRecord> logRecords = new CopyOnWriteArrayList<>();
    private final Logger logger = capturedLogger(logRecords);
    private volatile boolean pluginEnabled = true;
    private final ExecutorService chunkLoader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fake chunk loader");
        thread.setDaemon(true);
        return thread;
    });
    private final BukkitScheduler bukkitScheduler = FoliaFakes.fake(BukkitScheduler.class, "FoliaBukkitScheduler",
            (proxy, method, args) -> {
                bukkitSchedulerCalls.add(method.getName());
                throw new UnsupportedOperationException("Folia has no Bukkit scheduler");
            });
    private final Server server = FoliaFakes.fake(Server.class, "FakeFoliaServer", (proxy, method, args) ->
            switch (method.getName() + "/" + args.length) {
                case "getWorlds/0" -> worlds.values().stream().map(FakeFoliaWorld::world).toList();
                case "getWorld/1" -> {
                    if (!(args[0] instanceof UUID key)) {
                        yield FoliaFakes.UNHANDLED;
                    }
                    FakeFoliaWorld world = worlds.get(key);
                    yield world == null ? null : world.world();
                }
                case "getPlayer/1" -> {
                    if (!(args[0] instanceof UUID id)) {
                        yield FoliaFakes.UNHANDLED;
                    }
                    FakeFoliaPlayer player = players.get(id);
                    yield player == null ? null : player.player();
                }
                case "createInventory/3" -> {
                    if (!(args[1] instanceof Integer size) || !(args[2] instanceof String title)) {
                        yield FoliaFakes.UNHANDLED;
                    }
                    yield FakeFoliaInventory.chest((InventoryHolder) args[0], size, title);
                }
                case "createPlayerProfile/2" -> profile((UUID) args[0], (String) args[1]);
                case "isOwnedByCurrentRegion/3" -> {
                    if (!(args[0] instanceof World world) || !(args[1] instanceof Integer chunkX)
                            || !(args[2] instanceof Integer chunkZ)) {
                        yield FoliaFakes.UNHANDLED;
                    }
                    yield FakeFoliaTicks.currentOwner()
                            .equals(Optional.of(new FakeFoliaTicks.Region(world.getUID(), chunkX, chunkZ)));
                }
                case "isOwnedByCurrentRegion/1" -> {
                    if (!(args[0] instanceof Entity entity)) {
                        yield FoliaFakes.UNHANDLED;
                    }
                    yield FakeFoliaTicks.currentOwner()
                            .equals(Optional.of(new FakeFoliaTicks.Entity(entity.getUniqueId())));
                }
                case "getRegionScheduler/0" -> regionScheduler;
                case "getGlobalRegionScheduler/0" -> globalRegionScheduler;
                case "getAsyncScheduler/0" -> asyncScheduler;
                case "getScheduler/0" -> {
                    bukkitSchedulerCalls.add("getScheduler");
                    yield bukkitScheduler;
                }
                default -> FoliaFakes.UNHANDLED;
            });
    private final Plugin plugin = FoliaFakes.fake(Plugin.class, "FakeFoliaPlugin", (proxy, method, args) ->
            switch (method.getName() + "/" + args.length) {
                case "getServer/0" -> server;
                case "getLogger/0" -> logger;
                case "isEnabled/0" -> pluginEnabled;
                default -> FoliaFakes.UNHANDLED;
            });

    /**
     * Disables the plugin as a server does when it stops: a region task can no longer be scheduled for it.
     */
    void disablePlugin() {
        pluginEnabled = false;
    }

    /**
     * A plugin to bind the adapter under test to. Its server is this fake.
     */
    Plugin plugin() {
        return plugin;
    }

    FakeRegionScheduler regionScheduler() {
        return regionScheduler;
    }

    /**
     * Each call on the Bukkit scheduler, and each time it was asked for, in order.
     */
    List<String> bukkitSchedulerCalls() {
        return List.copyOf(bukkitSchedulerCalls);
    }

    UUID createWorld(int minHeight, int maxHeight) {
        FakeFoliaWorld world = new FakeFoliaWorld(minHeight, maxHeight, chunkLoader);
        worlds.put(world.key(), world);
        return world.key();
    }

    FakeFoliaWorld world(UUID world) {
        return worlds.get(world);
    }

    /**
     * Adds an online player standing at a position in a world.
     *
     * @return the player's UUID
     */
    UUID addPlayer(UUID world, double x, double y, double z) {
        FakeFoliaWorld in = worlds.get(world);
        FakeFoliaPlayer player = new FakeFoliaPlayer(ticks, in, x, y, z);
        players.put(player.id(), player);
        in.addPlayer(player.player());
        return player.id();
    }

    FakeFoliaPlayer player(UUID player) {
        return players.get(player);
    }

    /**
     * Takes a player off the server, as when they leave: their entity scheduler retires.
     */
    void removePlayer(UUID player) {
        FakeFoliaPlayer leaving = players.remove(player);
        leaving.world().removePlayer(leaving.player());
        leaving.scheduler().retire();
    }

    /**
     * Moves a player to another world, as a portal does. Their entity scheduler stays.
     */
    void movePlayer(UUID player, UUID world) {
        FakeFoliaPlayer moving = players.get(player);
        moving.world().removePlayer(moving.player());
        FakeFoliaWorld to = worlds.get(world);
        moving.moveTo(to);
        to.addPlayer(moving.player());
    }

    /**
     * Runs one tick of every scheduler, and waits until the tasks it ran have finished.
     */
    void tick() throws Exception {
        ticks.tick();
    }

    /**
     * Runs the action on the thread that owns the player, as one of their region tasks would.
     */
    void runAsPlayer(UUID playerId, Runnable action) throws Exception {
        ticks.startNow(plugin, new FakeFoliaTicks.Entity(playerId), task -> action.run());
        ticks.tick();
    }

    /**
     * Ticks until the future completes and returns its value.
     */
    <T> T runUntilDone(CompletableFuture<T> future) throws Exception {
        return ticks.runUntilDone(future);
    }

    void setBlock(UUID world, int x, int y, int z, String blockId) {
        worlds.get(world).setBlock(x, y, z, Material.matchMaterial(blockId));
    }

    /**
     * A profile whose textures hold a skin. It refuses no UUID with a blank name, and a name longer than 16
     * characters, as the server does.
     */
    private static PlayerProfile profile(UUID uniqueId, String name) {
        if (uniqueId == null && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("uniqueId is null and name is blank");
        }
        if (name != null && name.length() > 16) {
            throw new IllegalArgumentException("The name of the profile is longer than 16 characters");
        }
        URL[] skin = {null};
        PlayerTextures textures = FoliaFakes.fake(PlayerTextures.class, "FakeFoliaTextures", (proxy, method, args) ->
                switch (method.getName() + "/" + args.length) {
                    case "isEmpty/0" -> skin[0] == null;
                    case "getSkin/0" -> skin[0];
                    case "setSkin/1", "setSkin/2" -> {
                        skin[0] = (URL) args[0];
                        yield null;
                    }
                    default -> FoliaFakes.UNHANDLED;
                });
        return FoliaFakes.fake(PlayerProfile.class, "FakeFoliaProfile", (proxy, method, args) ->
                switch (method.getName() + "/" + args.length) {
                    case "getUniqueId/0" -> uniqueId;
                    case "getName/0" -> name;
                    case "getTextures/0" -> textures;
                    default -> FoliaFakes.UNHANDLED;
                });
    }

    /**
     * The messages logged at warning or above, in order.
     */
    List<String> warnings() {
        return logRecords.stream()
                .filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    private static Logger capturedLogger(List<LogRecord> records) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    void shutdown() {
        ticks.shutdown();
        chunkLoader.shutdownNow();
    }
}
