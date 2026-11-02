package me.rakkoon69.rakunloots.summon;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.WorldSettings;
import me.rakkoon69.rakunloots.lootbox.Interaction;
import me.rakkoon69.rakunloots.lootbox.InteractionResult;
import me.rakkoon69.rakunloots.lootbox.LifecycleFixture;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.SummonSource;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static me.rakkoon69.rakunloots.summon.TimerFixture.NETHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The location broadcast, the removal broadcast, and the owner remove message, through the lifecycle's observer and the
 * fake world. Every test reads the messages the players got.
 */
class WorldBroadcastTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID NETHER_PLAYER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final UUID BYSTANDER = UUID.fromString("aaaaaaaa-7777-8888-9999-000000000000");
    private static final Pattern AROUND = Pattern.compile("around (-?\\d+) (-?\\d+) (-?\\d+)");
    private static final Pattern TO = Pattern.compile("to (-?\\d+) (-?\\d+) (-?\\d+)");

    @TempDir
    Path folder;

    private LifecycleFixture base;
    private LootBoxStore store;
    private TimerFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        base = new LifecycleFixture(folder);
        store = base.openStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    private TimerFixture start(ConfigSnapshot config) {
        fixture = new TimerFixture(store, base, config, 11);
        fixture.playerInWorld(OWNER);
        fixture.playerInWorld(BYSTANDER);
        fixture.playerIn(NETHER, NETHER_PLAYER);
        return fixture;
    }

    private static ConfigSnapshot custom(String worldKeys, Map<String, String> loots) {
        return TimerFixture.config(TimerFixture.worlds(worldKeys), loots);
    }

    private static String plain(String text) {
        return text.replaceAll("§.", "");
    }

    /**
     * A box the timer summons at a spot, once the loot's summon delay has passed.
     */
    private LootBox timerBox(int x, int z, long delayTicks) {
        fixture.finder.spots.add(airSpot(x, z));
        CompletableFuture<TimerOutcome> outcome = fixture.timers.summonNow("world", true);
        fixture.step(delayTicks);
        return outcome.join().report().orElseThrow().box().orElseThrow();
    }

    /**
     * Acceptance: the location broadcast reaches every player in every world, and names an area of
     * 2 * public_location_horizontal_offset by 2 * public_location_vertical_offset that holds the real spot. Shipped
     * worlds.yml and airdrops.yml: offsets 40 and 15.
     */
    @Test
    void theLocationBroadcastOffsetsByTheWorldsPublicLocationOffsets() {
        start(shippedConfig());

        LootBox box = timerBox(3, 4, 60);

        for (UUID player : List.of(OWNER, BYSTANDER, NETHER_PLAYER)) {
            List<String> messages = fixture.messagesTo(player);
            assertEquals(1, messages.size(), player.toString());
            String text = plain(messages.get(0));
            Matcher from = AROUND.matcher(text);
            Matcher to = TO.matcher(text);
            assertTrue(from.find() && to.find(), text);
            int fromX = Integer.parseInt(from.group(1));
            int fromY = Integer.parseInt(from.group(2));
            int fromZ = Integer.parseInt(from.group(3));
            assertEquals(fromX + 80, Integer.parseInt(to.group(1)), "twice the horizontal offset wide");
            assertEquals(fromY + 30, Integer.parseInt(to.group(2)), "twice the vertical offset tall");
            assertEquals(fromZ + 80, Integer.parseInt(to.group(3)));
            BlockPosition spot = box.position();
            assertTrue(fromX <= spot.x() && spot.x() <= fromX + 80, text);
            assertTrue(fromY <= spot.y() && spot.y() <= fromY + 30, text);
            assertTrue(fromZ <= spot.z() && spot.z() <= fromZ + 80, text);
            assertTrue(text.contains("Air Supply"), text);
            assertTrue(text.contains("world"), text);
            assertFalse(Pattern.compile("<[a-z_]+>").matcher(text).find(), "every placeholder is filled: " + text);
        }
    }

    /**
     * With no offsets the area is the real spot.
     */
    @Test
    void withNoOffsetsTheBroadcastNamesTheRealSpot() {
        start(custom("loot_spawn_limit: 9\nloots:\n  fast: 1", Map.of("fast", TimerFixture.FAST)));

        timerBox(3, 4, 0);

        String text = plain(fixture.messagesTo(OWNER).get(0));
        Matcher from = AROUND.matcher(text);
        Matcher to = TO.matcher(text);
        assertTrue(from.find() && to.find(), text);
        assertEquals("3 64 4", from.group(1) + " " + from.group(2) + " " + from.group(3));
        assertEquals("3 64 4", to.group(1) + " " + to.group(2) + " " + to.group(3));
    }

    /**
     * Acceptance: the broadcast uses default messages and a loot's message overrides.
     */
    @Test
    void aLootsMessageOverrideRewordsTheBroadcast() {
        String reworded = TimerFixture.FAST + """
                messages:
                  lootbox_spawned_broadcast: "{prefix} Crate {box_name} is near {from_x} {from_z}!"
                """;
        start(custom("loot_spawn_limit: 9\nloots:\n  fast: 1", Map.of("fast", reworded)));

        timerBox(3, 4, 0);

        assertTrue(plain(fixture.messagesTo(OWNER).get(0)).matches("\\[RakunLoots\\] Crate fast is near -?\\d+ -?\\d+!"),
                fixture.messagesTo(OWNER).get(0));
    }

    /**
     * Acceptance: broadcast_remove announces a removal to everyone, in a world that has it on.
     */
    @Test
    void broadcastRemoveAnnouncesARemoval() {
        start(shippedConfig());
        LootBox box = timerBox(3, 4, 60);
        fixture.forgetMessages();

        clickAsOwnerOf(box);

        for (UUID player : List.of(OWNER, BYSTANDER, NETHER_PLAYER)) {
            List<String> messages = fixture.messagesTo(player);
            assertEquals(1, messages.stream().filter(text -> plain(text).contains("looted")).count(),
                    player + " " + messages);
        }
        String text = plain(fixture.messagesTo(BYSTANDER).get(0));
        assertTrue(text.contains("3 64 4") && text.contains("world") && text.contains("Air Supply"), text);
    }

    /**
     * Acceptance: broadcast_remove announces the removal only of boxes the world timer summoned. A box a summon item,
     * an admin command, or an unknown source summoned ends without an announcement, with the world's broadcast_remove
     * on.
     */
    @Test
    void broadcastRemoveAnnouncesOnlyBoxesTheWorldTimerSummoned() {
        start(shippedConfig());
        LootBox timer = timerBox(3, 4, 60);
        fixture.finder.spots.add(airSpot(5, 5));
        fixture.finder.spots.add(airSpot(7, 7));
        fixture.service.summon("airdrops", SummonTarget.world(WORLD), Optional.of(OWNER),
                SummonSource.summonItem(OWNER));
        fixture.service.adminSummon("airdrops", SummonTarget.world(WORLD), Optional.of(OWNER));
        fixture.step(60);
        LootBox item = fixture.lifecycle.boxAt(WORLD, new BlockPosition(5, 64, 5)).orElseThrow();
        LootBox admin = fixture.lifecycle.boxAt(WORLD, new BlockPosition(7, 64, 7)).orElseThrow();
        LootBox unknown = fixture.lifecycle.summon("airdrops", airSpot(9, 9), Optional.of(OWNER), SummonKind.UNKNOWN)
                .join().box().orElseThrow();
        assertEquals(SummonKind.SUMMON_ITEM, item.source());
        assertEquals(SummonKind.ADMIN_COMMAND, admin.source());

        for (LootBox box : List.of(item, admin, unknown)) {
            fixture.forgetMessages();
            clickAsOwnerOf(box);
            assertEquals(List.of(), fixture.messagesTo(BYSTANDER), box.source().name());
            assertEquals(List.of(), fixture.messagesTo(NETHER_PLAYER), box.source().name());
        }

        fixture.forgetMessages();
        clickAsOwnerOf(timer);
        assertEquals(1, fixture.messagesTo(BYSTANDER).stream().filter(text -> plain(text).contains("looted")).count(),
                fixture.messagesTo(BYSTANDER).toString());
    }

    /**
     * A world with broadcast_remove off announces nothing.
     */
    @Test
    void aWorldWithBroadcastRemoveOffAnnouncesNothing() {
        start(custom("broadcast_remove: false\nloot_spawn_limit: 9\nloots:\n  fast: 1",
                Map.of("fast", TimerFixture.FAST)));
        LootBox box = timerBox(3, 4, 0);
        fixture.forgetMessages();

        clickAsOwnerOf(box);

        assertEquals(List.of(), fixture.messagesTo(BYSTANDER));
        assertEquals(List.of(), fixture.messagesTo(NETHER_PLAYER));
    }

    /**
     * Acceptance: the owner remove message comes back to the loot file and the shipped airdrops.yml. The shipped
     * airdrop has message_owner_on_remove_enable on, so its owner is told when the box is looted, with the default
     * message.
     */
    @Test
    void theShippedAirdropTellsItsOwnerWhenItsBoxIsRemoved() {
        start(shippedConfig());
        assertTrue(fixture.config.loot("airdrops").orElseThrow().messageOwnerOnRemove());
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.service.summon("airdrops", SummonTarget.world(WORLD), Optional.of(OWNER),
                SummonSource.summonItem(OWNER));
        fixture.step(60);
        LootBox box = fixture.lifecycle.boxAt(WORLD, new BlockPosition(3, 64, 4)).orElseThrow();
        fixture.forgetMessages();

        clickAsOwnerOf(box);

        List<String> told = fixture.messagesTo(OWNER).stream().map(WorldBroadcastTest::plain)
                .filter(text -> text.contains("has disappeared")).toList();
        assertEquals(1, told.size(), fixture.messagesTo(OWNER).toString());
        assertEquals("[RakunLoots] Your Air Supply at 3 64 4 has disappeared.", told.get(0));
        assertTrue(fixture.messagesTo(BYSTANDER).stream().noneMatch(text -> plain(text).contains("disappeared")));
    }

    /**
     * A loot that leaves message_owner_on_remove_enable out tells its owner nothing, and a loot whose owner message
     * is reworded says the reworded text.
     */
    @Test
    void theOwnerIsToldOnlyWhenTheLootAsksAndWithTheLootsWording() {
        String silent = TimerFixture.FAST;
        String reworded = TimerFixture.FAST + """
                message_owner_on_remove_enable: true
                messages:
                  message_owner_on_remove: "{prefix} Gone: {loot_name} at {box_x}!"
                """;
        start(custom("loot_spawn_limit: 9\nbroadcast_remove: false\nloots:\n  silent: 1",
                Map.of("silent", silent, "reworded", reworded)));
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.finder.spots.add(airSpot(7, 7));
        fixture.service.summon("silent", SummonTarget.world(WORLD), Optional.of(OWNER), SummonSource.adminCommand());
        fixture.service.summon("reworded", SummonTarget.world(WORLD), Optional.of(OWNER), SummonSource.adminCommand());
        fixture.forgetMessages();

        clickAsOwnerOf(fixture.lifecycle.boxAt(WORLD, new BlockPosition(3, 64, 4)).orElseThrow());
        assertEquals(List.of(), fixture.messagesTo(OWNER), "the silent loot says nothing");

        clickAsOwnerOf(fixture.lifecycle.boxAt(WORLD, new BlockPosition(7, 64, 7)).orElseThrow());
        assertEquals(List.of("[RakunLoots] Gone: reworded at 7!"),
                fixture.messagesTo(OWNER).stream().map(WorldBroadcastTest::plain).toList());
    }

    /**
     * A box with no owner tells nobody, even when its loot asks.
     */
    @Test
    void aBoxWithNoOwnerTellsNobody() {
        start(shippedConfig());
        fixture.finder.spots.add(airSpot(3, 4));
        fixture.service.adminSummon("airdrops", SummonTarget.world(WORLD), Optional.empty());
        fixture.step(60);
        fixture.forgetMessages();

        fixture.lifecycle.interact(BYSTANDER, WORLD, new BlockPosition(3, 64, 4), true, Interaction.CLICK);

        assertTrue(fixture.messagesTo(OWNER).stream().noneMatch(text -> plain(text).contains("disappeared")));
    }

    /**
     * Acceptance: text uses default messages in every shipped language. Each of the three keys is defined in en, vi,
     * and zh, and the shipped worlds.yml keys are read from the shipped world.
     */
    @Test
    void everyShippedLanguageDefinesTheBroadcastMessages() {
        for (String language : List.of("en", "vi", "zh")) {
            ConfigSnapshot config = TimerFixture.config("worlds:\n  world:\n    loots:\n      fast: 1\n",
                    Map.of("fast", TimerFixture.FAST.replace("language: en", "language: " + language)));
            for (String key : List.of("lootbox_spawned_broadcast", "lootbox_removed_broadcast",
                    "message_owner_on_remove")) {
                assertFalse(config.message("fast", key).isBlank(), language + " " + key);
            }
        }
        WorldSettings shipped = shippedConfig().world("world").orElseThrow();
        assertEquals(40, shipped.publicLocationHorizontalOffset());
    }

    private void clickAsOwnerOf(LootBox box) {
        UUID owner = box.owner().orElse(OWNER);
        InteractionResult result = fixture.lifecycle.interact(owner, WORLD, box.position(), true, Interaction.CLICK);
        assertEquals(InteractionResult.ALLOWED, result);
    }
}
