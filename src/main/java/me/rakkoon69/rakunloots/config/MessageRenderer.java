package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.text.MessageText;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns stored text from a {@link ConfigSnapshot} into what a player sees: {@code {prefix}} becomes the {@code prefix}
 * message, placeholders are replaced, and colors are rendered.
 */
public final class MessageRenderer {

    private static final String PREFIX_KEY = "prefix";
    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)(?::([^}]*))?}");

    private MessageRenderer() {
    }

    /**
     * A loot's message, with its prefix, in legacy color codes. Blank when the message is blank, so the caller sends
     * nothing.
     */
    public static String render(ConfigSnapshot config, String lootId, String key) {
        String text = config.message(lootId, key);
        if (text.isBlank()) {
            return "";
        }
        return MessageText.color(text.replace("{prefix}", config.message(lootId, PREFIX_KEY)));
    }

    /**
     * A loot's message with each {@code {key}} in {@code values} replaced by its value, then the prefix and colors as
     * for {@link #render(ConfigSnapshot, String, String)}. A value that holds color codes keeps them. Blank when the
     * message is blank.
     */
    public static String render(ConfigSnapshot config, String lootId, String key, Map<String, String> values) {
        return render(config, lootId, key, values, Map.of());
    }

    /**
     * A loot's message with string and percent placeholders replaced.
     */
    public static String render(
            ConfigSnapshot config,
            String lootId,
            String key,
            Map<String, String> values,
            Map<String, Double> percents
    ) {
        String text = config.message(lootId, key);
        if (text.isBlank()) {
            return "";
        }
        if (percents != null && !percents.isEmpty()) {
            Matcher matcher = PERCENT_PLACEHOLDER.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String name = matcher.group(1);
                String colonArg = matcher.group(2);
                if (percents.containsKey(name)) {
                    int decimals = 1;
                    if (colonArg != null) {
                        try {
                            int parsed = Integer.parseInt(colonArg);
                            if (parsed >= 0 && parsed <= 6) {
                                decimals = parsed;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(MessageText.formatPercent(percents.get(name), decimals)));
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(sb);
            text = sb.toString();
        }
        if (values != null) {
            for (Map.Entry<String, String> value : values.entrySet()) {
                text = text.replace("{" + value.getKey() + "}", value.getValue());
            }
        }
        return MessageText.color(text.replace("{prefix}", config.message(lootId, PREFIX_KEY)));
    }
}
