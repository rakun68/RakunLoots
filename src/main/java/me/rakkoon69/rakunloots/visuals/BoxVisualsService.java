package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.lootbox.BoxObserver;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shows glow and a laser on each shown loot box whose loot asks for them, through the box visuals seam. It follows the
 * lifecycle as its {@link BoxObserver}: shown starts them, and out of view and ended stop them. After a reload, the
 * next tick restarts a box whose loot's glow or laser changed.
 *
 * <p>A box glows for every player in its world. A player who joins, changes world, or respawns into the box's world
 * sees the glow from then on, and the glow follows no player who left. The client only draws a glow within its own
 * render distance, so the world is the range that matters.
 *
 * <p>The libraries are set up once, when this is built. A library that throws while it is set up, such as GlowingBlocks
 * on a server that is not Paper or GuardianBeam on Folia, turns that visual off for every box until the next start,
 * with one low warning, and nothing else changes. A failure for one box leaves the box running without that visual,
 * with one low warning for each visual for each reload.
 *
 * <p>Every call is safe on any thread.
 */
public final class BoxVisualsService implements BoxObserver {

    private final ServerAdapter server;
    private final Warnings warnings;
    private final Optional<GlowLibrary> glow;
    private final Optional<LaserLibrary> laser;

    private final Object lock = new Object();
    private ConfigSnapshot config;
    /**
     * Each shown box, by box id.
     */
    private final Map<UUID, Running> running = new HashMap<>();
    private boolean glowFailureTold;
    private boolean laserFailureTold;
    /**
     * Each loot told, since the last reload, that its guardian laser draws as a crystal in a peaceful world.
     */
    private final Set<String> peacefulTold = new HashSet<>();
    private boolean stopped;

    /**
     * Sets up both libraries. A setup that throws anything leaves that visual off and warns low.
     */
    public BoxVisualsService(ConfigSnapshot config, ServerAdapter server, Supplier<GlowLibrary> glowSetup,
                             Supplier<LaserLibrary> laserSetup, Warnings warnings) {
        this.config = Objects.requireNonNull(config, "config");
        this.server = Objects.requireNonNull(server, "server");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.glow = setUp(glowSetup, VisualsWarning.Kind.GLOW_UNAVAILABLE);
        this.laser = server.laserSupported()
                ? setUp(laserSetup, VisualsWarning.Kind.LASER_UNAVAILABLE)
                : setUp(() -> {
                    throw new UnsupportedOperationException(
                            "GuardianBeam runs its beams on the Bukkit scheduler, which this server does not allow");
                }, VisualsWarning.Kind.LASER_UNAVAILABLE);
    }

    private <T> Optional<T> setUp(Supplier<T> setup, VisualsWarning.Kind unavailable) {
        try {
            return Optional.of(Objects.requireNonNull(setup.get(), "a library"));
        } catch (Throwable failure) {
            warnings.warn(VisualsWarning.unavailable(unavailable, failure));
            return Optional.empty();
        }
    }

    /**
     * Whether the glow library is set up. False when its setup threw.
     */
    public boolean glowAvailable() {
        return glow.isPresent();
    }

    /**
     * Whether the laser library is set up. False when its setup threw.
     */
    public boolean laserAvailable() {
        return laser.isPresent();
    }

