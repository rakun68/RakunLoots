package me.rakkoon69.rakunloots.animation;

import me.rakkoon69.rakunloots.animation.type.ShippedAnimations;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Animation types by name, as {@code animations.yml} writes them in {@code type}. Names are not case sensitive. There
 * is no unregister.
 */
public final class AnimationRegistry {

    private final Map<String, AnimationType> types = new LinkedHashMap<>();

    private AnimationRegistry() {
    }

    /**
     * A registry with the shipped types: {@code SPHERE} and {@code EXPLOSION}.
     */
    public static AnimationRegistry create() {
        AnimationRegistry registry = new AnimationRegistry();
        registry.types.putAll(ShippedAnimations.all());
        return registry;
    }

    /**
     * @return true when the name was accepted; false when refused (blank or already taken, shipped names included)
     */
    public boolean register(String name, AnimationType type) {
        Objects.requireNonNull(type, "type");
        if (name == null) {
            return false;
        }
        String canonical = name.trim().toUpperCase(Locale.ROOT);
        if (canonical.isEmpty() || types.containsKey(canonical)) {
            return false;
        }
        types.put(canonical, type);
        return true;
    }

    public Optional<AnimationType> find(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(types.get(name.trim().toUpperCase(Locale.ROOT)));
    }
}
