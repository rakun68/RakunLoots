package me.rakkoon69.rakunloots.config.summon;

import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.config.block.BlockListResolver;
import me.rakkoon69.rakunloots.server.UnloadedChunkPolicy;
import me.rakkoon69.rakunloots.warning.Severity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static me.rakkoon69.rakunloots.config.ConfigValues.blockEntriesOrDefault;
import static me.rakkoon69.rakunloots.config.ConfigValues.intValue;
import static me.rakkoon69.rakunloots.config.ConfigValues.strictBoolean;
import static me.rakkoon69.rakunloots.config.ConfigValues.strictInt;
import static me.rakkoon69.rakunloots.config.ConfigValues.textValue;
import static me.rakkoon69.rakunloots.config.ConfigValues.warnUnknownKeys;

/**
 * Parses a world's {@code summon_search}, a loot's {@code summon_settings}, and {@code placement_attempts}.
 */
public final class SummonConfig {

    private static final String KEY_PLACEMENT_ATTEMPTS = "placement_attempts";
    private static final String KEY_OLD_PLAYER_ATTEMPTS = "player_find_location_max_attempts";
    private static final String KEY_OLD_SERVER_ATTEMPTS = "server_find_location_max_attempts";
    private static final String KEY_SUMMON_SETTINGS = "summon_settings";
    private static final String KEY_SUMMON_SEARCH = "summon_search";
    private static final List<String> DEFAULT_FLOOR_SOLID = List.of("@solid");
    private static final List<String> DEFAULT_AVOID = List.of(
            "minecraft:lava",
            "minecraft:fire",
            "minecraft:campfire",
            "minecraft:soul_fire",
            "minecraft:soul_campfire"
    );
    private static final Set<String> SUMMON_SETTINGS_KEYS = Set.of(
            "summon_type", "y_range", "replaceable_blocks", "avoid_surfaces",
            "player_ring", "vertical_offset_enable", "vertical_offset", "attempts",
            "horizontal_offset",
            "top_surface", "any_surface", "buried", "deep_buried", "underwater", "anywhere"
    );

    private SummonConfig() {
    }

