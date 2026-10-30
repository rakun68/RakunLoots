package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider.Call;
import me.rakkoon69.rakunloots.hologram.RecordingHologramProvider.Recorded;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.flatChunk;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hologram service as the lifecycle's observer, over the shipped config: the lifecycle's own summon, tick, chunk
 * unload and load, and loot end reach the provider.
 */
class LootBoxHologramTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ChunkPosition CHUNK = new ChunkPosition(0, 0);

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;
    private final RecordingHologramProvider provider = new RecordingHologramProvider();

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        fixture.adapter.addOnlinePlayer(OWNER);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance, through the lifecycle: a summoned airdrop gets a hologram 2 blocks above its block with every
     * placeholder filled; each second updates it; its chunk's unload removes it and its load draws it again; a break
     * removes it; and stop removes all.
     */
    @Test
    void aSummonedBoxsHologramFollowsTheLifecycle() {
        ConfigSnapshot config = shippedConfig();
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 91);
        HologramService holograms = new HologramService(config, provider,
                id -> id.equals(OWNER) ? Optional.of("Rakkoon") : Optional.empty());
        lifecycle.addObserver(holograms);
        lifecycle.start().join();

        LootBox box = lifecycle.summon("airdrops", airSpot(2, 3), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();

        assertEquals(List.of(new Recorded(Call.CREATE, box.boxId(), new HologramPosition(WORLD, 2.5, 66.0, 3.5),
                List.of("§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>", "§7Owner: §eRakkoon", "§7Lock: §c0:10",
                        "§7Leaves in: §e6:00"), 48)), provider.calls());
        assertEquals(List.of(), fixture.warnings.lifecycleKinds(), "an observer failure would warn");

        provider.clear();
        fixture.adapter.globalScheduler().stepTicks(LootBoxLifecycle.TICK_PERIOD_TICKS);
        assertEquals(List.of(new Recorded(Call.UPDATE, box.boxId(), null,
                List.of("§f§l<< §r§r§x§f§a§d§7§7§8Air Supply §f§l>>", "§7Owner: §eRakkoon", "§7Lock: §c0:09",
                        "§7Leaves in: §e5:59"), null)), provider.calls());

        provider.clear();
        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        lifecycle.chunkUnloaded(WORLD, CHUNK);
        assertEquals(List.of(Call.REMOVE), provider.calls().stream().map(Recorded::call).toList());

        fixture.adapter.putChunk(flatChunk(0, 0), true, true);
        lifecycle.chunkLoaded(WORLD, CHUNK);
        assertEquals(List.of(Call.REMOVE, Call.CREATE), provider.calls().stream().map(Recorded::call).toList());

        provider.clear();
        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.BREAK);
        assertEquals(List.of(new Recorded(Call.REMOVE, box.boxId(), null, null, null)), provider.calls());
        assertTrue(provider.drawn().isEmpty());

        lifecycle.summon("airdrops", airSpot(4, 3), Optional.empty(), SummonKind.UNKNOWN).join();
        provider.clear();
        lifecycle.stop();
        holograms.stop();
        assertEquals(List.of(Call.REMOVE_ALL), provider.calls().stream().map(Recorded::call).toList());
        assertTrue(provider.drawn().isEmpty());
    }
}
