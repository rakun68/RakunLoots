package me.rakkoon69.rakunloots.animation;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import me.rakkoon69.rakunloots.server.ServerAdapter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Where an animation draws its particles. The plugin's sink hands them to the server adapter, which draws on the
 * thread that owns the spot; a test's sink records them.
 */
public interface ParticleSink {

    /**
     * Draws the particles, on the thread that owns the block at {@code owner}. Never loads a chunk.
     *
     * @return whether they were drawn; false when the world or the owner's chunk is not loaded
     */
    CompletableFuture<Boolean> draw(UUID worldKey, BlockPosition owner, List<ParticleDraw> draws);

    /**
     * A sink that draws through the server adapter.
     */
    static ParticleSink of(ServerAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        return adapter::drawParticles;
    }
}
