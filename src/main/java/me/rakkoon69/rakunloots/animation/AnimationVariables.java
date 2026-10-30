package me.rakkoon69.rakunloots.animation;

import me.rakkoon69.rakunloots.server.ParticleSpec;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the {@code variables} of an {@code animations.yml} entry for an animation type. A variable that is left out
 * uses its default. One that is there but cannot be used throws, so a typo never plays as a different animation.
 */
public final class AnimationVariables {

    private static final String DEFAULT_PARTICLE = "REDSTONE";
    private static final String DEFAULT_COLOR = "200:200:200";

    private final Map<String, Object> variables;

    public AnimationVariables(Map<String, Object> variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * A whole number from {@code min} up.
     *
     * @throws IllegalArgumentException when it is not a whole number, or below the minimum
     */
    public int integer(String name, int fallback, int min) {
        Object raw = variables.get(name);
        if (raw == null) {
            return fallback;
        }
        Optional<Double> number = number(raw);
        if (number.isEmpty() || number.get() % 1 != 0 || number.get() > Integer.MAX_VALUE
                || number.get() < min) {
            throw new IllegalArgumentException(name + " must be a whole number of at least " + min + ", not " + raw);
        }
        return number.get().intValue();
    }

    /**
     * A finite number above 0.
     *
     * @throws IllegalArgumentException when it is not a finite number above 0
     */
    public double positive(String name, double fallback) {
        Object raw = variables.get(name);
        if (raw == null) {
            return fallback;
        }
        Optional<Double> number = number(raw);
        if (number.isEmpty() || !(number.get() > 0)) {
            throw new IllegalArgumentException(name + " must be a number above 0, not " + raw);
        }
        return number.get();
    }

    /**
     * The particle written under {@code particles.<name>}: {@code type}, {@code count}, and for a dust particle
     * {@code color} as {@code R:G:B} and {@code size}.
     *
     * @throws IllegalArgumentException when the section is missing or a value cannot be used
     */
    public ParticleSpec particle(String name) {
        Object particles = variables.get("particles");
        Object raw = particles instanceof Map<?, ?> map ? map.get(name) : null;
        if (!(raw instanceof Map<?, ?> section)) {
            throw new IllegalArgumentException("particles." + name + " is missing");
        }
        AnimationVariables values = new AnimationVariables(toStringKeys(section));
        String type = DEFAULT_PARTICLE;
        Object rawType = section.get("type");
        if (rawType != null) {
            if (!(rawType instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("particles." + name + ".type must be a particle name, not " + rawType);
            }
            type = text.trim();
        }
        int count = values.integer("count", 1, 1);
        Optional<ParticleSpec.Dust> dust = Optional.empty();
        String upper = type.toUpperCase(java.util.Locale.ROOT);
        if (upper.equals("REDSTONE") || upper.equals("DUST")) {
            Object rawColor = section.get("color");
            String color = rawColor == null ? DEFAULT_COLOR : String.valueOf(rawColor);
            int[] channels = channels(name, color);
            dust = Optional.of(new ParticleSpec.Dust(channels[0], channels[1], channels[2],
                    (float) values.positive("size", 1.0)));
        }
        return new ParticleSpec(type, count, dust);
    }

    private static int[] channels(String name, String color) {
        String[] parts = color.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("particles." + name + ".color must be R:G:B, not " + color);
        }
        int[] channels = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                channels[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("particles." + name + ".color must be R:G:B, not " + color);
            }
            if (channels[i] < 0 || channels[i] > 255) {
                throw new IllegalArgumentException("particles." + name + ".color channels are 0 to 255, not " + color);
            }
        }
        return channels;
    }

    private static Map<String, Object> toStringKeys(Map<?, ?> map) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    /**
     * A finite number, from a number or a quoted number.
     */
    private static Optional<Double> number(Object raw) {
        double value;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else if (raw instanceof String text) {
            try {
                value = Double.parseDouble(text.trim());
            } catch (NumberFormatException failure) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
        return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
    }
}
