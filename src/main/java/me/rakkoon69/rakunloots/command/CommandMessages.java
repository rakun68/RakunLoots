package me.rakkoon69.rakunloots.command;

/**
 * Default messages by key. A key with no text returns the key itself, as the config module does.
 */
@FunctionalInterface
public interface CommandMessages {

    String text(String key);
}