    /**
     * Takes a reloaded config. Each box follows it from the next tick, and a failure warns again once for each visual.
     */
    public void reload(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            config = snapshot;
            glowFailureTold = false;
            laserFailureTold = false;
            peacefulTold.clear();
        }
    }

    /**
     * Stops every glow and laser and lets the libraries go. Later events show nothing.
     */
    public void stop() {
        synchronized (lock) {
            stopped = true;
            for (Running box : running.values()) {
                stopVisuals(box, !server.dropsEntitiesAtStop());
            }
            running.clear();
            glow.ifPresent(library -> {
                try {
                    library.close();
                } catch (Throwable failure) {
                    glowFailed(null, failure);
                }
            });
        }
    }

    @Override
    public void shown(BoxEvent box) {
        synchronized (lock) {
            if (stopped || running.containsKey(box.boxId())) {
                return;
            }
            start(box);
        }
    }

    @Override
    public void ticked(BoxEvent box) {
        synchronized (lock) {
            Running current = running.get(box.boxId());
            if (current == null || settings(box.lootId()).equals(current.settings)) {
                return;
            }
            stopVisuals(current, true);
            running.remove(box.boxId());
            start(box);
        }
    }

    @Override
    public void outOfView(BoxEvent box) {
        synchronized (lock) {
            stopBox(box.boxId());
        }
    }

    @Override
    public void ended(BoxEvent box) {
        synchronized (lock) {
            stopBox(box.boxId());
        }
    }

    /**
     * A player joined the server, standing in a world: every glowing box there glows for them.
     */
    public void playerJoined(UUID worldKey, UUID playerId) {
        synchronized (lock) {
            glowInWorld(worldKey, playerId);
        }
    }

    /**
     * A player is now in another world. They keep no glow of the world they left, since the client dropped it, and see
     * the glowing boxes of the new one.
     */
    public void playerChangedWorld(UUID worldKey, UUID playerId) {
        synchronized (lock) {
            for (Running box : running.values()) {
                if (!box.worldKey.equals(worldKey)) {
                    box.glowing.remove(playerId);
                }
            }
            glowInWorld(worldKey, playerId);
        }
    }

    /**
     * A player respawned in a world. The client dropped every glow it had, so each is sent again.
     */
    public void playerRespawned(UUID worldKey, UUID playerId) {
        synchronized (lock) {
            for (Running box : running.values()) {
                box.glowing.remove(playerId);
            }
            glowInWorld(worldKey, playerId);
        }
    }

    /**
     * A player left the server. Nothing is sent to them.
     */
    public void playerQuit(UUID playerId) {
        synchronized (lock) {
            for (Running box : running.values()) {
                box.glowing.remove(playerId);
            }
        }
    }

    private void glowInWorld(UUID worldKey, UUID playerId) {
        if (stopped) {
            return;
        }
        for (Running box : running.values()) {
            if (box.worldKey.equals(worldKey) && box.settings.glowEnabled()) {
                glowTo(box, playerId);
            }
        }
    }

    private VisualsSettings settings(String lootId) {
        return config.visuals(lootId).orElse(VisualsSettings.NONE);
    }

    private void start(BoxEvent event) {
        VisualsSettings settings = settings(event.lootId());
        Running box = new Running(event.boxId(), event.lootId(), event.worldKey(), event.position(), settings);
        running.put(event.boxId(), box);
        if (settings.glowEnabled() && glow.isPresent()) {
            if (glow.get().sharedByAll()) {
                glowForAll(box);
            } else {
                glowOnlinePlayers(box);
            }
        }
        if (settings.laser().isPresent() && laser.isPresent()) {
            startLaser(box, settings.laser().get());
        }
    }

    private void glowOnlinePlayers(Running box) {
        CompletableFuture<List<PlayerPosition>> positions;
        try {
            positions = server.playerPositions(box.worldKey);
        } catch (Throwable failure) {
            glowFailed(box, failure);
            return;
        }
        positions.whenComplete((players, failure) -> {
            synchronized (lock) {
                if (failure != null) {
                    glowFailed(box, failure);
                } else if (running.get(box.boxId) == box) {
                    for (PlayerPosition player : players) {
                        glowTo(box, player.playerId());
                    }
                }
            }
        });
    }

    /**
     * Starts the glow of a library drawn once for every player, whether or not a player is online, since it is one
     * entity in the world.
     */
    private void glowForAll(Running box) {
        box.glowingAll = true;
        afterwards(() -> glow.get().showToAll(box.worldKey, box.position, box.settings.glowColor()), failure -> {
            box.glowingAll = false;
            glowFailed(box, failure);
        });
    }

    private void glowTo(Running box, UUID playerId) {
        if (glow.isEmpty() || glow.get().sharedByAll() || !box.glowing.add(playerId)) {
            return;
        }
        afterwards(() -> glow.get().show(box.worldKey, box.position, playerId, box.settings.glowColor()),
                failure -> {
                    box.glowing.remove(playerId);
                    glowFailed(box, failure);
                });
    }

    /**
     * Starts the box's laser. A guardian laser in a peaceful world draws as a crystal laser, at least high enough for
     * the crystal to clear the box, since the client does not create a guardian there.
     */
    private void startLaser(Running box, LaserType type) {
        try {
            int startOffset = box.settings.laserStartOffset();
            if (type == LaserType.GUARDIAN && laser.get().peaceful(box.worldKey)) {
                type = LaserType.CRYSTAL;
                startOffset = Math.max(startOffset, LaserPlacement.CRYSTAL_CLEAR_OFFSET);
                if (peacefulTold.add(box.lootId)) {
                    warnings.warn(VisualsWarning.guardianInPeaceful(box.lootId, box.boxId));
                }
            }
            box.beam = laser.get().start(box.worldKey, box.position, type, box.settings.laserViewDistance(),
                    startOffset);
        } catch (Throwable failure) {
            laserFailed(box, failure);
        }
    }

    private void stopBox(UUID boxId) {
        Running box = running.remove(boxId);
        if (box != null) {
            stopVisuals(box, true);
        }
    }

    /**
     * @param cleanGlow false to leave the glow as it is, when the server drops its entities itself
     */
    private void stopVisuals(Running box, boolean cleanGlow) {
        if (cleanGlow) {
            for (UUID playerId : box.glowing) {
                afterwards(() -> glow.get().hide(box.worldKey, box.position, playerId),
                        failure -> glowFailed(box, failure));
            }
            if (box.glowingAll) {
                afterwards(() -> glow.get().hideFromAll(box.worldKey, box.position),
                        failure -> glowFailed(box, failure));
            }
        }
        box.glowing.clear();
        box.glowingAll = false;
        if (box.beam != null) {
            try {
                box.beam.stop();
            } catch (Throwable failure) {
                laserFailed(box, failure);
            }
            box.beam = null;
        }
    }

    /**
     * Runs a library call that returns a future, and hands a failure to {@code onFailure} whether the call threw or its
     * future failed.
     */
    private void afterwards(Supplier<CompletableFuture<Void>> call, Consumer<Throwable> onFailure) {
        CompletableFuture<Void> future;
        try {
            future = call.get();
        } catch (Throwable failure) {
            onFailure.accept(failure);
            return;
        }
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                synchronized (lock) {
                    onFailure.accept(failure);
                }
            }
        });
    }

    private void glowFailed(Running box, Throwable failure) {
        if (glowFailureTold) {
            return;
        }
        glowFailureTold = true;
        warnings.warn(box == null
                ? VisualsWarning.unavailable(VisualsWarning.Kind.GLOW_FAILED, unwrap(failure))
                : VisualsWarning.failed(VisualsWarning.Kind.GLOW_FAILED, box.lootId, box.boxId, unwrap(failure)));
    }

    private void laserFailed(Running box, Throwable failure) {
        if (laserFailureTold) {
            return;
        }
        laserFailureTold = true;
        warnings.warn(VisualsWarning.failed(VisualsWarning.Kind.LASER_FAILED, box.lootId, box.boxId,
                unwrap(failure)));
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    /**
     * One shown box: its loot's settings as they were when it started, the players it glows for or that it glows for
     * everyone, and its beam.
     */
    private static final class Running {

        private final UUID boxId;
        private final String lootId;
        private final UUID worldKey;
        private final BlockPosition position;
        private final VisualsSettings settings;
        private final Set<UUID> glowing = new LinkedHashSet<>();
        private boolean glowingAll;
        private LaserLibrary.Beam beam;

        private Running(UUID boxId, String lootId, UUID worldKey, BlockPosition position, VisualsSettings settings) {
            this.boxId = boxId;
            this.lootId = lootId;
            this.worldKey = worldKey;
            this.position = position;
            this.settings = settings;
        }
    }
}
