package me.rakkoon69.rakunloots.hologram;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider.Call;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider.Recorded;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hologram service driven through box observer events over the shipped config, checked by what a recording
 * provider was asked to draw.
 */
class HologramServiceTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final BlockPosition BLOCK = new BlockPosition(2, 64, 3);
    private static final OwnerNames NAMES = id -> id.equals(OWNER) ? Optional.of("Rakkoon") : Optional.empty();

    private final RecordingHologramProvider provider = new RecordingHologramProvider();

    /**
     * Acceptance: with the shipped config, a summoned box gets a hologram at its height with every placeholder filled.
     * The shipped airdrop has height 2 and no view range of its own, so the global 48 applies; its lines are the
     * English {@code hologram_lines}. The hologram's lowest line sits 2 blocks above the bottom of the block, centred.
     */
    @Test
    void aSummonedBoxGetsAHologramAtItsHeightWithEveryPlaceholderFilled() {
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NAMES);

        service.shown(event(BOX, "airdrops", Optional.of(OWNER), 10, OptionalInt.of(360)));

        List<String> lines = List.of(
                "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                "§7Owner: §eRakkoon",
                "§7Lock: §c0:10",
                "§7Leaves in: §e6:00");
        assertEquals(List.of(new Recorded(Call.CREATE, BOX, new HologramPosition(WORLD, 2.5, 66.0, 3.5), lines, 48)),
                provider.calls());
    }

    /**
     * Acceptance: a box with no owner, no lock left, and unending life shows the three edge messages.
     */
    @Test
    void aBoxWithNoOwnerNoLockLeftAndUnendingLifeShowsTheThreeEdgeMessages() {
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NAMES);

        service.shown(event(BOX, "airdrops", Optional.empty(), 0, OptionalInt.empty()));

        assertEquals(List.of(
                        "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                        "§7Owner: §e§7No owner",
                        "§7Lock: §c§aUnlocked",
                        "§7Leaves in: §e§aUnending"),
                onlyCreate().lines());
    }

    /**
     * The lines and the three edge messages come from the active language.
     */
    @Test
    void theLinesAndEdgeMessagesFollowTheActiveLanguage() {
        HologramService service = new HologramService(ShippedHologramConfig.load("zh", Map.of()), provider, NAMES);

        service.shown(event(BOX, "airdrops", Optional.empty(), 0, OptionalInt.empty()));

        assertEquals(List.of(
                        "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                        "§7所有者：§e§7无所有者",
                        "§7锁定：§c§a已解锁",
                        "§7剩余时间：§e§a永久"),
                onlyCreate().lines());
    }

    /**
     * Acceptance: a loot's own {@code hologram.lines} replace the default lines, and a message override under
     * {@code messages:} replaces an edge message. Time reads h:mm:ss from an hour.
     */
    @Test
    void aLootOverrideReplacesALine() {
        ConfigSnapshot config = ShippedHologramConfig.load("en", Map.of("custom", ShippedHologramConfig.loot("&6Custom", """
                messages:
                  hologram_unlocked: '&bOpen'
                hologram:
                  lines:
                    - '{name} by {owner}'
                    - '{lock} | {life}'
                """)));
        HologramService service = new HologramService(config, provider, NAMES);

        service.shown(event(BOX, "custom", Optional.of(OWNER), 0, OptionalInt.of(3665)));
        service.shown(event(OTHER_BOX, "airdrops", Optional.of(OWNER), 0, OptionalInt.of(59)));

        List<Recorded> creates = provider.calls(Call.CREATE);
        assertEquals(List.of("§6Custom by Rakkoon", "§bOpen | 1:01:05"), creates.get(0).lines());
        assertEquals(List.of(
                        "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                        "§7Owner: §eRakkoon",
                        "§7Lock: §c§aUnlocked",
                        "§7Leaves in: §e0:59"),
                creates.get(1).lines(), "the override is the custom loot's only");
    }

    /**
     * Acceptance: {@code enabled: false} creates nothing, and its box's later events draw nothing either.
     */
    @Test
    void enabledFalseCreatesNothing() {
        ConfigSnapshot config = ShippedHologramConfig.load("en", Map.of("hidden", ShippedHologramConfig.loot("Hidden", """
                hologram:
                  enabled: false
                """)));
        HologramService service = new HologramService(config, provider, NAMES);
        BoxEvent box = event(BOX, "hidden", Optional.empty(), 5, OptionalInt.of(60));

        service.shown(box);
        service.ticked(event(BOX, "hidden", Optional.empty(), 4, OptionalInt.of(59)));
        service.outOfView(box);
        service.ended(box);

        assertEquals(List.of(), provider.calls());
    }

    /**
     * A loot's own height and view range place the hologram and set who sees it.
     */
    @Test
    void aLootsHeightAndViewRangeAreUsed() {
        ConfigSnapshot config = ShippedHologramConfig.load("en", Map.of("tall", ShippedHologramConfig.loot("Tall", """
                hologram:
                  height: 3.25
                  view_range: 20
                """)));
        HologramService service = new HologramService(config, provider, NAMES);

        service.shown(event(BOX, "tall", Optional.empty(), 5, OptionalInt.of(60)));

        Recorded create = onlyCreate();
        assertEquals(new HologramPosition(WORLD, 2.5, 67.25, 3.5), create.position());
        assertEquals(20, create.viewRange());
    }

    /**
     * Acceptance: ticks update the provider only when the rendered lines change. A tick that changes nothing the lines
     * show sends nothing; a changed number sends the new lines once.
     */
    @Test
    void ticksUpdateTheProviderOnlyWhenTheRenderedLinesChange() {
        ConfigSnapshot config = ShippedHologramConfig.load("en", Map.of("still", ShippedHologramConfig.loot("Still", """
                hologram:
                  lines:
                    - '{name} {owner}'
                """)));
        HologramService service = new HologramService(config, provider, NAMES);
        service.shown(event(BOX, "airdrops", Optional.of(OWNER), 10, OptionalInt.of(360)));
        service.shown(event(OTHER_BOX, "still", Optional.of(OWNER), 5, OptionalInt.of(60)));
        provider.clear();

        service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 10, OptionalInt.of(360)));
        service.ticked(event(OTHER_BOX, "still", Optional.of(OWNER), 4, OptionalInt.of(59)));
        service.ticked(event(OTHER_BOX, "still", Optional.of(OWNER), 3, OptionalInt.of(58)));
        assertEquals(List.of(), provider.calls(), "nothing shown changed");

        service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 9, OptionalInt.of(359)));
        service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 9, OptionalInt.of(359)));

        assertEquals(List.of(new Recorded(Call.UPDATE, BOX, null, List.of(
                "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                "§7Owner: §eRakkoon",
                "§7Lock: §c0:09",
                "§7Leaves in: §e5:59"), null)), provider.calls());
    }

    /**
     * Ticked comes for every live box, shown or not; a box with no hologram draws nothing.
     */
    @Test
    void aTickForABoxWithNoHologramDrawsNothing() {
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NAMES);

        service.ticked(event(BOX, "airdrops", Optional.empty(), 9, OptionalInt.of(359)));
        service.ticked(event(OTHER_BOX, "gone", Optional.empty(), 9, OptionalInt.of(359)));
        service.shown(event(OTHER_BOX, "gone", Optional.empty(), 9, OptionalInt.of(359)));
        service.ended(event(BOX, "airdrops", Optional.empty(), 9, OptionalInt.of(359)));

        assertEquals(List.of(), provider.calls());
    }

    /**
     * Acceptance: out of view removes the hologram, and the box's next shown draws it again.
     */
    @Test
    void outOfViewRemovesTheHologram() {
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NAMES);
        BoxEvent box = event(BOX, "airdrops", Optional.empty(), 10, OptionalInt.of(360));
        service.shown(box);
        service.shown(event(OTHER_BOX, "airdrops", Optional.empty(), 10, OptionalInt.of(360)));
        provider.clear();

        service.outOfView(box);
        service.outOfView(box);
        service.ticked(event(BOX, "airdrops", Optional.empty(), 9, OptionalInt.of(359)));

        assertEquals(List.of(new Recorded(Call.REMOVE, BOX, null, null, null)), provider.calls());
        assertEquals(List.of(OTHER_BOX), List.copyOf(provider.drawn().keySet()));

        service.shown(event(BOX, "airdrops", Optional.empty(), 8, OptionalInt.of(358)));

        assertEquals(List.of(BOX), provider.calls(Call.CREATE).stream().map(Recorded::hologramId).toList());
    }

    /**
     * Acceptance: a loot end removes the hologram. Every loot end reaches the observer as ended, so one path covers
     * each of them.
     */
    @Test
    void anEndedBoxHasItsHologramRemoved() {
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NAMES);
        service.shown(event(BOX, "airdrops", Optional.empty(), 10, OptionalInt.of(360)));
        provider.clear();

        service.ended(event(BOX, "airdrops", Optional.empty(), 3, OptionalInt.of(0)));
        service.ended(event(BOX, "airdrops", Optional.empty(), 3, OptionalInt.of(0)));

        assertEquals(List.of(new Recorded(Call.REMOVE, BOX, null, null, null)), provider.calls());
        assertTrue(provider.drawn().isEmpty());
    }

    /**
     * Acceptance: stop removes every hologram through remove all, and draws nothing afterwards.
     */
    @Test
    void stopRemovesAll() {
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NAMES);
        service.shown(event(BOX, "airdrops", Optional.empty(), 10, OptionalInt.of(360)));
        service.shown(event(OTHER_BOX, "airdrops", Optional.empty(), 10, OptionalInt.of(360)));
        provider.clear();

        service.stop();
        service.shown(event(UUID.randomUUID(), "airdrops", Optional.empty(), 10, OptionalInt.of(360)));
        service.ticked(event(BOX, "airdrops", Optional.empty(), 9, OptionalInt.of(359)));

        assertEquals(List.of(new Recorded(Call.REMOVE_ALL, null, null, null, null)), provider.calls());
        assertTrue(provider.drawn().isEmpty());
    }

    /**
     * After a reload, the next tick removes a hologram whose loot turned its hologram off, moves one whose height
     * changed, and renders new lines.
     */
    @Test
    void aReloadTakesEffectOnTheNextTick() {
        String before = ShippedHologramConfig.loot("Crate", "hologram:\n  height: 1\n");
        HologramService service = new HologramService(
                ShippedHologramConfig.load("en", Map.of("crate", before, "hidden", before)), provider, NAMES);
        service.shown(event(BOX, "crate", Optional.empty(), 5, OptionalInt.of(60)));
        service.shown(event(OTHER_BOX, "hidden", Optional.empty(), 5, OptionalInt.of(60)));
        provider.clear();

        service.reload(ShippedHologramConfig.load("en", Map.of(
                "crate", ShippedHologramConfig.loot("New crate", "hologram:\n  height: 2.5\n"),
                "hidden", ShippedHologramConfig.loot("Crate", "hologram:\n  enabled: false\n"))));
        assertEquals(List.of(), provider.calls(), "nothing until the tick");

        service.ticked(event(BOX, "crate", Optional.empty(), 4, OptionalInt.of(59)));
        service.ticked(event(OTHER_BOX, "hidden", Optional.empty(), 4, OptionalInt.of(59)));

        List<Recorded> calls = provider.calls();
        assertEquals(List.of(Call.REMOVE, Call.CREATE, Call.REMOVE), calls.stream().map(Recorded::call).toList());
        assertEquals(new HologramPosition(WORLD, 2.5, 66.5, 3.5), calls.get(1).position());
        assertEquals("§f§l<< §rNew crate §f§l>>", calls.get(1).lines().get(0));
        assertEquals(OTHER_BOX, calls.get(2).hologramId());
    }

    /**
     * A reload that changes {@code hologram_provider} takes every hologram off the old provider, which is told to
     * remove all, and draws each shown box through the picked provider with its lines, position, and view range. The
     * next tick updates the new provider only.
     */
    @Test
    void aReloadThatChangesTheProviderMovesEveryShownHologram() {
        RecordingHologramProvider next = new RecordingHologramProvider();
        List<HologramProviderChoice> picked = new java.util.ArrayList<>();
        ConfigSnapshot before = ShippedHologramConfig.load("en", shippedWorlds(), Map.of(), "packets");
        HologramService service = new HologramService(before, provider, NAMES, snapshot -> {
            picked.add(snapshot.hologramProvider());
            return next;
        });
        service.shown(event(BOX, "airdrops", Optional.of(OWNER), 10, OptionalInt.of(360)));
        service.shown(event(OTHER_BOX, "airdrops", Optional.empty(), 0, OptionalInt.empty()));
        Map<UUID, List<String>> drawnBefore = provider.drawn();
        Recorded created = provider.calls(Call.CREATE).get(0);

        service.reload(ShippedHologramConfig.load("en", shippedWorlds(), Map.of(), "decentholograms"));

        assertEquals(List.of(HologramProviderChoice.DECENTHOLOGRAMS), picked);
        assertEquals(1, provider.calls(Call.REMOVE_ALL).size(), "the old provider was told to stop");
        assertTrue(provider.drawn().isEmpty());
        assertEquals(drawnBefore, next.drawn(), "every shown hologram moved with its lines");
        Recorded moved = next.calls(Call.CREATE).stream().filter(call -> call.hologramId().equals(BOX)).findFirst()
                .orElseThrow();
        assertEquals(created.position(), moved.position());
        assertEquals(created.viewRange(), moved.viewRange());

        provider.clear();
        next.clear();
        service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 9, OptionalInt.of(359)));
        assertEquals(List.of(), provider.calls());
        assertEquals(List.of(Call.UPDATE), next.calls().stream().map(Recorded::call).toList());
        service.ended(event(BOX, "airdrops", Optional.of(OWNER), 9, OptionalInt.of(359)));
        assertEquals(List.of(), provider.calls(), "the old provider is never called again");
        assertEquals(Set.of(OTHER_BOX), next.drawn().keySet());
    }

    /**
     * A reload that keeps the {@code hologram_provider} value keeps the provider and asks the picker nothing.
     */
    @Test
    void aReloadThatKeepsTheProviderValueKeepsTheProvider() {
        AtomicInteger picks = new AtomicInteger();
        ConfigSnapshot config = ShippedHologramConfig.load("en", shippedWorlds(), Map.of(), "packets");
        HologramService service = new HologramService(config, provider, NAMES, snapshot -> {
            picks.incrementAndGet();
            return new RecordingHologramProvider();
        });
        service.shown(event(BOX, "airdrops", Optional.of(OWNER), 10, OptionalInt.of(360)));
        provider.clear();

        service.reload(ShippedHologramConfig.load("zh", shippedWorlds(), Map.of(), "packets"));
        service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 9, OptionalInt.of(359)));

        assertEquals(0, picks.get());
        assertEquals(List.of(Call.UPDATE), provider.calls().stream().map(Recorded::call).toList());
        assertEquals(Set.of(BOX), provider.drawn().keySet());
    }

    private static String shippedWorlds() {
        try (java.io.InputStream in = HologramServiceTest.class.getResourceAsStream("/worlds.yml")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    /**
     * An owner's name is looked up once, not on every tick. A name the server does not know shows the owner's id.
     */
    @Test
    void anOwnersNameIsLookedUpOnce() {
        AtomicInteger lookups = new AtomicInteger();
        OwnerNames counting = id -> {
            lookups.incrementAndGet();
            return NAMES.name(id);
        };
        UUID stranger = UUID.fromString("99999999-0000-0000-0000-000000000000");
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, counting);

        service.shown(event(BOX, "airdrops", Optional.of(OWNER), 10, OptionalInt.of(360)));
        service.shown(event(OTHER_BOX, "airdrops", Optional.of(stranger), 10, OptionalInt.of(360)));
        for (int second = 1; second <= 3; second++) {
            service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 10 - second, OptionalInt.of(360 - second)));
        }

        assertEquals(2, lookups.get());
        assertEquals("§7Owner: §e" + stranger, provider.drawn().get(OTHER_BOX).get(1));
    }

    /**
     * A name the server cannot give is not asked for again: the owner's id is kept as their name, so two ticks make one
     * lookup.
     */
    @Test
    void anOwnerTheServerCannotNameIsLookedUpOnce() {
        AtomicInteger lookups = new AtomicInteger();
        OwnerNames unknown = id -> {
            lookups.incrementAndGet();
            return Optional.empty();
        };
        HologramService service = new HologramService(ShippedHologramConfig.load(), provider, unknown);
        service.shown(event(BOX, "airdrops", Optional.of(OWNER), 10, OptionalInt.of(360)));

        service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 9, OptionalInt.of(359)));
        service.ticked(event(BOX, "airdrops", Optional.of(OWNER), 8, OptionalInt.of(358)));

        assertEquals(1, lookups.get());
        assertEquals("§7Owner: §e" + OWNER, provider.drawn().get(BOX).get(1));
    }

    @Test
    void timeReadsMinutesAndSecondsOrHoursMinutesAndSeconds() {
        assertEquals("0:00", HologramService.time(0));
        assertEquals("0:09", HologramService.time(9));
        assertEquals("6:00", HologramService.time(360));
        assertEquals("59:59", HologramService.time(3599));
        assertEquals("1:00:00", HologramService.time(3600));
        assertEquals("27:46:40", HologramService.time(100_000));
    }

    private Recorded onlyCreate() {
        List<Recorded> calls = provider.calls();
        assertEquals(1, calls.size(), calls.toString());
        assertEquals(Call.CREATE, calls.get(0).call());
        return calls.get(0);
    }

    private static BoxEvent event(UUID boxId, String lootId, Optional<UUID> owner, int lock, OptionalInt life) {
        return new BoxEvent(boxId, lootId, WORLD, BLOCK, owner, lock, life);
    }
}
