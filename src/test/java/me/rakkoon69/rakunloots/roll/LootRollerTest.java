package me.rakkoon69.rakunloots.roll;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.Loot;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.items.ItemWarning;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootRollerTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    @Test
    void countStaysWithinMinMaxAndEveryCountCanOccur() {
        Loot loot = loot("""
                roll:
                  min: 1
                  max: 3
                  entries:
                    a:
                      item:
                        id: diamond
                    b:
                      item:
                        id: emerald
                    c:
                      item:
                        id: gold_ingot
                    d:
                      item:
                        id: iron_ingot
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(42L);

        Set<Integer> counts = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            counts.add(LootRoller.roll(loot, registry, random).contents().stacks().size());
        }

        assertEquals(Set.of(1, 2, 3), counts);
    }

    @Test
    void minZeroCanRollEmptyContents() {
        Loot loot = loot("""
                roll:
                  min: 0
                  max: 1
                  entries:
                    a:
                      item:
                        id: diamond
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(23L);

        Set<Integer> counts = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            counts.add(LootRoller.roll(loot, registry, random).contents().stacks().size());
        }

        assertEquals(Set.of(0, 1), counts);
    }

    @Test
    void heavierEntryIsDrawnMoreOftenThanLighterOne() {
        Loot loot = loot("""
                roll:
                  min: 1
                  max: 1
                  entries:
                    heavy:
                      weight: 9
                      item:
                        id: diamond
                    light:
                      weight: 1
                      item:
                        id: emerald
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(7L);

        int heavy = 0;
        int light = 0;
        for (int i = 0; i < 1000; i++) {
            Material drawn = type(LootRoller.roll(loot, registry, random).contents().stacks().get(0));
            if (drawn == Material.DIAMOND) {
                heavy++;
            } else if (drawn == Material.EMERALD) {
                light++;
            }
        }

        assertEquals(1000, heavy + light);
        assertTrue(heavy > light * 4, "heavy=" + heavy + " light=" + light);
        assertTrue(light > 0);
    }

    @Test
    void noEntryAppearsTwiceFromTheDraw() {
        Loot loot = loot("""
                roll:
                  min: 3
                  max: 3
                  entries:
                    heavy:
                      weight: 1000
                      item:
                        id: diamond
                    b:
                      item:
                        id: emerald
                    c:
                      item:
                        id: gold_ingot
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(3L);

        for (int i = 0; i < 200; i++) {
            List<Material> types = LootRoller.roll(loot, registry, random).contents().stacks().stream()
                    .map(LootRollerTest::type).toList();
            assertEquals(Set.of(Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT), new HashSet<>(types));
            assertEquals(3, types.size());
        }
    }

    @Test
    void maxAboveDrawableEntriesIsCapped() {
        Loot loot = loot("""
                roll:
                  min: 5
                  max: 10
                  entries:
                    a:
                      item:
                        id: diamond
                    b:
                      item:
                        id: emerald
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(5L);

        for (int i = 0; i < 100; i++) {
            RollResult result = LootRoller.roll(loot, registry, random);
            assertEquals(2, result.contents().stacks().size());
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Test
    void weightBelowZeroIsAlwaysPresentAndDoesNotUseUpTheCount() {
        Loot loot = loot("""
                roll:
                  min: 1
                  max: 1
                  entries:
                    always:
                      weight: -1
                      item:
                        id: nether_star
                    a:
                      item:
                        id: diamond
                    b:
                      item:
                        id: emerald
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(11L);

        for (int i = 0; i < 200; i++) {
            List<Material> types = LootRoller.roll(loot, registry, random).contents().stacks().stream()
                    .map(LootRollerTest::type).toList();
            assertEquals(2, types.size(), types.toString());
            assertTrue(types.contains(Material.NETHER_STAR));
            assertTrue(types.contains(Material.DIAMOND) || types.contains(Material.EMERALD));
        }
    }

    @Test
    void weightZeroEntryIsNeverPresent() {
        Loot loot = loot("""
                roll:
                  min: 1
                  max: 2
                  entries:
                    off:
                      weight: 0
                      item:
                        id: nether_star
                    a:
                      item:
                        id: diamond
                    b:
                      item:
                        id: emerald
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(13L);

        for (int i = 0; i < 200; i++) {
            assertTrue(LootRoller.roll(loot, registry, random).contents().stacks().stream()
                    .noneMatch(stack -> type(stack) == Material.NETHER_STAR));
        }
    }

    @Test
    void amountAboveMaxStackSizeSplitsAndOnlyFirstStackCarriesFixedSlot() {
        Loot loot = loot("""
                roll:
                  entries:
                    gems:
                      weight: -1
                      amount: 200
                      slot: 4
                      item:
                        id: diamond
                """);

        List<RolledStack> stacks = LootRoller.roll(loot, ItemSourceRegistry.create(), new Random(1L))
                .contents().stacks();

        assertEquals(List.of(64, 64, 64, 8), stacks.stream().map(stack -> stack.item().getAmount()).toList());
        assertTrue(stacks.stream().allMatch(stack -> type(stack) == Material.DIAMOND));
        assertEquals(OptionalInt.of(4), stacks.get(0).slot());
        assertTrue(stacks.subList(1, stacks.size()).stream().allMatch(stack -> stack.slot().isEmpty()));
    }

    @Test
    void entryWithoutSlotYieldsStacksWithNoSlot() {
        Loot loot = loot("""
                roll:
                  entries:
                    swords:
                      weight: -1
                      amount: 5
                      item:
                        id: diamond_sword
                """);

        List<RolledStack> stacks = LootRoller.roll(loot, ItemSourceRegistry.create(), new Random(1L))
                .contents().stacks();

        assertEquals(List.of(1, 1, 1, 1, 1), stacks.stream().map(stack -> stack.item().getAmount()).toList());
        assertTrue(stacks.stream().allMatch(stack -> type(stack) == Material.DIAMOND_SWORD));
        assertTrue(stacks.stream().allMatch(stack -> stack.slot().isEmpty()));
    }

    @Test
    void drawnEntryThatMissesIsLeftOutWithHighWarningAndNoReplacement() {
        Loot loot = loot("""
                roll:
                  min: 1
                  max: 1
                  entries:
                    ghost:
                      item:
                        id: not_a_material
                    real:
                      item:
                        id: diamond
                """);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        Random random = new Random(17L);

        int misses = 0;
        int hits = 0;
        for (int i = 0; i < 200; i++) {
            RollResult result = LootRoller.roll(loot, registry, random);
            if (result.warnings().isEmpty()) {
                hits++;
                assertEquals(List.of(Material.DIAMOND),
                        result.contents().stacks().stream().map(LootRollerTest::type).toList());
            } else {
                misses++;
                assertTrue(result.contents().stacks().isEmpty());
                assertMissed(result, "ghost");
            }
        }
        assertTrue(misses > 0);
        assertTrue(hits > 0);
    }

    @Test
    void alwaysIncludedEntryThatMissesIsLeftOutWithHighWarning() {
        Loot loot = loot("""
                roll:
                  min: 1
                  max: 1
                  entries:
                    ghost:
                      weight: -1
                      item:
                        source: nosuchplugin
                        id: thing
                    real:
                      item:
                        id: diamond
                """);

        RollResult result = LootRoller.roll(loot, ItemSourceRegistry.create(), new Random(19L));

        assertEquals(List.of(Material.DIAMOND),
                result.contents().stacks().stream().map(LootRollerTest::type).toList());
        assertMissed(result, "ghost");
    }

    @Test
    void rolledStacksDoNotShareTheSourceItem() {
        ItemStack coin = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemSourceRegistry registry = ItemSourceRegistry.create();
        registry.register("coins", id -> ItemResolveResult.hit("coins", id, coin));
        Loot loot = loot("""
                roll:
                  entries:
                    coins:
                      weight: -1
                      amount: 30
                      item:
                        source: coins
                        id: gold
                """);

        RollResult first = LootRoller.roll(loot, registry, new Random(1L));
        first.contents().stacks().get(0).item().setAmount(2);

        assertEquals(1, coin.getAmount());
        assertEquals(30, first.contents().stacks().get(0).item().getAmount());
    }

    private static void assertMissed(RollResult result, String entryId) {
        assertEquals(1, result.warnings().size());
        RollWarning warning = result.warnings().get(0);
        assertEquals(RollWarning.Kind.MISSED_ENTRY, warning.kind());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        assertEquals("demo", warning.lootId());
        assertEquals(entryId, warning.entryId());
    }

    private static Loot loot(String body) {
        ConfigSources sources = ConfigSources.builder()
                .globals("language: en\n")
                .language("en", "still_locked: locked\n")
                .loot("demo", "language: en\nopen_mode: drop\nblock: barrel\n" + body)
                .build();
        return ConfigLoader.load(sources, ACCEPTING, WarningKinds.all()).loot("demo").orElseThrow();
    }

    private static Material type(RolledStack stack) {
        return stack.item().getType();
    }
}
