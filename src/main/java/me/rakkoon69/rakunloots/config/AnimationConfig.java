package me.rakkoon69.rakunloots.config;

import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static me.rakkoon69.rakunloots.config.ConfigValues.quoteLine;
import static me.rakkoon69.rakunloots.config.ConfigValues.textValue;
import static me.rakkoon69.rakunloots.config.ConfigValues.yaml;

/**
 * Parses {@code animations.yml} and a loot's {@code animations:} section. A missing section uses its default
 * silently. A value that cannot be used warns high and is left out.
 */
final class AnimationConfig {

    static final String KEY_ANIMATIONS = "animations";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VARIABLES = "variables";

    private AnimationConfig() {
    }

    /**
     * The entries of {@code animations.yml}, by id in the order written. An entry that is not a section with a
     * {@code type}, or whose {@code variables} is not a section, warns high and is left out. A file that is not valid
     * YAML warns high and holds no entry.
     */
    static Map<String, AnimationDefinition> parseDefinitions(String content, List<ConfigWarning> warnings) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        Object loaded;
        try {
            loaded = yaml().load(content);
        } catch (YAMLException ex) {
            warnings.add(ConfigWarning.animationsParseRejected(quoteLine(content, ex)));
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            return Map.of();
        }
        Map<String, AnimationDefinition> definitions = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String id = String.valueOf(entry.getKey());
            Optional<AnimationDefinition> definition = definition(id, entry.getValue());
            if (definition.isPresent()) {
                definitions.put(id, definition.get());
            } else {
                warnings.add(ConfigWarning.unusableAnimation(id));
            }
        }
        return Collections.unmodifiableMap(definitions);
    }

    private static Optional<AnimationDefinition> definition(String id, Object raw) {
        if (!(raw instanceof Map<?, ?> section)) {
            return Optional.empty();
        }
        Optional<String> type = textValue(section.get(KEY_TYPE)).map(String::trim).filter(text -> !text.isEmpty());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Object rawVariables = section.get(KEY_VARIABLES);
        if (rawVariables == null) {
            return Optional.of(new AnimationDefinition(id, type.get().toUpperCase(Locale.ROOT), Map.of()));
        }
        if (!(rawVariables instanceof Map<?, ?> variables)) {
            return Optional.empty();
        }
        return Optional.of(new AnimationDefinition(id, type.get().toUpperCase(Locale.ROOT), toStringKeys(variables)));
    }

    private static Map<String, Object> toStringKeys(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    /**
     * The loot's animation ids for each moment. A key that is not a moment warns high as an unknown key. A moment
     * that is not a list of animation ids warns high and has none. An id that {@code animations.yml} does not define
     * warns high and stays in the list, so the animation service warns again when it would play. When the file was
     * not given at all ({@code definitions} is null) no id can be checked, so none warns.
     */
    static LootAnimations parseLoot(
            String lootId,
            Map<String, Object> root,
            Map<String, AnimationDefinition> definitions,
            List<ConfigWarning> warnings
    ) {
        Object section = root.get(KEY_ANIMATIONS);
        if (section == null) {
            return LootAnimations.NONE;
        }
        if (!(section instanceof Map<?, ?> map)) {
            warnings.add(ConfigWarning.unusableAnimations(lootId, KEY_ANIMATIONS));
            return LootAnimations.NONE;
        }
        Map<AnimationMoment, List<String>> ids = new EnumMap<>(AnimationMoment.class);
        for (Object key : map.keySet()) {
            if (momentOf(key).isEmpty()) {
                warnings.add(ConfigWarning.unknownKey(lootId, KEY_ANIMATIONS + "." + key));
            }
        }
        for (AnimationMoment moment : AnimationMoment.values()) {
            Object raw = map.get(moment.key());
            if (raw == null) {
                continue;
            }
            String path = KEY_ANIMATIONS + "." + moment.key();
            Optional<List<String>> list = idList(raw);
            if (list.isEmpty()) {
                warnings.add(ConfigWarning.unusableAnimations(lootId, path));
                continue;
            }
            for (String id : list.get()) {
                if (definitions != null && !definitions.containsKey(id)) {
                    warnings.add(ConfigWarning.unknownAnimation(lootId, path, id));
                }
            }
            ids.put(moment, list.get());
        }
        return new LootAnimations(ids);
    }

    private static Optional<AnimationMoment> momentOf(Object key) {
        for (AnimationMoment moment : AnimationMoment.values()) {
            if (moment.key().equals(key)) {
                return Optional.of(moment);
            }
        }
        return Optional.empty();
    }

    /**
     * A list of non-blank strings. Any other entry refuses the whole list.
     */
    private static Optional<List<String>> idList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Optional.empty();
        }
        List<String> ids = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String text) || text.isBlank()) {
                return Optional.empty();
            }
            ids.add(text.trim());
        }
        return Optional.of(List.copyOf(ids));
    }
}
