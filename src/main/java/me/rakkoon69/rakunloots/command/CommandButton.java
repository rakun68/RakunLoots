package me.rakkoon69.rakunloots.command;

/**
 * A clickable piece of a message: the text it shows, with {@code &} color codes, and the command a click runs, with its
 * leading slash.
 */
public record CommandButton(String label, String command) {
}
