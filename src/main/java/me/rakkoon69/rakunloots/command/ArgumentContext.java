package me.rakkoon69.rakunloots.command;

/**
 * What an argument type may look at while it parses or completes.
 */
public interface ArgumentContext {

    CommandSender sender();

    CommandSources sources();

    /**
     * The root node of the command being typed.
     */
    CommandNode root();
}
