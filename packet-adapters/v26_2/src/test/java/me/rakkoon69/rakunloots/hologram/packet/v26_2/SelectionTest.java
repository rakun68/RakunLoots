package me.rakkoon69.rakunloots.hologram.packet.v26_2;

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
     * Acceptance: selection picks {@code v26_2} for 26.2 and 26.3 on Spigot, Paper, and Folia, each running the same
     * class.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 26.2",
            "paper, 26.2",
            "folia, 26.2",
            "spigot, 26.3",
            "paper, 26.3",
            "folia, 26.3",
            "spigot, 26.2-R0.1-SNAPSHOT",
            "paper, 26.2-R0.1-SNAPSHOT",
            "folia, 26.2-R0.1-SNAPSHOT",
            "spigot, 26.3-R0.1-SNAPSHOT",
            "paper, 26.3-R0.1-SNAPSHOT",
            "folia, 26.3-R0.1-SNAPSHOT",
    })
    void selectionPicksThisRevision(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(Optional.empty(), match.refusal());
        assertInstanceOf(NmsPacketAdapter.class, match.adapter().orElseThrow());
    }

    /**
     * From 26.1 Paper and Folia give their build as the Bukkit version, which selection reads as its release. These
     * are the Bukkit versions of the builds this adapter was checked on.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "paper, 26.2.build.129-stable",
            "folia, 26.2.build.7-beta",
    })
    void selectionPicksThisRevisionFromPaperAndFoliasOwnBukkitVersion(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(Optional.empty(), match.refusal());
        assertInstanceOf(NmsPacketAdapter.class, match.adapter().orElseThrow());
    }

    /**
     * 26.1.2 is {@code v26_1}'s, whose classes are not on this test's class path, so it yields no adapter here.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 26.1.2",
            "paper, 26.1.2",
            "folia, 26.1.2",
    })
    void selectionDoesNotPickThisRevisionForTheVersionsAround26_2(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(Optional.empty(), match.adapter());
    }

    /**
     * Acceptance: selection picks nothing for a version no adapter covers, and says so naming it: 1.17.1, and 26.4,
     * past the last release an adapter covers.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "spigot, 1.17.1",
            "paper, 1.17.1",
            "spigot, 26.4",
            "paper, 26.4",
            "folia, 26.4",
    })
    void selectionPicksNothingForAVersionWithNoAdapter(String platform, String version) {
        PacketAdapterMatch match = PacketAdapters.select(server(platform, version));

        assertEquals(PacketAdapterMatch.none("there is no packet adapter for Minecraft " + version), match);
    }

    /**
     * {@code auto} picks packets on 26.2, ahead of an installed hologram plugin that could run, with no
     * warning.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"spigot", "paper", "folia"})
    void autoPicksPacketsOn26_2(String platform) {
        PacketHologramProvider packets = new PacketHologramProvider(
                PacketAdapters.select(server(platform, "26.2")), new FakeServerAdapter(UUID.randomUUID()));
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
