package me.rakkoon69.rakunloots.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reads the plugin's data folder into {@link ConfigSources}: {@code config.yml}, {@code worlds.yml},
 * {@code blocks.yml}, {@code animations.yml}, {@code warnings.yml}, every file of {@code languages/}, every file of
 * {@code loots/}, and every file of {@code menus/}, the id of a language, a loot, or a menu being its file name
 * without {@code .yml}. A file that is missing is left out of the sources, and the loader reports it.
 */
public final class ConfigFolder {

    /**
     * The files a server gets on first start, as resource paths in the jar and in the data folder.
     */
    public static final List<String> DEFAULT_RESOURCES = List.of(
            "config.yml",
            "worlds.yml",
            "blocks.yml",
            "animations.yml",
            "warnings.yml",
            "menus/claim.yml",
            "menus/wiki.yml",
            "languages/en.yml",
            "languages/vi.yml",
            "languages/zh.yml",
            "loots/airdrops.yml",
            "loots/buried_stash.yml",
            "loots/deep_vault.yml",
            "loots/head_trophy.yml",
            "loots/sky_lantern.yml",
            "loots/sunken_treasure.yml",
            "loots/treasure_chest.yml"
    );

    private ConfigFolder() {
    }

    /**
     * @throws UncheckedIOException when a file that exists cannot be read
     */
    public static ConfigSources read(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(file(dataFolder.resolve("config.yml")))
                .worlds(file(dataFolder.resolve("worlds.yml")))
                .blocks(file(dataFolder.resolve("blocks.yml")))
                .animations(file(dataFolder.resolve("animations.yml")))
                .warnings(file(dataFolder.resolve("warnings.yml")));
        for (Path language : yamlFiles(dataFolder.resolve("languages"))) {
            sources.language(id(language), file(language));
        }
        for (Path loot : yamlFiles(dataFolder.resolve("loots"))) {
            sources.loot(id(loot), file(loot));
        }
        for (Path menu : yamlFiles(dataFolder.resolve("menus"))) {
            sources.menu(id(menu), file(menu));
        }
        return sources.build();
    }

    private static String id(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".yml".length());
    }

    private static List<Path> yamlFiles(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".yml") && Files.isRegularFile(path))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String file(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