    public static SummonSearchSettings parseSummonSearch(
            String worldName,
            Map<String, Object> worldMap,
            List<ConfigWarning> warnings
    ) {
        SummonSearchSettings.Mode mode = SummonSearchSettings.DEFAULT_MODE;
        UnloadedChunkPolicy unloaded = SummonSearchSettings.DEFAULT_UNLOADED;
        SummonRing spawnRing = SummonSearchSettings.DEFAULT_SPAWN_RING;
        SummonRing smartRing = SummonSearchSettings.DEFAULT_SMART_RING;
        int pickBias = SummonSearchSettings.DEFAULT_PICK_BIAS;
        int medianPull = SummonSearchSettings.DEFAULT_MEDIAN_PULL;
        int idleSeconds = SummonSearchSettings.DEFAULT_IDLE_SECONDS;
        SummonSearchSettings.WhenAllAbsent whenAllAbsent = SummonSearchSettings.DEFAULT_WHEN_ALL_ABSENT;

        boolean oldSmart = false;
        if (worldMap.containsKey("smart_summon")) {
            warnings.add(ConfigWarning.oldKey("smart_summon"));
            Optional<Boolean> flag = strictBoolean(worldMap.get("smart_summon"));
            if (flag.isPresent() && flag.get()) {
                oldSmart = true;
                mode = SummonSearchSettings.Mode.SMART;
            }
        }
        if (worldMap.containsKey("smart_summon_distance")) {
            warnings.add(ConfigWarning.oldKey("smart_summon_distance"));
            Optional<Integer> distance = intValue(worldMap.get("smart_summon_distance"));
            if (distance.isPresent() && distance.get() >= 0) {
                smartRing = new SummonRing(smartRing.min(), distance.get());
            }
        }
        if (worldMap.containsKey("only_loaded_chunks")) {
            warnings.add(ConfigWarning.oldKey("only_loaded_chunks"));
            Optional<Boolean> flag = strictBoolean(worldMap.get("only_loaded_chunks"));
            if (flag.isPresent() && flag.get()) {
                unloaded = UnloadedChunkPolicy.SKIP;
            }
        }
        Integer oldX = intValue(worldMap.get("max_distance_to_world_spawn_point_x")).orElse(null);
        Integer oldZ = intValue(worldMap.get("max_distance_to_world_spawn_point_z")).orElse(null);
        if (worldMap.containsKey("max_distance_to_world_spawn_point_x")) {
            warnings.add(ConfigWarning.oldKey("max_distance_to_world_spawn_point_x"));
        }
        if (worldMap.containsKey("max_distance_to_world_spawn_point_z")) {
            warnings.add(ConfigWarning.oldKey("max_distance_to_world_spawn_point_z"));
        }
        if (oldX != null || oldZ != null) {
            int max = Math.max(oldX == null ? 0 : Math.max(0, oldX), oldZ == null ? 0 : Math.max(0, oldZ));
            spawnRing = new SummonRing(0, max);
        }

        Object section = worldMap.get(KEY_SUMMON_SEARCH);
        if (section instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;

            Optional<String> modeText = textValue(map.get("mode"));
            if (modeText.isPresent()) {
                String trimmed = modeText.get().trim();
                if ("spawn".equalsIgnoreCase(trimmed)) {
                    mode = SummonSearchSettings.Mode.SPAWN;
                } else if ("smart".equalsIgnoreCase(trimmed)) {
                    mode = SummonSearchSettings.Mode.SMART;
                } else {
                    warnings.add(ConfigWarning.unusableSummonSearch(worldName, "mode"));
                    mode = SummonSearchSettings.DEFAULT_MODE;
                }
            }

            Optional<String> unloadedText = textValue(map.get("unloaded_chunks"));
            if (unloadedText.isPresent()) {
                String trimmed = unloadedText.get().trim();
                if ("skip".equalsIgnoreCase(trimmed)) {
                    unloaded = UnloadedChunkPolicy.SKIP;
                } else if ("load".equalsIgnoreCase(trimmed)) {
                    unloaded = UnloadedChunkPolicy.LOAD;
                } else if ("generate".equalsIgnoreCase(trimmed)) {
                    unloaded = UnloadedChunkPolicy.GENERATE;
                } else {
                    warnings.add(ConfigWarning.unusableSummonSearch(worldName, "unloaded_chunks"));
                    unloaded = SummonSearchSettings.DEFAULT_UNLOADED;
                }
            }

            spawnRing = parseRing(
                    worldName,
                    map.get("spawn_ring"),
                    spawnRing,
                    warnings
            );
            Object smartNode = map.get("smart");
            if (smartNode instanceof Map<?, ?> smartRaw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> smart = (Map<String, Object>) smartRaw;
                smartRing = parseRing(
                        worldName,
                        smart.get("ring"),
                        smartRing,
                        warnings
                );
                pickBias = boundedInt(
                        worldName, smart.get("pick_bias"), SummonSearchSettings.DEFAULT_PICK_BIAS, 0, 100, "pick_bias", warnings);
                medianPull = boundedInt(
                        worldName, smart.get("median_pull"), SummonSearchSettings.DEFAULT_MEDIAN_PULL, 50, 100,
                        "median_pull", warnings);
                idleSeconds = boundedInt(
                        worldName, smart.get("idle_seconds"), SummonSearchSettings.DEFAULT_IDLE_SECONDS, 0,
                        Integer.MAX_VALUE, "idle_seconds", warnings);
                Optional<String> absent = textValue(smart.get("when_all_absent"));
                if (absent.isPresent()) {
                    String trimmed = absent.get().trim();
                    if ("spawn".equalsIgnoreCase(trimmed)) {
                        whenAllAbsent = SummonSearchSettings.WhenAllAbsent.SPAWN;
                    } else if ("none".equalsIgnoreCase(trimmed)) {
                        whenAllAbsent = SummonSearchSettings.WhenAllAbsent.NONE;
                    } else {
                        warnings.add(ConfigWarning.unusableSummonSearch(worldName, "when_all_absent"));
                        whenAllAbsent = SummonSearchSettings.DEFAULT_WHEN_ALL_ABSENT;
                    }
                }
            }
        } else if (!oldSmart && section != null) {
            warnings.add(ConfigWarning.unusableSummonSearch(worldName, KEY_SUMMON_SEARCH));
        }

        if (mode == SummonSearchSettings.Mode.SMART && unloaded == UnloadedChunkPolicy.SKIP) {
            warnings.add(ConfigWarning.smartWithSkip(worldName));
            unloaded = UnloadedChunkPolicy.LOAD;
        }

        return new SummonSearchSettings(
                mode, unloaded, spawnRing, smartRing, pickBias, medianPull, idleSeconds, whenAllAbsent);
    }

