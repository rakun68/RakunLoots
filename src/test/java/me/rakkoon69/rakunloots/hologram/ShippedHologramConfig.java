package me.rakkoon69.rakunloots.hologram;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The shipped config.yml, worlds.yml, blocks.yml, the three language files, and loots/airdrops.yml, through the real
 * config loader, with a chosen active language and any extra loot files.
 */
public final class ShippedHologramConfig {

    /**
     * Resolves written ids to namespaced lower case, as the server catalog does.
     */
    private static final NameCatalog SERVER_LIKE = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            if (writtenId.startsWith("@") || writtenId.startsWith("#")) {
                return Optional.empty();
            }
            String lower = writtenId.toLowerCase(Locale.ROOT);
            return Optional.of(lower.contains(":") ? lower : "minecraft:" + lower);
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            return Set.of("minecraft:tag_" + namespacedTag.replace(':', '_'));
        }
    };

    private ShippedHologramConfig() {
    }

    public static ConfigSnapshot load() {
        return load("en", Map.of());
    }

    public static ConfigSnapshot load(String language, Map<String, String> extraLoots) {
        return load(language, resource("/worlds.yml"), extraLoots);
    }

    /**
     * The shipped files with the given worlds.yml in place of the shipped one.
     */
    public static ConfigSnapshot load(String language, String worlds, Map<String, String> extraLoots) {
        return load(language, worlds, extraLoots, "auto");
    }

    /**
     * The shipped files with {@code hologram_provider} set to {@code provider} in config.yml.
     */
    public static ConfigSnapshot load(String language, String worlds, Map<String, String> extraLoots,
                                      String provider) {
        String globals = resource("/config.yml");
        if (!globals.contains("hologram_provider: auto")) {
            throw new IllegalStateException("config.yml no longer ships hologram_provider: auto");
        }
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(globals.replace("hologram_provider: auto", "hologram_provider: " + provider)
                        + "\nlanguage: " + language + "\n")
                .worlds(worlds)
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"))
                .language("vi", resource("/languages/vi.yml"))
                .language("zh", resource("/languages/zh.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"));
        extraLoots.forEach(sources::loot);
        ConfigSnapshot snapshot = ConfigLoader.load(sources.build(), SERVER_LIKE, WarningKinds.all());
        if (!snapshot.activeLanguage().equals(language)) {
            throw new IllegalStateException("active language " + snapshot.activeLanguage() + ", not " + language);
        }
        return snapshot;
    }

    /**
     * A barrel loot with one diamond, its display name, and extra top level keys.
     */
    static String loot(String displayName, String extra) {
        return """
                language: en
                display_name: '%s'
                open_mode: drop
                lock: 5
                life: 60
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                %s
                """.formatted(displayName, extra);
    }

    private static String resource(String path) {
        try (InputStream in = ShippedHologramConfig.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
