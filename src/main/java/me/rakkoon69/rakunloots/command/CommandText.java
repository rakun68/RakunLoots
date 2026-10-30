package me.rakkoon69.rakunloots.command;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {@code {placeholder}} tokens in a default message. {@code {prefix}} fills from the {@code prefix} message.
 * A placeholder with no value stays as written. A value is never read for placeholders again.
 */
final class CommandText {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
    private static final String PREFIX = "prefix";

    private final CommandMessages messages;

    CommandText(CommandMessages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    String text(String key) {
        return messages.text(key);
    }

    String format(String key, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(messages.text(key));
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.get(name);
            if (value == null && PREFIX.equals(name)) {
                value = messages.text(PREFIX);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
