package me.rakkoon69.rakunloots.hologram.fancy;

import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.HologramType;
import me.rakkoon69.rakunloots.hologram.FakeHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.OwnerNames;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FancyHolograms provider on Java 25 under the real hologram service over the shipped config, against a fake
 * FancyHolograms manager that keeps the real API's hologram data. The shipped airdrop's hologram sits 2 blocks above its
 * block with the global view range of 48.
 */
class FancyHologramsProviderTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_BOX = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final String BOX_NAME = "rakunloots_aaaaaaaa-0000-0000-0000-000000000001";
    private static final String OTHER_NAME = "rakunloots_aaaaaaaa-0000-0000-0000-000000000002";
    private static final OwnerNames NO_NAMES = id -> Optional.empty();

    private final FakeFancyHolograms plugin = new FakeFancyHolograms().withWorld(WORLD);
    private final FancyHologramsProvider provider = FakeFancyHolograms.provider(
            FakeHologramServer.paper("1.21.4").with("FancyHolograms", true), plugin);
    private final HologramService service = new HologramService(ShippedHologramConfig.load(), provider, NO_NAMES);

    /**
     * Acceptance: create draws a text hologram under a name from the box id, at the position, where a text display's
     * lowest line stands, with the rendered lines and the view range as its visibility distance.
     */
    @Test
    void createDrawsATextHologramAtThePositionUnderANameFromTheBoxId() {
        service.shown(box(BOX, 10));

        assertEquals(Set.of(BOX_NAME), plugin.names());
        TextHologramData data = text(BOX_NAME);
        assertEquals(HologramType.TEXT, data.getType());
        Location location = data.getLocation();
        assertSame(plugin.world(WORLD), location.getWorld());
        assertEquals(new HologramPosition(WORLD, 2.5, 66.0, 3.5),
                new HologramPosition(WORLD, location.getX(), location.getY(), location.getZ()));
        assertEquals(Display.Billboard.CENTER, data.getBillboard());
        assertEquals(48, data.getVisibilityDistance());
        assertEquals(List.of(
                "§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>",
                "§7Owner: §e§7No owner",
                "§7Lock: §c0:10",
                "§7Leaves in: §e6:00"), data.getText());
    }

    /**
     * Acceptance: a tick that changes the lines updates them in place, and the box's end removes the hologram.
     */
    @Test
    void updateReplacesTheLinesAndRemoveTakesTheHologramAway() {
        service.shown(box(BOX, 10));
        TextHologramData shown = text(BOX_NAME);
        shown.setHasChanges(false);

        service.ticked(box(BOX, 9));

        assertSame(shown, text(BOX_NAME));
        assertEquals("§7Lock: §c0:09", shown.getText().get(2));
        assertTrue(shown.hasChanges(), "FancyHolograms' hologram thread sends changed data on");

        service.ended(box(BOX, 9));
        assertEquals(Set.of(), plugin.names());
    }

    /**
     * Acceptance: remove all, run when the plugin stops, removes every hologram the provider draws and no other
     * hologram FancyHolograms has.
     */
    @Test
    void removeAllTakesAwayEveryHologramItDrawsAndNoOther() {
        plugin.createForeign("spawn_welcome", WORLD);
        service.shown(box(BOX, 10));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        assertEquals(Set.of("spawn_welcome", BOX_NAME, OTHER_NAME), plugin.names());

        service.stop();

        assertEquals(Set.of("spawn_welcome"), plugin.names());
        assertEquals(Set.of("spawn_welcome"), plugin.savedNames());
    }

    /**
     * Update and remove of an id the provider does not draw do nothing, even when FancyHolograms has a hologram of the
     * name that id would give.
     */
    @Test
    void updateAndRemoveOfAnUnknownIdDoNothing() {
        plugin.createForeign(BOX_NAME, WORLD);

        provider.update(BOX, List.of("§cnot mine"));
        provider.remove(BOX);
        provider.removeAll();

        assertEquals(List.of("someone else's"), text(BOX_NAME).getText());
    }

    /**
     * FancyHolograms replaces a hologram when another is added under its name, so create refuses a name FancyHolograms
     * already has, and the provider leaves that hologram alone on remove and remove all.
     */
    @Test
    void aNameFancyHologramsAlreadyHasIsRefusedAndLeftAlone() {
        plugin.createForeign(BOX_NAME, WORLD);

        assertThrows(IllegalArgumentException.class, () -> provider.create(BOX,
                new HologramPosition(WORLD, 2.5, 66.0, 3.5), List.of("§6Air Supply"), 48));
        provider.remove(BOX);
        provider.removeAll();

        assertEquals(List.of("someone else's"), text(BOX_NAME).getText());
        assertEquals(Set.of(BOX_NAME), plugin.savedNames());
    }

    /**
     * A hologram that took a box's name after the box's own was dropped is not removed with the box.
     */
    @Test
    void aHologramThatTookTheNameLaterIsLeftAlone() {
        service.shown(box(BOX, 10));
        plugin.removeHologram(plugin.getHologram(BOX_NAME).orElseThrow());
        plugin.createForeign(BOX_NAME, WORLD);

        service.ended(box(BOX, 10));

        assertEquals(List.of("someone else's"), text(BOX_NAME).getText());
    }

    /**
     * Acceptance: no hologram is created persistent, through a whole life of boxes, so FancyHolograms' save writes none
     * to {@code holograms.yml} and its reload, which unloads and loads only persistent holograms, keeps them. The real
     * API's hologram data is persistent unless told otherwise.
     */
    @Test
    void noHologramIsCreatedPersistent() {
        assertTrue(new TextHologramData("any", new Location(plugin.world(WORLD), 0, 0, 0)).isPersistent(),
                "FancyHolograms' default");
        service.shown(box(BOX, 10));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        service.outOfView(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));
        service.shown(boxAt(OTHER_BOX, new BlockPosition(-10, 64, 3)));

        plugin.saveHolograms();
        plugin.reloadHolograms();

        assertEquals(List.of(BOX_NAME, OTHER_NAME, OTHER_NAME),
                plugin.created().stream().map(HologramData::getName).toList());
        assertEquals(List.of(false, false, false),
                plugin.created().stream().map(HologramData::isPersistent).toList());
        assertEquals(Set.of(), plugin.savedNames());
        assertEquals(Set.of(BOX_NAME, OTHER_NAME), plugin.names());
        service.ticked(box(BOX, 9));
        assertEquals("§7Lock: §c0:09", text(BOX_NAME).getText().get(2));
    }

    /**
     * A box in a world that is not loaded is refused and not recorded.
     */
    @Test
    void aWorldThatIsNotLoadedIsRefused() {
        UUID elsewhere = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

        assertThrows(IllegalStateException.class, () -> provider.create(BOX,
                new HologramPosition(elsewhere, 0.5, 64.0, 0.5), List.of("§6Air Supply"), 48));

        assertEquals(Set.of(), plugin.names());
        provider.remove(BOX);
    }

    /**
     * Acceptance: can run is false when FancyHolograms is missing or disabled, below Minecraft 1.21.4, on Spigot, and
     * on Folia without {@code folia-supported: true}.
     */
    @ParameterizedTest(name = "{0} {1} with {2}")
    @CsvSource({
            "paper, 1.21.4, enabled, ''",
            "paper, 26.1, enabled, ''",
            "folia, 1.21.4, folia, ''",
            "paper, 1.21.4, missing, FancyHolograms is not installed",
            "paper, 1.21.4, disabled, FancyHolograms is not enabled",
            "paper, 1.21.3, enabled, 'FancyHolograms needs Minecraft 1.21.4 or newer, and this server runs 1.21.3'",
            "spigot, 1.21.4, enabled, 'FancyHolograms needs Paper, and this server is Spigot'",
            "folia, 1.21.4, enabled, "
                    + "'FancyHolograms does not declare folia-supported: true, and this server is Folia'",
    })
    void canRunNeedsFancyHologramsOnPaperFrom1214AndFoliaSupportOnFolia(String platform, String version,
                                                                        String installed, String refusal) {
        FakeHologramServer server = server(platform, version, installed);

        CanRun canRun = FakeFancyHolograms.provider(server, new FakeFancyHolograms()).canRun();

        assertEquals(refusal.isEmpty() ? CanRun.YES : CanRun.no(refusal), canRun);
    }

    /**
     * The same server answers the same through the loader, which the core calls, on this Java 25 test JVM.
     */
    @ParameterizedTest(name = "{0} {1} with {2}")
    @CsvSource({
            "paper, 1.21.4, missing",
            "paper, 1.21.3, enabled",
            "spigot, 1.21.4, enabled",
            "folia, 1.21.4, enabled",
            "folia, 1.21.4, folia",
    })
    void theLoaderAnswersAsTheProviderDoes(String platform, String version, String installed) {
        FakeHologramServer server = server(platform, version, installed);
        FancyHologramsProvider provider = FakeFancyHolograms.provider(server, new FakeFancyHolograms());

        HologramProvider loaded = FancyHologramsProviderLoader.load(server, Runtime.version().feature(),
                () -> provider);

        assertEquals(provider.canRun(), loaded.canRun());
        assertEquals(provider.canRun().yes(), loaded == provider);
    }

    static FakeHologramServer server(String platform, String version, String installed) {
        FakeHologramServer server = switch (platform) {
            case "paper" -> FakeHologramServer.paper(version);
            case "spigot" -> FakeHologramServer.spigot(version);
            default -> FakeHologramServer.folia(version);
        };
        switch (installed) {
            case "enabled" -> server.with("FancyHolograms", false);
            case "folia" -> server.with("FancyHolograms", true);
            case "disabled" -> server.withDisabled("FancyHolograms");
            default -> {
            }
        }
        return server;
    }

    private TextHologramData text(String name) {
        HologramData data = plugin.data(name).orElseThrow(() -> new AssertionError("no hologram " + name));
        return assertInstanceOf(TextHologramData.class, data);
    }

    private static BoxEvent box(UUID boxId, int lock) {
        return new BoxEvent(boxId, "airdrops", WORLD, new BlockPosition(2, 64, 3), Optional.empty(), lock,
                OptionalInt.of(360));
    }

    private static BoxEvent boxAt(UUID boxId, BlockPosition position) {
        return new BoxEvent(boxId, "airdrops", WORLD, position, Optional.empty(), 10, OptionalInt.of(360));
    }
}
