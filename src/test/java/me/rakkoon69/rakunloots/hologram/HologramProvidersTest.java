package me.rakkoon69.rakunloots.hologram;

import me.rakkoon69.rakunloots.config.HologramProviderChoice;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.hologram.decent.DecentHologramsProvider;
import me.rakkoon69.rakunloots.hologram.decent.FakeDecentHolograms;
import me.rakkoon69.rakunloots.hologram.fancy.FancyHologramsProviderLoader;
import me.rakkoon69.rakunloots.hologram.fancy.RecordingFancyHolograms;
import me.rakkoon69.rakunloots.hologram.holographic.FakeHolographicDisplays;
import me.rakkoon69.rakunloots.hologram.holographic.HolographicDisplaysProvider;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapterMatch;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapters;
import me.rakkoon69.rakunloots.hologram.packet.PacketHologramProvider;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warnings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static me.rakkoon69.rakunloots.config.HologramProviderChoice.AUTO;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.DECENTHOLOGRAMS;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.FANCYHOLOGRAMS;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.HOLOGRAPHICDISPLAYS;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.NONE;
import static me.rakkoon69.rakunloots.config.HologramProviderChoice.PACKETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider choice over fake server probes. DecentHolograms is the real provider over a fake DecentHolograms, which
 * can run from 1.18, and HolographicDisplays the real provider over a fake HolographicDisplays, which can run up to
 * 1.20.4 and never on Folia. FancyHolograms is the real loader on Java 25, which checks its requirement, Paper from
 * 1.21.4, and builds a recording provider in place of the Java 25 one. Each needs {@code folia-supported: true} on
 * Folia. Packets is the real packet provider with no adapter matched, as on a version without one, since this test
 * classpath carries no version adapter. Where a test needs the real adapter selection, its versions are ones no
 * adapter ever covers: below 1.18, and after 26.3.
 */
class HologramProvidersTest {

    private static final PluginRequirement FANCY = FancyHologramsProviderLoader.REQUIREMENT;
    private static final PluginRequirement HOLOGRAPHIC = HolographicDisplaysProvider.REQUIREMENT;

    private final List<HologramWarning> warnings = new ArrayList<>();
    private final Warnings sink = w -> warnings.add((HologramWarning) w);

    /**
     * Acceptance: {@code auto}, the shipped default, tries packets, DecentHolograms, FancyHolograms, then
     * HolographicDisplays, picks the first that can run, and asks none after it.
     */
    @Test
    void autoFollowsTheOrderAndPicksTheFirstThatCanRun() {
        assertEquals(AUTO, ShippedHologramConfig.load().hologramProvider());
        assertEquals(List.of(PACKETS, DECENTHOLOGRAMS, FANCYHOLOGRAMS, HOLOGRAPHICDISPLAYS), HologramProviders.AUTO_ORDER);

        for (int first = 0; first < 4; first++) {
            Map<HologramProviderChoice, RecordingHologramProvider> providers = new EnumMap<>(HologramProviderChoice.class);
            for (int index = 0; index < 4; index++) {
                providers.put(HologramProviders.AUTO_ORDER.get(index), new RecordingHologramProvider(
                        index >= first ? CanRun.YES : CanRun.no("not this one")));
            }

            HologramProvider chosen = HologramProviders.choose(AUTO, Map.copyOf(providers), sink);

            HologramProviderChoice expected = HologramProviders.AUTO_ORDER.get(first);
            assertSame(providers.get(expected), chosen, "expected " + expected);
            for (int index = 0; index < 4; index++) {
                assertEquals(index <= first ? 1 : 0,
                        providers.get(HologramProviders.AUTO_ORDER.get(index)).canRunAsked(),
                        "asked " + HologramProviders.AUTO_ORDER.get(index) + " with " + expected + " first");
            }
            assertEquals(first == 0 ? List.of() : List.of(HologramWarning.Kind.PACKETS_CANNOT_RUN),
                    warnings.stream().map(HologramWarning::kind).toList(), "warnings with " + expected + " first");
            warnings.clear();
        }
    }

