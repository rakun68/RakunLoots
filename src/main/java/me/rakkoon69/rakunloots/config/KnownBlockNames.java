package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.match.CloseMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Every block id and block tag of every supported version, built from a list of lines: ids, then tags written with a
 * leading {@code #}. Ids and tags are two separate pools, for exact lookup and for close matches.
 * A load builds it on its first miss and drops it when the load ends.
 */
final class KnownBlockNames {

    private static final String SHIPPED_RESOURCE = "/known_blocks.txt";
    private static final String TAG_PREFIX = "#";
    private static final String DEFAULT_NAMESPACE = "minecraft:";

    private final Set<String> ids;
    private final Set<String> tags;
    private final CloseMatcher idMatcher;
    private final CloseMatcher tagMatcher;

    private KnownBlockNames(List<String> lines) {
        List<String> idList = new ArrayList<>();
        List<String> tagList = new ArrayList<>();
        for (String line : lines) {
            String name = line.trim();
            if (name.isEmpty()) {
                continue;
            }
            (name.startsWith(TAG_PREFIX) ? tagList : idList).add(name);
        }
        ids = new HashSet<>(idList);
        tags = new HashSet<>(tagList);
        idMatcher = CloseMatcher.of(idList);
        tagMatcher = CloseMatcher.of(tagList);
    }

    /**
     * The shipped list. Each call reads the jar again.
     */
    static Supplier<List<String>> shippedLines() {
        return () -> {
            try (InputStream in = KnownBlockNames.class.getResourceAsStream(SHIPPED_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("missing resource " + SHIPPED_RESOURCE);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            } catch (IOException ex) {
                throw new IllegalStateException("cannot read " + SHIPPED_RESOURCE, ex);
            }
        };
    }

    static KnownBlockNames of(List<String> lines) {
        return new KnownBlockNames(lines);
    }

    /**
     * True when some version has this block id, or this tag written with its leading {@code #}. A name written
     * without a namespace, as the server accepts it, is read as {@code minecraft:}.
     */
    boolean knows(String name) {
        String full = withNamespace(name);
        return name.startsWith(TAG_PREFIX) ? tags.contains(full) : ids.contains(full);
    }

    /**
     * Up to 3 names of the same pool as the one typed, closest first. A name typed without a namespace gets its
     * matches without {@code minecraft:}, so {@code stoen} suggests {@code stone}.
     */
    List<String> closest(String name) {
        String full = withNamespace(name);
        List<String> matches = (name.startsWith(TAG_PREFIX) ? tagMatcher : idMatcher).closest(full);
        if (full.equals(name)) {
            return matches;
        }
        String lead = name.startsWith(TAG_PREFIX) ? TAG_PREFIX : "";
        String prefix = lead + DEFAULT_NAMESPACE;
        return matches.stream()
                .map(match -> match.startsWith(prefix) ? lead + match.substring(prefix.length()) : match)
                .toList();
    }

    private static String withNamespace(String name) {
        boolean tag = name.startsWith(TAG_PREFIX);
        String path = tag ? name.substring(TAG_PREFIX.length()) : name;
        return path.indexOf(':') >= 0 ? name : (tag ? TAG_PREFIX : "") + DEFAULT_NAMESPACE + path;
    }
}
