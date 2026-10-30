package me.rakkoon69.rakunloots.items;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaAdditionsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void vanillaDiamondKeepsColoredNameAndLoreWithAmpersandAndHex() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        ItemReference reference = ItemReference.of("vanilla", "diamond", VanillaAdditions.builder()
                .name("&cRuby <#00FF00>Gem")
                .lore(List.of("&7Line <#112233>two"))
                .build());

        ItemResolveResult result = registry.resolve(reference);
        assertTrue(result.isHit());
        ItemMeta meta = result.item().orElseThrow().getItemMeta();
        assertTrue(meta.hasDisplayName());
        assertTrue(meta.getDisplayName().contains(ChatColor.RED.toString()));
        assertTrue(meta.getDisplayName().contains("Ruby"));
        assertTrue(meta.hasLore());
        assertEquals(1, meta.getLore().size());
        assertTrue(meta.getLore().get(0).contains("Line"));
    }

    @Test
    void missingAdditionLeavesVanillaValueAndEmptyClears() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult plain = registry.resolve(ItemReference.of("vanilla", "diamond"));
        ItemMeta plainMeta = plain.item().orElseThrow().getItemMeta();
        assertFalse(plainMeta.hasDisplayName());
        assertFalse(plainMeta.hasLore());

        ItemResolveResult cleared = registry.resolve(ItemReference.of("vanilla", "diamond", VanillaAdditions.builder()
                .name("")
                .lore(List.of())
                .flags(List.of())
                .build()));
        ItemMeta clearedMeta = cleared.item().orElseThrow().getItemMeta();
        assertFalse(clearedMeta.hasDisplayName());
        assertTrue(clearedMeta.getLore() == null || clearedMeta.getLore().isEmpty());
        assertEquals(0, clearedMeta.getItemFlags().size());
    }

    @Test
    void enchantmentsApplyByEitherNameStoredOnBookAndLastLevelWins() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult sword = registry.resolve(ItemReference.of("vanilla", "diamond_sword", VanillaAdditions.builder()
                .enchants(List.of("sharpness:2", "DAMAGE_ALL:5", "sharpness:3"))
                .build()));
        assertTrue(sword.isHit());
        assertEquals(3, sword.item().orElseThrow().getEnchantmentLevel(Enchantment.DAMAGE_ALL));

        ItemResolveResult book = registry.resolve(ItemReference.of("vanilla", "enchanted_book", VanillaAdditions.builder()
                .enchants(List.of("sharpness:2"))
                .build()));
        assertTrue(book.isHit());
        EnchantmentStorageMeta storage = (EnchantmentStorageMeta) book.item().orElseThrow().getItemMeta();
        assertEquals(2, storage.getStoredEnchantLevel(Enchantment.DAMAGE_ALL));
        assertFalse(book.item().orElseThrow().containsEnchantment(Enchantment.DAMAGE_ALL));
    }

    @Test
    void unknownEnchantmentIsSkippedWithHighWarningAndItemRemains() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult result = registry.resolve(ItemReference.of("vanilla", "diamond", VanillaAdditions.builder()
                .name("&aOk")
                .enchants(List.of("not_an_enchant:1"))
                .build()));

        assertTrue(result.isHit());
        assertEquals(Material.DIAMOND, result.item().orElseThrow().getType());
        assertTrue(result.item().orElseThrow().getItemMeta().hasDisplayName());
        assertTrue(result.warnings().stream().anyMatch(w ->
                w.kind() == ItemWarning.Kind.SKIPPED_ADDITION
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    @Test
    void badFlagModelDataUnbreakableDamageOrNbtIsSkippedWithHighWarningAndItemRemains() {
        VanillaAdditionsApplier.NbtWriter previous = VanillaAdditionsApplier.nbtWriter;
        try {
            VanillaAdditionsApplier.nbtWriter = (stack, nbt, warnings) ->
                    warnings.add(ItemWarning.skippedAddition("nbt:rejected"));

            ItemSourceRegistry registry = ItemSourceRegistry.create();

            ItemResolveResult result = registry.resolve(ItemReference.of("vanilla", "diamond_sword", VanillaAdditions.builder()
                    .name("&aKeep")
                    .flags(List.of("NOT_A_FLAG"))
                    .customModelData("nope")
                    .unbreakable("maybe")
                    .damage("broken")
                    .nbt(Map.of("x", "y"))
                    .build()));

            assertTrue(result.isHit());
            assertEquals(Material.DIAMOND_SWORD, result.item().orElseThrow().getType());
            assertTrue(result.item().orElseThrow().getItemMeta().hasDisplayName());
            assertTrue(result.warnings().stream().filter(w -> w.kind() == ItemWarning.Kind.SKIPPED_ADDITION).count() >= 1);
        } finally {
            VanillaAdditionsApplier.nbtWriter = previous;
        }
    }

    @Test
    void nbtIsAppliedFirstAndExplicitKeyWins() {
        VanillaAdditionsApplier.NbtWriter previous = VanillaAdditionsApplier.nbtWriter;
        try {
            VanillaAdditionsApplier.nbtWriter = (stack, nbt, warnings) -> {
                ItemMeta meta = stack.getItemMeta();
                meta.setDisplayName("from-nbt");
                stack.setItemMeta(meta);
            };

            ItemSourceRegistry registry = ItemSourceRegistry.create();
            ItemResolveResult result = registry.resolve(ItemReference.of("vanilla", "diamond", VanillaAdditions.builder()
                    .nbt(Map.of("mark", "yes"))
                    .name("&aFromExplicit")
                    .damage(0)
                    .build()));

            assertTrue(result.isHit());
            ItemStack item = result.item().orElseThrow();
            assertTrue(item.getItemMeta().hasDisplayName());
            assertTrue(item.getItemMeta().getDisplayName().contains("FromExplicit"));
            assertFalse(item.getItemMeta().getDisplayName().contains("from-nbt"));
            assertEquals(0, ((Damageable) item.getItemMeta()).getDamage());
        } finally {
            VanillaAdditionsApplier.nbtWriter = previous;
        }
    }

    @Test
    void nonVanillaHitIgnoresVanillaAdditionsWithNoWarning() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        ItemStack coin = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta coinMeta = coin.getItemMeta();
        coinMeta.setDisplayName("CoinOwnName");
        coin.setItemMeta(coinMeta);

        assertTrue(registry.register("coins", id -> ItemResolveResult.hit("coins", id, coin.clone())));

        ItemResolveResult result = registry.resolve(ItemReference.of("coins", "gold", VanillaAdditions.builder()
                .name("&cShouldNotApply")
                .build()));

        assertTrue(result.isHit());
        assertEquals("CoinOwnName", result.item().orElseThrow().getItemMeta().getDisplayName());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void emptyNbtMapAddsNothing() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult result = registry.resolve(ItemReference.of("vanilla", "diamond", VanillaAdditions.builder()
                .nbt(Map.of())
                .build()));

        assertTrue(result.isHit());
        assertTrue(result.warnings().isEmpty());
        assertEquals(Material.DIAMOND, result.item().orElseThrow().getType());
    }

    @Test
    void hideEnchantsFlagAppliesAndDamageZeroSetsDamage() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult result = registry.resolve(ItemReference.of("vanilla", "diamond_sword", VanillaAdditions.builder()
                .flags(List.of("HIDE_ENCHANTS"))
                .unbreakable(true)
                .damage(0)
                .customModelData(12)
                .build()));

        assertTrue(result.isHit());
        ItemMeta meta = result.item().orElseThrow().getItemMeta();
        assertTrue(meta.getItemFlags().contains(ItemFlag.HIDE_ENCHANTS));
        assertTrue(meta.isUnbreakable());
        assertEquals(12, meta.getCustomModelData());
        assertEquals(0, ((Damageable) meta).getDamage());
    }
}
