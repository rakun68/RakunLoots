package me.rakkoon69.rakunloots.animation.type;

import me.rakkoon69.rakunloots.animation.AnimationContext;
import me.rakkoon69.rakunloots.animation.AnimationRegistry;
import me.rakkoon69.rakunloots.animation.AnimationType;
import me.rakkoon69.rakunloots.animation.AnimationVariables;
import me.rakkoon69.rakunloots.animation.RecordingParticleSink;
import me.rakkoon69.rakunloots.animation.RunningAnimation;
import me.rakkoon69.rakunloots.server.ParticleSpec;
import me.rakkoon69.rakunloots.fake.FakeServerAdapter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry and the two shipped types on their own: names, timing, the ported shape, and the variables they refuse.
 */
class AnimationTypesTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    private final FakeServerAdapter adapter = new FakeServerAdapter(WORLD);
    private final RecordingParticleSink sink = new RecordingParticleSink();
    private final List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();

    private AnimationContext context() {
        return new AnimationContext(WORLD, 3.5, 64.5, 4.5, sink, adapter.global(), failures::add);
    }

    private static Map<String, Object> particles(String colour) {
        Map<String, Object> outerLine = new LinkedHashMap<>();
        outerLine.put("type", "REDSTONE");
        outerLine.put("count", 1);
        outerLine.put("size", 1.0);
        outerLine.put("color", colour);
        return new LinkedHashMap<>(Map.of("outer_line", outerLine));
    }

    private static Map<String, Object> variables(Object delay, Object interval, Object loops) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("delay", delay);
        variables.put("interval", interval);
        variables.put("loops", loops);
        variables.put("radius", 2.0);
        variables.put("count", 4);
        variables.put("particles", particles("1:2:3"));
        return variables;
    }

    @Test
    void theRegistryHoldsSphereAndExplosionByNameWhateverTheCase() {
        AnimationRegistry registry = AnimationRegistry.create();

        assertTrue(registry.find("SPHERE").isPresent());
        assertTrue(registry.find("explosion").isPresent());
        assertTrue(registry.find(" Sphere ").isPresent());
        assertTrue(registry.find("SPIRAL").isEmpty());
    }

    @Test
    void aNewTypeIsOneRegistrationAndAShippedOrTakenNameIsRefused() {
        AnimationRegistry registry = AnimationRegistry.create();
        AnimationType type = (variables, context) -> null;

        assertTrue(registry.register("spiral", type));

        assertEquals(Optional.of(type), registry.find("SPIRAL"));
        assertFalse(registry.register("SPIRAL", type), "taken");
        assertFalse(registry.register("sphere", type), "shipped");
        assertFalse(registry.register("  ", type), "blank");
        assertFalse(registry.register(null, type), "no name");
    }

    /**
     * A sphere draws {@code loops} frames, the first after {@code delay} ticks and the rest {@code interval} apart,
     * then is finished. Wrong version: a loop count off by one, as the old cancel-then-draw had, draws a fourth frame.
     */
    @Test
    void aSphereDrawsItsLoopsAtItsDelayAndIntervalThenFinishes() {
        RunningAnimation animation = new SphereAnimation().start(variables(5, 7, 3), context());

        adapter.globalScheduler().stepTicks(4);
        assertEquals(0, sink.frames().size());
        adapter.globalScheduler().stepTicks(1);
        assertEquals(1, sink.frames().size(), "tick 5");
        adapter.globalScheduler().stepTicks(7);
        assertEquals(2, sink.frames().size(), "tick 12");
        assertFalse(animation.finished());
        adapter.globalScheduler().stepTicks(7);
        assertEquals(3, sink.frames().size(), "tick 19");
        assertTrue(animation.finished());
        adapter.globalScheduler().stepTicks(100);
        assertEquals(3, sink.frames().size());
        assertEquals(List.of(), failures);
    }

    @Test
    void aStoppedSphereDrawsNoMore() {
        RunningAnimation animation = new SphereAnimation().start(variables(0, 10, 26), context());
        adapter.globalScheduler().stepTicks(11);
        assertEquals(2, sink.frames().size(), "ticks 1 and 11: a delay of 0 draws on the next tick");

        animation.stop();
        adapter.globalScheduler().stepTicks(100);

        assertEquals(2, sink.frames().size());
        assertTrue(animation.finished());
    }

    /**
     * The ported shape: {@code count} rings from the top of the sphere to its bottom, 20 points each, every point on
     * the sphere around the centre. The old defaults hold for what the entry leaves out.
     */
    @Test
    void aSphereIsRingsOfPointsOnTheSphereAndTheOldDefaultsHold() {
        Map<String, Object> onlyParticles = new LinkedHashMap<>();
        onlyParticles.put("particles", particles("210:60:60"));
        new SphereAnimation().start(onlyParticles, context());

        adapter.globalScheduler().stepTicks(1 + 10 * 25);

        assertEquals(26, sink.frames().size(), "loops default 26, interval default 10");
        RecordingParticleSink.Frame frame = sink.frames().get(0);
        assertEquals(10 * 20, frame.draws().size(), "count default 10 rings of 20 points");
        assertEquals(1.2, frame.farthestFrom(3.5, 64.5, 4.5), 1e-9, "radius default 1.2");
        assertEquals(64.5 + 1.2, frame.draws().get(0).y(), 1e-9, "the first ring is the top");
        assertEquals(new ParticleSpec("REDSTONE", 1, Optional.of(new ParticleSpec.Dust(210, 60, 60, 1.0f))),
                frame.draws().get(0).spec());
    }

    @Test
    void explosionDrawsTheFramesSphereDoesAsTheOldAnimationsDid() {
        AnimationContext context = context();
        new SphereAnimation().start(variables(0, 10, 1), context);
        RecordingParticleSink explosionSink = new RecordingParticleSink();
        new ExplosionAnimation().start(variables(0, 10, 1), new AnimationContext(WORLD, 3.5, 64.5, 4.5,
                explosionSink, adapter.global(), failures::add));

        adapter.globalScheduler().stepTicks(2);

        assertEquals(sink.frames(), explosionSink.frames());
        assertEquals(1, sink.frames().size());
    }

    @Test
    void variablesTheTypeCannotUseAreRefusedAndNothingStarts() {
        for (Map<String, Object> bad : List.of(
                with("radius", "wide"), with("radius", 0), with("radius", -1.5), with("count", 0),
                with("loops", 0), with("interval", 0), with("delay", -1), with("loops", 2.5),
                with("particles", Map.of()), with("particles", Map.of("outer_line", "REDSTONE")),
                with("particles", particles("red")), with("particles", particles("1:2")),
                with("particles", particles("300:0:0")))) {
            assertThrows(IllegalArgumentException.class, () -> new SphereAnimation().start(bad, context()),
                    bad.toString());
        }
        adapter.globalScheduler().stepTicks(100);
        assertEquals(0, sink.frames().size());
        assertEquals(1, new AnimationVariables(Map.of()).integer("loops", 1, 1));
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> variables = variables(0, 10, 1);
        variables.put(key, value);
        return variables;
    }
}