    private static SummonRing parseRing(
            String worldName,
            Object raw,
            SummonRing defaults,
            List<ConfigWarning> warnings
    ) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults;
        }
        int min = intValue(map.get("min")).orElse(defaults.min());
        int max = intValue(map.get("max")).orElse(defaults.max());
        if (min < 0 || max < 0) {
            warnings.add(ConfigWarning.unusableSummonSearch(worldName, "ring"));
            return defaults;
        }
        if (min > max) {
            warnings.add(ConfigWarning.unusableSummonSearch(worldName, "ring"));
            int swap = min;
            min = max;
            max = swap;
        }
        return new SummonRing(min, max);
    }

    private static int boundedInt(
            String worldName,
            Object raw,
            int defaultValue,
            int minInclusive,
            int maxInclusive,
            String key,
            List<ConfigWarning> warnings
    ) {
        if (raw == null) {
            return defaultValue;
        }
        Optional<Integer> parsed = intValue(raw);
        if (parsed.isEmpty() || parsed.get() < minInclusive || parsed.get() > maxInclusive) {
            warnings.add(ConfigWarning.unusableSummonSearch(worldName, key));
            return defaultValue;
        }
        return parsed.get();
    }

    public static SummonRules parseSummonRules(
            String lootId,
            Map<String, Object> root,
            BlockListResolver blockLists,
            Map<PlacementRule, Integer> placementAttempts,
            List<ConfigWarning> warnings
    ) {
        Object raw = root.get(KEY_SUMMON_SETTINGS);
        Map<String, Object> settings = Map.of();
        if (raw instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            settings = typed;
            warnUnknownKeys(lootId, settings, SUMMON_SETTINGS_KEYS, warnings);
        } else if (raw != null) {
            warnings.add(ConfigWarning.unusableSummonType(lootId));
        }

        PlacementRule rule = PlacementRule.TOP_SURFACE;
        Optional<String> typeText = textValue(settings.get("summon_type"));
        if (typeText.isPresent()) {
            Optional<PlacementRule> parsed = PlacementRule.fromKey(typeText.get());
            if (parsed.isEmpty()) {
                warnings.add(ConfigWarning.unusableSummonType(lootId));
            } else {
                rule = parsed.get();
            }
        } else if (settings.containsKey("summon_type")) {
            warnings.add(ConfigWarning.unusableSummonType(lootId));
        }

        SummonRules.Builder builder = SummonRules.builder(rule);
        applyRuleOptions(lootId, rule, settings.get(rule.key()), blockLists, builder, warnings);

        builder.heightRange(parseHeightRange(lootId, settings.get("y_range"), warnings));

        List<String> replaceWritten = blockEntriesOrDefault(settings.get("replaceable_blocks"), rule.defaultReplace());
        builder.replaceableBlocks(blockLists.resolve(lootId, replaceWritten));

        List<String> avoidWritten = blockEntriesOrDefault(settings.get("avoid_surfaces"), DEFAULT_AVOID);
        builder.avoidSurfaces(blockLists.resolve(lootId, avoidWritten));

        int attemptsDefault = placementAttempts.getOrDefault(rule, rule.defaultAttempts());
        if (settings.containsKey("attempts")) {
            OptionalInt attempts = strictInt(settings.get("attempts"));
            if (attempts.isEmpty() || attempts.getAsInt() < 1) {
                warnings.add(ConfigWarning.unusableSummonOption(lootId, "attempts"));
                builder.attempts(attemptsDefault);
            } else {
                builder.attempts(attempts.getAsInt());
            }
        } else {
            builder.attempts(attemptsDefault);
        }

        SummonRing playerRing = parsePlayerRing(lootId, settings, warnings);
        builder.playerRing(playerRing);

        boolean verticalEnabled = false;
        if (settings.containsKey("vertical_offset_enable")) {
            Optional<Boolean> flag = strictBoolean(settings.get("vertical_offset_enable"));
            if (flag.isEmpty()) {
                warnings.add(ConfigWarning.unusableSummonOption(lootId, "vertical_offset_enable"));
            } else {
                verticalEnabled = flag.get();
            }
        }
        OptionalInt verticalOffset = OptionalInt.empty();
        if (settings.containsKey("vertical_offset")) {
            OptionalInt parsed = strictInt(settings.get("vertical_offset"));
            if (parsed.isEmpty() || parsed.getAsInt() < 0) {
                warnings.add(ConfigWarning.unusableSummonOption(lootId, "vertical_offset"));
            } else {
                verticalOffset = parsed;
            }
        }
        builder.verticalOffset(verticalEnabled, verticalOffset);
        builder.solidBlocks(placementGroup(blockLists, lootId, "solid", warnings));
        builder.nonSolidBlocks(placementGroup(blockLists, lootId, "non_solid", warnings));
        builder.plantsBlocks(placementGroup(blockLists, lootId, "plants", warnings));
        builder.naturalGroundBlocks(placementGroup(blockLists, lootId, "natural_ground", warnings));

        return builder.build();
    }

    private static Set<String> placementGroup(
            BlockListResolver blockLists,
            String lootId,
            String name,
            List<ConfigWarning> warnings
    ) {
        Set<String> group = blockLists.groups().get(name);
        if (group == null) {
            warnings.add(ConfigWarning.unknownGroup(Optional.of(name), Optional.of(lootId), "@" + name));
            return Set.of();
        }
        return group;
    }

    private static void applyRuleOptions(
            String lootId,
            PlacementRule rule,
            Object rawOptions,
            BlockListResolver blockLists,
            SummonRules.Builder builder,
            List<ConfigWarning> warnings
    ) {
        Map<String, Object> options = Map.of();
        if (rawOptions instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            options = typed;
        } else if (rawOptions != null) {
            warnings.add(ConfigWarning.unusableSummonOption(lootId, rule.key()));
        }

        switch (rule) {
            case TOP_SURFACE -> {
                Optional<String> plants = textValue(options.get("plants"));
                if (plants.isPresent()) {
                    Optional<PlantsMode> mode = PlantsMode.fromKey(plants.get());
                    if (mode.isEmpty()) {
                        warnings.add(ConfigWarning.unusableSummonOption(lootId, "plants"));
                    } else {
                        builder.plantsMode(mode.get());
                    }
                } else if (options.containsKey("plants")) {
                    warnings.add(ConfigWarning.unusableSummonOption(lootId, "plants"));
                }
            }
            case ANY_SURFACE -> {
                List<String> floor = blockEntriesOrDefault(options.get("floor"), DEFAULT_FLOOR_SOLID);
                builder.floorBlocks(blockLists.resolve(lootId, floor));
                if (options.containsKey("headroom")) {
                    OptionalInt headroom = strictInt(options.get("headroom"));
                    if (headroom.isEmpty() || headroom.getAsInt() < 1) {
                        warnings.add(ConfigWarning.unusableSummonOption(lootId, "headroom"));
                    } else {
                        builder.headroom(headroom.getAsInt());
                    }
                }
            }
            case BURIED -> {
                Object depth = options.get("depth");
                if (depth instanceof Map<?, ?> depthMap) {
                    int min = intValue(depthMap.get("min")).orElse(1);
                    int max = intValue(depthMap.get("max")).orElse(3);
                    if (min > max) {
                        warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
                        int swap = min;
                        min = max;
                        max = swap;
                    }
                    if (min < 0 || max < 0) {
                        warnings.add(ConfigWarning.unusableSummonOption(lootId, "depth"));
                    } else {
                        builder.buriedDepth(min, max);
                    }
                } else if (depth != null) {
                    warnings.add(ConfigWarning.unusableSummonOption(lootId, "depth"));
                }
            }
            case DEEP_BURIED -> {
                if (options.containsKey("enclosed")) {
                    Optional<Boolean> enclosed = strictBoolean(options.get("enclosed"));
                    if (enclosed.isEmpty()) {
                        warnings.add(ConfigWarning.unusableSummonOption(lootId, "enclosed"));
                    } else {
                        builder.deepBuriedEnclosed(enclosed.get());
                    }
                }
            }
            case UNDERWATER -> {
                Optional<String> position = textValue(options.get("position"));
                if (position.isPresent()) {
                    Optional<UnderwaterPosition> parsed = UnderwaterPosition.fromKey(position.get());
                    if (parsed.isEmpty()) {
                        warnings.add(ConfigWarning.unusableSummonOption(lootId, "position"));
                    } else {
                        builder.underwaterPosition(parsed.get());
                    }
                } else if (options.containsKey("position")) {
                    warnings.add(ConfigWarning.unusableSummonOption(lootId, "position"));
                }
                if (options.containsKey("cave_water")) {
                    Optional<Boolean> cave = strictBoolean(options.get("cave_water"));
                    if (cave.isEmpty()) {
                        warnings.add(ConfigWarning.unusableSummonOption(lootId, "cave_water"));
                    } else {
                        builder.caveWater(cave.get());
                    }
                }
                if (options.containsKey("min_depth")) {
                    OptionalInt depth = strictInt(options.get("min_depth"));
                    if (depth.isEmpty() || depth.getAsInt() < 1) {
                        warnings.add(ConfigWarning.unusableSummonOption(lootId, "min_depth"));
                    } else {
                        builder.minWaterDepth(depth.getAsInt());
                    }
                }
            }
            case ANYWHERE -> {
                // no rule-specific options
            }
        }
    }

    private static HeightRange parseHeightRange(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return new HeightRange(-64, 319);
        }
        Optional<String> text = textValue(raw);
        if (text.isEmpty()) {
            warnings.add(ConfigWarning.unusableSummonOption(lootId, "y_range"));
            return new HeightRange(-64, 319);
        }
        String[] parts = text.get().split(":");
        if (parts.length != 2) {
            warnings.add(ConfigWarning.unusableSummonOption(lootId, "y_range"));
            return new HeightRange(-64, 319);
        }
        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            if (min > max) {
                warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
                return new HeightRange(max, min);
            }
            return new HeightRange(min, max);
        } catch (NumberFormatException ex) {
            warnings.add(ConfigWarning.unusableSummonOption(lootId, "y_range"));
            return new HeightRange(-64, 319);
        }
    }

    private static SummonRing parsePlayerRing(
            String lootId,
            Map<String, Object> settings,
            List<ConfigWarning> warnings
    ) {
        int min = 0;
        int max = 16;
        Object ring = settings.get("player_ring");
        if (ring instanceof Map<?, ?> map) {
            min = intValue(map.get("min")).orElse(0);
            max = intValue(map.get("max")).orElse(16);
        } else if (settings.containsKey("horizontal_offset")) {
            warnings.add(ConfigWarning.oldKey("horizontal_offset"));
            OptionalInt offset = strictInt(settings.get("horizontal_offset"));
            if (offset.isEmpty() || offset.getAsInt() < 0) {
                warnings.add(ConfigWarning.unusableSummonOption(lootId, "horizontal_offset"));
            } else {
                max = offset.getAsInt();
            }
        }
        if (settings.containsKey("horizontal_offset") && ring instanceof Map<?, ?>) {
            warnings.add(ConfigWarning.oldKey("horizontal_offset"));
        }
        if (min < 0 || max < 0) {
            warnings.add(ConfigWarning.unusableSummonOption(lootId, "player_ring"));
            return new SummonRing(0, 16);
        }
        if (min > max) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
            return new SummonRing(max, min);
        }
        return new SummonRing(min, max);
    }

    public static Map<PlacementRule, Integer> parsePlacementAttempts(
            Map<String, Object> root,
            List<ConfigWarning> warnings
    ) {
        Map<PlacementRule, Integer> defaults = defaultPlacementAttempts();
        if (root.containsKey(KEY_OLD_PLAYER_ATTEMPTS)) {
            warnings.add(ConfigWarning.oldKey(KEY_OLD_PLAYER_ATTEMPTS));
        }
        if (root.containsKey(KEY_OLD_SERVER_ATTEMPTS)) {
            warnings.add(ConfigWarning.oldKey(KEY_OLD_SERVER_ATTEMPTS));
        }
        Object raw = root.get(KEY_PLACEMENT_ATTEMPTS);
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults;
        }
        Map<PlacementRule, Integer> out = new LinkedHashMap<>(defaults);
        for (PlacementRule rule : PlacementRule.values()) {
            if (!map.containsKey(rule.key())) {
                continue;
            }
            OptionalInt parsed = strictInt(map.get(rule.key()));
            if (parsed.isEmpty() || parsed.getAsInt() < 1) {
                warnings.add(new ConfigWarning(
                        Severity.HIGH,
                        ConfigWarning.Kind.UNUSABLE_SUMMON_OPTION,
                        Optional.empty(),
                        Optional.of(rule.key())
                ));
            } else {
                out.put(rule, parsed.getAsInt());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public static Map<PlacementRule, Integer> defaultPlacementAttempts() {
        Map<PlacementRule, Integer> out = new LinkedHashMap<>();
        for (PlacementRule rule : PlacementRule.values()) {
            out.put(rule, rule.defaultAttempts());
        }
        return out;
    }
}
