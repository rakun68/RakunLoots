package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static me.rakkoon69.rakunloots.config.ConfigValues.blockEntries;
import static me.rakkoon69.rakunloots.config.ConfigValues.parseRoot;

/**
 * Loads block groups from the shipped and the owner's {@code blocks.yml}.
 */
final class BlockGroupsConfig {

    private static final String KEY_GROUPS = "groups";
    private static final String KEY_OLD_NON_SOLID_BLOCKS = "non_solid_blocks";
    private static final String GROUP_NON_SOLID = "non_solid";
    private static final String SHIPPED_BLOCKS_RESOURCE = "/blocks.yml";
    private static final Pattern GROUP_NAME = Pattern.compile("[a-z0-9_]+");

    private BlockGroupsConfig() {
    }

    /**
     * The shipped groups, with each owner group of the same name in its place.
     * The old {@code non_solid_blocks} list fills {@code non_solid} when the owner's file does not define it.
     */
    static BlockListResolver load(
            ConfigSources sources,
            NameCatalog nameCatalog,
            Supplier<List<String>> knownBlockLines,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        Map<String, List<String>> written = new LinkedHashMap<>(ShippedBlocks.GROUPS);
        Map<String, List<String>> owner = Map.of();
        if (sources.blocks().isPresent()) {
            try {
                owner = legalGroupNames(parseBlockGroups(sources.blocks().get()), warnings);
            } catch (YAMLException ex) {
                warnings.add(ConfigWarning.blocksParseRejected(ConfigValues.quoteLine(sources.blocks().get(), ex)));
            }
        }
        written.putAll(owner);

        Optional<List<String>> oldNonSolid = parseOldNonSolid(sources.globals(), warnings);
        if (oldNonSolid.isPresent() && !owner.containsKey(GROUP_NON_SOLID)) {
            written.put(GROUP_NON_SOLID, oldNonSolid.get());
        }
        return BlockListResolver.load(written, nameCatalog, knownBlockLines, resolvedNames, warnings);
    }

    private static Optional<List<String>> parseOldNonSolid(Optional<String> globals, List<ConfigWarning> warnings) {
        if (globals.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> root = parseRoot("config.yml", globals.get());
        if (!root.containsKey(KEY_OLD_NON_SOLID_BLOCKS)) {
            return Optional.empty();
        }
        warnings.add(ConfigWarning.oldKey(KEY_OLD_NON_SOLID_BLOCKS));
        Object raw = root.get(KEY_OLD_NON_SOLID_BLOCKS);
        if (!(raw instanceof List<?>)) {
            return Optional.empty();
        }
        return Optional.of(blockEntries(raw));
    }

    private static Map<String, List<String>> legalGroupNames(
            Map<String, List<String>> groups,
            List<ConfigWarning> warnings
    ) {
        Map<String, List<String>> legal = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (GROUP_NAME.matcher(entry.getKey()).matches()) {
                legal.put(entry.getKey(), entry.getValue());
            } else {
                warnings.add(ConfigWarning.illegalGroupName(entry.getKey()));
            }
        }
        return legal;
    }

    private static Map<String, List<String>> parseBlockGroups(String content) {
        Object groupsNode = parseRoot(content).get(KEY_GROUPS);
        if (!(groupsNode instanceof Map<?, ?> groupsMap)) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : groupsMap.entrySet()) {
            if (entry.getKey() != null) {
                out.put(String.valueOf(entry.getKey()).trim(), blockEntries(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The groups in the bundled {@code blocks.yml}, read once. The plugin jar always carries the file.
     */
    private static final class ShippedBlocks {
        static final Map<String, List<String>> GROUPS = parseBlockGroups(read());

        private static String read() {
            try (InputStream in = BlockGroupsConfig.class.getResourceAsStream(SHIPPED_BLOCKS_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException(SHIPPED_BLOCKS_RESOURCE + " is missing from the plugin jar");
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
