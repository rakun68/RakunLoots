package me.rakkoon69.rakunloots.config;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The {@code claim_command} block of {@code config.yml}: the name and aliases the claim command registers under, and
 * who has its permission by default. Names are lower case and hold no space or slash.
 */
public final class ClaimCommandSettings {

    public static final String DEFAULT_NAME = "claimsummon";
    public static final List<String> DEFAULT_ALIASES = List.of("rklclaim");
    public static final DefaultAccess DEFAULT_PERMISSION = DefaultAccess.EVERYONE;

    private static final Pattern USABLE = Pattern.compile("[^\\s/]+");

    private final String name;
    private final List<String> aliases;
    private final DefaultAccess permissionDefault;

    ClaimCommandSettings(String name, List<String> aliases, DefaultAccess permissionDefault) {
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
        this.permissionDefault = Objects.requireNonNull(permissionDefault, "permissionDefault");
    }

    static ClaimCommandSettings defaults() {
        return new ClaimCommandSettings(DEFAULT_NAME, DEFAULT_ALIASES, DEFAULT_PERMISSION);
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

    /**
     * Who has the claim command's permission when no permission plugin says otherwise.
     */
    public DefaultAccess permissionDefault() {
        return permissionDefault;
    }
}
