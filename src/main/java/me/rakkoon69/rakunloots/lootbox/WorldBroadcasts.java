package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.Loot;
import me.rakkoon69.rakunloots.config.MessageRenderer;
import me.rakkoon69.rakunloots.config.WorldSettings;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the server and a box's owner are told about a loot box: a location broadcast when the world timer summons one,
 * a removal broadcast when a box the world timer summoned ends in a world with {@code broadcast_remove}, and the owner
 * remove message when a loot with {@code message_owner_on_remove_enable} ends. All text is a default message that a
 * loot's message overrides can reword.
 *
 * <p>As a {@link BoxObserver} it hears each loot end. Every call returns at once and works through the server adapter,
 * so it is safe on any thread. It reaches the server through each loaded world's players, since the server adapter
 * sends to one player at a time.
 */
public final class WorldBroadcasts implements BoxObserver {

    static final String SPAWNED_KEY = "lootbox_spawned_broadcast";
    static final String REMOVED_KEY = "lootbox_removed_broadcast";
    static final String OWNER_REMOVED_KEY = "message_owner_on_remove";
    private static final String UNENDING_KEY = "hologram_unending";

    private final ServerAdapter adapter;
    private volatile ConfigSnapshot config;
    private final Warnings warnings;
    private final Random random;

    public WorldBroadcasts(ServerAdapter adapter, ConfigSnapshot config, Warnings warnings, Random random) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Takes a reloaded config.
     */
    public void reload(ConfigSnapshot snapshot) {
        this.config = Objects.requireNonNull(snapshot, "snapshot");
    }

    /**
     * Tells the whole server roughly where a box the world timer summoned is. The area is centered within the world's
     * public location offsets of the real spot, so it holds the spot and does not give it away.
     */
    public CompletableFuture<Void> spawned(WorldSettings world, LootBox box) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(box, "box");
        ConfigSnapshot snapshot = config;
        int horizontal = world.publicLocationHorizontalOffset();
        int vertical = world.publicLocationVerticalOffset();
        BlockPosition spot = box.position();
        // The area's center sits within the offsets of the spot, and the area reaches one offset each way from its
        // center, so it always holds the spot.
        int fromX = spot.x() + random.nextInt(2 * horizontal + 1) - 2 * horizontal;
        int fromY = spot.y() + random.nextInt(2 * vertical + 1) - 2 * vertical;
        int fromZ = spot.z() + random.nextInt(2 * horizontal + 1) - 2 * horizontal;
        Map<String, String> values = values(snapshot, box.lootId(), world.worldName(), spot,
                box.remainingLockSeconds(), box.remainingLifeSeconds());
        values.put("from_x", String.valueOf(fromX));
        values.put("from_y", String.valueOf(fromY));
        values.put("from_z", String.valueOf(fromZ));
        values.put("to_x", String.valueOf(fromX + 2 * horizontal));
        values.put("to_y", String.valueOf(fromY + 2 * vertical));
        values.put("to_z", String.valueOf(fromZ + 2 * horizontal));
        return broadcast(MessageRenderer.render(snapshot, box.lootId(), SPAWNED_KEY, values), "spawned broadcast");
    }

    /**
     * A box ended, whatever ended it. Announces it when the world timer summoned it and its world has
     * {@code broadcast_remove}, as the old plugin did, so a box a summon item, an admin command, or an unknown source
     * summoned ends without an announcement. Tells its owner when its loot asks for that.
     */
    @Override
    public void ended(BoxEvent box) {
        ConfigSnapshot snapshot = config;
        Optional<Loot> loot = snapshot.loot(box.lootId());
        boolean tellOwner = loot.isPresent() && loot.get().messageOwnerOnRemove() && box.owner().isPresent();
        CompletableFuture<List<LoadedWorld>> worlds;
        try {
            worlds = adapter.loadedWorlds();
        } catch (RuntimeException failure) {
            warnings.warn(LifecycleWarning.timerFailed("removal message", failure));
            return;
        }
        worlds.thenCompose(loaded -> {
            Optional<LoadedWorld> world = loaded.stream().filter(candidate -> candidate.key().equals(box.worldKey()))
                    .findFirst();
            if (world.isEmpty()) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            Map<String, String> values = values(snapshot, box.lootId(), world.get().name(), box.position(),
                    box.remainingLockSeconds(), box.remainingLifeSeconds());
            CompletableFuture<Void> told = CompletableFuture.completedFuture(null);
            if (tellOwner) {
                String text = MessageRenderer.render(snapshot, box.lootId(), OWNER_REMOVED_KEY, values);
                if (!text.isEmpty()) {
                    told = adapter.sendMessage(box.owner().orElseThrow(), text).thenApply(sent -> null);
                }
            }
            Optional<WorldSettings> settings = snapshot.world(world.get().name());
            if (box.source() == SummonKind.WORLD_TIMER && settings.isPresent() && settings.get().broadcastRemove()) {
                CompletableFuture<Void> announced =
                        broadcast(MessageRenderer.render(snapshot, box.lootId(), REMOVED_KEY, values), "removal broadcast");
                return told.thenCompose(done -> announced);
            }
            return told;
        }).exceptionally(failure -> {
            warnings.warn(LifecycleWarning.timerFailed("removal message", unwrap(failure)));
            return null;
        });
    }

    /**
     * Sends text to every player of every loaded world. Blank text sends nothing.
     */
    private CompletableFuture<Void> broadcast(String text, String step) {
        if (text.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return adapter.loadedWorlds().thenCompose(worlds -> {
            CompletableFuture<?>[] sends = worlds.stream()
                    .map(world -> adapter.playerPositions(world.key()).thenCompose(players -> {
                        CompletableFuture<?>[] each = players.stream()
                                .map(PlayerPosition::playerId)
                                .map(player -> adapter.sendMessage(player, text))
                                .toArray(CompletableFuture<?>[]::new);
                        return CompletableFuture.allOf(each);
                    }))
                    .toArray(CompletableFuture<?>[]::new);
            return CompletableFuture.allOf(sends);
        }).whenComplete((done, failure) -> {
            if (failure != null) {
                warnings.warn(LifecycleWarning.timerFailed(step, unwrap(failure)));
            }
        }).handle((done, failure) -> null);
    }

    /**
     * The placeholders every box message knows.
     */
    private static Map<String, String> values(ConfigSnapshot snapshot, String lootId, String worldName,
                                              BlockPosition position, int lockSeconds, OptionalInt lifeSeconds) {
        String name = snapshot.loot(lootId).map(Loot::displayName).orElse(lootId);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("box_name", name);
        values.put("loot_name", name);
        values.put("box_world", worldName);
        values.put("box_x", String.valueOf(position.x()));
        values.put("box_y", String.valueOf(position.y()));
        values.put("box_z", String.valueOf(position.z()));
        values.put("box_locked", String.valueOf(lockSeconds));
        values.put("box_lifespan", lifeSeconds.isPresent()
                ? String.valueOf(lifeSeconds.getAsInt())
                : snapshot.message(lootId, UNENDING_KEY));
        return values;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    @Override
    public void shown(BoxEvent box) {
    }

    @Override
    public void ticked(BoxEvent box) {
    }

    @Override
    public void outOfView(BoxEvent box) {
    }
}