    /**
     * Acceptance: over fake probes, {@code auto} skips each provider that cannot run on this server: a missing or
     * disabled plugin, Spigot for FancyHolograms, Folia without {@code folia-supported: true}, and each provider's
     * version edges. Packets cannot run without an adapter, so {@code auto} moving on to a plugin warns high naming
     * the version.
     */
    @ParameterizedTest(name = "{0} {1} with {2} picks {3}")
    @CsvSource({
            "paper, 1.20.4, decent hd, DECENTHOLOGRAMS",
            "paper, 1.18, decent hd, DECENTHOLOGRAMS",
            "paper, 1.17.1, decent hd, HOLOGRAPHICDISPLAYS",
            "paper, 1.20.4, decent-disabled hd, HOLOGRAPHICDISPLAYS",
            "paper, 1.21.4, fancy hd, FANCYHOLOGRAMS",
            "paper, 1.21.3, fancy hd, NONE",
            "paper, 1.21.4, decent fancy hd, DECENTHOLOGRAMS",
            "spigot, 1.21.4, fancy, NONE",
            "spigot, 1.21.4, fancy decent, DECENTHOLOGRAMS",
            "paper, 1.20.4, hd, HOLOGRAPHICDISPLAYS",
            "paper, 1.20.5, hd, NONE",
            "spigot, 1.18.2, hd, HOLOGRAPHICDISPLAYS",
            "folia, 1.20.4, decent hd, NONE",
            "folia, 1.20.4, decent-folia hd, DECENTHOLOGRAMS",
            "folia, 1.20.4, hd-folia, NONE",
            "folia, 1.21.4, fancy decent, NONE",
            "folia, 1.21.4, fancy-folia decent, FANCYHOLOGRAMS",
            "paper, 1.21.4, none, NONE",
    })
    void autoSkipsEachProviderThatCannotRunOnThisServer(String platform, String version, String plugins,
                                                         HologramProviderChoice expected) {
        FakeHologramServer server = server(platform, version, plugins);
        Map<HologramProviderChoice, HologramProvider> providers = plugins(server);

        HologramProvider chosen = HologramProviders.choose(AUTO, providers, sink);

        if (expected == NONE) {
            assertSame(HologramProvider.NONE, chosen);
            assertEquals(1, warnings.size(), warnings.toString());
            assertEquals(HologramWarning.Kind.NO_PROVIDER, warnings.get(0).kind());
        } else {
            assertSame(providers.get(expected), chosen);
            assertEquals(1, warnings.size(), warnings.toString());
            assertEquals(HologramWarning.Kind.PACKETS_CANNOT_RUN, warnings.get(0).kind());
            assertEquals(Map.of(PACKETS, "there is no packet adapter for Minecraft " + version),
                    warnings.get(0).reasons());
        }
    }

    /**
     * Acceptance: a named provider that cannot run falls back to {@code auto} with one high warning naming it and the
     * reason. It is asked once.
     */
    @Test
    void aNamedProviderThatCannotRunFallsBackWithTheWarning() {
        FakeHologramServer server = FakeHologramServer.paper("1.20.4").with("HolographicDisplays", false);
        RecordingHologramProvider packets = new RecordingHologramProvider(CanRun.no("no adapter"));
        RecordingHologramProvider decent =
                new RecordingHologramProvider(DecentHologramsProvider.REQUIREMENT.check(server));
        RecordingHologramProvider holographic = new RecordingHologramProvider(HOLOGRAPHIC.check(server));

        HologramProvider chosen = HologramProviders.choose(DECENTHOLOGRAMS,
                Map.of(PACKETS, packets, DECENTHOLOGRAMS, decent, HOLOGRAPHICDISPLAYS, holographic), sink);

        assertSame(holographic, chosen);
        assertEquals(2, warnings.size(), warnings.toString());
        HologramWarning warning = warnings.get(0);
        assertEquals(HologramWarning.Kind.PROVIDER_CANNOT_RUN, warning.kind());
        assertEquals(Severity.HIGH, warning.severity());
        assertEquals(Optional.of(DECENTHOLOGRAMS), warning.provider());
        assertEquals(Map.of(DECENTHOLOGRAMS, "DecentHolograms is not installed"), warning.reasons());
        assertEquals(HologramWarning.Kind.PACKETS_CANNOT_RUN, warnings.get(1).kind());
        assertEquals(Map.of(PACKETS, "no adapter"), warnings.get(1).reasons());
        assertEquals(1, decent.canRunAsked());
        assertEquals(1, packets.canRunAsked());
    }

