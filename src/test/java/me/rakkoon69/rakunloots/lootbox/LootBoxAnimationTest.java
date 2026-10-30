package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.WarningKinds;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.rakkoon69.rakunloots.animation.AnimationRegistry;
import me.rakkoon69.rakunloots.animation.AnimationService;
import me.rakkoon69.rakunloots.animation.AnimationType;
import me.rakkoon69.rakunloots.animation.AnimationWarning;
import me.rakkoon69.rakunloots.animation.ParticleSink;
import me.rakkoon69.rakunloots.animation.RecordingParticleSink;
import me.rakkoon69.rakunloots.animation.RunningAnimation;
import me.rakkoon69.rakunloots.config.AnimationMoment;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import me.rakkoon69.rakunloots.server.ParticleSpec;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.SummonKind;
import me.rakkoon69.rakunloots.summon.MissReason;
import me.rakkoon69.rakunloots.summon.OccupiedCheck;
import me.rakkoon69.rakunloots.summon.Spot;
import me.rakkoon69.rakunloots.summon.SpotFinder;
import me.rakkoon69.rakunloots.summon.SummonResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.WORLD;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.airSpot;
import static me.rakkoon69.rakunloots.lootbox.LifecycleFixture.flatChunk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animation service as the lifecycle's observer, over the shipped {@code animations.yml} and
 * {@code loots/airdrops.yml}, with the lifecycle and the summon service driven in memory and a recording particle
 * sink. Each test checks what a player would see: which particles are drawn, where, and when.
 *
 * <p>The shipped airdrop's animations tell apart by colour: summon is red {@code 210:60:60}, alive is blue
 * {@code 110:110:210}, unlocked is green {@code 110:210:110}, and open and remove are both white
 * {@code 255:255:255}. Tests that must tell open from remove use loots of their own with their own animations.
 */
class LootBoxAnimationTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final String RED = "210:60:60";
    private static final String BLUE = "110:110:210";
    private static final String GREEN = "110:210:110";
    private static final String OPEN_MARK = "10:20:30";
    private static final String REMOVE_MARK = "40:50:60";
    private static final String LAYER_MARK = "70:80:90";
    private static final long SECOND = LootBoxLifecycle.TICK_PERIOD_TICKS;
    private static final int DELAY = 60;
    private static final ChunkPosition CHUNK = new ChunkPosition(0, 0);

    @TempDir
    Path folder;

    private LifecycleFixture fixture;
    private LootBoxStore store;
    private LootBoxLifecycle lifecycle;
    private SummonService summons;
    private final Finder finder = new Finder();
    private final RecordingParticleSink sink = new RecordingParticleSink();
    private final List<AnimationWarning> animationWarnings = new CopyOnWriteArrayList<>();
    private AnimationService animations;

    @BeforeEach
    void setUp() throws Exception {
        StreamableServerMock.mock();
        fixture = new LifecycleFixture(folder);
        store = fixture.openStore();
        fixture.adapter.addOnlinePlayer(OWNER);
        fixture.adapter.addOnlinePlayer(OTHER);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        MockBukkit.unmock();
    }

    private void start(ConfigSnapshot config) {
        start(config, AnimationRegistry.create(), sink);
    }

    private void start(ConfigSnapshot config, AnimationRegistry registry, ParticleSink particles) {
        lifecycle = new LootBoxLifecycle(fixture.adapter, config, ItemSourceRegistry.create(), store, new Random(1),
                fixture.warnings);
        summons = new SummonService(fixture.adapter, config, lifecycle, finder, SummonGiveBack.NONE, fixture.warnings);
        animations = new AnimationService(config, registry, particles, fixture.adapter.global(),
                w -> animationWarnings.add((AnimationWarning) w));
        lifecycle.addObserver(animations);
        lifecycle.start().join();
    }

    private void step(long ticks) {
        fixture.adapter.globalScheduler().stepTicks(ticks);
    }

    private LootBox summonBox(String lootId, int x, int z) {
        return lifecycle.summon(lootId, airSpot(x, z), Optional.of(OWNER), SummonKind.UNKNOWN).join().box().orElseThrow();
    }

    /**
     * Loads the shipped files, with extra animation entries and extra loots.
     */
    private static ConfigSnapshot config(String extraAnimations, Map<String, String> extraLoots) {
        ConfigSources.Builder sources = ConfigSources.builder()
                .globals(LifecycleFixture.resource("/config.yml"))
                .worlds(LifecycleFixture.resource("/worlds.yml"))
                .blocks(LifecycleFixture.resource("/blocks.yml"))
                .language("en", LifecycleFixture.resource("/languages/en.yml"))
                .animations(LifecycleFixture.resource("/animations.yml") + "\n" + extraAnimations)
                .loot("airdrops", LifecycleFixture.resource("/loots/airdrops.yml"));
        extraLoots.forEach(sources::loot);
        return ConfigLoader.load(sources.build(), LifecycleFixture.SERVER_LIKE, WarningKinds.all());
    }

    /**
     * An animation entry drawing {@code loops} spheres of one dust colour, 1 block wide.
     */
    private static String mark(String id, String colour, int loops) {
        return id + ":\n  type: SPHERE\n  variables:\n    delay: 0\n    interval: 10\n    loops: " + loops
                + "\n    radius: 1\n    count: 2\n    particles:\n      outer_line:\n        type: REDSTONE\n"
                + "        count: 1\n        size: 1.0\n        color: '" + colour + "'\n";
    }

    private static String marks() {
        return mark("OPEN_MARK", OPEN_MARK, 1) + mark("REMOVE_MARK", REMOVE_MARK, 1)
                + mark("LAYER_MARK", LAYER_MARK, 1);
    }

    /**
     * A loot with no lock that ends after {@code life} seconds, with the given animations block.
     */
    private static String loot(String openMode, int lock, int life, String animationsBlock) {
        return "language: en\nopen_mode: " + openMode + "\nlock: " + lock + "\nlife: " + life + "\nblock: barrel\n"
                + "roll:\n  entries:\n    one:\n      item:\n        id: diamond\n" + animationsBlock;
    }

    private static String markedLoot(String openMode) {
        return loot(openMode, 0, 600, "animations:\n  open:\n    - OPEN_MARK\n  remove:\n    - REMOVE_MARK\n");
    }

    /**
     * Acceptance, summon: the summon animation plays at the pending spot during the delay, and stops when the box is
     * placed. Wrong version: one that never stops it keeps drawing red after tick 60.
     */
    @Test
    void theSummonAnimationPlaysAtThePendingSpotDuringTheDelayAndStopsWhenTheBoxIsPlaced() {
        start(config("", Map.of()));
        finder.spots.add(airSpot(3, 4));

        CompletableFuture<SummonReport> report = summons.adminSummon("airdrops", SummonTarget.world(WORLD),
                Optional.empty());

        assertEquals(List.of(), sink.colours(), "nothing is drawn before the first tick");
        step(DELAY - 1);
        assertEquals(List.of(RED, RED, RED, RED, RED, RED), sink.colours(), "ticks 1, 11, 21, 31, 41, and 51");
        for (RecordingParticleSink.Frame frame : sink.frames()) {
            assertEquals(new BlockPosition(3, 64, 4), frame.owner());
            assertEquals(WORLD, frame.worldKey());
            assertEquals(0.8, frame.farthestFrom(3.5, 64.5, 4.5), 1e-9, "the red sphere's radius around the block");
            assertEquals(160, frame.draws().size(), "8 rings of 20 points");
            assertEquals(new ParticleSpec("REDSTONE", 1, Optional.of(new ParticleSpec.Dust(210, 60, 60, 1.0f))),
                    frame.draws().get(0).spec());
        }

        step(1);
        assertTrue(report.join().box().isPresent(), "the box is placed at tick 60");
        sink.clear();
        step(DELAY * 2);
        assertEquals(0, sink.count(RED), "the summon animation stopped when the block was written");
        assertTrue(sink.count(BLUE) > 0, "the box's own alive animation took over");
    }

    /**
     * Acceptance, summon: an abandoned summon stops its summon animation. Wrong version: one that leaves it running
     * keeps drawing red after the abandon.
     */
    @Test
    void anAbandonedSummonStopsItsSummonAnimation() {
        start(config("", Map.of()));
        finder.spots.add(airSpot(3, 4));
        CompletableFuture<SummonReport> report = summons.adminSummon("airdrops", SummonTarget.world(WORLD),
                Optional.empty());
        step(25);
        assertEquals(List.of(RED, RED, RED), sink.colours(), "ticks 1, 11, and 21");

        summons.chunkUnloaded(WORLD, CHUNK);
        assertEquals(Optional.of(AbandonReason.CHUNK_UNLOADED), report.join().abandoned());
        step(DELAY * 3);

        assertEquals(List.of(RED, RED, RED), sink.colours(), "nothing more, and no box was placed");
        assertEquals(List.of(), animationWarnings);
    }

    /**
     * Acceptance, alive: the alive animation runs while the box is shown, again each second once it finished, stops
     * when the box goes out of view, and starts again when it is shown again. Wrong version: one that ignores out of
     * view keeps drawing while the chunk is unloaded.
     */
    @Test
    void theAliveAnimationRunsWhileTheBoxIsShown() {
        start(config("", Map.of()));
        LootBox box = summonBox("airdrops", 3, 4);

        step(3 * SECOND);
        assertEquals(List.of(BLUE, BLUE, BLUE), sink.colours(), "ticks 1, 21, and 41: a burst each second");
        for (RecordingParticleSink.Frame frame : sink.frames()) {
            assertEquals(box.position(), frame.owner());
            assertEquals(1.8, frame.farthestFrom(3.5, 64.5, 4.5), 1e-9);
        }

        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        lifecycle.chunkUnloaded(WORLD, CHUNK);
        step(3 * SECOND);
        assertEquals(3, sink.frames().size(), "nothing while the box is out of view");

        fixture.adapter.putChunk(flatChunk(0, 0), true, true);
        lifecycle.chunkLoaded(WORLD, CHUNK);
        step(2);
        assertEquals(4, sink.frames().size(), "shown again, so it draws again");
        step(SECOND);
        assertEquals(5, sink.frames().size(), "and each second after");
        assertEquals(List.of(), animationWarnings);
    }

    /**
     * Acceptance, unlocked: the unlocked animation fires once, on the tick the lock reaches 0, and not for a box that
     * never had a lock. Wrong version: one that fires on every tick with no lock left draws green each second.
     */
    @Test
    void theUnlockedAnimationFiresOnceWhenTheLockReachesZero() {
        start(config(marks(), Map.of("unlocked", loot("drop", 0, 600, "animations:\n  unlocked:\n    - OPEN_MARK\n"))));
        summonBox("airdrops", 3, 4);
        summonBox("unlocked", 5, 4);
        sink.clear();

        step(10 * SECOND - 1);
        assertEquals(0, sink.count(GREEN), "the lock still runs on its last second");
        step(1);
        assertEquals(0, sink.count(GREEN), "the lock reached 0 on this tick; the frame is drawn on the next");
        step(1);
        assertEquals(1, sink.count(GREEN));
        assertEquals(new BlockPosition(3, 64, 4), sink.frames().stream()
                .filter(frame -> frame.colour().equals(GREEN)).findFirst().orElseThrow().owner());

        step(20 * SECOND);
        assertEquals(1, sink.count(GREEN), "once");
        assertEquals(0, sink.count(OPEN_MARK), "a box that never had a lock never unlocks");
    }

    /**
     * Acceptance, open: a drop open click plays the open animation before the box ends, and an inventory open plays
     * it once the server confirms the open. The box ends with its own remove animation.
     */
    @Test
    void theOpenAnimationPlaysWhenAPlayerOpensTheBox() {
        start(config(marks(), Map.of("dropping", markedLoot("drop"), "inventory", markedLoot("inventory"))));
        LootBox drop = summonBox("dropping", 3, 4);
        LootBox inventory = summonBox("inventory", 5, 4);
        sink.clear();

        lifecycle.interact(OTHER, WORLD, drop.position(), false, Interaction.CLICK);
        step(2);
        assertEquals(List.of(OPEN_MARK, REMOVE_MARK), sink.colours(), "open before the end, then the end");
        assertEquals(drop.position(), sink.frames().get(0).owner());

        sink.clear();
        lifecycle.interact(OTHER, WORLD, inventory.position(), false, Interaction.CLICK);
        step(2);
        assertEquals(List.of(OPEN_MARK), sink.colours(), "an inventory open does not end the box");
        assertEquals(inventory.position(), sink.frames().get(0).owner());
    }

    /**
     * Acceptance, remove: every loot end plays the remove animation once, at the box's block: a timeout, a break, a
     * drop open click, and an admin removal. A remove animation of several loops goes on after the box is gone.
     */
    @Test
    void theRemoveAnimationPlaysOnEachLootEnd() {
        start(config(marks(), Map.of("short", loot("drop", 0, 3, "animations:\n  remove:\n    - REMOVE_MARK\n"),
                "markedDrop", markedLoot("drop"))));

        LootBox timeout = summonBox("short", 3, 4);
        sink.clear();
        step(3 * SECOND + 2);
        assertTrue(lifecycle.boxAt(WORLD, timeout.position()).isEmpty(), "the box timed out");
        assertEquals(List.of(REMOVE_MARK), sink.colours(), "timeout");
        assertEquals(timeout.position(), sink.frames().get(0).owner());

        LootBox broken = summonBox("markedDrop", 5, 4);
        sink.clear();
        lifecycle.interact(OWNER, WORLD, broken.position(), false, Interaction.BREAK);
        step(2);
        assertEquals(List.of(REMOVE_MARK), sink.colours(), "break");

        LootBox opened = summonBox("markedDrop", 7, 4);
        sink.clear();
        lifecycle.interact(OTHER, WORLD, opened.position(), false, Interaction.CLICK);
        step(2);
        assertEquals(1, sink.count(REMOVE_MARK), "drop open");

        summonBox("markedDrop", 9, 4);
        sink.clear();
        lifecycle.remove(box -> true);
        step(2);
        assertEquals(List.of(REMOVE_MARK), sink.colours(), "admin removal");

        LootBox airdrop = summonBox("airdrops", 11, 4);
        step(2);
        sink.clear();
        lifecycle.interact(OWNER, WORLD, airdrop.position(), false, Interaction.BREAK);
        step(6 * 10);
        assertTrue(lifecycle.boxAt(WORLD, airdrop.position()).isEmpty());
        assertEquals(6, sink.count("255:255:255"), "the shipped white sphere draws its 6 loops after the box is gone");
    }

    /**
     * Acceptance: a missing id warns high and plays nothing, at load and at the moment it would play, once per
     * reload; an unknown type warns high and plays nothing; variables the type cannot use warn high and play
     * nothing. A loot's other animations still play.
     */
    @Test
    void aMissingIdAnUnknownTypeAndBadVariablesWarnHighAndPlayNothing() {
        String bad = "SPIRAL:\n  type: SPIRAL\n  variables: {}\n"
                + "BAD_RADIUS:\n  type: SPHERE\n  variables:\n    radius: wide\n    particles:\n      outer_line:\n"
                + "        type: REDSTONE\n";
        ConfigSnapshot config = config(marks() + bad, Map.of("typo", loot("drop", 0, 600,
                "animations:\n  open:\n    - NO_SUCH_ANIMATION\n    - SPIRAL\n    - BAD_RADIUS\n    - OPEN_MARK\n")));
        List<ConfigWarning> loadWarnings = config.warnings().stream()
                .filter(warning -> warning.kind() == ConfigWarning.Kind.UNKNOWN_ANIMATION).toList();
        assertEquals(1, loadWarnings.size(), "at load: " + config.warnings());
        assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, loadWarnings.get(0).severity());
        assertEquals(Optional.of("typo"), loadWarnings.get(0).lootId());
        assertEquals(Optional.of("NO_SUCH_ANIMATION"), loadWarnings.get(0).writtenId());
        assertEquals(Optional.of("animations.open"), loadWarnings.get(0).key());

        start(config);
        assertEquals(List.of("UNKNOWN_TYPE SPIRAL SPIRAL"), animationWarnings.stream()
                .map(AnimationWarning::toString).toList(), "an unknown type warns when the service takes the config");
        LootBox box = summonBox("typo", 3, 4);
        for (int open = 0; open < 3; open++) {
            lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);
        }
        step(2);

        assertEquals(List.of(OPEN_MARK), sink.colours().stream().distinct().toList(),
                "the animations that can play still play, and nothing else is drawn");
        assertEquals(List.of(
                "UNKNOWN_TYPE SPIRAL SPIRAL",
                "UNKNOWN_ANIMATION typo OPEN NO_SUCH_ANIMATION",
                "UNUSABLE_VARIABLES BAD_RADIUS radius must be a number above 0, not wide"),
                animationWarnings.stream().map(AnimationWarning::toString).toList(),
                "each once, though three opens asked for them");
        for (AnimationWarning warning : animationWarnings) {
            assertEquals(me.rakkoon69.rakunloots.warning.Severity.HIGH, warning.severity());
        }
        assertEquals(AnimationMoment.OPEN, animationWarnings.get(1).moment().orElseThrow());

        animations.reload(config);
        assertEquals(4, animationWarnings.size(), "a reload reports an unknown type again");
        lifecycle.interact(OTHER, WORLD, summonBox("typo", 5, 4).position(), false, Interaction.CLICK);
        assertEquals(6, animationWarnings.size(), "and the other two once more");
    }

    /**
     * Acceptance: a loot names several animations for one moment, and they layer. A new animation type is one class
     * and one registration; the service and the lifecycle are unchanged.
     */
    @Test
    void severalAnimationsLayerAndANewTypeIsOneClassAndOneRegistration() {
        AtomicInteger started = new AtomicInteger();
        AnimationRegistry registry = AnimationRegistry.create();
        assertTrue(registry.register("CROSS", new CrossAnimation(started)));
        String cross = "CROSS_MARK:\n  type: cross\n  variables:\n    size: 3\n";
        start(config(marks() + cross, Map.of("layered", loot("inventory", 0, 600,
                "animations:\n  open:\n    - OPEN_MARK\n    - CROSS_MARK\n    - LAYER_MARK\n"))), registry, sink);
        LootBox box = summonBox("layered", 3, 4);

        lifecycle.interact(OTHER, WORLD, box.position(), false, Interaction.CLICK);
        step(2);

        assertEquals(1, started.get());
        assertEquals(List.of(OPEN_MARK, "CRIT", LAYER_MARK), sink.colours(),
                "the three layered animations drew, the new type through the context's sink and timer");
        assertEquals(List.of(), animationWarnings);
    }

    /**
     * A draw that fails warns high once, however many frames fail, and the box goes on. A draw that throws, as the
     * adapter does for a particle name the server lacks, stops that animation and warns.
     */
    @Test
    void aFailingDrawWarnsOnceAndTheBoxGoesOn() {
        sink.failAsynchronously(new IllegalStateException("region gone"));
        start(config("", Map.of()));
        LootBox box = summonBox("airdrops", 3, 4);

        step(5 * SECOND);

        assertTrue(sink.frames().size() >= 5, "it kept drawing");
        assertEquals(1, animationWarnings.size(), animationWarnings.toString());
        assertEquals(AnimationWarning.Kind.DRAW_FAILED, animationWarnings.get(0).kind());
        assertEquals("BLUE_SPHERE", animationWarnings.get(0).animationId());
        assertEquals("region gone", animationWarnings.get(0).cause().orElseThrow().getMessage());
        assertTrue(lifecycle.boxAt(WORLD, box.position()).isPresent());
        assertEquals(List.of(), fixture.warnings.lifecycleKinds());

        sink.clear();
        sink.failImmediately(new IllegalArgumentException("not a particle on this server: NOPE"));
        step(5 * SECOND);
        assertEquals(2, animationWarnings.size(), "a throwing draw is a second kind of failure only once");
        assertEquals(List.of(), fixture.warnings.lifecycleKinds(), "the observer never threw into the lifecycle");
    }

    /**
     * Particles draw through the server adapter on the thread that owns the spot, and nothing loads a chunk: the
     * adapter sink draws nothing for a box whose chunk is unloaded, and reads no chunk.
     */
    @Test
    void particlesDrawThroughTheServerAdapterAndLoadNoChunk() {
        start(config("", Map.of()), AnimationRegistry.create(), ParticleSink.of(fixture.adapter));
        LootBox box = summonBox("airdrops", 3, 4);
        int readsBefore = fixture.adapter.reads().size();

        step(2);
        assertEquals(1, fixture.adapter.particleCalls().size());
        assertEquals(box.position(), fixture.adapter.particleCalls().get(0).owner());
        assertEquals(WORLD, fixture.adapter.particleCalls().get(0).worldKey());
        assertEquals(200, fixture.adapter.particleCalls().get(0).draws().size(), "10 rings of 20 points");

        fixture.adapter.putChunk(flatChunk(0, 0), false, true);
        step(SECOND * 2);
        assertEquals(readsBefore, fixture.adapter.reads().size(), "no chunk was read or loaded");
        assertEquals(List.of(), animationWarnings);
    }

    /**
     * Stop ends every animation, and later events play nothing. A reload makes the next tick follow the new loot.
     */
    @Test
    void stopEndsEveryAnimationAndAReloadChangesTheAliveAnimation() {
        start(config(marks(), Map.of("changing", loot("drop", 0, 600, "animations:\n  alive:\n    - OPEN_MARK\n"))));
        summonBox("changing", 3, 4);
        step(2);
        assertEquals(List.of(OPEN_MARK), sink.colours());

        animations.reload(config(marks(), Map.of("changing", loot("drop", 0, 600,
                "animations:\n  alive:\n    - REMOVE_MARK\n"))));
        step(SECOND);
        assertEquals(List.of(OPEN_MARK, REMOVE_MARK), sink.colours(), "the next tick followed the new list");

        animations.stop();
        sink.clear();
        step(5 * SECOND);
        summonBox("changing", 5, 4);
        step(5 * SECOND);
        assertEquals(List.of(), sink.colours());
    }

    /**
     * A box whose loot is gone, or whose loot names no animation, plays nothing and warns nothing.
     */
    @Test
    void aLootThatNamesNoAnimationPlaysNothingAndWarnsNothing() {
        start(config("", Map.of("quiet", loot("drop", 0, 600, ""))));
        LootBox box = summonBox("quiet", 3, 4);

        step(5 * SECOND);
        lifecycle.interact(OWNER, WORLD, box.position(), false, Interaction.BREAK);
        step(5 * SECOND);

        assertEquals(List.of(), sink.colours());
        assertEquals(List.of(), animationWarnings);
    }

    /**
     * A new animation type for the registry test: it draws one point of its own particle on the next tick.
     */
    private static final class CrossAnimation implements AnimationType {

        private final AtomicInteger started;

        CrossAnimation(AtomicInteger started) {
            this.started = started;
        }

        @Override
        public RunningAnimation start(Map<String, Object> variables, me.rakkoon69.rakunloots.animation.AnimationContext
                context) {
            started.incrementAndGet();
            var handle = context.scheduler().run(() -> context.sink().draw(context.worldKey(), context.owner(),
                    List.of(new ParticleDraw(context.x(), context.y(), context.z(),
                            new ParticleSpec("CRIT", 1, Optional.empty())))));
            return new RunningAnimation() {
                @Override
                public void stop() {
                    handle.cancel();
                }

                @Override
                public boolean finished() {
                    return false;
                }
            };
        }
    }

    /**
     * Hands out the spots it was given, one per search, then misses.
     */
    private static final class Finder implements SpotFinder {

        final List<Spot> spots = new CopyOnWriteArrayList<>();

        private CompletableFuture<SummonResult> next() {
            if (spots.isEmpty()) {
                return CompletableFuture.completedFuture(SummonResult.miss(MissReason.ATTEMPTS_USED_UP));
            }
            return CompletableFuture.completedFuture(SummonResult.hit(spots.remove(0)));
        }

        @Override
        public CompletableFuture<SummonResult> serverSummon(UUID worldKey, String lootId, OccupiedCheck occupied) {
            return next();
        }

        @Override
        public CompletableFuture<SummonResult> aroundCenter(UUID worldKey, String lootId, int x, int y, int z,
                                                            OccupiedCheck occupied) {
            return next();
        }
    }
}
