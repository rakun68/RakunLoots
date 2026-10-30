package me.rakkoon69.rakunloots.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSourceRegistryTest {

    @Test
    void diamondAndDiamondUppercaseResolveToVanillaHitWithAmountOne() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult lower = registry.resolve(ItemReference.of(null, "diamond"));
        ItemResolveResult upper = registry.resolve(ItemReference.of("vanilla", "DIAMOND"));

        assertTrue(lower.isHit());
        assertEquals("vanilla", lower.source());
        assertEquals("diamond", lower.id());
        assertEquals(Material.DIAMOND, lower.item().orElseThrow().getType());
        assertEquals(1, lower.item().orElseThrow().getAmount());

        assertTrue(upper.isHit());
        assertEquals("vanilla", upper.source());
        assertEquals("DIAMOND", upper.id());
        assertEquals(Material.DIAMOND, upper.item().orElseThrow().getType());
        assertEquals(1, upper.item().orElseThrow().getAmount());
    }

    @Test
    void missingOrBlankSourceIsVanilla() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult missing = registry.resolve(ItemReference.of(null, "diamond"));
        ItemResolveResult blank = registry.resolve(ItemReference.of("  ", "diamond"));

        assertTrue(missing.isHit());
        assertEquals("vanilla", missing.source());
        assertTrue(blank.isHit());
        assertEquals("vanilla", blank.source());
    }

    @Test
    void unknownMaterialAndBlankIdAreMissesWithHighWarningAndNoItem() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult unknown = registry.resolve(ItemReference.of("vanilla", "not_a_material"));
        ItemResolveResult blankId = registry.resolve(ItemReference.of("vanilla", "  "));

        assertTrue(unknown.isMiss());
        assertEquals(MissReason.UNKNOWN_ID, unknown.missReason().orElseThrow());
        assertTrue(unknown.item().isEmpty());
        assertTrue(unknown.warnings().stream().anyMatch(w ->
                w.kind() == ItemWarning.Kind.UNKNOWN_ID
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));

        assertTrue(blankId.isMiss());
        assertEquals(MissReason.BLANK_ID, blankId.missReason().orElseThrow());
        assertTrue(blankId.item().isEmpty());
        assertTrue(blankId.warnings().stream().anyMatch(w ->
                w.kind() == ItemWarning.Kind.BLANK_ID
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    @Test
    void unknownSourceIsMissWithHighWarning() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult result = registry.resolve(ItemReference.of("vanila", "diamond"));

        assertTrue(result.isMiss());
        assertEquals(MissReason.UNKNOWN_SOURCE, result.missReason().orElseThrow());
        assertTrue(result.item().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w ->
                w.kind() == ItemWarning.Kind.UNKNOWN_SOURCE
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    @Test
    void fakeSourceRegistersResolvesAndCannotBeReplaced() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        ItemStack coin = new ItemStack(Material.GOLD_NUGGET, 1);

        assertTrue(registry.register("coins", id -> ItemResolveResult.hit("coins", id, coin)));
        ItemResolveResult first = registry.resolve(ItemReference.of("COINS", "gold"));
        assertTrue(first.isHit());
        assertEquals("coins", first.source());
        assertEquals("gold", first.id());
        assertEquals(Material.GOLD_NUGGET, first.item().orElseThrow().getType());

        ItemStack other = new ItemStack(Material.IRON_NUGGET, 1);
        assertFalse(registry.register("coins", id -> ItemResolveResult.hit("coins", id, other)));
        ItemResolveResult stillFirst = registry.resolve(ItemReference.of("coins", "gold"));
        assertEquals(Material.GOLD_NUGGET, stillFirst.item().orElseThrow().getType());
    }

    @Test
    void shippedNameAndBlankNameAreRefusedAndExistingSourceStays() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        ItemStack impostor = new ItemStack(Material.DIRT, 1);

        assertFalse(registry.register("VaNiLlA", id -> ItemResolveResult.hit("vanilla", id, impostor)));
        assertFalse(registry.register("mmoitems", id -> ItemResolveResult.hit("mmoitems", id, impostor)));
        assertFalse(registry.register("  ", id -> ItemResolveResult.hit("blank", id, impostor)));
        assertFalse(registry.register("", id -> ItemResolveResult.hit("blank", id, impostor)));

        ItemResolveResult vanilla = registry.resolve(ItemReference.of("vanilla", "diamond"));
        assertTrue(vanilla.isHit());
        assertEquals(Material.DIAMOND, vanilla.item().orElseThrow().getType());
    }

    @Test
    void sourceAndIdAreTrimmedAndSourceStoredLowercaseWhileIdKeepsCase() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        ItemStack coin = new ItemStack(Material.GOLD_NUGGET, 1);
        assertTrue(registry.register(" Coins ", id -> ItemResolveResult.hit("coins", id, coin)));

        ItemResolveResult result = registry.resolve(ItemReference.of(" CoInS ", " Gold "));
        assertTrue(result.isHit());
        assertEquals("coins", result.source());
        assertEquals("Gold", result.id());
    }
}
