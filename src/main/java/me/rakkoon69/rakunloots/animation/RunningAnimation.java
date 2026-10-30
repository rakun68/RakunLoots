package me.rakkoon69.rakunloots.animation;

/**
 * One animation that has started. The animation service stops it when its moment ends.
 */
public interface RunningAnimation {

    /**
     * Stops drawing. Does nothing when it already stopped or finished.
     */
    void stop();

    /**
     * Whether it has no more to draw: it drew its last frame, was stopped, or failed.
     */
    boolean finished();
}
