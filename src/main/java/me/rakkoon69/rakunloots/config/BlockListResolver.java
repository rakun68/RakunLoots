package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Turns a written block list into a flat set of current block ids.
 * An entry is {@code @group}, {@code #namespace:tag}, or a block id. Every name resolves through the name catalog.
 * Groups that sit in a cycle are refused before any list resolves.
 * An entry written with a leading {@code ?} is optional: it matches only the exact id or tag the server knows, never
 * through an old name, and a miss is collected for one low warning instead of warned on its own.
 */
final class BlockListResolver {

    private static final String GROUP_PREFIX = "@";
    private static final String TAG_PREFIX = "#";
    private static final String OPTIONAL_PREFIX = "?";
    /** A config of thousands of typos must not run thousands of close-match searches, or log thousands of lines. */
    static final int TYPO_MATCH_LIMIT = 20;

    private final NameCatalog nameCatalog;
    private final Supplier<List<String>> knownBlockLines;
    /** Built on the first miss of a load, dropped when the load ends. */
    private KnownBlockNames known;
    private final Map<String, String> resolvedNames;
    private final List<ConfigWarning> warnings;
    private final Map<String, List<String>> writtenGroups;
    private final Set<String> refused = new LinkedHashSet<>();
    private final Set<String> skippedOptional = new LinkedHashSet<>();
    /** Optional entries already warned as typos, so each warns once. */
    private final Set<String> typos = new LinkedHashSet<>();
    private final Map<String, Set<String>> groups = new LinkedHashMap<>();
    /** Typos warned on their own this load, counted until the load ends. */
    private int typoCount;
    /** Typos past the limit this load, for the one line that lists them. */
    private final Set<String> moreTypos = new LinkedHashSet<>();

    private BlockListResolver(
            Map<String, List<String>> writtenGroups,
            NameCatalog nameCatalog,
            Supplier<List<String>> knownBlockLines,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        this.writtenGroups = writtenGroups;
        this.nameCatalog = nameCatalog;
        this.knownBlockLines = knownBlockLines;
        this.resolvedNames = resolvedNames;
        this.warnings = warnings;
    }

    /**
     * Resolves every written group. Names newly met are added to {@code resolvedNames} and warned once.
     */
    static BlockListResolver load(
            Map<String, List<String>> writtenGroups,
            NameCatalog nameCatalog,
            Supplier<List<String>> knownBlockLines,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        BlockListResolver resolver = new BlockListResolver(
                new LinkedHashMap<>(Objects.requireNonNull(writtenGroups, "writtenGroups")),
                Objects.requireNonNull(nameCatalog, "nameCatalog"),
                Objects.requireNonNull(knownBlockLines, "knownBlockLines"),
                Objects.requireNonNull(resolvedNames, "resolvedNames"),
                Objects.requireNonNull(warnings, "warnings")
        );
        resolver.refuseCycles();
        for (String name : resolver.writtenGroups.keySet()) {
            if (!resolver.refused.contains(name)) {
                resolver.group(name);
            }
        }
        return resolver;
    }

    /**
     * Resolved groups by name, in written order. Refused groups are absent.
     */
    Map<String, Set<String>> groups() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    /**
     * A block list written in a loot. An unknown or refused group drops that entry with a high warning naming the loot.
     */
    Set<String> resolve(String lootId, List<String> entries) {
        Objects.requireNonNull(lootId, "lootId");
        Objects.requireNonNull(entries, "entries");
        return resolveEntries(entries, Optional.empty(), Optional.of(lootId), Optional.empty());
    }

    /**
     * A block list written in the globals file. An unknown or refused group drops that entry with a high warning
     * naming the key.
     */
    Set<String> resolveGlobal(String key, List<String> entries) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entries, "entries");
        return resolveEntries(entries, Optional.empty(), Optional.empty(), Optional.of(key));
    }

    /**
     * Adds one low warning listing every optional entry skipped since the last call, in the order they were met.
     * Nothing is added when none was skipped. Call once after every list has resolved.
     */
    void warnSkippedOptional() {
        if (!skippedOptional.isEmpty()) {
            warnings.add(ConfigWarning.skippedOptionalEntries(List.copyOf(skippedOptional)));
            skippedOptional.clear();
        }
        if (!moreTypos.isEmpty()) {
            warnings.add(ConfigWarning.moreTypos(List.copyOf(moreTypos)));
            moreTypos.clear();
        }
        typoCount = 0;
        known = null;
    }

    private Set<String> group(String name) {
        Set<String> done = groups.get(name);
        if (done != null) {
            return done;
        }
        Set<String> ids = resolveEntries(
                writtenGroups.get(name), Optional.of(name), Optional.empty(), Optional.empty());
        if (ids.isEmpty()) {
            warnings.add(ConfigWarning.emptyGroup(name));
        }
        groups.put(name, ids);
        return ids;
    }

    private Set<String> resolveEntries(
            List<String> entries,
            Optional<String> groupName,
            Optional<String> lootId,
            Optional<String> globalKey
    ) {
        Set<String> ids = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry.startsWith(OPTIONAL_PREFIX)) {
                optionalEntry(entry, ids);
            } else if (entry.startsWith(GROUP_PREFIX)) {
                String referenced = entry.substring(GROUP_PREFIX.length()).trim();
                if (!writtenGroups.containsKey(referenced) || refused.contains(referenced)) {
                    warnings.add(globalKey.isPresent()
                            ? ConfigWarning.unknownGroup(globalKey.get(), entry)
                            : ConfigWarning.unknownGroup(groupName, lootId, entry));
                } else {
                    ids.addAll(group(referenced));
                }
            } else if (entry.startsWith(TAG_PREFIX)) {
                String tag = entry.substring(TAG_PREFIX.length()).trim();
                Set<String> members = nameCatalog.tagMembers(tag);
                if (members.isEmpty()) {
                    String name = TAG_PREFIX + tag;
                    if (known().knows(name)) {
                        skippedOptional.add(entry);
                    } else {
                        typo(entry, name, matches -> globalKey.isPresent()
                                ? ConfigWarning.unknownTag(globalKey.get(), entry, matches)
                                : ConfigWarning.unknownTag(groupName, lootId, entry, matches));
                    }
                } else {
                    ids.addAll(members);
                }
            } else {
                blockId(entry).ifPresent(ids::add);
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Adds the exact id or the tag's members when this server knows the name, else collects the entry as skipped.
     * A written id that the catalog answers with another id is an old name, so it is skipped too.
     */
    private void optionalEntry(String entry, Set<String> ids) {
        String name = entry.substring(OPTIONAL_PREFIX.length()).trim();
        Set<String> members = name.startsWith(TAG_PREFIX)
                ? nameCatalog.tagMembers(name.substring(TAG_PREFIX.length()).trim())
                : nameCatalog.currentId(name).filter(name::equals).map(Set::of).orElse(Set.of());
        if (!members.isEmpty()) {
            ids.addAll(members);
        } else if (known().knows(name)) {
            skippedOptional.add(entry);
        } else if (typos.add(name)) {
            typo(name, name, matches -> name.startsWith(TAG_PREFIX)
                    ? ConfigWarning.unknownTag(Optional.empty(), Optional.empty(), name, matches)
                    : ConfigWarning.unknownName(name, matches));
        }
    }

    /**
     * The names every supported version knows. Read from the list on the first miss of a load.
     */
    private KnownBlockNames known() {
        if (known == null) {
            known = KnownBlockNames.of(knownBlockLines.get());
        }
        return known;
    }

    /**
     * Warns a typo high with the close matches of {@code name}. Past {@link #TYPO_MATCH_LIMIT} in a load, the entry
     * runs no search and joins the one line that lists the rest.
     */
    private void typo(String entry, String name, Function<List<String>, ConfigWarning> warning) {
        if (typoCount < TYPO_MATCH_LIMIT) {
            typoCount++;
            warnings.add(warning.apply(known().closest(name)));
        } else {
            moreTypos.add(entry);
        }
    }

    private Optional<String> blockId(String written) {
        Optional<String> current = nameCatalog.currentId(written);
        boolean seen = resolvedNames.containsKey(written);
        resolvedNames.putIfAbsent(written, current.orElse(written));
        if (!seen) {
            if (current.isEmpty()) {
                if (known().knows(written)) {
                    skippedOptional.add(written);
                } else {
                    typo(written, written, matches -> ConfigWarning.unknownName(written, matches));
                }
            } else if (!current.get().equals(written)) {
                warnings.add(ConfigWarning.oldName(written, current.get()));
            }
        }
        return current;
    }

    /**
     * Tarjan's strongly connected components over {@code @group} references between written groups.
     * A component of two or more groups, or one group naming itself, is a cycle.
     */
    private void refuseCycles() {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : writtenGroups.entrySet()) {
            Set<String> targets = new LinkedHashSet<>();
            for (String written : entry.getValue()) {
                if (written.startsWith(GROUP_PREFIX)) {
                    String referenced = written.substring(GROUP_PREFIX.length()).trim();
                    if (writtenGroups.containsKey(referenced)) {
                        targets.add(referenced);
                    }
                }
            }
            edges.put(entry.getKey(), targets);
        }
        new CycleFinder(edges).run();
    }

    private final class CycleFinder {
        private final Map<String, Set<String>> edges;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new LinkedHashSet<>();
        private int next;

        CycleFinder(Map<String, Set<String>> edges) {
            this.edges = edges;
        }

        void run() {
            for (String name : edges.keySet()) {
                if (!index.containsKey(name)) {
                    visit(name);
                }
            }
        }

        private void visit(String name) {
            index.put(name, next);
            lowLink.put(name, next);
            next++;
            stack.push(name);
            onStack.add(name);

            for (String target : edges.get(name)) {
                if (!index.containsKey(target)) {
                    visit(target);
                    lowLink.put(name, Math.min(lowLink.get(name), lowLink.get(target)));
                } else if (onStack.contains(target)) {
                    lowLink.put(name, Math.min(lowLink.get(name), index.get(target)));
                }
            }

            if (!lowLink.get(name).equals(index.get(name))) {
                return;
            }
            List<String> component = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(name));

            if (component.size() > 1 || edges.get(name).contains(name)) {
                Collections.sort(component);
                for (String group : component) {
                    refused.add(group);
                    warnings.add(ConfigWarning.groupCycle(group, component));
                }
            }
        }
    }
}
