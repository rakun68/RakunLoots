package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.visuals.GlowColor;
import me.rakkoon69.rakunloots.visuals.LaserType;
import me.rakkoon69.rakunloots.visuals.VisualsSettings;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static me.rakkoon69.rakunloots.config.ConfigValues.strictBoolean;
import static me.rakkoon69.rakunloots.config.ConfigValues.strictInt;
import static me.rakkoon69.rakunloots.config.ConfigValues.textValue;

/**
 * Parses a loot's {@code glow:} and {@code laser:} sections. A missing value uses its default silently. A value that
 * cannot be used warns high and uses its default.
 */
final class VisualsConfig {

    static final String KEY_GLOW = "glow";
    static final String KEY_LASER = "laser";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_COLOR = "color";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VIEW_DISTANCE = "view_distance";
    private static final String KEY_START_OFFSET = "start_offset";
    private static final String NO_LASER = "NONE";
    private static final Set<String> GLOW_KEYS = Set.of(KEY_ENABLED, KEY_COLOR);
    private static final Set<String> LASER_KEYS = Set.of(KEY_TYPE, KEY_VIEW_DISTANCE, KEY_START_OFFSET);

    private VisualsConfig() {
    }

    static VisualsSettings parseLootVisuals(String lootId, Map<String, Object> root, List<ConfigWarning> warnings) {
        boolean glowEnabled = VisualsSettings.DEFAULT_GLOW_ENABLED;
        GlowColor glowColor = VisualsSettings.DEFAULT_GLOW_COLOR;
        Map<String, Object> glow =
                section(lootId, root, KEY_GLOW, GLOW_KEYS, ConfigWarning.Kind.UNUSABLE_GLOW, warnings);
        Object rawEnabled = glow.get(KEY_ENABLED);
        if (rawEnabled != null) {
            Optional<Boolean> parsed = strictBoolean(rawEnabled);
            if (parsed.isPresent()) {
                glowEnabled = parsed.get();
            } else {
                warnings.add(ConfigWarning.unusableGlowValue(lootId, KEY_ENABLED));
            }
        }
        Object rawColor = glow.get(KEY_COLOR);
        if (rawColor != null) {
            Optional<GlowColor> parsed = textValue(rawColor).flatMap(GlowColor::parse);
            if (parsed.isPresent()) {
                glowColor = parsed.get();
            } else {
                warnings.add(ConfigWarning.unusableGlowValue(lootId, KEY_COLOR));
            }
        }

        Optional<LaserType> laser = Optional.empty();
        int viewDistance = VisualsSettings.DEFAULT_LASER_VIEW_DISTANCE;
        int startOffset = VisualsSettings.DEFAULT_LASER_START_OFFSET;
        Map<String, Object> laserSection =
                section(lootId, root, KEY_LASER, LASER_KEYS, ConfigWarning.Kind.UNUSABLE_LASER, warnings);
        Object rawType = laserSection.get(KEY_TYPE);
        if (rawType != null) {
            Optional<String> text = textValue(rawType).map(value -> value.trim().toUpperCase(Locale.ROOT));
            if (text.isEmpty() || !text.get().equals(NO_LASER)) {
                laser = text.flatMap(VisualsConfig::laserType);
                if (laser.isEmpty()) {
                    warnings.add(ConfigWarning.unusableLaserValue(lootId, KEY_TYPE));
                }
            }
        }
        Object rawDistance = laserSection.get(KEY_VIEW_DISTANCE);
        if (rawDistance != null) {
            OptionalInt parsed = strictInt(rawDistance);
            if (parsed.isPresent() && parsed.getAsInt() >= 1) {
                viewDistance = parsed.getAsInt();
            } else {
                warnings.add(ConfigWarning.unusableLaserValue(lootId, KEY_VIEW_DISTANCE));
            }
        }
        Object rawOffset = laserSection.get(KEY_START_OFFSET);
        if (rawOffset != null) {
            OptionalInt parsed = strictInt(rawOffset);
            if (parsed.isPresent() && parsed.getAsInt() >= VisualsSettings.MIN_LASER_START_OFFSET
                    && parsed.getAsInt() <= VisualsSettings.MAX_LASER_START_OFFSET) {
                startOffset = parsed.getAsInt();
            } else {
                warnings.add(ConfigWarning.unusableLaserValue(lootId, KEY_START_OFFSET));
            }
        }
        return new VisualsSettings(glowEnabled, glowColor, laser, viewDistance, startOffset);
    }

    /**
     * A loot's section as a map; empty when it is missing or not a map, and the second case warns on the section's own
     * key. Keys it does not know warn as unknown keys.
     */
    private static Map<String, Object> section(String lootId, Map<String, Object> root, String key, Set<String> known,
                                               ConfigWarning.Kind kind, List<ConfigWarning> warnings) {
        Object raw = root.get(key);
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> rawMap)) {
            warnings.add(ConfigWarning.lootHigh(kind, lootId, Optional.empty(), Optional.of(key)));
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        ConfigValues.warnUnknownKeys(lootId, map, known, warnings);
        return map;
    }

    private static Optional<LaserType> laserType(String upperCase) {
        for (LaserType type : LaserType.values()) {
            if (type.name().equals(upperCase)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
