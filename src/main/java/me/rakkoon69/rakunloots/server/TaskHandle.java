package me.rakkoon69.rakunloots.server;

/**
 * The cancel handle of one task scheduled through a server adapter.
 */
public interface TaskHandle {

    /**
     * Stops the task. A task that has not run yet never runs, and a repeating task runs no more. A run already
     * started on another thread finishes. Cancelling twice does nothing.
     */
    void cancel();
}
