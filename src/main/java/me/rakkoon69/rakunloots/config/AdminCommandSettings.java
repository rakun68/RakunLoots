package me.rakkoon69.rakunloots.config;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The {@code admin_command} block of {@code config.yml}: the name and aliases the admin command registers under.
 * Names are lower case and hold no space or slash.
 */
public final class AdminCommandSettings {

    public static final String DEFAULT_NAME = "rkl";
    public static final List<String> DEFAULT_ALIASES = List.of("rkloots");

    private static final Pattern USABLE = Pattern.compile("[^\\s/]+");

    private final String name;
    private final List<String> aliases;

    AdminCommandSettings(String name, List<String> aliases) {
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
    }

    static AdminCommandSettings defaults() {
        return new AdminCommandSettings(DEFAULT_NAME, DEFAULT_ALIASES);
    }

    /**
     * Whether the text can name a command: not blank, and no space or slash.
     */
    static boolean isUsableName(String text) {
        return USABLE.matcher(text).matches();
    }

    /**
     * The command's name, lower case.
     */
    public String name() {
        return name;
    }

    /**
     * The other names of the command, lower case, none equal to the name or to each other. May be empty.
     */
    public List<String> aliases() {
        return aliases;
    }
}
