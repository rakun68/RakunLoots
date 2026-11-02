package me.rakkoon69.rakunloots.items;

import me.rakkoon69.rakunloots.items.source.ShippedItemSources;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves item references. Create one in tests or hold one on the plugin.
 * There is no unregister. Resolve does not write to the log.
 */
public final class ItemSourceRegistry {

    /**
     * The source a reference without one resolves through, and the one whose items take vanilla additions here.
     */
    private static final String VANILLA = "vanilla";

    static final Set<String> SHIPPED_NAMES = Set.of(
            "vanilla",
            "mmoitems",
            "itemedit",
            "executableitems",
            "oraxen",
            "itemsadder"
    );

    private final Map<String, ItemSource> sources = new LinkedHashMap<>();

    private ItemSourceRegistry() {
    }

    public static ItemSourceRegistry create() {
        ItemSourceRegistry registry = new ItemSourceRegistry();
        registry.sources.putAll(ShippedItemSources.all());
        return registry;
    }

    /**
     * @return true when the name was accepted; false when refused (blank, shipped, or already taken)
     */
    public boolean register(String name, ItemSource source) {
        Objects.requireNonNull(source, "source");
        if (name == null) {
            return false;
        }
        String canonical = name.trim().toLowerCase(Locale.ROOT);
        if (canonical.isEmpty()) {
            return false;
        }
        if (SHIPPED_NAMES.contains(canonical)) {
            return false;
        }
        if (sources.containsKey(canonical)) {
            return false;
        }
        sources.put(canonical, source);
        return true;
    }

    public ItemResolveResult resolve(ItemReference reference) {
        Objects.requireNonNull(reference, "reference");
        String rawSource = reference.source();
        String id = reference.id() == null ? "" : reference.id().trim();

        String canonicalSource;
        if (rawSource == null || rawSource.trim().isEmpty()) {
            canonicalSource = VANILLA;
        } else {
            canonicalSource = rawSource.trim().toLowerCase(Locale.ROOT);
        }

        ItemSource source = sources.get(canonicalSource);
        if (source == null) {
            return ItemResolveResult.miss(
                    canonicalSource,
                    id,
                    MissReason.UNKNOWN_SOURCE,
                    ItemWarning.unknownSource(canonicalSource)
            );
        }

        if (id.isEmpty()) {
            return ItemResolveResult.miss(
                    canonicalSource,
                    id,
                    MissReason.BLANK_ID,
                    ItemWarning.blankId()
            );
        }

        ItemResolveResult result = source.resolve(id);
        if (result.isMiss()) {
            return result;
        }

        VanillaAdditions additions = reference.additions();
        if (VANILLA.equals(canonicalSource)) {
            return VanillaAdditionsApplier.apply(
                    result.source(),
                    result.id(),
                    result.item().orElseThrow(),
                    additions
            );
        }
        // Non-vanilla sources keep the item they built; additions are ignored with no warning.
        return result;
    }
}
