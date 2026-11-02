package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.GlowColor;
import me.rakkoon69.rakunloots.server.BlockPosition;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A glow library that records what it was asked, and can be made to fail as GlowingBlocks does: a call that throws, or
 * a future that fails.
 */
public final class FakeGlowLibrary implements GlowLibrary {

    public enum Op { SHOW, HIDE, SHOW_ALL, HIDE_ALL, CLOSE }

    public record Call(Op op, UUID worldKey, BlockPosition position, UUID playerId, GlowColor color) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private RuntimeException throwing;
    private Throwable failingFuture;
    private boolean shared;

    public List<Call> calls() {
        return calls;
    }

    public void clear() {
        calls.clear();
    }

    /**
     * Every later show and hide throws this.
     */
    public void throwOnCalls(RuntimeException failure) {
        throwing = failure;
    }

    /**
     * Every later show and hide returns a future that fails with this.
     */
    public void failFutures(Throwable failure) {
        failingFuture = failure;
    }

    @Override
    public CompletableFuture<Void> show(UUID worldKey, BlockPosition position, UUID playerId, GlowColor color) {
        refuseWhenShared();
        calls.add(new Call(Op.SHOW, worldKey, position, playerId, color));
        return result();
    }

    @Override
    public CompletableFuture<Void> hide(UUID worldKey, BlockPosition position, UUID playerId) {
        refuseWhenShared();
        calls.add(new Call(Op.HIDE, worldKey, position, playerId, null));
        return result();
    }

    /**
     * Makes this fake draw a glow once for every player, as a block display does: it then refuses {@link #show} and
     * {@link #hide}, as the real one does not support them.
     */
    public FakeGlowLibrary drawnForAll() {
        shared = true;
        return this;
    }

    @Override
    public boolean sharedByAll() {
        return shared;
    }

    @Override
    public CompletableFuture<Void> showToAll(UUID worldKey, BlockPosition position, GlowColor color) {
        if (!shared) {
            return GlowLibrary.super.showToAll(worldKey, position, color);
        }
        calls.add(new Call(Op.SHOW_ALL, worldKey, position, null, color));
        return result();
    }

    @Override
    public CompletableFuture<Void> hideFromAll(UUID worldKey, BlockPosition position) {
        if (!shared) {
            return GlowLibrary.super.hideFromAll(worldKey, position);
        }
        calls.add(new Call(Op.HIDE_ALL, worldKey, position, null, null));
        return result();
    }

    @Override
    public void close() {
        calls.add(new Call(Op.CLOSE, null, null, null, null));
    }

    private void refuseWhenShared() {
        if (shared) {
            throw new UnsupportedOperationException("a block display glow is drawn for every player at once");
        }
    }

    private CompletableFuture<Void> result() {
        if (throwing != null) {
            throw throwing;
        }
        if (failingFuture != null) {
            return CompletableFuture.failedFuture(failingFuture);
        }
        return CompletableFuture.completedFuture(null);
    }
}
