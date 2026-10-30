package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.server.BukkitNameCatalog;
import me.rakkoon69.rakunloots.summon.PlacementRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippedConfigQuietTest {

    private static final List<String> SHIPPED_LOOTS = List.of("airdrops", "buried_stash", "deep_vault", "head_trophy",
            "sky_lantern", "sunken_treasure", "treasure_chest");

    /**
     * Loots meant for the summon item only: the timer would mostly find no spot for them.
     */
    private static final Set<String> NOT_ON_THE_TIMER = Set.of("sky_lantern", "sunken_treasure");

    /**
     * The server's real material list (the Bukkit enum on the test class path) answers block and item names, so a
     * name that is not a block or an item warns. Block tags need a running server, so a tag has one member here.
     */
    private static final NameCatalog STRICT_CATALOG = new NameCatalog() {
        private final BukkitNameCatalog real = new BukkitNameCatalog();

        @Override
        public Optional<String> currentId(String writtenId) {
            return real.currentId(writtenId);
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            return Set.of("minecraft:" + namespacedTag.replace(':', '_'));
        }

        @Override
        public boolean soundExists(String key) {
            return "minecraft:ui.button.click".equals(key);
        }
    };

    private static ConfigSources.Builder shippedSources() throws IOException {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .animations(resource("/animations.yml"))
                .warnings(resource("/warnings.yml"))
                .menu("claim", resource("/menus/claim.yml"))
                .menu("wiki", resource("/menus/wiki.yml"))
                .language("en", resource("/languages/en.yml"))
                .language("vi", resource("/languages/vi.yml"))
                .language("zh", resource("/languages/zh.yml"));
        for (String lootId : SHIPPED_LOOTS) {
            sources.loot(lootId, resource("/loots/" + lootId + ".yml"));
        }
        return sources;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> lootFile(String lootId) throws IOException {
        return (java.util.Map<String, Object>) new org.yaml.snakeyaml.Yaml().load(resource("/loots/" + lootId + ".yml"));
    }

    /**
     * Every file in the jar's loots folder is saved on first start, and the other way round.
     */
    @Test
    void everyShippedLootFileIsASavedDefault() throws Exception {
        java.nio.file.Path folder = java.nio.file.Path.of(ShippedConfigQuietTest.class.getResource("/loots").toURI());
        List<String> onDisk;
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(folder)) {
            onDisk = files.map(path -> path.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - ".yml".length()))
                    .sorted()
                    .toList();
        }
        List<String> saved = ConfigFolder.DEFAULT_RESOURCES.stream()
                .filter(path -> path.startsWith("loots/"))
                .map(path -> path.substring("loots/".length(), path.length() - ".yml".length()))
                .sorted()
                .toList();
        assertEquals(SHIPPED_LOOTS, onDisk);
        assertEquals(SHIPPED_LOOTS, saved);
    }

    /**
     * Every file in the jar's menus folder is saved on first start, and the other way round.
     */
    @Test
    void everyShippedMenuFileIsASavedDefault() throws Exception {
        Path folder = Path.of(ShippedConfigQuietTest.class.getResource("/menus").toURI());
        List<String> onDisk;
        try (Stream<Path> files = Files.list(folder)) {
            onDisk = files.map(path -> path.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - ".yml".length()))
                    .sorted()
                    .toList();
        }
        List<String> saved = ConfigFolder.DEFAULT_RESOURCES.stream()
                .filter(path -> path.startsWith("menus/"))
                .map(path -> path.substring("menus/".length(), path.length() - ".yml".length()))
                .sorted()
                .toList();
        assertEquals(List.of("claim", "wiki"), onDisk);
        assertEquals(List.of("claim", "wiki"), saved);
    }

    /**
     * Each shipped loot is on the timer in worlds.yml, or is named above as summon item only; the timer names no loot
     * that is not shipped.
     */
    @Test
    void worldsYmlHasTheLootsMeantForTheTimer() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shippedSources().build(), STRICT_CATALOG, WarningKinds.all());
        java.util.Map<String, Integer> chances = snapshot.world("world").orElseThrow().loots();

        assertTrue(chances.containsKey("airdrops"));
        for (String lootId : SHIPPED_LOOTS) {
            assertEquals(!NOT_ON_THE_TIMER.contains(lootId), chances.containsKey(lootId), lootId);
        }
        assertTrue(SHIPPED_LOOTS.containsAll(chances.keySet()), chances.toString());
    }

    @Test
    void shippedResourcesLoadWithNoWarnings() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shippedSources().build(), STRICT_CATALOG, WarningKinds.all());
        assertEquals(SHIPPED_LOOTS.stream().sorted().toList(), snapshot.loots().keySet().stream().sorted().toList());

        // The allowed warnings: buried_stash is unending on purpose (life: -1), noted once at low severity, and
        // blocks.yml's optional entries this server lacks are listed in one low warning. STRICT_CATALOG is the
        // class path's Bukkit API, so which entries it lists depends on that version.
        assertTrue(snapshot.loot("buried_stash").orElseThrow().lifeSeconds().isEmpty());
        List<ConfigWarning> unending = snapshot.warnings().stream()
                .filter(w -> w.kind() == ConfigWarning.Kind.NEGATIVE_LIFE)
                .toList();
        assertEquals(1, unending.size(), unending.toString());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.LOW, unending.get(0).severity());
        assertEquals(Optional.of("buried_stash"), unending.get(0).lootId());

        String warnings = snapshot.warnings().stream()
                .filter(w -> w.kind() != ConfigWarning.Kind.NEGATIVE_LIFE)
                .filter(w -> w.kind() != ConfigWarning.Kind.SKIPPED_OPTIONAL_ENTRIES)
                .map(w -> w.severity() + " " + w.kind() + " " + w.key().orElse("") + " " + w.lootId().orElse(""))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        assertTrue(warnings.isEmpty(), warnings);

        assertEquals(6, snapshot.claimMenu().rows());
        assertEquals(49, snapshot.claimMenu().buttons().get("claim_all").slot());
        assertEquals(6, snapshot.wikiListMenu().rows());
        assertEquals(49, snapshot.wikiLootMenu().buttons().get("info").slot());

        assertEquals(PlacementRule.TOP_SURFACE, snapshot.summonRules("airdrops").orElseThrow().rule());
        assertTrue(snapshot.summonRules("airdrops").orElseThrow().replaceableBlocks().contains("minecraft:grass"));
        assertTrue(snapshot.summonRules("airdrops").orElseThrow().replaceableBlocks().contains("minecraft:cactus"));
        assertTrue(snapshot.summonRules("airdrops").orElseThrow().avoidSurfaces().contains("minecraft:cactus"));

        Loot loot = snapshot.loot("airdrops").orElseThrow();
        assertEquals(2, loot.roll().min());
        assertEquals(5, loot.roll().max());
        assertEquals(10, loot.lockSeconds());
        assertEquals(360, loot.lifeSeconds().orElseThrow());
        assertEquals(60, loot.summonDelayTicks());
        for (String key : List.of("summon_spot_held", "summon_block_changed", "summon_block_refused",
                "summon_failed", "summon_chunk_unloaded", "summon_interrupted")) {
            assertTrue(!snapshot.message("airdrops", key).isBlank(), key);
        }
        assertEquals(List.of(
                "you_can_use_vanilla_item",
                "or_itemedit_items",
                "or_mmoitems_items",
                "or_any_custom_items",
                "place_anywhere",
                "with_any_chance",
                "amethyst"
        ), loot.roll().entries().stream().map(ContentEntry::id).toList());
        assertEquals(List.of(
                "diamond",
                "cobweb",
                "golden_apple",
                "enchanted_golden_apple",
                "diamond",
                "emerald",
                "amethyst_shard"
        ), loot.roll().entries().stream().map(ContentEntry::item).map(ItemReference::id).toList());
        assertEquals(List.of(-50, -50, 10, 10, 50, 20, 10),
                loot.roll().entries().stream().map(ContentEntry::weight).toList());
    }

    /**
     * Every key phase 6 took out of the shipped airdrops, except commands, is back in its new form: the owner message
     * under messages, with the rest already in the file. head_data is not there, because a barrel has no head. The
     * shipped file stays quiet with them.
     */
    @Test
    void shippedAirdropsHasTheRestoredKeys() throws IOException {
        java.util.Map<String, Object> root = lootFile("airdrops");

        for (String key : List.of("display_name", "description", "life", "lock", "summon_item",
                "message_owner_on_remove_enable", "messages", "block", "loot_end", "summon_delay",
                "summon_settings", "hologram", "glow", "laser", "animations", "roll", "open_mode")) {
            assertTrue(root.containsKey(key), key);
        }
        assertTrue(!root.containsKey("commands"));
        assertTrue(!root.containsKey("head_data"));
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .language("en", resource("/languages/en.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), written -> Optional.of(written), WarningKinds.all());
        assertEquals("{prefix} &7Your {box_name} &r&7at &c{box_x} {box_y} {box_z} &r&7has disappeared.",
                snapshot.message("airdrops", "message_owner_on_remove"));
        assertTrue(snapshot.warnings().stream().noneMatch(w -> w.lootId().equals(Optional.of("airdrops"))),
                snapshot.warnings().toString());
    }

    /**
     * G4: the head loot's block is player_head with a head_data that reaches the loaded loot; no other loot has one.
     */
    @Test
    void theHeadLootHasAWorkingHeadData() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shippedSources().build(), STRICT_CATALOG, WarningKinds.all());

        Loot head = snapshot.loot("head_trophy").orElseThrow();
        assertEquals("player_head", head.block());
        assertTrue(head.headData().orElseThrow().startsWith("eyJ0ZXh0dXJlcyI6"), head.headData().toString());
        for (String lootId : SHIPPED_LOOTS) {
            assertEquals(lootId.equals("head_trophy"), lootFile(lootId).containsKey("head_data"), lootId);
        }
    }

    /**
     * G3: every shipped loot has its own summon item with a name and lore, so a player can tell it from the vanilla
     * item; no two loots share a name.
     */
    @Test
    void everyShippedLootHasItsOwnNamedSummonItem() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shippedSources().build(), STRICT_CATALOG, WarningKinds.all());

        java.util.Set<String> names = new java.util.HashSet<>();
        for (String lootId : SHIPPED_LOOTS) {
            ItemReference item = snapshot.loot(lootId).orElseThrow().summonItem().orElseThrow();
            assertTrue(item.additions().name().isPresent(), lootId + " name");
            assertTrue(!item.additions().lore().orElseThrow().isEmpty(), lootId + " lore");
            assertTrue(names.add(item.additions().name().get()), lootId + " shares a name");
        }
        assertEquals(SHIPPED_LOOTS.size(), names.size());
    }

    /**
     * Item references from other plugins are not in a shipped file: a fresh server lacks them. Every item is vanilla.
     */
    @Test
    void noShippedItemComesFromAnotherPlugin() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shippedSources().build(), STRICT_CATALOG, WarningKinds.all());

        for (String lootId : SHIPPED_LOOTS) {
            Loot loot = snapshot.loot(lootId).orElseThrow();
            List<ItemReference> items = new java.util.ArrayList<>(
                    loot.roll().entries().stream().map(ContentEntry::item).toList());
            loot.summonItem().ifPresent(items::add);
            for (ItemReference item : items) {
                assertEquals("vanilla", item.source(), lootId + " " + item.id());
            }
        }
    }

    /**
     * Across the shipped loots every loot option is used at least once: each root key, each loot_end flag, each item
     * addition, each placement type with its options, both plants modes, both open modes, a hologram with every key,
     * a glow, both lasers and none, and an animation of each type at each moment.
     */
    @Test
    @SuppressWarnings("unchecked")
    void theShippedLootsTogetherUseEveryLootOption() throws IOException {
        java.util.Set<String> rootKeys = new java.util.HashSet<>();
        java.util.Set<String> lootEnd = new java.util.HashSet<>();
        java.util.Set<String> itemKeys = new java.util.HashSet<>();
        java.util.Set<String> entryKeys = new java.util.HashSet<>();
        java.util.Set<String> settingsKeys = new java.util.HashSet<>();
        java.util.Set<String> rules = new java.util.HashSet<>();
        java.util.Set<String> ruleOptions = new java.util.HashSet<>();
        java.util.Set<String> openModes = new java.util.HashSet<>();
        java.util.Set<String> hologramKeys = new java.util.HashSet<>();
        java.util.Set<String> glowKeys = new java.util.HashSet<>();
        java.util.Set<String> lasers = new java.util.HashSet<>();
        java.util.Set<String> moments = new java.util.HashSet<>();
        java.util.Set<String> animationIds = new java.util.HashSet<>();
        for (String lootId : SHIPPED_LOOTS) {
            java.util.Map<String, Object> root = lootFile(lootId);
            rootKeys.addAll(root.keySet());
            openModes.add(String.valueOf(root.get("open_mode")));
            lootEnd.addAll(((java.util.Map<String, Object>) root.getOrDefault("loot_end", java.util.Map.of())).keySet());
            java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
            items.add((java.util.Map<String, Object>) root.get("summon_item"));
            java.util.Map<String, Object> entries = (java.util.Map<String, Object>)
                    ((java.util.Map<String, Object>) root.get("roll")).get("entries");
            for (Object entry : entries.values()) {
                entryKeys.addAll(((java.util.Map<String, Object>) entry).keySet());
                items.add((java.util.Map<String, Object>) ((java.util.Map<String, Object>) entry).get("item"));
            }
            for (java.util.Map<String, Object> item : items) {
                itemKeys.addAll(item.keySet());
            }
            java.util.Map<String, Object> settings = (java.util.Map<String, Object>) root.get("summon_settings");
            settingsKeys.addAll(settings.keySet());
            rules.add(String.valueOf(settings.get("summon_type")));
            for (PlacementRule rule : PlacementRule.values()) {
                Object options = settings.get(rule.key());
                if (options instanceof java.util.Map<?, ?> map) {
                    for (Object key : map.keySet()) {
                        ruleOptions.add(rule.key() + "." + key + (key.equals("plants") ? "=" + map.get(key) : ""));
                    }
                }
            }
            hologramKeys.addAll(((java.util.Map<String, Object>) root.getOrDefault("hologram", java.util.Map.of())).keySet());
            glowKeys.addAll(((java.util.Map<String, Object>) root.getOrDefault("glow", java.util.Map.of())).keySet());
            java.util.Map<String, Object> laser = (java.util.Map<String, Object>) root.get("laser");
            lasers.add(laser == null ? "NONE" : String.valueOf(laser.get("type")).toUpperCase(java.util.Locale.ROOT));
            java.util.Map<String, Object> animations = (java.util.Map<String, Object>) root.getOrDefault("animations", java.util.Map.of());
            for (java.util.Map.Entry<String, Object> moment : animations.entrySet()) {
                moments.add(moment.getKey());
                animationIds.addAll((List<String>) moment.getValue());
            }
        }
        java.util.Map<String, Object> animationFile = new org.yaml.snakeyaml.Yaml().load(resource("/animations.yml"));
        java.util.Set<String> animationTypes = new java.util.HashSet<>();
        for (String id : animationIds) {
            animationTypes.add(String.valueOf(((java.util.Map<String, Object>) animationFile.get(id)).get("type")));
        }

        assertEquals(java.util.Set.of("language", "messages", "open_mode", "lock", "life", "summon_delay",
                "message_owner_on_remove_enable", "loot_end", "display_name", "description", "block", "head_data",
                "roll", "summon_item", "summon_settings", "hologram", "glow", "laser", "animations"), rootKeys);
        assertEquals(java.util.Set.of("allow_explosion", "drop_block", "timeout_spill", "empty_removal"), lootEnd);
        assertEquals(java.util.Set.of("weight", "amount", "slot", "item"), entryKeys);
        assertEquals(java.util.Set.of("source", "id", "name", "lore", "enchants", "flags", "custom_model_data",
                "unbreakable", "damage", "nbt"), itemKeys);
        assertEquals(java.util.Set.of("summon_type", "y_range", "replaceable_blocks", "avoid_surfaces", "player_ring",
                "vertical_offset_enable", "vertical_offset", "attempts", "top_surface", "any_surface", "buried",
                "deep_buried", "underwater"), settingsKeys);
        assertEquals(java.util.Set.of("top_surface", "any_surface", "buried", "deep_buried", "underwater", "anywhere"),
                rules);
        assertTrue(ruleOptions.containsAll(java.util.Set.of("top_surface.plants=replace", "top_surface.plants=on_top",
                "any_surface.floor", "any_surface.headroom", "buried.depth", "deep_buried.enclosed",
                "underwater.position", "underwater.cave_water", "underwater.min_depth")), ruleOptions.toString());
        assertEquals(java.util.Set.of("drop", "inventory"), openModes);
        assertEquals(java.util.Set.of("enabled", "height", "view_range", "lines"), hologramKeys);
        assertEquals(java.util.Set.of("enabled", "color"), glowKeys);
        assertEquals(java.util.Set.of("GUARDIAN", "CRYSTAL", "NONE"), lasers);
        assertEquals(java.util.Set.of("summon", "alive", "unlocked", "open", "remove"), moments);
        assertEquals(java.util.Set.of("SPHERE", "EXPLOSION"), animationTypes);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ShippedConfigQuietTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
