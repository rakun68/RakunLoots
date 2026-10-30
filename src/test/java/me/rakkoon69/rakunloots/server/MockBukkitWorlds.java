package me.rakkoon69.rakunloots.server;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A recording MockBukkit server with fake chunk worlds and recording players, for the tests of the adapters that
 * run on the Bukkit main thread. Stop it with {@code MockBukkit.unmock()}.
 */
final class MockBukkitWorlds {

    private static final int MAX_TICKS = 20;

    private final RecordingServerMock server;
    private final Map<UUID, FakeChunkWorld> worlds = new HashMap<>();
    private int players;

    private MockBukkitWorlds(RecordingServerMock server) {
        this.server = server;
    }

    static MockBukkitWorlds start() {
        return new MockBukkitWorlds(RecordingServerMock.mock());
    }

    RecordingServerMock server() {
        return server;
    }

    /**
     * A plugin to bind the adapter under test to.
     */
    Plugin plugin() {
        return MockBukkit.createMockPlugin();
    }

    UUID createWorld(int minHeight, int maxHeight) {
        FakeChunkWorld world = new FakeChunkWorld(minHeight, maxHeight);
        server.addWorld(world);
        worlds.put(world.getUID(), world);
        return world.getUID();
    }

    FakeChunkWorld world(UUID world) {
        return worlds.get(world);
    }

    void setBlock(UUID world, int x, int y, int z, String blockId) {
        worlds.get(world).setBlock(x, y, z, Material.matchMaterial(blockId));
    }

    UUID addPlayer(UUID world, double x, double y, double z) {
        RecordingPlayer player = new RecordingPlayer(server, "player" + ++players);
        server.addPlayer(player);
        player.setLocation(new Location(worlds.get(world), x, y, z));
        return player.getUniqueId();
    }

    RecordingPlayer player(UUID player) {
        return (RecordingPlayer) server.getPlayer(player);
    }

    /**
     * The players added so far, in the order they were added.
     */
    List<RecordingPlayer> players() {
        return server.getOnlinePlayers().stream().map(RecordingPlayer.class::cast).toList();
    }

    /**
     * Ticks the main thread, which is the test thread, and finishes async chunk loads after each tick, as Paper
     * does, until the future is done.
     */
    <T> T runUntilDone(CompletableFuture<T> future) throws Exception {
        for (int tick = 0; tick < MAX_TICKS && !future.isDone(); tick++) {
            server.getScheduler().performOneTick();
            worlds.values().forEach(FakeChunkWorld::finishAsyncLoads);
        }
        return future.get(5, TimeUnit.SECONDS);
    }
}
