package me.rakkoon69.rakunloots.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One entry of {@code animations.yml}: its id, the name of its type, and the variables its type reads. Holds no
 * server types, and does not know which types exist; the animation registry resolves the type.
 *
 * @param id        the entry's key in {@code animations.yml}
 * @param type      the type name, upper case
 * @param variables the entry's {@code variables} section, nested maps and lists included, all unmodifiable
 */
public record AnimationDefinition(String id, String type, Map<String, Object> variables) {

    public AnimationDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        variables = freezeMap(Objects.requireNonNull(variables, "variables"));
    }

    private static Map<String, Object> freezeMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            return freezeMap(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AnimationDefinition::freeze).toList();
        }
        return value;
    }
}
