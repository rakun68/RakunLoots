package me.rakkoon69.rakunloots.items;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * MMOItems item source. Id is TYPE:ITEM split on the first colon.
 */
public final class MmoItemsItemSource implements ItemSource {

    static final String NAME = "mmoitems";
    private static final String PLUGIN_NAME = "MMOItems";

    private final BooleanSupplier present;
    private final BiFunction<String, String, ItemStack> builder;

    MmoItemsItemSource(BooleanSupplier present, BiFunction<String, String, ItemStack> builder) {
        this.present = Objects.requireNonNull(present, "present");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    static MmoItemsItemSource create() {
        return new MmoItemsItemSource(
                () -> isPluginEnabled(PLUGIN_NAME),
                MmoItemsItemSource::buildFromPlugin
        );
    }

    @Override
    public ItemResolveResult resolve(String id) {
        if (!present.getAsBoolean()) {
            return ItemResolveResult.miss(NAME, id, MissReason.PLUGIN_ABSENT, ItemWarning.pluginAbsent(NAME));
        }
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            return ItemResolveResult.miss(NAME, id, MissReason.UNKNOWN_ID, ItemWarning.unknownId(id));
        }
        String type = id.substring(0, colon);
        String itemId = id.substring(colon + 1);
        ItemStack item = builder.apply(type, itemId);
        if (item == null) {
            return ItemResolveResult.miss(NAME, id, MissReason.UNKNOWN_ID, ItemWarning.unknownId(id));
        }
        item.setAmount(1);
        return ItemResolveResult.hit(NAME, id, item);
    }

    private static ItemStack buildFromPlugin(String type, String itemId) {
        try {
            return net.Indyuce.mmoitems.MMOItems.plugin.getItem(
                    net.Indyuce.mmoitems.MMOItems.plugin.getTypes().get(type),
                    itemId
            );
        } catch (Throwable ex) {
            return null;
        }
    }

    static boolean isPluginEnabled(String name) {
        try {
            var plugin = Bukkit.getPluginManager().getPlugin(name);
            return plugin != null && plugin.isEnabled();
        } catch (Throwable ex) {
            return false;
        }
    }
}
