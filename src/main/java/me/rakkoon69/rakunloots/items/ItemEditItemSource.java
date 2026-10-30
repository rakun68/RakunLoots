package me.rakkoon69.rakunloots.items;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * ItemEdit item source. The id is taken as one string. ItemEdit is reached by reflection.
 */
public final class ItemEditItemSource implements ItemSource {

    static final String NAME = "itemedit";
    private static final String PLUGIN_NAME = "ItemEdit";
    private static final String ITEM_EDIT_CLASS = "emanondev.itemedit.ItemEdit";

    private final BooleanSupplier present;
    private final Function<String, ItemStack> builder;

    ItemEditItemSource(BooleanSupplier present, Function<String, ItemStack> builder) {
        this.present = Objects.requireNonNull(present, "present");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    static ItemEditItemSource create() {
        return new ItemEditItemSource(
                () -> MmoItemsItemSource.isPluginEnabled(PLUGIN_NAME),
                ItemEditItemSource::findItem
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

    /**
     * Looks up an ItemEdit server item by reflection, so the build has no ItemEdit dependency.
     * Calls {@code emanondev.itemedit.ItemEdit.get().getServerStorage().getItem(id)}.
     *
     * @return the stored item, or null when ItemEdit is not loaded or has no item with that id
     */
    public static ItemStack findItem(String id) {
        try {
            Class<?> itemEdit = Class.forName(ITEM_EDIT_CLASS);
            Object plugin = itemEdit.getMethod("get").invoke(null);
            Method getServerStorage = itemEdit.getMethod("getServerStorage");
            Object storage = getServerStorage.invoke(plugin);
            if (storage == null) {
                return null;
            }
            Object item = getServerStorage.getReturnType()
                    .getMethod("getItem", String.class)
                    .invoke(storage, id);
            return item instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return null;
        }
    }
}
