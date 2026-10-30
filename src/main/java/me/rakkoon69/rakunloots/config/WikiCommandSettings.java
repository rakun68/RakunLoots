package me.rakkoon69.rakunloots.config;

import java.util.List;
import java.util.Objects;

/**
 * The {@code wiki_command} block of {@code config.yml}: the name and aliases the loots wiki command registers under,
 * and who has its permission by default.
 */
public final class WikiCommandSettings {

    public static final String DEFAULT_NAME = "loots";
    public static final List<String> DEFAULT_ALIASES = List.of("rklloots");
    public static final DefaultAccess DEFAULT_PERMISSION = DefaultAccess.EVERYONE;

    private final String name;
    private final List<String> aliases;
    private final DefaultAccess permissionDefault;

    WikiCommandSettings(String name, List<String> aliases, DefaultAccess permissionDefault) {
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
        this.permissionDefault = Objects.requireNonNull(permissionDefault, "permissionDefault");
    }

    static WikiCommandSettings defaults() {
        return new WikiCommandSettings(DEFAULT_NAME, DEFAULT_ALIASES, DEFAULT_PERMISSION);
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

    /**
     * Who has the wiki command's permission when no permission plugin says otherwise.
     */
    public DefaultAccess permissionDefault() {
        return permissionDefault;
    }
}
