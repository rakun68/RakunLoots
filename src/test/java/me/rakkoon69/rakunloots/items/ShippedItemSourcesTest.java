package me.rakkoon69.rakunloots.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippedItemSourcesTest {

    @Test
    void fiveShippedNamesAreRegisteredAndCannotBeReplaced() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        ItemStack impostor = new ItemStack(Material.DIRT, 1);

        for (String name : new String[]{"mmoitems", "itemedit", "executableitems", "oraxen", "itemsadder"}) {
            assertFalse(registry.register(name, id -> ItemResolveResult.hit(name, id, impostor)));
            ItemResolveResult result = registry.resolve(ItemReference.of(name, "anything"));
            assertTrue(result.isMiss());
            assertEquals(MissReason.PLUGIN_ABSENT, result.missReason().orElseThrow());
            assertTrue(result.item().isEmpty());
            assertTrue(result.warnings().stream().anyMatch(w ->
                    w.kind() == ItemWarning.Kind.PLUGIN_ABSENT
                            && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
        }
    }

    @Test
    void missingPluginIsPluginAbsentMissWithHighWarningAndNoItem() {
        ItemSourceRegistry registry = ItemSourceRegistry.create();

        ItemResolveResult result = registry.resolve(ItemReference.of("mmoitems", "SWORD:CUTLASS"));

        assertTrue(result.isMiss());
        assertEquals(MissReason.PLUGIN_ABSENT, result.missReason().orElseThrow());
        assertTrue(result.item().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w ->
                w.kind() == ItemWarning.Kind.PLUGIN_ABSENT
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));
    }

    @Test
    void mmoItemsIdSplitsOnFirstColonAndMissingHalfIsUnknownId() {
        AtomicReference<String> seenType = new AtomicReference<>();
        AtomicReference<String> seenItemId = new AtomicReference<>();
        ItemSource source = new MmoItemsItemSource(
                () -> true,
                (type, itemId) -> {
                    seenType.set(type);
                    seenItemId.set(itemId);
                    return new ItemStack(Material.DIAMOND_SWORD, 1);
                }
        );

        assertEquals(MissReason.UNKNOWN_ID, source.resolve("SWORD").missReason().orElseThrow());
        assertEquals(MissReason.UNKNOWN_ID, source.resolve("SWORD:").missReason().orElseThrow());
        assertEquals(MissReason.UNKNOWN_ID, source.resolve(":CUTLASS").missReason().orElseThrow());
        assertTrue(source.resolve("SWORD").warnings().stream().anyMatch(w ->
                w.kind() == ItemWarning.Kind.UNKNOWN_ID && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH));

        ItemResolveResult hit = source.resolve("SWORD:FOO:BAR");
        assertTrue(hit.isHit());
        assertEquals("SWORD", seenType.get());
        assertEquals("FOO:BAR", seenItemId.get());
        assertEquals(Material.DIAMOND_SWORD, hit.item().orElseThrow().getType());
    }

    @Test
    void itemsAdderKeepsColonAndOtherShippedIdsAreTakenWhole() {
        AtomicReference<String> itemsAdderId = new AtomicReference<>();
        AtomicReference<String> itemEditId = new AtomicReference<>();
        AtomicReference<String> executableId = new AtomicReference<>();
        AtomicReference<String> oraxenId = new AtomicReference<>();

        ItemSource itemsAdder = new ItemsAdderItemSource(() -> true, id -> {
            itemsAdderId.set(id);
            return new ItemStack(Material.STONE, 1);
        });
        ItemSource itemEdit = new ItemEditItemSource(() -> true, id -> {
            itemEditId.set(id);
            return new ItemStack(Material.STONE, 1);
        });
        ItemSource executable = new ExecutableItemsItemSource(() -> true, id -> {
            executableId.set(id);
            return new ItemStack(Material.STONE, 1);
        });
        ItemSource oraxen = new OraxenItemSource(() -> true, id -> {
            oraxenId.set(id);
            return new ItemStack(Material.STONE, 1);
        });

        assertTrue(itemsAdder.resolve("namespace:item_id").isHit());
        assertEquals("namespace:item_id", itemsAdderId.get());

        assertTrue(itemEdit.resolve("my-item").isHit());
        assertEquals("my-item", itemEditId.get());

        assertTrue(executable.resolve("Free_Spit").isHit());
        assertEquals("Free_Spit", executableId.get());

        assertTrue(oraxen.resolve("my_oraxen_item").isHit());
        assertEquals("my_oraxen_item", oraxenId.get());
    }

    @Test
    void shippedAdapterMissesHaveNoAdditionWarnings() {
        ItemSource source = new MmoItemsItemSource(
                () -> true,
                (type, itemId) -> new ItemStack(Material.DIAMOND, 1)
        );
        ItemResolveResult result = source.resolve("SWORD:CUTLASS");
        assertTrue(result.isHit());
        assertTrue(result.warnings().isEmpty());
    }
}
