package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBannedLootBlocksTest {

    private static final String SHIPPED_LIST = "banned_loot_blocks:\n  - '@banned_loot_blocks'\n";

    /**
     * Written ids resolve to namespaced lower case, as the server catalog does. {@code #minecraft:sand} holds sand
     * and red sand, as on a 1.18 server. Every other tag holds one stand-in block, so no tag warns.
     */
    private static final NameCatalog SERVER_LIKE = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            if (writtenId.startsWith("@") || writtenId.startsWith("#")) {
                return Optional.empty();
            }
            String lower = writtenId.toLowerCase(Locale.ROOT);
            return Optional.of(lower.contains(":") ? lower : "minecraft:" + lower);
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            if ("minecraft:sand".equals(namespacedTag)) {
                return Set.of("minecraft:sand", "minecraft:red_sand");
            }
            return Set.of("minecraft:tag_" + namespacedTag.replace(':', '_'));
        }
    };

    @Test
    void shippedListRefusesWaterCarpetAndSandWithHighWarningNamingLootAndBlock() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .loot("pond", loot("water"))
                .loot("rug", loot("white_carpet"))
                .loot("dune", loot("sand"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertRefused(snapshot, "pond", "minecraft:water");
        assertRefused(snapshot, "rug", "minecraft:white_carpet");
        assertRefused(snapshot, "dune", "minecraft:sand");
    }

    @Test
    void allowedBlockSuchAsChestLoads() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .loot("stash", loot("chest"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertEquals("minecraft:chest", snapshot.loot("stash").orElseThrow().block());
        assertTrue(snapshot.summonRules("stash").isPresent());
        assertNoBannedWarning(snapshot);
    }

    @Test
    void configListWithoutSandLetsSandLootLoad() throws IOException {
        String globals = resource("/config.yml");
        assertTrue(globals.contains(SHIPPED_LIST), "shipped config.yml names the shipped group");
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .globals(globals.replace(SHIPPED_LIST, """
                        banned_loot_blocks:
                          - '@liquids'
                          - 'minecraft:white_carpet'
                        """))
                .loot("dune", loot("sand"))
                .loot("pond", loot("water"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertEquals("minecraft:sand", snapshot.loot("dune").orElseThrow().block());
        assertRefused(snapshot, "pond", "minecraft:water");
    }

    @Test
    void ownerGroupWithoutGravityBlocksLetsSandLootLoad() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .blocks("""
                        groups:
                          banned_loot_blocks:
                            - '@liquids'
                        """)
                .loot("dune", loot("sand"))
                .loot("pond", loot("water"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertEquals("minecraft:sand", snapshot.loot("dune").orElseThrow().block());
        assertRefused(snapshot, "pond", "minecraft:water");
    }

    @Test
    void emptyConfigListBansNothing() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .globals(resource("/config.yml").replace(SHIPPED_LIST, "banned_loot_blocks: []\n"))
                .loot("pond", loot("water"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertEquals("minecraft:water", snapshot.loot("pond").orElseThrow().block());
    }

    @Test
    void summonRuleNamingBannedBlocksStillLoads() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .loot("stash", loot("chest") + """
                        summon_settings:
                          summon_type: underwater
                          replaceable_blocks:
                            - minecraft:water
                            - minecraft:tall_grass
                          avoid_surfaces:
                            - '@banned_loot_blocks'
                        """)
                .build(), SERVER_LIKE, WarningKinds.all());

        assertTrue(snapshot.loot("stash").isPresent());
        assertEquals(
                Set.of("minecraft:water", "minecraft:tall_grass"),
                snapshot.summonRules("stash").orElseThrow().replaceableBlocks()
        );
        assertTrue(snapshot.summonRules("stash").orElseThrow().avoidSurfaces().contains("minecraft:sand"));
        assertNoBannedWarning(snapshot);
    }

    @Test
    void shippedAirdropsStillLoadsEveryLoot() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertEquals(Set.of("airdrops"), snapshot.loots().keySet());
        assertEquals("minecraft:barrel", snapshot.loot("airdrops").orElseThrow().block());
        assertTrue(snapshot.summonRules("airdrops").isPresent());
        assertNoBannedWarning(snapshot);
    }

    @Test
    void shippedGroupHoldsEachKindOfBannedBlock() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped().build(), SERVER_LIKE, WarningKinds.all());

        Set<String> banned = snapshot.group("banned_loot_blocks").orElseThrow();
        for (String id : List.of(
                "minecraft:water", "minecraft:lava", "minecraft:bubble_column",
                "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
                "minecraft:white_carpet", "minecraft:moss_carpet",
                "minecraft:redstone_wire", "minecraft:tripwire",
                "minecraft:tall_grass", "minecraft:sugar_cane",
                "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
                "minecraft:white_concrete_powder", "minecraft:black_concrete_powder",
                "minecraft:tag_minecraft_doors", "minecraft:tag_minecraft_beds",
                "minecraft:tag_minecraft_anvil", "minecraft:tag_minecraft_fire", "minecraft:tag_minecraft_portals",
                "minecraft:piston_head", "minecraft:moving_piston", "minecraft:structure_void"
        )) {
            assertTrue(banned.contains(id), id);
        }
        for (String id : List.of("minecraft:chest", "minecraft:barrel", "minecraft:player_head", "minecraft:stone")) {
            assertFalse(banned.contains(id), id);
        }
    }

    @Test
    void nonListValueWarnsHighAndUsesShippedGroup() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .globals(resource("/config.yml").replace(SHIPPED_LIST, "banned_loot_blocks: minecraft:stone\n"))
                .loot("dune", loot("sand"))
                .loot("quarry", loot("stone"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNUSABLE_BANNED_LOOT_BLOCKS
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && "banned_loot_blocks".equals(w.key().orElse(null))));
        assertRefused(snapshot, "dune", "minecraft:sand");
        assertTrue(snapshot.loot("quarry").isPresent());
    }

    @Test
    void unknownGroupInConfigListWarnsHighNamingTheKey() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(shipped()
                .globals(resource("/config.yml").replace(SHIPPED_LIST, """
                        banned_loot_blocks:
                          - '@no_such_group'
                          - minecraft:sand
                        """))
                .loot("dune", loot("sand"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                w.kind() == ConfigWarning.Kind.UNKNOWN_GROUP
                        && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                        && "@no_such_group".equals(w.writtenId().orElse(null))
                        && "banned_loot_blocks".equals(w.key().orElse(null))));
        assertRefused(snapshot, "dune", "minecraft:sand");
    }

    private static void assertRefused(ConfigSnapshot snapshot, String lootId, String blockId) {
        assertTrue(snapshot.loot(lootId).isEmpty(), lootId + " should not load");
        assertTrue(snapshot.summonRules(lootId).isEmpty(), lootId + " should have no summon rules");
        assertTrue(snapshot.warnings().stream().anyMatch(w ->
                        w.kind() == ConfigWarning.Kind.BANNED_LOOT_BLOCK
                                && w.severity() == me.rakkoon69.rakunloots.warning.Severity.HIGH
                                && lootId.equals(w.lootId().orElse(null))
                                && blockId.equals(w.currentId().orElse(null))),
                lootId + " should warn high naming " + blockId);
    }

    private static void assertNoBannedWarning(ConfigSnapshot snapshot) {
        assertFalse(snapshot.warnings().stream().anyMatch(w -> w.kind() == ConfigWarning.Kind.BANNED_LOOT_BLOCK));
    }

    private static ConfigSources.Builder shipped() throws IOException {
        return ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .language("en", resource("/languages/en.yml"));
    }

    private static String loot(String block) {
        return """
                language: en
                open_mode: inventory
                block: %s
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                """.formatted(block);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigBannedLootBlocksTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
