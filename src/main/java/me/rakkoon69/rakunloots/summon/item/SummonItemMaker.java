package me.rakkoon69.rakunloots.summon.item;

import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Makes a loot's summon item and recognizes one. The item is the loot's summon item reference, resolved through the
 * item sources, and marked in its persistent data with the loot's id and a hash of the resolved item. Recognition
 * reads the loot id; the hash tells whether the loot's summon item changed since the item was made.
 *
 * <p>The mark is persistent data on the item's meta, which Spigot, Paper, and Folia all read the same way.
 */
public final class SummonItemMaker implements SummonItemFactory {

    /**
     * Holds the id of the loot the item summons.
     */
    public static final NamespacedKey LOOT_ID_KEY = key("rakunloots:summon_loot");

    /**
     * Holds the hash of the loot's resolved summon item at the time the item was made.
     */
    public static final NamespacedKey HASH_KEY = key("rakunloots:summon_hash");

    private final ItemSourceRegistry items;

    public SummonItemMaker(ItemSourceRegistry items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    private static NamespacedKey key(String text) {
        return Objects.requireNonNull(NamespacedKey.fromString(text), text);
    }

    /**
     * One marked summon item of the loot, as the loot describes it now.
     *
     * @throws IllegalStateException when the loot has no summon item or its reference cannot be resolved
     */
    @Override
    public ItemStack create(Loot loot) {
        ItemStack item = resolve(loot);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("the summon item of loot " + loot.id() + " has no item meta to mark");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(LOOT_ID_KEY, PersistentDataType.STRING, loot.id());
        data.set(HASH_KEY, PersistentDataType.STRING, hash(item));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The loot id a summon item is marked with, or empty for any other item.
     */
    public Optional<String> lootIdOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer()
                .get(LOOT_ID_KEY, PersistentDataType.STRING));
    }

    /**
     * Whether a marked summon item no longer matches its loot's summon item: its hash differs from the hash of the
     * loot's summon item as it resolves now. An item whose loot's summon item cannot be resolved now is not outdated,
     * since nothing can replace it.
     */
    public boolean isOutdated(ItemStack stack, Loot loot) {
        Optional<String> stored = Optional.ofNullable(stack.getItemMeta())
                .map(meta -> meta.getPersistentDataContainer().get(HASH_KEY, PersistentDataType.STRING));
        try {
            return !stored.equals(Optional.of(hash(resolve(loot))));
        } catch (IllegalStateException cannotResolve) {
            return false;
        }
    }

    private ItemStack resolve(Loot loot) {
        Objects.requireNonNull(loot, "loot");
        ItemResolveResult result = items.resolve(loot.summonItem().orElseThrow(() ->
                new IllegalStateException("loot " + loot.id() + " has no summon item")));
        if (result.isMiss()) {
            throw new IllegalStateException("the summon item of loot " + loot.id() + " could not be built: "
                    + result.warnings());
        }
        ItemStack item = result.item().orElseThrow().clone();
        item.setAmount(1);
        return item;
    }

    /**
     * A hash of what the item is, whatever its amount: SHA-256 of its serialized form with map keys sorted, so the
     * same item always gives the same hash.
     */
    static String hash(ItemStack item) {
        ItemStack one = item.clone();
        one.setAmount(1);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical(one).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static String canonical(Object value) {
        if (value instanceof ConfigurationSerializable serializable) {
            return ConfigurationSerialization.getAlias(serializable.getClass()) + canonical(serializable.serialize());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, String> sorted = new TreeMap<>();
            map.forEach((key, entry) -> sorted.put(String.valueOf(key), canonical(entry)));
            return sorted.toString();
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            collection.forEach(entry -> parts.add(canonical(entry)));
            return parts.toString();
        }
        return String.valueOf(value);
    }
}
