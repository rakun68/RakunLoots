package me.rakkoon69.rakunloots.items;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * ExecutableItems item source. The id is taken as one string. Built with no player and amount 1.
 */
public final class ExecutableItemsItemSource implements ItemSource {

    static final String NAME = "executableitems";
    private static final String PLUGIN_NAME = "ExecutableItems";

    private final BooleanSupplier present;
    private final Function<String, ItemStack> builder;

    ExecutableItemsItemSource(BooleanSupplier present, Function<String, ItemStack> builder) {
        this.present = Objects.requireNonNull(present, "present");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    static ExecutableItemsItemSource create() {
        return new ExecutableItemsItemSource(
                () -> MmoItemsItemSource.isPluginEnabled(PLUGIN_NAME),
                ExecutableItemsItemSource::buildFromPlugin
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
            var opt = com.ssomar.score.api.executableitems.ExecutableItemsAPI
                    .getExecutableItemsManager()
                    .getExecutableItem(id);
            if (opt.isEmpty()) {
                return null;
            }
            return opt.get().buildItem(1, java.util.Optional.empty(), java.util.Optional.empty());
        } catch (Throwable ex) {
            return null;
        }
    }
}
