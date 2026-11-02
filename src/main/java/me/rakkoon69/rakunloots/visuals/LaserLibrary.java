package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.LaserType;
import me.rakkoon69.rakunloots.server.BlockPosition;

import java.util.UUID;

/**
 * What shows a beam from a block to the sky: the box visuals seam over GuardianBeam. {@link GuardianBeamLaser} is the
 * real one; a test fakes it.
 */
public interface LaserLibrary {

    /**
     * Starts a beam from a block to the sky that players within the view distance see, and lasts until stopped.
     *
     * @param viewDistance blocks from the block within which a player sees the beam
     * @param startOffset  blocks above the bottom of the block where the beam's entity sits
     * @throws Exception when the beam cannot start
     */
    Beam start(UUID worldKey, BlockPosition position, LaserType type, int viewDistance,
               int startOffset) throws Exception;

    /**
     * Whether the world is on peaceful, where the client does not create a hostile mob such as a guardian, so a
     * guardian laser there draws nothing.
     *
     * @throws Exception when the world cannot be read
     */
    boolean peaceful(UUID worldKey) throws Exception;

    /**
     * One beam that is showing.
     */
    interface Beam {

        /**
         * Takes the beam away for every player.
         */
        void stop() throws Exception;
    }
}
