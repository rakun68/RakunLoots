package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Loads globals, language files, world settings, and loot files from in-memory contents.
 * Takes no Bukkit types. Returns immutable loots on the snapshot when a loot file has the new shape.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static ConfigSnapshot load(
            ConfigSources sources, NameCatalog nameCatalog, Collection<? extends WarningKind> knownKinds) {
        return load(sources, nameCatalog, KnownBlockNames.shippedLines(), knownKinds);
    }

    /**
     * Loads with another list of known block names in place of the shipped one: ids, then tags written with a leading
     * {@code #}. The supplier is called at most once, on the first block list miss of the load.
     */
    public static ConfigSnapshot load(
            ConfigSources sources, NameCatalog nameCatalog, Supplier<List<String>> knownBlockLines,
            Collection<? extends WarningKind> knownKinds) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(nameCatalog, "nameCatalog");
        Objects.requireNonNull(knownBlockLines, "knownBlockLines");
        Objects.requireNonNull(knownKinds, "knownKinds");
        return ConfigLoadSupport.load(sources, nameCatalog, knownBlockLines, knownKinds);
    }
}