    /**
     * Acceptance: with no packet adapter matched for the server's version, on Spigot, Paper, and Folia alike, packets
     * cannot run, and {@code auto} moves on to the first plugin that can, with one high warning naming the version.
     * Named, packets gives the named provider warning with the same reason, and no second one.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 1.17.1, hd, HOLOGRAPHICDISPLAYS",
            "paper, 1.17.1, hd, HOLOGRAPHICDISPLAYS",
            "folia, 26.4, decent-folia, DECENTHOLOGRAMS",
            "paper, 26.4, decent-folia, DECENTHOLOGRAMS",
    })
    void withNoAdapterMatchedAutoMovesOnWithAHighWarningNamingTheVersion(String platform, String version,
                                                                         String plugins,
                                                                         HologramProviderChoice expected) {
        FakeHologramServer server = server(platform, version, plugins);
        Map<HologramProviderChoice, HologramProvider> providers = new EnumMap<>(plugins(server));
        providers.put(PACKETS, new PacketHologramProvider(PacketAdapters.select(server),
                new FakeServerAdapter(UUID.randomUUID())));

        HologramProvider chosen = HologramProviders.choose(AUTO, providers, sink);

        String reason = "there is no packet adapter for Minecraft " + version;
        assertEquals(CanRun.no(reason), providers.get(PACKETS).canRun());
        assertSame(providers.get(expected), chosen);
        assertEquals(1, warnings.size(), warnings.toString());
        HologramWarning warning = warnings.get(0);
        assertEquals(HologramWarning.Kind.PACKETS_CANNOT_RUN, warning.kind());
        assertEquals(Severity.HIGH, warning.severity());
        assertEquals(Optional.of(PACKETS), warning.provider());
        assertEquals(Map.of(PACKETS, reason), warning.reasons());

        warnings.clear();
        chosen = HologramProviders.choose(PACKETS, providers, sink);

        assertSame(providers.get(expected), chosen);
        assertEquals(List.of(HologramWarning.providerCannotRun(PACKETS, reason).toString()),
                warnings.stream().map(HologramWarning::toString).toList());
    }

    /**
     * A named provider that can run is picked, even when one earlier in {@code auto}'s order could run too.
     */
    @Test
    void aNamedProviderThatCanRunIsPicked() {
        RecordingHologramProvider packets = new RecordingHologramProvider();
        RecordingHologramProvider holographic = new RecordingHologramProvider();

        HologramProvider chosen = HologramProviders.choose(HOLOGRAPHICDISPLAYS,
                Map.of(PACKETS, packets, HOLOGRAPHICDISPLAYS, holographic), sink);

        assertSame(holographic, chosen);
        assertEquals(0, packets.canRunAsked());
        assertEquals(List.of(), warnings);
    }

    /**
     * No provider that can run means no holograms, with one high warning that carries each provider's reason in
     * {@code auto}'s order. Packets with no adapter for the version gives that reason, and no warning of its own.
     */
    @Test
    void noProviderThatCanRunMeansNoHologramsWithOneWarning() {
        FakeHologramServer server = FakeHologramServer.folia("1.20.4")
                .with("DecentHolograms", false)
                .withDisabled("HolographicDisplays");

        HologramProvider chosen = HologramProviders.choose(AUTO, plugins(server), sink);

        assertSame(HologramProvider.NONE, chosen);
        assertEquals(1, warnings.size(), warnings.toString());
        HologramWarning warning = warnings.get(0);
        assertEquals(HologramWarning.Kind.NO_PROVIDER, warning.kind());
        assertEquals(Severity.HIGH, warning.severity());
        Map<HologramProviderChoice, String> reasons = new LinkedHashMap<>();
        reasons.put(PACKETS, "there is no packet adapter for Minecraft 1.20.4");
        reasons.put(DECENTHOLOGRAMS,
                "DecentHolograms does not declare folia-supported: true, and this server is Folia");
        reasons.put(FANCYHOLOGRAMS, "FancyHolograms is not installed");
        reasons.put(HOLOGRAPHICDISPLAYS, "HolographicDisplays is not enabled");
        assertEquals(List.copyOf(reasons.entrySet()), List.copyOf(warning.reasons().entrySet()));
    }

