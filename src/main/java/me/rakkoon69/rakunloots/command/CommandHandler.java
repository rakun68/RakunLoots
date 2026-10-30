package me.rakkoon69.rakunloots.command;

/**
 * What a node does once every argument parsed.
 */
@FunctionalInterface
public interface CommandHandler {

    void run(CommandInvocation invocation);
}
