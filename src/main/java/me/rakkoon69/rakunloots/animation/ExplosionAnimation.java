package me.rakkoon69.rakunloots.animation;

import java.util.Map;

/**
 * The {@code EXPLOSION} type, which the old animations drew exactly as {@code SPHERE}, and still does. It takes the
 * same variables. Shipped entries use it with {@code loops: 1} for a single burst.
 */
final class ExplosionAnimation implements AnimationType {

    static final String NAME = "EXPLOSION";

    @Override
    public RunningAnimation start(Map<String, Object> variables, AnimationContext context) {
        return SphereFrame.start(variables, context);
    }
}
