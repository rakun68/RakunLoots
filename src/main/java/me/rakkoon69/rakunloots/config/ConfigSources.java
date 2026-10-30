package me.rakkoon69.rakunloots.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory file contents for a config load. No paths, no Bukkit types.
 */
public final class ConfigSources {

    private final Optional<String> globals;
    private final Optional<String> worlds;
    private final Optional<String> blocks;
    private final Optional<String> animations;
    private final Optional<String> warnings;
    private final Map<String, String> languages;
    private final Map<String, String> loots;
    private final Map<String, String> menus;

    private ConfigSources(
            Optional<String> globals,
            Optional<String> worlds,
            Optional<String> blocks,
            Optional<String> animations,
            Optional<String> warnings,
            Map<String, String> languages,
            Map<String, String> loots,
            Map<String, String> menus
    ) {
        this.globals = globals;
        this.worlds = worlds;
        this.blocks = blocks;
        this.animations = animations;
        this.warnings = warnings;
        this.languages = languages;
        this.loots = loots;
        this.menus = menus;
    }

    public Optional<String> globals() {
        return globals;
    }

    public Optional<String> worlds() {
        return worlds;
    }

    public Optional<String> blocks() {
        return blocks;
    }

    /**
     * The content of {@code animations.yml}, or empty when no file was given. With none given, a loot's animation ids
     * are not checked against it.
     */
    public Optional<String> animations() {
        return animations;
    }

    /**
     * The content of {@code warnings.yml}, or empty when no file was given. With none given, every warning kind keeps
     * its default severity.
     */
    public Optional<String> warnings() {
        return warnings;
    }

    public Map<String, String> languages() {
        return languages;
    }

    public Map<String, String> loots() {
        return loots;
    }

    /**
     * The content of each {@code menus/} file, keyed by the file name without {@code .yml}.
     */
    public Map<String, String> menus() {
        return menus;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Optional<String> globals = Optional.empty();
        private Optional<String> worlds = Optional.empty();
        private Optional<String> blocks = Optional.empty();
        private Optional<String> animations = Optional.empty();
        private Optional<String> warnings = Optional.empty();
        private final Map<String, String> languages = new LinkedHashMap<>();
        private final Map<String, String> loots = new LinkedHashMap<>();
        private final Map<String, String> menus = new LinkedHashMap<>();

        public Builder globals(String content) {
            this.globals = Optional.ofNullable(content);
            return this;
        }

        public Builder worlds(String content) {
            this.worlds = Optional.ofNullable(content);
            return this;
        }

        public Builder blocks(String content) {
            this.blocks = Optional.ofNullable(content);
            return this;
        }

        public Builder animations(String content) {
            this.animations = Optional.ofNullable(content);
            return this;
        }

        public Builder warnings(String content) {
            this.warnings = Optional.ofNullable(content);
            return this;
        }

        public Builder language(String id, String content) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(content, "content");
            languages.put(id, content);
            return this;
        }

        public Builder loot(String id, String content) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(content, "content");
            loots.put(id, content);
            return this;
        }

        public Builder menu(String id, String content) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(content, "content");
            menus.put(id, content);
            return this;
        }

        public ConfigSources build() {
            return new ConfigSources(
                    globals,
                    worlds,
                    blocks,
                    animations,
                    warnings,
                    Collections.unmodifiableMap(new LinkedHashMap<>(languages)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(loots)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(menus))
            );
        }
    }
}