    /**
     * Each version and platform rule gives its own reason.
     */
    @Test
    void eachRequirementNamesWhatTheServerLacks() {
        FakeHologramServer spigot = FakeHologramServer.spigot("1.21.4").with("FancyHolograms", false);
        FakeHologramServer old = FakeHologramServer.paper("1.21.3").with("FancyHolograms", false);
        FakeHologramServer late = FakeHologramServer.paper("1.20.5").with("HolographicDisplays", false);

        assertEquals(CanRun.no("FancyHolograms needs Paper, and this server is Spigot"), FANCY.check(spigot));
        assertEquals(CanRun.no("FancyHolograms needs Minecraft 1.21.4 or newer, and this server runs 1.21.3"),
                FANCY.check(old));
        assertEquals(CanRun.no("HolographicDisplays runs up to Minecraft 1.20.4, and this server runs 1.20.5"),
                HOLOGRAPHIC.check(late));
        assertEquals(CanRun.YES, FANCY.check(FakeHologramServer.paper("26.1").with("FancyHolograms", false)));
    }

    /**
     * A named provider and then every other failing gives both warnings, the second naming every reason.
     */
    @Test
    void aNamedProviderThatCannotRunWithNothingElseGivesBothWarnings() {
        HologramProvider chosen = HologramProviders.choose(FANCYHOLOGRAMS,
                plugins(FakeHologramServer.spigot("1.21.4").with("FancyHolograms", false)), sink);

        assertSame(HologramProvider.NONE, chosen);
        assertEquals(List.of(HologramWarning.Kind.PROVIDER_CANNOT_RUN, HologramWarning.Kind.NO_PROVIDER),
                warnings.stream().map(HologramWarning::kind).toList());
        assertEquals("FancyHolograms needs Paper, and this server is Spigot",
                warnings.get(1).reasons().get(FANCYHOLOGRAMS));
    }

    /**
     * Acceptance: {@code none} creates nothing, and asks no provider whether it can run.
     */
    @Test
    void noneCreatesNothing() {
        RecordingHologramProvider packets = new RecordingHologramProvider();
        RecordingHologramProvider decent = new RecordingHologramProvider();

        HologramProvider chosen = HologramProviders.choose(NONE, Map.of(PACKETS, packets, DECENTHOLOGRAMS, decent),
                sink);
        HologramService service = new HologramService(ShippedHologramConfig.load(), chosen, id -> Optional.empty());
        BoxEvent box = new BoxEvent(UUID.randomUUID(), "airdrops", UUID.randomUUID(), new BlockPosition(0, 64, 0),
                Optional.empty(), 10, OptionalInt.of(360));
        service.shown(box);
        service.ticked(box);
        service.ended(box);
        service.stop();

        assertSame(HologramProvider.NONE, chosen);
        assertEquals(List.of(), packets.calls());
        assertEquals(List.of(), decent.calls());
        assertEquals(0, packets.canRunAsked() + decent.canRunAsked());
        assertEquals(List.of(), warnings);
    }

    /**
     * A provider whose check throws, such as one missing a class of its plugin, cannot run, and the choice goes on.
     */
    @Test
    void aProviderWhoseCheckThrowsCannotRun() {
        HologramProvider broken = new ThrowingProvider();
        RecordingHologramProvider holographic = new RecordingHologramProvider();

        HologramProvider chosen = HologramProviders.choose(AUTO,
                Map.of(DECENTHOLOGRAMS, broken, HOLOGRAPHICDISPLAYS, holographic), sink);

        assertSame(holographic, chosen);
        warnings.clear();
        HologramProviders.choose(DECENTHOLOGRAMS, Map.of(DECENTHOLOGRAMS, broken), sink);
        assertTrue(warnings.get(0).reasons().get(DECENTHOLOGRAMS).contains("NoClassDefFoundError"),
                warnings.toString());
    }

    @Test
    void autoAndNoneAreNotProviders() {
        assertThrows(IllegalArgumentException.class, () -> HologramProviders.choose(AUTO,
                Map.of(NONE, new RecordingHologramProvider()), sink));
    }

