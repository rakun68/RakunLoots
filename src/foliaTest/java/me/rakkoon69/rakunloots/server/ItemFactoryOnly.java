package me.rakkoon69.rakunloots.server;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;

import java.lang.reflect.Field;

/**
 * Sets a stand-in as the static Bukkit server for the length of a try block, so {@code ItemStack#isSimilar} can read
 * its item factory. The stand-in knows nothing else, so any other static Bukkit call still fails the test. Its factory
 * makes no item meta, so every stack reads as one without meta and two stacks of the same type are similar.
 */
final class ItemFactoryOnly implements AutoCloseable {

    private static final ItemFactory NO_META = FoliaFakes.fake(ItemFactory.class, "NoMetaItemFactory",
            (proxy, method, args) -> switch (method.getName() + "/" + args.length) {
                case "getItemMeta/1" -> null;
                case "equals/2" -> args[0] == args[1];
                default -> FoliaFakes.UNHANDLED;
            });

    private ItemFactoryOnly() {
    }

    static ItemFactoryOnly install() throws ReflectiveOperationException {
        Server standIn = FoliaFakes.fake(Server.class, "ItemFactoryOnlyServer", (proxy, method, args) ->
                method.getName().equals("getItemFactory") && args.length == 0 ? NO_META : FoliaFakes.UNHANDLED);
        set(standIn);
        return new ItemFactoryOnly();
    }

    @Override
    public void close() throws ReflectiveOperationException {
        set(null);
    }

    private static void set(Server server) throws ReflectiveOperationException {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }
}
