package me.rakkoon69.rakunloots.animation.type;

import me.rakkoon69.rakunloots.animation.AnimationContext;
import me.rakkoon69.rakunloots.animation.AnimationType;
import me.rakkoon69.rakunloots.animation.RunningAnimation;

import java.util.Map;

/**
 * The {@code SPHERE} type: a sphere of particles around the centre, drawn {@code loops} times, {@code interval} ticks
 * apart, after {@code delay} ticks. Variables: {@code delay}, {@code interval}, {@code loops}, {@code radius},
 * {@code count} for the rings, and {@code particles.outer_line}.
 */
final class SphereAnimation implements AnimationType {

    static final String NAME = "SPHERE";

    @Override
    public RunningAnimation start(Map<String, Object> variables, AnimationContext context) {
        return SphereFrame.start(variables, context);
    }
}
