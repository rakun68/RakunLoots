package me.rakkoon69.rakunloots.lootbox;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.visuals.BoxVisualsService;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Call;
import me.rakkoon69.rakunloots.visuals.FakeGlowLibrary.Op;
import me.rakkoon69.rakunloots.visuals.FakeLaserLibrary;
import me.rakkoon69.rakunloots.visuals.FakeLaserLibrary.Start;
import me.rakkoon69.rakunloots.visuals.GlowColor;
import me.rakkoon69.rakunloots.visuals.LaserType;
import me.rakkoon69.rakunloots.visuals.VisualsWarning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.flatChunk;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.position;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.shippedConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The box visuals service as the lifecycle's observer, over the shipped config: the lifecycle's own summon, chunk
 * unload and load, and loot end reach the fake glow and laser libraries.
 */
class LootBoxVisualsTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ChunkPosition CHUNK = new ChunkPosition(0, 0);

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;
    private final FakeGlowLibrary glow = new FakeGlowLibrary();
    private final FakeLaserLibrary laser = new FakeLaserLibrary();
    private final List<VisualsWarning> warnings = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        fixture.adapter.setPlayers(List.of(new PlayerPosition(OWNER, 2, 64, 3)));
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    /**
     * Acceptance, through the lifecycle: a summoned airdrop glows gold and shows a guardian laser; its chunk's unload
     * stops both and its load starts them again; a break stops both; and neither the lifecycle nor the service warns.
     */
    @Test
    void aSummonedBoxsGlowAndLaserFollowTheLifecycle() {
        ConfigSnapshot config = shippedConfig();
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 91);
        lifecycle.addObserver(new BoxVisualsService(config, fixture.adapter, () -> glow, () -> laser,
                w -> warnings.add((VisualsWarning) w)));
        lifecycle.start().join();

        LootBox box = lifecycle.summon("airdrops", airSpot(2, 3), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();

        Call shown = new Call(Op.SHOW, WORLD, box.position(), OWNER, GlowColor.GOLD);
        Start beam = new Start(WORLD, box.position(), LaserType.GUARDIAN, 50, 0);
        assertEquals(List.of(shown), glow.calls());
        assertEquals(List.of(beam), laser.starts());

        glow.clear();
        laser.clear();
        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        lifecycle.chunkUnloaded(WORLD, CHUNK);
        assertEquals(List.of(new Call(Op.HIDE, WORLD, box.position(), OWNER, null)), glow.calls());
        assertEquals(List.of(beam), laser.stops());

        glow.clear();
        laser.clear();
        fixture.adapter.putChunk(flatChunk(0, 0), true, true);
        lifecycle.chunkLoaded(WORLD, CHUNK);
        assertEquals(List.of(shown), glow.calls());
        assertEquals(List.of(beam), laser.starts());

        glow.clear();
        laser.clear();
        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.BREAK);
        assertEquals(List.of(new Call(Op.HIDE, WORLD, box.position(), OWNER, null)), glow.calls());
        assertEquals(List.of(beam), laser.stops());

        assertEquals(List.of(), fixture.warnings.lifecycleKinds(), "an observer failure would warn");
        assertEquals(List.of(), warnings);
        assertEquals(position(airSpot(2, 3)), box.position());
    }

    /**
     * Acceptance: a failing library leaves the box running through the lifecycle, so the box still opens and ends.
     */
    @Test
    void aFailingGlowAndLaserLeaveTheLifecycleBoxRunningWithOneLowWarningEach() {
        glow.throwOnCalls(new IllegalStateException("no packets"));
        laser.failStarts(new ReflectiveOperationException("no beam"));
        ConfigSnapshot config = shippedConfig();
        LootBoxLifecycle lifecycle = fixture.lifecycle(config, store, 91);
        lifecycle.addObserver(new BoxVisualsService(config, fixture.adapter, () -> glow, () -> laser,
                w -> warnings.add((VisualsWarning) w)));
        lifecycle.start().join();

        LootBox first = lifecycle.summon("airdrops", airSpot(2, 3), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();
        LootBox second = lifecycle.summon("airdrops", airSpot(4, 3), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();

        assertEquals(List.of(VisualsWarning.Kind.GLOW_FAILED, VisualsWarning.Kind.LASER_FAILED),
                warnings.stream().map(VisualsWarning::kind).toList());
        assertEquals(List.of(), fixture.warnings.lifecycleKinds());
        lifecycle.interact(OWNER, WORLD, second.position(), false, Interaction.BREAK);
        lifecycle.interact(OWNER, WORLD, first.position(), false, Interaction.BREAK);
        assertEquals(List.of(), fixture.warnings.lifecycleKinds());
    }
}
