package me.rakkoon69.rakunloots.storage;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockUnsafeValues;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.inventory.ItemFactoryMock;
import be.seeseemelk.mockbukkit.inventory.meta.ItemMetaMock;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * A MockBukkit server whose item stacks come back through Bukkit object streams, as they do on a real server.
 * MockBukkit leaves the pieces that reading a stack needs unimplemented and does not register its item meta.
 * Only what MockBukkit's plain item meta round-trips is covered; enchantments are not.
 */
public final class StreamableServerMock extends ServerMock {

    private final MockUnsafeValues unsafe = new MockUnsafeValues() {
        @Override
        public Material getMaterial(String material, int version) {
            return Material.getMaterial(material);
        }
    };

    private final ItemFactory itemFactory = new ItemFactoryMock() {
        /**
         * Empty meta equals no meta, as on CraftBukkit, so a plain stack writes no meta.
         */
        @Override
        public boolean equals(ItemMeta meta1, ItemMeta meta2) {
            ItemMeta empty = new ItemMetaMock();
            return Objects.equals(meta1 == null ? empty : meta1, meta2 == null ? empty : meta2);
        }

        @Override
        public @NotNull ItemStack ensureServerConversions(@NotNull ItemStack item) {
            return item;
        }
    };

    private StreamableServerMock() {
    }

    /**
     * Starts the mock server in place of {@code MockBukkit.mock()}. Stop it with {@code MockBukkit.unmock()}.
     */
    public static StreamableServerMock mock() {
        StreamableServerMock server = MockBukkit.mock(new StreamableServerMock());
        rememberItemMeta();
        return server;
    }

    /**
     * Makes stored stacks that carry item meta unreadable, as when a server downgrade drops a class they name.
     * Stacks without meta still read. {@link #rememberItemMeta()} undoes it.
     */
    static void forgetItemMeta() {
        ConfigurationSerialization.unregisterClass(ItemMetaMock.class.getName());
    }

    static void rememberItemMeta() {
        ConfigurationSerialization.registerClass(StreamableItemMeta.class, ItemMetaMock.class.getName());
    }

    @Override
    public MockUnsafeValues getUnsafe() {
        return unsafe;
    }

    @Override
    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    /**
     * Read back in place of MockBukkit's meta, which throws when the stack sets the data version.
     */
    public static final class StreamableItemMeta extends ItemMetaMock {

        private StreamableItemMeta(ItemMeta meta) {
            super(meta);
        }

        public static StreamableItemMeta deserialize(Map<String, Object> args) {
            return new StreamableItemMeta(ItemMetaMock.deserialize(args));
        }

        @Override
        public void setVersion(int version) {
            // CraftBukkit upgrades old meta here. The mock has nothing to upgrade.
        }
    }
}