    @Test
    void versionsReadAndOrderAsReleases() {
        assertEquals(new MinecraftVersion(1, 20, 4), MinecraftVersion.parse("1.20.4-R0.1-SNAPSHOT"));
        assertEquals(new MinecraftVersion(1, 21, 0), MinecraftVersion.parse("1.21"));
        assertEquals(new MinecraftVersion(26, 1, 0), MinecraftVersion.parse("26.1-R0.1-SNAPSHOT"));
        assertTrue(MinecraftVersion.parse("1.20.10").isAbove(MinecraftVersion.parse("1.20.4")));
        assertTrue(MinecraftVersion.parse("1.21.11").isBelow(MinecraftVersion.parse("26.1")));
        assertEquals("1.21", MinecraftVersion.parse("1.21.0").toString());
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("1.x.2"));
    }

    /**
     * From 26.1 Paper and Folia give their own build as the Bukkit version: the {@code version} of the API jar's
     * {@code apiVersioning.json}, which {@code Versioning.getBukkitVersion} reads. These are the values of Paper
     * 26.1.2 build 74, Paper 26.2 build 129, Folia 26.1.2 build 8, and Folia 26.2 build 7.
     */
    @Test
    void paperAndFoliaBukkitVersionsFrom26_1ReadAsTheirRelease() {
        assertEquals(new MinecraftVersion(26, 1, 2), MinecraftVersion.parse("26.1.2.build.74-stable"));
        assertEquals(new MinecraftVersion(26, 2, 0), MinecraftVersion.parse("26.2.build.129-stable"));
        assertEquals(new MinecraftVersion(26, 1, 2), MinecraftVersion.parse("26.1.2.build.8-stable"));
        assertEquals(new MinecraftVersion(26, 2, 0), MinecraftVersion.parse("26.2.build.7-beta"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("26.2.1.1"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("build.7-beta"));
    }

    /**
     * Each plugin word installs a plugin: {@code decent}, {@code fancy}, {@code hd}; {@code -folia} declares Folia
     * support; {@code -disabled} installs it disabled.
     */
    private static FakeHologramServer server(String platform, String version, String plugins) {
        FakeHologramServer server = switch (platform) {
            case "paper" -> FakeHologramServer.paper(version);
            case "spigot" -> FakeHologramServer.spigot(version);
            default -> FakeHologramServer.folia(version);
        };
        for (String word : plugins.split(" ")) {
            String[] parts = word.split("-");
            String name = switch (parts[0]) {
                case "decent" -> "DecentHolograms";
                case "fancy" -> "FancyHolograms";
                case "hd" -> "HolographicDisplays";
                default -> null;
            };
            if (name == null) {
                continue;
            }
            if (parts.length > 1 && parts[1].equals("disabled")) {
                server.withDisabled(name);
            } else {
                server.with(name, parts.length > 1 && parts[1].equals("folia"));
            }
        }
        return server;
    }

    /**
     * The three plugin providers over this server, and the packet provider with no adapter for its version.
     */
    private static Map<HologramProviderChoice, HologramProvider> plugins(HologramServer server) {
        return Map.of(
                PACKETS, new PacketHologramProvider(
                        PacketAdapterMatch.none("there is no packet adapter for Minecraft " + server.minecraftVersion()),
                        new FakeServerAdapter(UUID.randomUUID())),
                DECENTHOLOGRAMS, FakeDecentHolograms.provider(server, new FakeDecentHolograms()),
                FANCYHOLOGRAMS, RecordingFancyHolograms.provider(server),
                HOLOGRAPHICDISPLAYS, FakeHolographicDisplays.provider(server, new FakeHolographicDisplays()));
    }

    private static final class ThrowingProvider implements HologramProvider {
        @Override
        public CanRun canRun() {
            throw new NoClassDefFoundError("eu/decentsoftware/holograms/api/DHAPI");
        }

        @Override
        public void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
            throw new AssertionError("never chosen");
        }

        @Override
        public void update(UUID hologramId, List<String> lines) {
            throw new AssertionError("never chosen");
        }

        @Override
        public void remove(UUID hologramId) {
            throw new AssertionError("never chosen");
        }

        @Override
        public void removeAll() {
            throw new AssertionError("never chosen");
        }
    }
}
