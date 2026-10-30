package me.rakkoon69.rakunloots.server;

/**
 * Runs tasks on one of a server adapter's threads: at a location, for a player, globally, or async. Delays and
 * periods are in server ticks.
 */
public interface TaskScheduler {

    /**
     * Runs the task once, as soon as it can. It never runs on the calling thread before this call returns.
     */
    TaskHandle run(Runnable task);

    /**
     * Runs the task once, the given number of ticks from now.
     *
     * @param delayTicks at least 1
     * @throws IllegalArgumentException when the delay is below 1
     */
    TaskHandle runLater(Runnable task, long delayTicks);

    /**
     * Runs the task the given number of ticks from now, and then every period, until it is cancelled.
     *
     * @param delayTicks  at least 1
     * @param periodTicks at least 1
     * @throws IllegalArgumentException when the delay or the period is below 1
     */
    TaskHandle repeat(Runnable task, long delayTicks, long periodTicks);

}
