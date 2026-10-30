package me.rakkoon69.rakunloots.hologram.packet.v1_19_R1;

import me.rakkoon69.rakunloots.hologram.FakeHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.hologram.HologramProviderChoice;
import me.rakkoon69.rakunloots.hologram.HologramProviders;
import me.rakkoon69.rakunloots.hologram.HologramWarning;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapterMatch;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapters;
import me.rakkoon69.rakunloots.hologram.packet.PacketHologramProvider;
import me.rakkoon69.rakunloots.summon.FakeServerAdapter;
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
     * Acceptance: selection picks {@code v1_19_R1} for 1.19 to 1.19.2 on Spigot and Paper.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 1.19",
            "paper, 1.19",
            "spigot, 1.19.1",
            "paper, 1.19.1",
            "spigot, 1.19.2",
            "paper, 1.19.2",
            "spigot, 1.19.2-R0.1-SNAPSHOT",
    })
    void selectionPicksThisRevision(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(Optional.empty(), match.refusal());
        assertInstanceOf(NmsPacketAdapter.class, match.adapter().orElseThrow());
    }

    /**
     * The versions on either side of 1.19 to 1.19.2 are not this revision's: 1.18.2 is {@code v1_18_R2}'s and 1.19.3
     * {@code v1_19_R2}'s, whose classes are not on this test's class path, so neither yields an adapter here.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 1.18.2",
            "paper, 1.18.2",
            "spigot, 1.19.3",
            "paper, 1.19.3",
    })
    void selectionDoesNotPickThisRevisionForTheVersionsAround1_19_To1_19_2(String platform, String version) {
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
     * {@code auto} picks packets on 1.19.2, ahead of an installed hologram plugin that could run, with no
     * warning.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"spigot", "paper"})
    void autoPicksPacketsOn1_19_2(String platform) {
        PacketHologramProvider packets = new PacketHologramProvider(
                PacketAdapters.select(server(platform, "1.19.2")), new FakeServerAdapter(UUID.randomUUID()));
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
        return platform.equals("paper") ? FakeHologramServer.paper(version) : FakeHologramServer.spigot(version);
    }
}
