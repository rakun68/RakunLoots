package me.rakkoon69.rakunloots.items;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import me.rakkoon69.rakunloots.text.MessageText;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies vanilla additions to a built vanilla stack. Nbt first, then explicit keys.
 * One bad addition is skipped with a high warning; the item still builds.
 */
final class VanillaAdditionsApplier {

    @FunctionalInterface
    interface NbtWriter {
        void write(ItemStack stack, Map<String, Object> nbt, List<ItemWarning> warnings);
    }

    /** Overridable for tests that prove nbt-then-explicit ordering without CraftBukkit. */
    static NbtWriter nbtWriter = VanillaAdditionsApplier::writeNbtWithLibrary;

    private VanillaAdditionsApplier() {
    }

    static ItemResolveResult apply(String source, String id, ItemStack item, VanillaAdditions additions) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(additions, "additions");
        if (additions.isEmpty()) {
            return ItemResolveResult.hit(source, id, item);
        }

        List<ItemWarning> warnings = new ArrayList<>();
        ItemStack stack = item.clone();

        additions.nbt().ifPresent(nbt -> {
            if (!nbt.isEmpty()) {
                nbtWriter.write(stack, nbt, warnings);
            }
        });

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return ItemResolveResult.hit(source, id, stack, warnings);
        }

        additions.name().ifPresent(name -> {
            if (name.isEmpty()) {
                meta.setDisplayName(null);
            } else {
                meta.setDisplayName(MessageText.color(name));
            }
        });

        additions.lore().ifPresent(lore -> {
            if (lore.isEmpty()) {
                meta.setLore(null);
            } else {
                List<String> colored = new ArrayList<>(lore.size());
                for (String line : lore) {
                    colored.add(MessageText.color(line));
                }
                meta.setLore(colored);
            }
        });

        additions.enchants().ifPresent(enchants -> applyEnchants(stack, meta, enchants, warnings));

        additions.flags().ifPresent(flags -> applyFlags(meta, flags, warnings));

        additions.customModelData().ifPresent(value -> applyCustomModelData(meta, value, warnings));

        additions.unbreakable().ifPresent(value -> applyUnbreakable(meta, value, warnings));

        additions.damage().ifPresent(value -> applyDamage(meta, value, warnings));

        stack.setItemMeta(meta);
        return ItemResolveResult.hit(source, id, stack, warnings);
    }

    private static void writeNbtWithLibrary(ItemStack stack, Map<String, Object> nbt, List<ItemWarning> warnings) {
        try {
            NBT.modify(stack, (Consumer<ReadWriteItemNBT>) compound -> {
                writeMap(compound, nbt, warnings);
            });
        } catch (Throwable ex) {
            warnings.add(ItemWarning.skippedAddition("nbt"));
        }
    }

    private static void writeMap(ReadWriteNBT compound, Map<String, Object> map, List<ItemWarning> warnings) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || key.isEmpty()) {
                warnings.add(ItemWarning.skippedAddition("nbt:" + key));
                continue;
            }
            try {
                if (value instanceof Map<?, ?> nested) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> asMap = (Map<String, Object>) nested;
                    ReadWriteNBT child = compound.getOrCreateCompound(key);
                    writeMap(child, new LinkedHashMap<>(asMap), warnings);
                } else if (value instanceof String s) {
                    compound.setString(key, s);
                } else if (value instanceof Integer i) {
                    compound.setInteger(key, i);
                } else if (value instanceof Long l) {
                    compound.setLong(key, l);
                } else if (value instanceof Double d) {
                    compound.setDouble(key, d);
                } else if (value instanceof Float f) {
                    compound.setFloat(key, f);
                } else if (value instanceof Boolean b) {
                    compound.setBoolean(key, b);
                } else if (value instanceof Byte by) {
                    compound.setByte(key, by);
                } else if (value instanceof Short sh) {
                    compound.setShort(key, sh);
                } else {
                    warnings.add(ItemWarning.skippedAddition("nbt:" + key));
                }
            } catch (RuntimeException ex) {
                warnings.add(ItemWarning.skippedAddition("nbt:" + key));
            }
        }
    }

    private static void applyEnchants(
            ItemStack stack,
            ItemMeta meta,
            List<String> enchants,
            List<ItemWarning> warnings
    ) {
        if (enchants.isEmpty()) {
            if (meta instanceof EnchantmentStorageMeta storage) {
                for (Enchantment enchantment : new ArrayList<>(storage.getStoredEnchants().keySet())) {
                    storage.removeStoredEnchant(enchantment);
                }
            } else {
                for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
                    meta.removeEnchant(enchantment);
                }
            }
            return;
        }

        Map<Enchantment, Integer> resolved = new LinkedHashMap<>();
        for (String line : enchants) {
            int colon = line == null ? -1 : line.indexOf(':');
            if (line == null || colon <= 0 || colon == line.length() - 1) {
                warnings.add(ItemWarning.skippedAddition("enchant:" + line));
                continue;
            }
            String name = line.substring(0, colon).trim();
            String levelText = line.substring(colon + 1).trim();
            Enchantment enchantment = resolveEnchantment(name);
            if (enchantment == null) {
                warnings.add(ItemWarning.skippedAddition("enchant:" + line));
                continue;
            }
            int level;
            try {
                level = Integer.parseInt(levelText);
            } catch (NumberFormatException ex) {
                warnings.add(ItemWarning.skippedAddition("enchant:" + line));
                continue;
            }
            if (level < 1) {
                warnings.add(ItemWarning.skippedAddition("enchant:" + line));
                continue;
            }
            resolved.put(enchantment, level);
        }

        boolean book = stack.getType() == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta;
        if (book) {
            EnchantmentStorageMeta storage = (EnchantmentStorageMeta) meta;
            for (Map.Entry<Enchantment, Integer> entry : resolved.entrySet()) {
                storage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
        } else {
            for (Map.Entry<Enchantment, Integer> entry : resolved.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
    }

    private static Enchantment resolveEnchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Enchantment byName = Enchantment.getByName(name.toUpperCase(Locale.ROOT));
        if (byName != null) {
            return byName;
        }
        String key = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        return Enchantment.getByKey(NamespacedKey.minecraft(key));
    }

    private static void applyFlags(ItemMeta meta, List<String> flags, List<ItemWarning> warnings) {
        if (flags.isEmpty()) {
            for (ItemFlag flag : new ArrayList<>(meta.getItemFlags())) {
                meta.removeItemFlags(flag);
            }
            return;
        }
        for (String flagName : flags) {
            try {
                ItemFlag flag = ItemFlag.valueOf(flagName.trim().toUpperCase(Locale.ROOT));
                meta.addItemFlags(flag);
            } catch (RuntimeException ex) {
                warnings.add(ItemWarning.skippedAddition("flag:" + flagName));
            }
        }
    }

    private static void applyCustomModelData(ItemMeta meta, Object value, List<ItemWarning> warnings) {
        Integer parsed = asInteger(value);
        if (parsed == null) {
            warnings.add(ItemWarning.skippedAddition("custom_model_data"));
            return;
        }
        meta.setCustomModelData(parsed);
    }

    private static void applyUnbreakable(ItemMeta meta, Object value, List<ItemWarning> warnings) {
        Boolean parsed = asBoolean(value);
        if (parsed == null) {
            warnings.add(ItemWarning.skippedAddition("unbreakable"));
            return;
        }
        meta.setUnbreakable(parsed);
    }

    private static void applyDamage(ItemMeta meta, Object value, List<ItemWarning> warnings) {
        if (!(meta instanceof Damageable damageable)) {
            warnings.add(ItemWarning.skippedAddition("damage"));
            return;
        }
        Integer parsed = asInteger(value);
        if (parsed == null) {
            warnings.add(ItemWarning.skippedAddition("damage"));
            return;
        }
        damageable.setDamage(parsed);
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(s.trim())) {
                return false;
            }
        }
        return null;
    }
}
