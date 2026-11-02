package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.GlowColor;
import me.rakkoon69.rakunloots.server.BlockPosition;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What makes a block glow: the box visuals seam over GlowingBlocks and block display entities. {@link GlowingBlocksGlow}
 * is the real one below Minecraft 1.19.4, and a block display one from 1.19.4 (ADR 0016); a test fakes it.
 *
 * <p>Each call may be made from any thread and returns at once. A call that cannot run on this thread hands its work to
 * the thread that can, and reports a failure through the future it returns. A call may also throw.
 */
public interface GlowLibrary {

    /**
     * Makes a block glow in a colour for one player. A player who is not online sees nothing and it is not a failure.
     */
    CompletableFuture<Void> show(UUID worldKey, BlockPosition position, UUID playerId, GlowColor color);

    /**
     * Takes the glow of a block away for one player.
     */
    CompletableFuture<Void> hide(UUID worldKey, BlockPosition position, UUID playerId);

    /**
     * Whether this library draws a block's glow once for every player, as a block display entity does, instead of
     * once for each player, as GlowingBlocks does. The box visuals service then calls {@link #showToAll} and
     * {@link #hideFromAll}, once for each box, and never {@link #show} or {@link #hide}.
     */
    default boolean sharedByAll() {
        return false;
    }

    /**
     * Makes a block glow in a colour for every player. Only a library that is {@link #sharedByAll} supports it. The
     * future fails when the glow cannot be made; a block that is no longer there glows nothing and it is not a failure.
     */
    default CompletableFuture<Void> showToAll(UUID worldKey, BlockPosition position, GlowColor color) {
        throw new UnsupportedOperationException("this glow library is drawn for each player");
    }

    /**
     * Takes the glow of a block away for every player. Only a library that is {@link #sharedByAll} supports it.
     */
    default CompletableFuture<Void> hideFromAll(UUID worldKey, BlockPosition position) {
        throw new UnsupportedOperationException("this glow library is drawn for each player");
    }

    /**
     * Takes every glow away and lets the library go. Called when the plugin stops.
     */
    void close();
}
