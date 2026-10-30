package me.rakkoon69.rakunloots.hologram;

import java.util.Objects;

/**
 * A Minecraft release such as 1.20.4 or 26.1, ordered by release. A missing patch is 0, so 1.21 is 1.21.0.
 */
public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {

    public MinecraftVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("negative version part: " + major + "." + minor + "." + patch);
        }
    }

    /**
     * Reads {@code 1.20.4}, {@code 1.21}, or {@code 26.1}, and the server's Bukkit version such as
     * {@code 1.20.4-R0.1-SNAPSHOT}, whose part from the first {@code -} is ignored. From 26.1 Paper and Folia give
     * their build too, as in {@code 26.1.2.build.74-stable}, whose part from {@code .build.} is ignored as well.
     *
     * @throws IllegalArgumentException when the text is not a version
     */
    public static MinecraftVersion parse(String text) {
        Objects.requireNonNull(text, "text");
        String release = text.strip();
        int dash = release.indexOf('-');
        if (dash >= 0) {
            release = release.substring(0, dash);
        }
        int build = release.indexOf(".build.");
        if (build >= 0) {
            release = release.substring(0, build);
        }
        String[] parts = release.split("\\.", -1);
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("not a Minecraft version: " + text);
        }
        try {
            return new MinecraftVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    parts.length == 3 ? Integer.parseInt(parts[2]) : 0);
        } catch (NumberFormatException notNumber) {
            throw new IllegalArgumentException("not a Minecraft version: " + text, notNumber);
        }
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    public boolean isBelow(MinecraftVersion other) {
        return compareTo(other) < 0;
    }

    public boolean isAbove(MinecraftVersion other) {
        return compareTo(other) > 0;
    }

    /**
     * {@code 1.20.4}, or {@code 1.21} when the patch is 0.
     */
    @Override
    public String toString() {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }
}
