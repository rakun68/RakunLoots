package me.rakkoon69.rakunloots.items;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Oraxen item source. The id is taken as one string.
 */
public final class OraxenItemSource implements ItemSource {

    static final String NAME = "oraxen";
    private static final String PLUGIN_NAME = "Oraxen";

    private final BooleanSupplier present;
    private final Function<String, ItemStack> builder;

    OraxenItemSource(BooleanSupplier present, Function<String, ItemStack> builder) {
        this.present = Objects.requireNonNull(present, "present");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    static OraxenItemSource create() {
        return new OraxenItemSource(
                () -> MmoItemsItemSource.isPluginEnabled(PLUGIN_NAME),
                OraxenItemSource::buildFromPlugin
        );
    }

    @Override
    public ItemResolveResult resolve(String id) {
        if (!present.getAsBoolean()) {
            return ItemResolveResult.miss(NAME, id, MissReason.PLUGIN_ABSENT, ItemWarning.pluginAbsent(NAME));
        }
        ItemStack item = builder.apply(id);
        if (item == null) {
            return ItemResolveResult.miss(NAME, id, MissReason.UNKNOWN_ID, ItemWarning.unknownId(id));
        }
        item.setAmount(1);
        return ItemResolveResult.hit(NAME, id, item);
    }

    private static ItemStack buildFromPlugin(String id) {
        try {
            var builder = io.th0rgal.oraxen.api.OraxenItems.getItemById(id);
            if (builder == null) {
                return null;
            }
            return builder.build();
        } catch (Throwable ex) {
            return null;
        }
    }
}
