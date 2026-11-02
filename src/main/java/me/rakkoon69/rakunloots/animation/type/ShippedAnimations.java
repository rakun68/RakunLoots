package me.rakkoon69.rakunloots.animation.type;

import me.rakkoon69.rakunloots.animation.AnimationType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The animation types RakunLoots ships, the one entry to this package.
 */
public final class ShippedAnimations {

    private ShippedAnimations() {
    }

    /**
     * Each shipped type by its name: {@code SPHERE} and {@code EXPLOSION}, in that order.
     */
    public static Map<String, AnimationType> all() {
        Map<String, AnimationType> types = new LinkedHashMap<>();
        types.put(SphereAnimation.NAME, new SphereAnimation());
        types.put(ExplosionAnimation.NAME, new ExplosionAnimation());
        return types;
    }
}
