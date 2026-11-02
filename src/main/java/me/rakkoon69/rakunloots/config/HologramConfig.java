package me.rakkoon69.rakunloots.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static me.rakkoon69.rakunloots.config.ConfigValues.strictBoolean;
import static me.rakkoon69.rakunloots.config.ConfigValues.strictInt;
import static me.rakkoon69.rakunloots.config.ConfigValues.textValue;
import static me.rakkoon69.rakunloots.config.ConfigValues.warnUnknownKeys;

/**
 * Parses the globals' {@code hologram} section and {@code hologram_provider}, and a loot's {@code hologram:} section.
 * A missing value uses its default silently. A value that cannot be used warns high and uses its default.
 */
final class HologramConfig {

    static final String KEY_HOLOGRAM = "hologram";
    private static final String KEY_PROVIDER = "hologram_provider";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_VIEW_RANGE = "view_range";
    private static final String KEY_LINES = "lines";
    private static final Set<String> LOOT_HOLOGRAM_KEYS = Set.of(KEY_ENABLED, KEY_HEIGHT, KEY_VIEW_RANGE, KEY_LINES);

    private HologramConfig() {
    }

    /**
     * The global view range in blocks, from {@code hologram.view_range}.
     */
    static int parseGlobalViewRange(Map<String, Object> root, List<ConfigWarning> warnings) {
        Object section = root.get(KEY_HOLOGRAM);
        if (section == null) {
            return HologramSettings.DEFAULT_VIEW_RANGE;
        }
        if (!(section instanceof Map<?, ?> map)) {
            warnings.add(ConfigWarning.unusableHologramValue(KEY_HOLOGRAM));
            return HologramSettings.DEFAULT_VIEW_RANGE;
        }
        Object raw = map.get(KEY_VIEW_RANGE);
        if (raw == null) {
            return HologramSettings.DEFAULT_VIEW_RANGE;
        }
        OptionalInt viewRange = viewRange(raw);
        if (viewRange.isEmpty()) {
            warnings.add(ConfigWarning.unusableHologramValue(KEY_VIEW_RANGE));
            return HologramSettings.DEFAULT_VIEW_RANGE;
        }
        return viewRange.getAsInt();
    }

    static HologramProviderChoice parseProvider(Map<String, Object> root, List<ConfigWarning> warnings) {
        Object raw = root.get(KEY_PROVIDER);
        if (raw == null) {
            return HologramProviderChoice.AUTO;
        }
        Optional<HologramProviderChoice> parsed = textValue(raw).flatMap(HologramConfig::providerChoice);
        if (parsed.isEmpty()) {
            warnings.add(ConfigWarning.unknownHologramProvider(KEY_PROVIDER));
            return HologramProviderChoice.AUTO;
        }
        return parsed.get();
    }

    /**
     * The loot's hologram. Each key that cannot be used warns high on its own and uses its default; the others stay.
     */
    static HologramSettings parseLootHologram(
            String lootId,
            Map<String, Object> root,
            int globalViewRange,
            List<ConfigWarning> warnings
    ) {
        Object section = root.get(KEY_HOLOGRAM);
        HologramSettings defaults = new HologramSettings(
                HologramSettings.DEFAULT_ENABLED, HologramSettings.DEFAULT_HEIGHT, globalViewRange, Optional.empty());
        if (section == null) {
            return defaults;
        }
        if (!(section instanceof Map<?, ?> rawMap)) {
            warnings.add(ConfigWarning.unusableHologramValue(lootId, KEY_HOLOGRAM));
            return defaults;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        warnUnknownKeys(lootId, map, LOOT_HOLOGRAM_KEYS, warnings);

        boolean enabled = HologramSettings.DEFAULT_ENABLED;
        Object rawEnabled = map.get(KEY_ENABLED);
        if (rawEnabled != null) {
            Optional<Boolean> parsed = strictBoolean(rawEnabled);
            if (parsed.isPresent()) {
                enabled = parsed.get();
            } else {
                warnings.add(ConfigWarning.unusableHologramValue(lootId, KEY_ENABLED));
            }
        }

        double height = HologramSettings.DEFAULT_HEIGHT;
        Object rawHeight = map.get(KEY_HEIGHT);
        if (rawHeight != null) {
            Optional<Double> parsed = height(rawHeight);
            if (parsed.isPresent()) {
                height = parsed.get();
            } else {
                warnings.add(ConfigWarning.unusableHologramValue(lootId, KEY_HEIGHT));
            }
        }

        int viewRange = globalViewRange;
        Object rawViewRange = map.get(KEY_VIEW_RANGE);
        if (rawViewRange != null) {
            OptionalInt parsed = viewRange(rawViewRange);
            if (parsed.isPresent()) {
                viewRange = parsed.getAsInt();
            } else {
                warnings.add(ConfigWarning.unusableHologramValue(lootId, KEY_VIEW_RANGE));
            }
        }

        Optional<List<String>> lines = Optional.empty();
        Object rawLines = map.get(KEY_LINES);
        if (rawLines != null) {
            lines = lines(rawLines);
            if (lines.isEmpty()) {
                warnings.add(ConfigWarning.unusableHologramValue(lootId, KEY_LINES));
            }
        }

        return new HologramSettings(enabled, height, viewRange, lines);
    }

    /**
     * A whole number of blocks, at least 1.
     */
    private static OptionalInt viewRange(Object raw) {
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty() || parsed.getAsInt() < 1) {
            return OptionalInt.empty();
        }
        return parsed;
    }

    /**
     * A finite number of blocks, 0 or more. A quoted number is read as a number, as {@code strictInt} does.
     */
    private static Optional<Double> height(Object raw) {
        double value;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else if (raw instanceof String text) {
            try {
                value = Double.parseDouble(text.trim());
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
        if (!Double.isFinite(value) || value < 0) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * A list of one or more strings. Any other entry refuses the whole list.
     */
    private static Optional<List<String>> lines(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return Optional.empty();
        }
        List<String> lines = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String text)) {
                return Optional.empty();
            }
            lines.add(text);
        }
        return Optional.of(List.copyOf(lines));
    }

    private static Optional<HologramProviderChoice> providerChoice(String raw) {
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        for (HologramProviderChoice choice : HologramProviderChoice.values()) {
            if (choice.name().equals(trimmed)) {
                return Optional.of(choice);
            }
        }
        return Optional.empty();
    }
}
