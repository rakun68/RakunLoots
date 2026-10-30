package me.rakkoon69.rakunloots.wiki;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.items.ItemWarning;
import me.rakkoon69.rakunloots.items.MissReason;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.warning.Warnings;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase9WikiInfoResolutionProofTest {

    @Test
    void infoDecorationUsesTheSameResolutionThatBuiltItsIcon() throws Exception {
        StreamableServerMock.mock();
        try {
            UUID world = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            FakeServerAdapter adapter = new FakeServerAdapter(world);
            adapter.addOnlinePlayer(player);
            String language;
            try (var stream = getClass().getResourceAsStream("/languages/en.yml")) {
                language = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
            }
            ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder().globals("language: en\n")
                    .worlds("{}\n").language("en", language)
                    .menu("wiki", "list:\n  rows: 2\n  content_slots: ['0-8']\n"
                            + "loot:\n  rows: 2\n  content_slots: ['0-8']\n  buttons:\n"
                            + "    info:\n      slot: 10\n      item: {source: changing, id: book}\n")
                    .loot("demo", "open_mode: drop\nblock: barrel\nroll:\n  entries:\n"
                            + "    a:\n      item: {id: diamond}\n")
                    .build(), id -> Optional.of(id), WarningKinds.all());
            assertTrue(snapshot.loot("demo").isPresent());
            ItemSourceRegistry items = ItemSourceRegistry.create();
            AtomicInteger resolutions = new AtomicInteger();
            assertTrue(items.register("changing", id -> resolutions.getAndIncrement() == 0
                    ? ItemResolveResult.miss("changing", id, MissReason.UNKNOWN_ID, ItemWarning.unknownId(id))
                    : ItemResolveResult.hit("changing", id, new ItemStack(Material.BOOK))));
            MenuRenderer renderer = new MenuRenderer(items, key -> snapshot.message("", key), Warnings.ignore());
            MenuService menus = new MenuService(adapter, renderer);
            LootChanceCache cache = new LootChanceCache(adapter.async());
            WikiMenu wiki = new WikiMenu(adapter, () -> snapshot, items, menus, renderer, cache,
                    (what, cause) -> {
                        throw new AssertionError(what, cause);
                    });
            cache.load(snapshot);
            wiki.open(player, new SenderLocation(world, 0, 64, 0));
            menus.click(player, 0).join();
            ItemStack info = adapter.menuOpens().get(1).contents().get(10);
            assertEquals(Material.BARRIER, info.getType());
            assertTrue(info.getItemMeta().getDisplayName().contains("Missing changing:book"),
                    "The first resolution missed, but the barrier was relabelled: "
                            + info.getItemMeta().getDisplayName());
            assertEquals(1, resolutions.get(),
                    "decoration must use the item resolution that actually built the icon");
        } finally {
            MockBukkit.unmock();
        }
    }
}
