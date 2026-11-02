package me.rakkoon69.rakunloots.hologram.packet.v1_20_R4;

import me.rakkoon69.rakunloots.hologram.FakeHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.config.HologramProviderChoice;
import me.rakkoon69.rakunloots.hologram.HologramProviders;
import me.rakkoon69.rakunloots.hologram.HologramWarning;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapterMatch;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapters;
import me.rakkoon69.rakunloots.hologram.packet.PacketHologramProvider;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The shipped revision table and the core's selection, loading this adapter by class name next to the server code it
 * runs on, over fake server probes.
 */
class SelectionTest {

    /**
     * Bootstraps the registries, as a server has by the time a plugin enables: from 1.19 the adapter's data
     * serializers need them when it loads.
     */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Acceptance: selection picks {@code v1_20_R4} for 1.20.5 and 1.20.6 on Spigot, Paper, and Folia.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 1.20.5",
            "paper, 1.20.5",
            "folia, 1.20.5",
            "spigot, 1.20.6",
            "paper, 1.20.6",
            "folia, 1.20.6",
            "spigot, 1.20.6-R0.1-SNAPSHOT",
    })
    void selectionPicksThisRevision(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(Optional.empty(), match.refusal());
        assertInstanceOf(NmsPacketAdapter.class, match.adapter().orElseThrow());
    }

    /**
     * The versions on either side of 1.20.5 and 1.20.6 are not this revision's: 1.20.4 is {@code v1_20_R3}'s and 1.21
     * the next revision's, whose classes are not on this test's class path, so neither yields an adapter here.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 1.20.4",
            "paper, 1.20.4",
            "spigot, 1.21",
            "paper, 1.21",
    })
    void selectionDoesNotPickThisRevisionForTheVersionsAround1_20_5(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(Optional.empty(), match.adapter());
    }

    /**
     * Acceptance: selection picks nothing for a version no adapter covers, and says so naming it.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 1.17.1",
            "paper, 1.17.1",
    })
    void selectionPicksNothingForAVersionWithNoAdapter(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(PacketAdapterMatch.none("there is no packet adapter for Minecraft " + version), match);
    }

    /**
     * {@code auto} picks packets on 1.20.6, ahead of an installed hologram plugin that could run, with no
     * warning.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"spigot", "paper", "folia"})
    void autoPicksPacketsOn1_20_6(String platform) {
        PacketHologramProvider packets = new PacketHologramProvider(
                PacketAdapters.select(server(platform, "1.20.6")), new FakeServerAdapter(UUID.randomUUID()));
        RecordingHologramProvider decent = new RecordingHologramProvider();
        List<HologramWarning> warnings = new ArrayList<>();

        HologramProvider chosen = HologramProviders.choose(HologramProviderChoice.AUTO,
                Map.of(HologramProviderChoice.PACKETS, packets, HologramProviderChoice.DECENTHOLOGRAMS, decent),
                warning -> warnings.add((HologramWarning) warning));

        assertSame(packets, chosen);
        assertEquals(CanRun.YES, packets.canRun());
        assertEquals(0, decent.canRunAsked());
        assertEquals(List.of(), warnings);
    }

    private static FakeHologramServer server(String platform, String version) {
        return switch (platform) {
            case "paper" -> FakeHologramServer.paper(version);
            case "folia" -> FakeHologramServer.folia(version);
            default -> FakeHologramServer.spigot(version);
        };
    }
}
