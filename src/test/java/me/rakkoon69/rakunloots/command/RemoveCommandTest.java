package me.rakkoon69.rakunloots.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.lootbox.BoxObserver;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkView;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.LootBoxWarning;
import me.rakkoon69.rakunloots.storage.SqliteStores;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
import me.rakkoon69.rakunloots.summon.Spot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rkl remove} through the command registry, over a real lifecycle, a SQLite store, the fake server adapter,
 * and the shipped messages: each scope ends exactly the matching boxes as an admin removal, and a box whose block no
 * longer matches keeps the block.
 */
class RemoveCommandTest {

    private static final UUID WORLD = FakeCommandSources.WORLD_KEY;
    private static final int GROUND_Y = 63;
    /**
     * The lifecycle saves every 60 seconds.
     */
    private static final long SAVE_TICKS = 20L * 60;
    private static final String BASE = "rakunloots.command";
    private static final List<String> REMOVE_PERMISSIONS = List.of(BASE + ".remove", BASE + ".remove.all",
            BASE + ".remove.world", BASE + ".remove.loot", BASE + ".remove.near");

    @TempDir
    Path folder;

    private final FakeServerAdapter adapter = new FakeServerAdapter(WORLD);
    private final FakeCommandSources sources = new FakeCommandSources();
    private final List<String> ended = new CopyOnWriteArrayList<>();
    private LootBoxStore store;
    private LootBoxLifecycle lifecycle;
    private CommandRegistry registry;
    private FakeCommandSender admin;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                adapter.putChunk(new ChunkView(WORLD, chunkX, chunkZ, 0, 256,
                        (x, y, z) -> y == GROUND_Y ? "minecraft:grass_block" : "minecraft:air"), true, true);
            }
        }
        store = SqliteStores.open(folder);
        ConfigSnapshot config = ShippedHologramConfig.load("en", Map.of("airdrop1", CRATE));
        lifecycle = new LootBoxLifecycle(adapter, config, ItemSourceRegistry.create(), store, new Random(7),
                warning -> {
                    if (warning instanceof me.rakkoon69.rakunloots.lootbox.LifecycleWarning) {
                        throw new AssertionError("unexpected warning " + warning);
                    }
                });
        lifecycle.start().join();
        lifecycle.addObserver(new BoxObserver() {
            @Override
            public void shown(BoxEvent box) {
            }

            @Override
            public void ticked(BoxEvent box) {
            }

            @Override
            public void outOfView(BoxEvent box) {
            }

            @Override
            public void ended(BoxEvent box) {
                ended.add(box.boxId().toString());
            }
        });
        CommandMessages messages = key -> config.message("", key);
        registry = new CommandRegistry(sources, messages);
        registry.register(CommandNode.builder("rkl").permissionBase(BASE)
                .child(RemoveCommand.node(lifecycle, sources))
                .child(HelpCommand.node())
                .build());
        admin = new FakeCommandSender("Admin", withHelp(REMOVE_PERMISSIONS));
    }

    @AfterEach
    void tearDown() throws Exception {
        lifecycle.stop();
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance: {@code remove all} ends every box. It is a loot end: nothing drops, the block item of a loot with
     * {@code drop_block} on is not given, the blocks go, and each observer hears each box ended.
     */
    @Test
    void removeAllEndsEveryBoxAsAnAdminRemovalDroppingNothing() {
        LootBox first = summon("airdrops", 2, 2);
        LootBox second = summon("airdrops", 4, 4);
        LootBox crate = summon("airdrop1", 6, 6);

        List<String> shown = run(admin, "rkl remove all");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 Removed &e3 &7loot box(es)."), shown);
        for (LootBox box : List.of(first, second, crate)) {
            assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, box.position()));
            assertEquals("minecraft:air", adapter.blockAt(box.position()));
        }
        assertEquals(List.of(), adapter.drops(), "no stack and no block item");
        assertEquals(Set.of(first.boxId().toString(), second.boxId().toString(), crate.boxId().toString()),
                Set.copyOf(ended));
        assertEquals(3, ended.size(), "each box ended once");
    }

    /**
     * Acceptance: {@code remove world <world>} ends the boxes of that world only.
     */
    @Test
    void removeWorldEndsTheBoxesOfThatWorldOnly() {
        LootBox box = summon("airdrops", 2, 2);

        List<String> other = run(admin, "rkl remove world creative");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 Removed &e0 &7loot box(es)."), other);
        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent(), "creative holds no box, world's stays");
        assertEquals(List.of(), ended);

        List<String> own = run(admin, "rkl remove world world");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 Removed &e1 &7loot box(es)."), own);
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, box.position()));
        assertEquals(List.of(box.boxId().toString()), ended);
    }

    /**
     * A world that is named but not loaded says so and ends nothing.
     */
    @Test
    void removeWorldOfAWorldThatIsNotLoadedSaysSoAndEndsNothing() {
        LootBox box = summon("airdrops", 2, 2);

        List<String> shown = run(admin, "rkl remove world world_nether");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 &cThe world &fworld_nether &cis not loaded."), shown);
        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent());
    }

    /**
     * Acceptance: {@code remove loot <loot>} ends the boxes of that loot only.
     */
    @Test
    void removeLootEndsTheBoxesOfThatLootOnly() {
        LootBox airdrop = summon("airdrops", 2, 2);
        LootBox crate = summon("airdrop1", 6, 6);

        List<String> shown = run(admin, "rkl remove loot airdrop1");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 Removed &e1 &7loot box(es)."), shown);
        assertTrue(lifecycle.boxAt(WORLD, airdrop.position()).isPresent());
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, crate.position()));
        assertEquals("minecraft:barrel", adapter.blockAt(airdrop.position()));
        assertEquals("minecraft:air", adapter.blockAt(crate.position()));
        assertEquals(List.of(crate.boxId().toString()), ended);
        assertEquals(List.of(), adapter.drops());
    }

    /**
     * Acceptance: {@code remove near <radius>} measures from the sender in the sender's world, to the centre of each
     * box's block.
     */
    @Test
    void removeNearEndsTheBoxesWithinTheRadiusOfThePlayerOnly() {
        LootBox close = summon("airdrops", 2, 2);
        LootBox edge = summon("airdrops", 8, 2);
        LootBox far = summon("airdrops", 20, 20);
        FakeCommandSender here = new FakeCommandSender("Steve", withHelp(REMOVE_PERMISSIONS))
                .at(new SenderLocation(WORLD, 2.5, 64.5, 2.5));

        List<String> shown = run(here, "rkl remove near 6");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 Removed &e2 &7loot box(es)."), shown);
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, close.position()));
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, edge.position()), "exactly 6 blocks away");
        assertTrue(lifecycle.boxAt(WORLD, far.position()).isPresent());
        assertEquals(Set.of(close.boxId().toString(), edge.boxId().toString()), Set.copyOf(ended));
    }

    /**
     * A player in another world is not near any box of this one.
     */
    @Test
    void removeNearMeasuresOnlyInThePlayersWorld() {
        LootBox box = summon("airdrops", 2, 2);
        FakeCommandSender elsewhere = new FakeCommandSender("Steve", withHelp(REMOVE_PERMISSIONS))
                .at(new SenderLocation(FakeCommandSources.CREATIVE_KEY, 2.5, 64.5, 2.5));

        List<String> shown = run(elsewhere, "rkl remove near 50");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 Removed &e0 &7loot box(es)."), shown);
        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent());
    }

    /**
     * Acceptance: {@code near} needs a player sender.
     */
    @Test
    void removeNearNeedsAPlayerSender() {
        LootBox box = summon("airdrops", 2, 2);

        List<String> shown = run(admin, "rkl remove near 50");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 &cOnly a player can use remove near, because it measures from "
                + "where you stand."), shown);
        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent());
        assertEquals(List.of(), ended);
    }

    /**
     * Acceptance: a box whose block no longer matches keeps the block. The box still ends, drops nothing, and the
     * block another plugin put there is not touched.
     */
    @Test
    void aBoxWhoseBlockNoLongerMatchesEndsAndKeepsTheBlock() {
        LootBox changed = summon("airdrops", 2, 2);
        LootBox intact = summon("airdrops", 4, 4);
        adapter.putBlock(changed.position(), "minecraft:stone");
        int writes = adapter.blockWrites().size();

        List<String> shown = run(admin, "rkl remove all");

        assertEquals(List.of("&f[&bRakunLoots&f]&7 Removed &e2 &7loot box(es)."), shown);
        assertEquals(Optional.empty(), lifecycle.boxAt(WORLD, changed.position()));
        assertEquals("minecraft:stone", adapter.blockAt(changed.position()), "the builder's block stays");
        assertEquals("minecraft:air", adapter.blockAt(intact.position()));
        assertEquals(writes + 1, adapter.blockWrites().size(), "only the matching block was written");
        assertEquals(List.of(), adapter.drops());
        assertEquals(Set.of(changed.boxId().toString(), intact.boxId().toString()), Set.copyOf(ended));
    }

    /**
     * A removed box's row goes with the next save, so a restart does not bring it back.
     */
    @Test
    void aRemovedBoxIsNotKeptAcrossARestart() throws Exception {
        LootBox box = summon("airdrops", 2, 2);
        adapter.asyncScheduler().stepTicks(SAVE_TICKS);
        run(admin, "rkl remove all");
        adapter.asyncScheduler().stepTicks(SAVE_TICKS);

        try (LootBoxStore reader = SqliteStores.open(folder)) {
            ConfigSnapshot config = ShippedHologramConfig.load("en", Map.of("airdrop1", CRATE));
            assertFalse(reader.loadAll(config, world -> true).boxes().stream()
                    .anyMatch(loaded -> loaded.box().boxId().equals(box.boxId())));
        }
    }

    /**
     * A sender without the node's permission is told the command is unknown, and nothing ends.
     */
    @Test
    void aSenderWithoutThePermissionOfANodeCannotRunIt() {
        LootBox box = summon("airdrops", 2, 2);
        FakeCommandSender partial = new FakeCommandSender("Alex", BASE + ".remove", BASE + ".remove.loot");

        run(partial, "rkl remove all");

        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent());
        assertEquals(List.of(), ended);
        assertEquals(List.of("loot"), registry.complete(partial, "rkl remove "));
    }

    /**
     * Every remove node's help keys have a text in every shipped language, and every remove permission is declared
     * in {@code plugin.yml} as a child of {@code rakunloots.admin}.
     */
    @Test
    void theShippedFilesHoldTheRemoveHelpAndPermissions() throws IOException {
        CommandNode root = registry.root("rkl").orElseThrow();
        CommandNode remove = root.children().get(0);
        List<CommandNode> nodes = new ArrayList<>(List.of(remove));
        nodes.addAll(remove.children());
        ConfigSnapshot snapshot = ShippedHologramConfig.load("en", Map.of());
        Map<String, Map<String, Object>> permissions = permissions();
        @SuppressWarnings("unchecked")
        Map<String, Boolean> children = (Map<String, Boolean>) permissions.get("rakunloots.admin").get("children");

        assertEquals(List.of("all", "world", "loot", "near"), remove.children().stream().map(CommandNode::name).toList());
        for (CommandNode node : nodes) {
            List<String> keys = new ArrayList<>(List.of(node.helpKey("summary"), node.helpKey("description"),
                    node.helpKey("example")));
            node.arguments().forEach(argument -> keys.add(node.argumentHelpKey(argument)));
            for (String language : List.of("en", "vi", "zh")) {
                assertTrue(snapshot.languageKeys(language).containsAll(keys), language + " lacks help for " + node.path());
            }
            String permission = node.permission().orElseThrow();
            assertTrue(permissions.containsKey(permission), permission + " is not declared");
            assertEquals(Boolean.TRUE, children.get(permission), permission + " is not a child of rakunloots.admin");
        }
        for (String key : List.of("cmd_remove_done", "cmd_remove_near_needs_player", "cmd_remove_world_not_loaded")) {
            for (String language : List.of("en", "vi", "zh")) {
                assertTrue(snapshot.languageKeys(language).contains(key), language + " lacks " + key);
            }
        }
    }

    private List<String> run(FakeCommandSender sender, String line) {
        int before = sender.messages().size();
        registry.dispatch(sender, line);
        return sender.messages().subList(before, sender.messages().size());
    }

    private LootBox summon(String lootId, int x, int z) {
        var outcome = lifecycle.summon(lootId, new Spot(WORLD, x, GROUND_Y + 1, z, "minecraft:air"), Optional.empty(), SummonKind.UNKNOWN)
                .join();
        return outcome.box().orElseThrow(() -> new AssertionError("refused " + outcome.refusal()));
    }

    private static String[] withHelp(List<String> permissions) {
        List<String> all = new ArrayList<>(permissions);
        all.add(BASE + ".help");
        return all.toArray(new String[0]);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> permissions() throws IOException {
        try (InputStream in = RemoveCommandTest.class.getResourceAsStream("/plugin.yml")) {
            Map<String, Object> plugin = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return (Map<String, Map<String, Object>>) plugin.get("permissions");
        }
    }

    private static final String CRATE = """
            language: en
            open_mode: drop
            lock: 0
            life: 360
            block: barrel
            loot_end:
              allow_explosion: true
              drop_block: true
            roll:
              entries:
                gold:
                  weight: -1
                  amount: 7
                  item:
                    id: gold_ingot
            """;
}
