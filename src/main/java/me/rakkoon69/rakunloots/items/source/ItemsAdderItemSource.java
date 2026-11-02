package me.rakkoon69.rakunloots.items.source;

import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSource;
import me.rakkoon69.rakunloots.items.ItemWarning;
import me.rakkoon69.rakunloots.items.MissReason;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * ItemsAdder item source. The id keeps its colon (namespace:id).
 */
final class ItemsAdderItemSource implements ItemSource {

    static final String NAME = "itemsadder";
    private static final String PLUGIN_NAME = "ItemsAdder";

    private final BooleanSupplier present;
    private final Function<String, ItemStack> builder;

    ItemsAdderItemSource(BooleanSupplier present, Function<String, ItemStack> builder) {
        this.present = Objects.requireNonNull(present, "present");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    static ItemsAdderItemSource create() {
        return new ItemsAdderItemSource(
                () -> MmoItemsItemSource.isPluginEnabled(PLUGIN_NAME),
                ItemsAdderItemSource::buildFromPlugin
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
            var stack = dev.lone.itemsadder.api.CustomStack.getInstance(id);
            if (stack == null) {
                return null;
            }
            return stack.getItemStack();
        } catch (Throwable ex) {
            return null;
        }
    }
}
