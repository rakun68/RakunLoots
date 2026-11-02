package me.rakkoon69.rakunloots.items.source;

import me.rakkoon69.rakunloots.items.ItemSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The item sources RakunLoots ships, the one entry to this package.
 */
public final class ShippedItemSources {

    private ShippedItemSources() {
    }

    /**
     * Each shipped source by its name, vanilla first, then the plugin adapters.
     */
    public static Map<String, ItemSource> all() {
        Map<String, ItemSource> sources = new LinkedHashMap<>();
        sources.put(VanillaItemSource.NAME, new VanillaItemSource());
        sources.put(MmoItemsItemSource.NAME, MmoItemsItemSource.create());
        sources.put(ItemEditItemSource.NAME, ItemEditItemSource.create());
        sources.put(ExecutableItemsItemSource.NAME, ExecutableItemsItemSource.create());
        sources.put(OraxenItemSource.NAME, OraxenItemSource.create());
        sources.put(ItemsAdderItemSource.NAME, ItemsAdderItemSource.create());
        return sources;
    }
}
