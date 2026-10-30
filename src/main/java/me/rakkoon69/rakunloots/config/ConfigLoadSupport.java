package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.hologram.HologramProviderChoice;
import me.rakkoon69.rakunloots.hologram.HologramSettings;
import me.rakkoon69.rakunloots.visuals.VisualsSettings;
import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.items.VanillaAdditions;
import me.rakkoon69.rakunloots.summon.PlacementRule;
import me.rakkoon69.rakunloots.summon.SummonRules;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.WarningLevel;
import me.rakkoon69.rakunloots.warning.WarningKind;
import me.rakkoon69.rakunloots.warning.WarningSettings;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static me.rakkoon69.rakunloots.config.ConfigValues.blockEntries;
import static me.rakkoon69.rakunloots.config.ConfigValues.parseRoot;
import static me.rakkoon69.rakunloots.config.ConfigValues.quoteLine;
import static me.rakkoon69.rakunloots.config.ConfigValues.strictBoolean;
import static me.rakkoon69.rakunloots.config.ConfigValues.strictInt;
import static me.rakkoon69.rakunloots.config.ConfigValues.stringValue;
import static me.rakkoon69.rakunloots.config.ConfigValues.textValue;
import static me.rakkoon69.rakunloots.config.ConfigValues.warnUnknownKeys;
import static me.rakkoon69.rakunloots.config.ConfigValues.yaml;
import static me.rakkoon69.rakunloots.config.ConfigValues.intValue;

final class ConfigLoadSupport {

    private static final String FALLBACK_LANGUAGE = "en";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_WARNING_LEVEL = "warning-level";
    private static final String KEY_DEBUG = "debug";
    private static final String KEY_MESSAGES = "messages";
    private static final String KEY_STORAGE = "storage";
    private static final String KEY_STORAGE_TYPE = "type";
    private static final String KEY_STORAGE_HOST = "host";
    private static final String KEY_STORAGE_PORT = "port";
    private static final String KEY_STORAGE_DATABASE = "database";
    private static final String KEY_STORAGE_USER = "user";
    private static final String KEY_STORAGE_PASSWORD = "password";
    private static final String KEY_STORAGE_TABLE_PREFIX = "table_prefix";
    private static final String KEY_STORAGE_BACKUPS_KEPT = "backups_kept";
    private static final String KEY_CLAIM_COMMAND = "claim_command";
    private static final String KEY_ADMIN_COMMAND = "admin_command";
    private static final String KEY_WIKI_COMMAND = "wiki_command";
    private static final String KEY_COMMAND_NAME = "name";
    private static final String KEY_COMMAND_ALIASES = "aliases";
    private static final String KEY_PERMISSION_DEFAULT = "permission_default";
    private static final String KEY_BANNED_LOOT_BLOCKS = "banned_loot_blocks";
    private static final List<String> DEFAULT_BANNED_LOOT_BLOCKS = List.of("@banned_loot_blocks");
    private static final int DEFAULT_LIFE_SECONDS = 360;
    private static final String KEY_YAML_QUOTE_HINT = "yaml_quote_hint";

    private static final Set<String> LOOT_ROOT_KEYS = Set.of(
            "language", "messages", "open_mode", "lock", "life", "summon_delay", "message_owner_on_remove_enable",
            "loot_end", "display_name", "description", "block", "head_data", "roll", "summon_item", "summon_settings",
            "hologram", "glow", "laser", "animations", "wiki"
    );
    private static final Set<String> LOOT_END_KEYS = Set.of(
            "allow_explosion", "drop_block", "timeout_spill", "empty_removal"
    );
    private static final Set<String> ROLL_KEYS = Set.of("min", "max", "entries");
    private static final Set<String> ENTRY_KEYS = Set.of("weight", "amount", "slot", "item");
    private static final Set<String> ITEM_KEYS = Set.of(
            "source", "id", "name", "lore", "enchants", "flags",
            "custom_model_data", "unbreakable", "damage", "nbt"
    );
    private static final Set<String> WIKI_KEYS = Set.of("show", "icon");

    private ConfigLoadSupport() {
    }

    static ConfigSnapshot load(
            ConfigSources sources, NameCatalog nameCatalog, Supplier<List<String>> knownBlockLines,
            Collection<? extends WarningKind> knownKinds) {
        Objects.requireNonNull(nameCatalog, "nameCatalog");
        Objects.requireNonNull(knownKinds, "knownKinds");
        try {
            return loadFiles(sources, nameCatalog, knownBlockLines, knownKinds);
        } catch (ConfigParseException error) {
            throw error.withHint(quoteHint(sources));
        }
    }

    /**
     * The {@code yaml_quote_hint} text for an error that stops the load: from the active language when its file can be
     * read and has the key, else from the English file, else from the English file in the jar.
     */
    private static String quoteHint(ConfigSources sources) {
        String active = FALLBACK_LANGUAGE;
        if (sources.globals().isPresent()) {
            try {
                active = textValue(parseRoot(sources.globals().get()).get(KEY_LANGUAGE)).orElse(FALLBACK_LANGUAGE);
            } catch (YAMLException unreadable) {
                // The globals are the broken file, so English is the language.
            }
        }
        for (String language : List.of(active, FALLBACK_LANGUAGE)) {
            String content = sources.languages().get(language);
            if (content != null) {
                try {
                    Object text = parseRoot(content).get(KEY_YAML_QUOTE_HINT);
                    if (text instanceof String hint) {
                        return hint;
                    }
                } catch (YAMLException unreadable) {
                    // Try the next language.
                }
            }
        }
        try (java.io.InputStream in = ConfigLoadSupport.class.getResourceAsStream("/languages/en.yml")) {
            if (in != null) {
                Object text = parseRoot(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                        .get(KEY_YAML_QUOTE_HINT);
                if (text instanceof String hint) {
                    return hint;
                }
            }
        } catch (java.io.IOException | YAMLException unreadable) {
            // No hint is better than a second failure.
        }
        return "";
    }

    private static ConfigSnapshot loadFiles(
            ConfigSources sources, NameCatalog nameCatalog, Supplier<List<String>> knownBlockLines,
            Collection<? extends WarningKind> knownKinds) {

        List<ConfigWarning> rawWarnings = new ArrayList<>();
        Globals globals = parseGlobals(sources.globals(), rawWarnings);
        WarningSettings warningSettings = WarningsConfig.parse(sources.warnings().orElse(null), knownKinds, rawWarnings);

        Map<String, Map<String, String>> languages = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sources.languages().entrySet()) {
            Map<String, String> flat = parseFlatStringMap("languages/" + entry.getKey() + ".yml", entry.getValue());
            languages.put(entry.getKey(), flat);
            scanDecimalsInLanguage(entry.getKey(), flat, rawWarnings);
        }

        Map<String, Map<String, String>> lootOverrides = new LinkedHashMap<>();
        Map<String, Optional<String>> lootLanguages = new LinkedHashMap<>();
        Map<String, Loot> loots = new LinkedHashMap<>();
        Map<String, Map<String, Object>> parsedLootRoots = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : sources.loots().entrySet()) {
            String lootId = entry.getKey();
            ParseResult parsed = parseLootDocument(entry.getValue());
            if (parsed.isRejected()) {
                rawWarnings.add(ConfigWarning.yamlParseRejected(lootId, parsed.quoteLine()));
                continue;
            }
            Map<String, Object> root = parsed.root();
            parsedLootRoots.put(lootId, root);
            Map<String, String> overrides = parseLootOverrides(root);
            lootOverrides.put(lootId, overrides);
            scanDecimalsInLoot(lootId, overrides, rawWarnings);
            lootLanguages.put(lootId, parseLootLanguage(root));
        }

        String resolvedLanguage = globals.language;
        if (!languages.containsKey(resolvedLanguage)) {
            rawWarnings.add(ConfigWarning.missingLanguage(resolvedLanguage));
            resolvedLanguage = FALLBACK_LANGUAGE;
        }

        for (Map.Entry<String, Optional<String>> entry : lootLanguages.entrySet()) {
            String lootId = entry.getKey();
            Optional<String> stamp = entry.getValue();
            if (stamp.isEmpty()) {
                rawWarnings.add(ConfigWarning.lootLanguageMissing(lootId, resolvedLanguage));
                continue;
            }
            String lootLanguage = stamp.get();
            if (!lootLanguage.equals(resolvedLanguage)) {
                Severity severity = lootOverrides.getOrDefault(lootId, Collections.emptyMap()).isEmpty()
                        ? Severity.LOW
                        : Severity.HIGH;
                rawWarnings.add(ConfigWarning.lootLanguageClash(severity, lootId, lootLanguage, resolvedLanguage));
            }
        }

        MessageResolver resolver = new DefaultMessageResolver(
                resolvedLanguage,
                languages,
                lootOverrides,
                rawWarnings
        );

        Map<String, String> resolvedNames = resolveNames(sources, parsedLootRoots, nameCatalog, rawWarnings);
        Map<String, WorldSettings> worlds = parseWorlds(sources.worlds(), rawWarnings);
        BlockListResolver blockLists = BlockGroupsConfig.load(sources, nameCatalog, knownBlockLines, resolvedNames, rawWarnings);
        Map<PlacementRule, Integer> placementAttempts = globals.placementAttempts;
        Set<String> bannedLootBlocks = blockLists.resolveGlobal(KEY_BANNED_LOOT_BLOCKS, globals.bannedLootBlocks);

        Map<String, WikiSettings> wikis = new LinkedHashMap<>();
        Map<String, SummonRules> summonRules = new LinkedHashMap<>();
        Map<String, HologramSettings> holograms = new LinkedHashMap<>();
        Map<String, VisualsSettings> visuals = new LinkedHashMap<>();
        Map<String, AnimationDefinition> animationDefinitions =
                AnimationConfig.parseDefinitions(sources.animations().orElse(null), rawWarnings);
        Map<String, AnimationDefinition> animationIds = sources.animations().isPresent() ? animationDefinitions : null;
        Map<String, LootAnimations> lootAnimations = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : parsedLootRoots.entrySet()) {
            String lootId = entry.getKey();
            Optional<Loot> loot = parseLoot(
                    lootId, entry.getValue(), nameCatalog, resolvedNames, bannedLootBlocks, rawWarnings);
            loot.ifPresent(value -> {
                loots.put(lootId, value);
                wikis.put(lootId, parseWiki(
                        lootId, entry.getValue().get("wiki"), nameCatalog, resolvedNames, rawWarnings));
                summonRules.put(
                        lootId,
                        SummonConfig.parseSummonRules(lootId, entry.getValue(), blockLists, placementAttempts, rawWarnings)
                );
                holograms.put(
                        lootId,
                        HologramConfig.parseLootHologram(lootId, entry.getValue(), globals.hologramViewRange, rawWarnings)
                );
                visuals.put(lootId, VisualsConfig.parseLootVisuals(lootId, entry.getValue(), rawWarnings));
                lootAnimations.put(
                        lootId,
                        AnimationConfig.parseLoot(lootId, entry.getValue(), animationIds, rawWarnings)
                );
            });
        }

        blockLists.warnSkippedOptional();
        addQuoteHints(rawWarnings, resolver);
        MenuConfig.Menus menus = MenuConfig.parse(sources, nameCatalog, resolvedNames, rawWarnings);

        Map<String, Optional<String>> immutableLootLanguages =
                Collections.unmodifiableMap(new LinkedHashMap<>(lootLanguages));
        Map<String, Map<String, String>> immutableLootOverrides = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : lootOverrides.entrySet()) {
            immutableLootOverrides.put(
                    entry.getKey(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue()))
            );
        }

        return new ConfigSnapshot(
                resolvedLanguage,
                globals.warningLevel,
                warningSettings,
                globals.debug,
                resolver,
                immutableLootLanguages,
                Collections.unmodifiableMap(immutableLootOverrides),
                Collections.unmodifiableMap(new LinkedHashMap<>(resolvedNames)),
                worlds,
                Collections.unmodifiableMap(loots),
                Collections.unmodifiableMap(wikis),
                Collections.unmodifiableMap(summonRules),
                Collections.unmodifiableMap(holograms),
                Collections.unmodifiableMap(visuals),
                animationDefinitions,
                Collections.unmodifiableMap(lootAnimations),
                globals.hologramViewRange,
                globals.hologramProvider,
                blockLists.groups(),
                globals.storage,
                globals.claimCommand,
                globals.adminCommand,
                globals.wikiCommand,
                menus.claim(),
                menus.wikiList(),
                menus.wikiLoot(),
                rawWarnings
        );
    }

    /**
     * Gives each YAML error on a line that holds {@code {} the {@code yaml_quote_hint} message of the active
     * language, with the line filled in.
     */
    private static void addQuoteHints(List<ConfigWarning> warnings, MessageResolver resolver) {
        if (warnings.stream().noneMatch(warning -> warning.quoteLine().isPresent())) {
            return;
        }
        String text = resolver.resolve("", KEY_YAML_QUOTE_HINT);
        warnings.replaceAll(warning -> warning.quoteLine().isPresent()
                ? warning.withHint(text.replace("{line}", Integer.toString(warning.quoteLine().getAsInt())))
                : warning);
    }

    private static ParseResult parseLootDocument(String content) {
        if (content == null || content.isBlank()) {
            return ParseResult.ok(Collections.emptyMap());
        }
        try {
            Object loaded = yaml().load(content);
            if (loaded == null) {
                return ParseResult.ok(Collections.emptyMap());
            }
            if (loaded instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> root = (Map<String, Object>) map;
                return ParseResult.ok(root);
            }
            return ParseResult.ok(Collections.emptyMap());
        } catch (YAMLException ex) {
            return ParseResult.yamlRejected(quoteLine(content, ex));
        }
    }

    private static Optional<Loot> parseLoot(
            String lootId,
            Map<String, Object> root,
            NameCatalog nameCatalog,
            Map<String, String> resolvedNames,
            Set<String> bannedLootBlocks,
            List<ConfigWarning> warnings
    ) {
        warnUnknownKeys(lootId, root, LOOT_ROOT_KEYS, warnings);

        Optional<OpenMode> openMode = parseOpenMode(lootId, root.get("open_mode"), warnings);
        if (openMode.isEmpty()) {
            return Optional.empty();
        }

        int lockSeconds = parseLock(lootId, root.get("lock"), warnings);
        OptionalInt lifeSeconds = parseLife(lootId, root.get("life"), warnings);
        int summonDelayTicks = parseSummonDelay(lootId, root.get("summon_delay"), warnings);
        boolean messageOwnerOnRemove = parseMessageOwnerOnRemove(lootId, root.get("message_owner_on_remove_enable"), warnings);
        LootEndRules lootEnd = parseLootEnd(lootId, root.get("loot_end"), warnings);
        String displayName = parseDisplayName(lootId, root.get("display_name"), warnings);
        List<String> description = parseDescription(lootId, root.get("description"), warnings);

        Optional<String> block = parseBlock(lootId, root.get("block"), nameCatalog, resolvedNames, warnings);
        if (block.isEmpty()) {
            return Optional.empty();
        }
        if (bannedLootBlocks.contains(block.get())) {
            warnings.add(ConfigWarning.bannedLootBlock(lootId, String.valueOf(root.get("block")).trim(), block.get()));
            return Optional.empty();
        }

        Optional<String> headData = parseHeadData(lootId, block.get(), root.get("head_data"), warnings);

        Optional<Roll> roll = parseRoll(lootId, root.get("roll"), nameCatalog, resolvedNames, warnings);
        if (roll.isEmpty()) {
            return Optional.empty();
        }

        Optional<ItemReference> summonItem = parseSummonItem(lootId, root.get("summon_item"), nameCatalog,
                resolvedNames, warnings);

        return Optional.of(new Loot(
                lootId,
                openMode.get(),
                lockSeconds,
                lifeSeconds,
                lootEnd,
                displayName,
                description,
                block.get(),
                headData,
                roll.get(),
                summonDelayTicks,
                messageOwnerOnRemove,
                summonItem
        ));
    }

    /**
     * The loot's summon item, written like a roll entry's item. One that cannot be used leaves the loot without a
     * summon item and warns high; the loot itself still loads.
     */
    private static Optional<ItemReference> parseSummonItem(
            String lootId,
            Object raw,
            NameCatalog nameCatalog,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        if (raw == null) {
            return Optional.empty();
        }
        List<ConfigWarning> found = new ArrayList<>();
        Optional<ItemReference> item = parseItemReference(lootId, "summon_item", raw, nameCatalog, resolvedNames,
                found);
        if (item.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_SUMMON_ITEM, lootId));
            return Optional.empty();
        }
        warnings.addAll(found);
        return item;
    }

    /**
     * A loot's {@code wiki}: a yes or no, or a map of {@code show} and {@code icon}. A missing value, or one with an
     * unknown key, shows the loot with its block as the icon. A value that cannot be used warns high and falls back
     * the same way.
     */
    private static WikiSettings parseWiki(
            String lootId,
            Object raw,
            NameCatalog nameCatalog,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        if (raw == null) {
            return WikiSettings.shown();
        }
        if (raw instanceof Boolean show) {
            return show ? WikiSettings.shown() : new WikiSettings(false, Optional.empty());
        }
        if (!(raw instanceof Map<?, ?> map)) {
            warnings.add(ConfigWarning.unusableWikiValue(lootId, "wiki"));
            return WikiSettings.shown();
        }

        boolean unknown = false;
        for (Object key : map.keySet()) {
            if (!WIKI_KEYS.contains(String.valueOf(key))) {
                warnings.add(ConfigWarning.unknownKey(lootId, "wiki." + key));
                unknown = true;
            }
        }
        if (unknown) {
            return WikiSettings.shown();
        }

        boolean usable = true;
        boolean show = true;
        Object rawShow = map.get("show");
        if (rawShow != null) {
            Optional<Boolean> parsed = strictBoolean(rawShow);
            if (parsed.isPresent()) {
                show = parsed.get();
            } else {
                warnings.add(ConfigWarning.unusableWikiValue(lootId, "wiki.show"));
                usable = false;
            }
        }

        Optional<ItemReference> icon = Optional.empty();
        Object rawIcon = map.get("icon");
        if (rawIcon != null) {
            if (rawIcon instanceof Map<?, ?>) {
                icon = parseItemReference(lootId, "wiki.icon", rawIcon, nameCatalog, resolvedNames, warnings);
                if (icon.isEmpty()) {
                    usable = false;
                }
            } else {
                warnings.add(ConfigWarning.unusableWikiValue(lootId, "wiki.icon"));
                usable = false;
            }
        }
        if (!usable) {
            return WikiSettings.shown();
        }
        return new WikiSettings(show, icon);
    }

    private static Optional<OpenMode> parseOpenMode(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_OPEN_MODE, lootId));
            return Optional.empty();
        }
        if (!(raw instanceof String text)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_OPEN_MODE, lootId));
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_OPEN_MODE, lootId));
            return Optional.empty();
        }
        if ("drop".equalsIgnoreCase(trimmed)) {
            return Optional.of(OpenMode.DROP);
        }
        if ("inventory".equalsIgnoreCase(trimmed)) {
            return Optional.of(OpenMode.INVENTORY);
        }
        warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_OPEN_MODE, lootId));
        return Optional.empty();
    }

    private static int parseLock(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return 0;
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_LOCK, lootId));
            return 0;
        }
        if (parsed.getAsInt() < 0) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_LOCK, lootId));
            return 0;
        }
        return parsed.getAsInt();
    }

    private static int parseSummonDelay(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return 0;
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty() || parsed.getAsInt() < 0) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_SUMMON_DELAY, lootId));
            return 0;
        }
        return parsed.getAsInt();
    }

    private static boolean parseMessageOwnerOnRemove(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return false;
        }
        Optional<Boolean> bool = strictBoolean(raw);
        if (bool.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_MESSAGE_OWNER_ON_REMOVE, lootId));
            return false;
        }
        return bool.get();
    }

    private static OptionalInt parseLife(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return OptionalInt.of(DEFAULT_LIFE_SECONDS);
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_LIFE, lootId));
            return OptionalInt.of(DEFAULT_LIFE_SECONDS);
        }
        if (parsed.getAsInt() < 0) {
            warnings.add(ConfigWarning.lootLow(ConfigWarning.Kind.NEGATIVE_LIFE, lootId));
            return OptionalInt.empty();
        }
        return parsed;
    }

    private static LootEndRules parseLootEnd(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return LootEndRules.allOff();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_LOOT_END, lootId));
            return LootEndRules.allOff();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> endMap = (Map<String, Object>) map;
        warnUnknownKeys(lootId, endMap, LOOT_END_KEYS, warnings);
        return new LootEndRules(
                parseLootEndFlag(lootId, endMap.get("allow_explosion"), warnings),
                parseLootEndFlag(lootId, endMap.get("drop_block"), warnings),
                parseLootEndFlag(lootId, endMap.get("timeout_spill"), warnings),
                parseLootEndFlag(lootId, endMap.get("empty_removal"), warnings)
        );
    }

    private static boolean parseLootEndFlag(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return false;
        }
        Optional<Boolean> bool = strictBoolean(raw);
        if (bool.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_LOOT_END, lootId));
            return false;
        }
        return bool.get();
    }

    private static String parseDisplayName(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return lootId;
        }
        if (!(raw instanceof String text)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_DISPLAY_NAME, lootId));
            return lootId;
        }
        return text;
    }

    private static List<String> parseDescription(String lootId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_DESCRIPTION, lootId));
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Object line : list) {
            if (line instanceof String text) {
                lines.add(text);
            } else {
                warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_DESCRIPTION, lootId));
            }
        }
        return lines;
    }

    private static Optional<String> parseBlock(
            String lootId,
            Object raw,
            NameCatalog nameCatalog,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        if (!(raw instanceof String text) || text.isBlank()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.MISSING_BLOCK, lootId));
            return Optional.empty();
        }
        String written = text.trim();
        Optional<String> current = nameCatalog.currentId(written);
        if (current.isEmpty()) {
            resolvedNames.putIfAbsent(written, written);
            warnings.add(ConfigWarning.unknownName(written));
            return Optional.empty();
        }
        String resolved = current.get();
        resolvedNames.putIfAbsent(written, resolved);
        if (!resolved.equals(written)) {
            warnings.add(ConfigWarning.oldName(written, resolved));
        }
        return Optional.of(resolved);
    }

    private static Optional<String> parseHeadData(
            String lootId,
            String resolvedBlock,
            Object raw,
            List<ConfigWarning> warnings
    ) {
        // The server catalog gives namespaced ids; a test catalog may not.
        boolean playerHead = "player_head".equalsIgnoreCase(resolvedBlock)
                || "minecraft:player_head".equalsIgnoreCase(resolvedBlock);
        if (raw == null) {
            return Optional.empty();
        }
        if (!(raw instanceof String text)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_HEAD_DATA, lootId));
            return Optional.empty();
        }
        if (!playerHead) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    private static Optional<Roll> parseRoll(
            String lootId,
            Object raw,
            NameCatalog nameCatalog,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        if (!(raw instanceof Map<?, ?>)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_ROLL, lootId));
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rollMap = (Map<String, Object>) raw;
        warnUnknownKeys(lootId, rollMap, ROLL_KEYS, warnings);

        Object entriesRaw = rollMap.get("entries");
        if (!(entriesRaw instanceof Map<?, ?>)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.UNUSABLE_ROLL, lootId));
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> entriesMap = (Map<String, Object>) entriesRaw;

        List<ContentEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : entriesMap.entrySet()) {
            String entryId = String.valueOf(entry.getKey());
            Optional<ContentEntry> parsed = parseContentEntry(
                    lootId, entryId, entry.getValue(), nameCatalog, resolvedNames, warnings);
            parsed.ifPresent(entries::add);
        }

        if (entries.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.NO_CONTENT_ENTRIES, lootId));
            return Optional.empty();
        }

        resolveDuplicateSlots(lootId, entries, warnings);

        int weightedCount = 0;
        for (ContentEntry entry : entries) {
            if (entry.weight() > 0) {
                weightedCount++;
            }
        }

        int min = parseMinMax(lootId, rollMap.get("min"), 0, warnings);
        int max = parseMinMax(lootId, rollMap.get("max"), weightedCount, warnings);
        if (min > max) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
            min = max;
        }
        if (min > weightedCount) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
            min = weightedCount;
        }
        if (max > weightedCount) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
            max = weightedCount;
        }

        return Optional.of(new Roll(min, max, entries));
    }

    private static int parseMinMax(
            String lootId,
            Object raw,
            int omittedDefault,
            List<ConfigWarning> warnings
    ) {
        if (raw == null) {
            return omittedDefault;
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
            return omittedDefault;
        }
        if (parsed.getAsInt() < 0) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.CORRECTED_MIN_MAX, lootId));
            return 0;
        }
        return parsed.getAsInt();
    }

    private static Optional<ContentEntry> parseContentEntry(
            String lootId,
            String entryId,
            Object raw,
            NameCatalog nameCatalog,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        if (!(raw instanceof Map<?, ?>)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, lootId, entryId));
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> entryMap = (Map<String, Object>) raw;
        warnUnknownKeys(lootId, entryMap, ENTRY_KEYS, warnings);

        Optional<ItemReference> item = parseItemReference(
                lootId, entryId, entryMap.get("item"), nameCatalog, resolvedNames, warnings);
        if (item.isEmpty()) {
            return Optional.empty();
        }

        OptionalInt amountOpt = parseAmount(lootId, entryId, entryMap.get("amount"), warnings);
        if (amountOpt.isEmpty()) {
            return Optional.empty();
        }
        OptionalInt weightOpt = parseWeight(lootId, entryId, entryMap.get("weight"), warnings);
        if (weightOpt.isEmpty()) {
            return Optional.empty();
        }

        OptionalInt slot = parseSlot(lootId, entryId, entryMap.get("slot"), warnings);
        return Optional.of(new ContentEntry(entryId, item.get(), amountOpt.getAsInt(), weightOpt.getAsInt(), slot));
    }

    private static OptionalInt parseAmount(String lootId, String entryId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return OptionalInt.of(1);
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty() || parsed.getAsInt() < 1) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, lootId, entryId));
            return OptionalInt.empty();
        }
        return parsed;
    }

    private static OptionalInt parseWeight(String lootId, String entryId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return OptionalInt.of(1);
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, lootId, entryId));
            return OptionalInt.empty();
        }
        if (parsed.getAsInt() == 0) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, lootId, entryId));
            return OptionalInt.empty();
        }
        return parsed;
    }

    private static OptionalInt parseSlot(String lootId, String entryId, Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty() || parsed.getAsInt() < 0) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.REJECTED_SLOT, lootId, entryId));
            return OptionalInt.empty();
        }
        return parsed;
    }

    private static void resolveDuplicateSlots(String lootId, List<ContentEntry> entries, List<ConfigWarning> warnings) {
        Set<Integer> seen = new LinkedHashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            ContentEntry entry = entries.get(i);
            if (entry.slot().isEmpty()) {
                continue;
            }
            int slot = entry.slot().getAsInt();
            if (!seen.add(slot)) {
                warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.REJECTED_SLOT, lootId, entry.id()));
                entries.set(i, new ContentEntry(
                        entry.id(), entry.item(), entry.amount(), entry.weight(), OptionalInt.empty()));
            }
        }
    }

    static Optional<ItemReference> parseItemReference(
            String lootId,
            String entryId,
            Object raw,
            NameCatalog nameCatalog,
            Map<String, String> resolvedNames,
            List<ConfigWarning> warnings
    ) {
        if (!(raw instanceof Map<?, ?>)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, lootId, entryId));
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> itemMap = (Map<String, Object>) raw;
        warnUnknownKeys(lootId, itemMap, ITEM_KEYS, warnings);

        Object sourceRaw = itemMap.get("source");
        String source;
        if (sourceRaw == null) {
            source = "vanilla";
        } else if (!(sourceRaw instanceof String sourceText)) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, lootId, entryId));
            return Optional.empty();
        } else {
            String trimmed = sourceText.trim();
            source = trimmed.isEmpty() ? "vanilla" : trimmed.toLowerCase(Locale.ROOT);
        }

        Object idRaw = itemMap.get("id");
        if (!(idRaw instanceof String idText) || idText.trim().isEmpty()) {
            warnings.add(ConfigWarning.lootHigh(ConfigWarning.Kind.SKIPPED_CONTENT_ENTRY, lootId, entryId));
            return Optional.empty();
        }
        String id = idText.trim();

        if ("vanilla".equals(source)) {
            Optional<String> current = nameCatalog.currentId(id);
            if (current.isEmpty()) {
                resolvedNames.putIfAbsent(id, id);
                warnings.add(ConfigWarning.unknownName(id));
            } else {
                String resolved = current.get();
                resolvedNames.putIfAbsent(id, resolved);
                if (!resolved.equals(id)) {
                    warnings.add(ConfigWarning.oldName(id, resolved));
                }
                id = resolved;
            }
        }

        VanillaAdditions additions = parseVanillaAdditions(itemMap);
        return Optional.of(ItemReference.of(source, id, additions));
    }

    private static VanillaAdditions parseVanillaAdditions(Map<String, Object> itemMap) {
        VanillaAdditions.Builder builder = VanillaAdditions.builder();
        boolean any = false;

        if (itemMap.containsKey("name")) {
            Object name = itemMap.get("name");
            builder.name(name instanceof String s ? s : String.valueOf(name));
            any = true;
        }
        if (itemMap.containsKey("lore")) {
            builder.lore(toStringListKeepingBad(itemMap.get("lore")));
            any = true;
        }
        if (itemMap.containsKey("enchants")) {
            builder.enchants(toStringListKeepingBad(itemMap.get("enchants")));
            any = true;
        }
        if (itemMap.containsKey("flags")) {
            builder.flags(toStringListKeepingBad(itemMap.get("flags")));
            any = true;
        }
        if (itemMap.containsKey("custom_model_data")) {
            builder.customModelData(itemMap.get("custom_model_data"));
            any = true;
        }
        if (itemMap.containsKey("unbreakable")) {
            builder.unbreakable(itemMap.get("unbreakable"));
            any = true;
        }
        if (itemMap.containsKey("damage")) {
            builder.damage(itemMap.get("damage"));
            any = true;
        }
        if (itemMap.containsKey("nbt")) {
            Object nbt = itemMap.get("nbt");
            if (nbt instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nbtMap = (Map<String, Object>) map;
                builder.nbt(nbtMap);
            } else {
                builder.nbt(Map.of());
            }
            any = true;
        }
        return any ? builder.build() : VanillaAdditions.none();
    }

    private static List<String> toStringListKeepingBad(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of(raw == null ? "" : String.valueOf(raw));
        }
        List<String> out = new ArrayList<>();
        for (Object entry : list) {
            out.add(entry == null ? "" : String.valueOf(entry));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, WorldSettings> parseWorlds(Optional<String> content, List<ConfigWarning> warnings) {
        if (content.isEmpty()) {
            warnings.add(ConfigWarning.missingWorlds());
            return Map.of();
        }

        Map<String, Object> root = parseRoot("worlds.yml", content.get());
        Object worldsNode = root.get("worlds");
        if (!(worldsNode instanceof Map<?, ?> worldsMap)) {
            return Map.of();
        }

        Map<String, WorldSettings> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : worldsMap.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> worldMap)) {
                continue;
            }
            String worldName = String.valueOf(entry.getKey());
            out.put(worldName, parseWorldSettings(worldName, (Map<String, Object>) worldMap, warnings));
        }
        return Collections.unmodifiableMap(out);
    }

    private static WorldSettings parseWorldSettings(
            String worldName,
            Map<String, Object> worldMap,
            List<ConfigWarning> warnings
    ) {
        int timeBetween = intValue(worldMap.get("time_between_loot_spawn"))
                .orElse(WorldSettings.DEFAULT_TIME_BETWEEN_LOOT_SPAWN);
        int spawnLimit = intValue(worldMap.get("loot_spawn_limit"))
                .orElse(WorldSettings.DEFAULT_LOOT_SPAWN_LIMIT);

        Map<String, Integer> loots = new LinkedHashMap<>();
        Object lootsNode = worldMap.get("loots");
        if (lootsNode instanceof Map<?, ?> lootsMap) {
            for (Map.Entry<?, ?> lootEntry : lootsMap.entrySet()) {
                if (lootEntry.getKey() == null) {
                    continue;
                }
                String lootId = String.valueOf(lootEntry.getKey());
                intValue(lootEntry.getValue()).ifPresent(chance -> loots.put(lootId, chance));
            }
        }

        int minimumPlayers = worldCount(worldName, "minimum_player_to_spawn_loot",
                worldMap.get("minimum_player_to_spawn_loot"), WorldSettings.DEFAULT_MINIMUM_PLAYER_TO_SPAWN_LOOT,
                warnings);
        List<String> alsoCount = new ArrayList<>();
        Object counted = worldMap.get("also_count_players_from");
        if (counted instanceof List<?> names) {
            for (Object name : names) {
                if (name instanceof String || name instanceof Number || name instanceof Boolean) {
                    stringValue(name).ifPresent(alsoCount::add);
                } else {
                    warnings.add(ConfigWarning.unusableWorldValue(worldName, "also_count_players_from"));
                }
            }
        } else if (counted != null) {
            warnings.add(ConfigWarning.unusableWorldValue(worldName, "also_count_players_from"));
        }
        int horizontalOffset = worldCount(worldName, "public_location_horizontal_offset",
                worldMap.get("public_location_horizontal_offset"), WorldSettings.DEFAULT_PUBLIC_LOCATION_OFFSET,
                warnings);
        int verticalOffset = worldCount(worldName, "public_location_vertical_offset",
                worldMap.get("public_location_vertical_offset"), WorldSettings.DEFAULT_PUBLIC_LOCATION_OFFSET,
                warnings);
        boolean broadcastRemove = false;
        Object rawBroadcast = worldMap.get("broadcast_remove");
        if (rawBroadcast != null) {
            Optional<Boolean> parsed = strictBoolean(rawBroadcast);
            if (parsed.isEmpty()) {
                warnings.add(ConfigWarning.unusableWorldValue(worldName, "broadcast_remove"));
            } else {
                broadcastRemove = parsed.get();
            }
        }

        SummonSearchSettings summonSearch = SummonConfig.parseSummonSearch(worldName, worldMap, warnings);
        return new WorldSettings(worldName, timeBetween, spawnLimit, loots, summonSearch, minimumPlayers, alsoCount,
                horizontalOffset, verticalOffset, broadcastRemove);
    }

    /**
     * A whole number of zero or more in a world's settings. A missing key is the default quietly; a bad value warns
     * high and is the default.
     */
    private static int worldCount(
            String worldName,
            String key,
            Object raw,
            int fallback,
            List<ConfigWarning> warnings
    ) {
        if (raw == null) {
            return fallback;
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty() || parsed.getAsInt() < 0) {
            warnings.add(ConfigWarning.unusableWorldValue(worldName, key));
            return fallback;
        }
        return parsed.getAsInt();
    }

    private static Map<String, String> resolveNames(
            ConfigSources sources,
            Map<String, Map<String, Object>> parsedLootRoots,
            NameCatalog nameCatalog,
            List<ConfigWarning> warnings
    ) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String written : collectWrittenNames(sources, parsedLootRoots)) {
            if (resolved.containsKey(written)) {
                continue;
            }
            Optional<String> current = nameCatalog.currentId(written);
            if (current.isEmpty()) {
                resolved.put(written, written);
                warnings.add(ConfigWarning.unknownName(written));
            } else if (current.get().equals(written)) {
                resolved.put(written, written);
            } else {
                resolved.put(written, current.get());
                warnings.add(ConfigWarning.oldName(written, current.get()));
            }
        }
        return resolved;
    }

    private static Set<String> collectWrittenNames(
            ConfigSources sources,
            Map<String, Map<String, Object>> parsedLootRoots
    ) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> root : parsedLootRoots.values()) {
            collectNamesFromLoot(root, names);
        }
        return names;
    }

    private static void collectNamesFromLoot(Map<String, Object> root, Set<String> names) {
        Object blockSettings = root.get("loot_block_settings");
        if (blockSettings instanceof Map<?, ?> map) {
            stringValue(map.get("material")).ifPresent(names::add);
        }

        Object summonSettings = root.get("summon_settings");
        if (summonSettings instanceof Map<?, ?> map) {
            addStringList(map.get("replaceable_blocks"), names);
            addStringList(map.get("avoid_surfaces"), names);
        }

        Object contentSettings = root.get("loot_content_settings");
        if (contentSettings instanceof Map<?, ?> map) {
            Object lootContent = map.get("loot_content");
            if (lootContent instanceof Map<?, ?> contents) {
                for (Object entry : contents.values()) {
                    if (entry instanceof Map<?, ?> itemEntry) {
                        Object item = itemEntry.get("item");
                        if (item instanceof Map<?, ?> itemMap) {
                            stringValue(itemMap.get("type")).ifPresent(type -> addItemTypeId(type, names));
                        }
                    }
                }
            }
        }

        Object summonItem = root.get("summon_loot_item");
        if (summonItem instanceof Map<?, ?> map) {
            stringValue(map.get("type")).ifPresent(type -> addItemTypeId(type, names));
        }
    }

    private static void addStringList(Object value, Set<String> names) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object entry : list) {
            stringValue(entry).ifPresent(name -> {
                String trimmed = name.trim();
                if (trimmed.startsWith("@") || trimmed.startsWith("#")) {
                    return;
                }
                names.add(trimmed);
            });
        }
    }

    private static void addItemTypeId(String type, Set<String> names) {
        String trimmed = type.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return;
        }
        String source = trimmed.substring(0, colon);
        String id = trimmed.substring(colon + 1);
        if ("Vanilla".equalsIgnoreCase(source) && !id.isBlank()) {
            names.add(id);
        }
    }

    private static Optional<String> parseLootLanguage(Map<String, Object> root) {
        return textValue(root.get(KEY_LANGUAGE));
    }

    private static Globals parseGlobals(Optional<String> content, List<ConfigWarning> warnings) {
        if (content.isEmpty()) {
            warnings.add(ConfigWarning.missingGlobals());
            return Globals.defaults();
        }

        Map<String, Object> root = parseRoot("config.yml", content.get());
        String language = textValue(root.get(KEY_LANGUAGE)).orElse(FALLBACK_LANGUAGE);

        WarningLevel warningLevel = WarningLevel.LOW;
        Optional<String> warningLevelRaw = textValue(root.get(KEY_WARNING_LEVEL));
        if (warningLevelRaw.isPresent()) {
            Optional<WarningLevel> parsed = parseWarningLevel(warningLevelRaw.get());
            if (parsed.isPresent()) {
                warningLevel = parsed.get();
            } else {
                warnings.add(ConfigWarning.unknownWarningLevel());
            }
        }

        boolean debug = false;
        if (root.containsKey(KEY_DEBUG)) {
            Object raw = root.get(KEY_DEBUG);
            if (raw instanceof Boolean bool) {
                debug = bool;
            } else {
                Optional<String> asString = textValue(raw);
                if (asString.isPresent()
                        && ("true".equalsIgnoreCase(asString.get()) || "false".equalsIgnoreCase(asString.get()))) {
                    debug = Boolean.parseBoolean(asString.get());
                } else {
                    warnings.add(ConfigWarning.unusableDebug());
                }
            }
        }

        StorageSettings storage = parseStorage(root.get(KEY_STORAGE), warnings);
        ClaimCommandSettings claimCommand = parseClaimCommand(root.get(KEY_CLAIM_COMMAND), warnings);
        AdminCommandSettings adminCommand = parseAdminCommand(root.get(KEY_ADMIN_COMMAND), warnings);
        WikiCommandSettings wikiCommand = parseWikiCommand(root.get(KEY_WIKI_COMMAND), warnings);
        Map<PlacementRule, Integer> placementAttempts = SummonConfig.parsePlacementAttempts(root, warnings);
        List<String> bannedLootBlocks = parseBannedLootBlocks(root, warnings);
        int hologramViewRange = HologramConfig.parseGlobalViewRange(root, warnings);
        HologramProviderChoice hologramProvider = HologramConfig.parseProvider(root, warnings);

        return new Globals(language, warningLevel, debug, storage, claimCommand, adminCommand, wikiCommand,
                placementAttempts, bannedLootBlocks, hologramViewRange, hologramProvider);
    }

    /**
     * The written entries. A missing key uses the shipped group silently. A value that is not a list warns high and
     * uses the shipped group. An empty list bans nothing.
     */
    private static List<String> parseBannedLootBlocks(Map<String, Object> root, List<ConfigWarning> warnings) {
        Object raw = root.get(KEY_BANNED_LOOT_BLOCKS);
        if (raw == null) {
            return DEFAULT_BANNED_LOOT_BLOCKS;
        }
        if (!(raw instanceof List<?>)) {
            warnings.add(ConfigWarning.unusableBannedLootBlocks(KEY_BANNED_LOOT_BLOCKS));
            return DEFAULT_BANNED_LOOT_BLOCKS;
        }
        return blockEntries(raw);
    }

    /**
     * A missing value uses its default silently. A value that cannot be used warns high and uses its default.
     */
    private static StorageSettings parseStorage(Object raw, List<ConfigWarning> warnings) {
        if (raw == null) {
            return StorageSettings.defaults();
        }
        if (!(raw instanceof Map<?, ?> storage)) {
            warnings.add(ConfigWarning.unusableStorageValue(KEY_STORAGE));
            return StorageSettings.defaults();
        }

        StorageType type = StorageSettings.DEFAULT_TYPE;
        Object rawType = storage.get(KEY_STORAGE_TYPE);
        if (rawType != null) {
            Optional<StorageType> parsed = textValue(rawType).flatMap(ConfigLoadSupport::parseStorageType);
            if (parsed.isPresent()) {
                type = parsed.get();
            } else {
                warnings.add(ConfigWarning.unknownStorageType(KEY_STORAGE_TYPE));
            }
        }

        String host = storageText(storage, KEY_STORAGE_HOST, StorageSettings.DEFAULT_HOST, false, warnings);
        int port = storageInt(storage, KEY_STORAGE_PORT, StorageSettings.DEFAULT_PORT, 1, 65535, warnings);
        String database = storageText(storage, KEY_STORAGE_DATABASE, StorageSettings.DEFAULT_DATABASE, false, warnings);
        String user = storageText(storage, KEY_STORAGE_USER, StorageSettings.DEFAULT_USER, false, warnings);
        String password = storageText(storage, KEY_STORAGE_PASSWORD, StorageSettings.DEFAULT_PASSWORD, true, warnings);

        String tablePrefix = StorageSettings.DEFAULT_TABLE_PREFIX;
        Object rawPrefix = storage.get(KEY_STORAGE_TABLE_PREFIX);
        if (rawPrefix != null) {
            Optional<String> parsed = textValue(rawPrefix).filter(StorageSettings::isUsableTablePrefix);
            if (parsed.isPresent()) {
                tablePrefix = parsed.get();
            } else {
                warnings.add(ConfigWarning.unusableStorageValue(KEY_STORAGE_TABLE_PREFIX));
            }
        }

        int backupsKept = storageInt(
                storage, KEY_STORAGE_BACKUPS_KEPT, StorageSettings.DEFAULT_BACKUPS_KEPT, 0, Integer.MAX_VALUE, warnings);

        return new StorageSettings(type, host, port, database, user, password, tablePrefix, backupsKept);
    }

    /**
     * The name and aliases of a command block, {@code claim_command} or {@code admin_command}. A missing block or
     * key uses its default silently. A name or alias that cannot name a command warns high; a bad name uses the
     * default name, a bad alias is left out, and aliases that are not a list use the default aliases. An alias that
     * repeats the name or an earlier alias is dropped without a warning.
     */
    private static CommandWords parseCommandWords(Object raw, String blockKey, String defaultName,
                                                  List<String> defaultAliases, List<ConfigWarning> warnings,
                                                  Function<String, ConfigWarning> unusable) {
        if (raw == null) {
            return new CommandWords(defaultName, defaultAliases);
        }
        if (!(raw instanceof Map<?, ?> block)) {
            warnings.add(unusable.apply(blockKey));
            return new CommandWords(defaultName, defaultAliases);
        }

        String name = defaultName;
        Object rawName = block.get(KEY_COMMAND_NAME);
        if (rawName != null) {
            Optional<String> parsed = textValue(rawName).map(String::trim).filter(ClaimCommandSettings::isUsableName);
            if (parsed.isPresent()) {
                name = parsed.get().toLowerCase(Locale.ROOT);
            } else {
                warnings.add(unusable.apply(KEY_COMMAND_NAME));
            }
        }

        List<String> aliases = defaultAliases;
        Object rawAliases = block.get(KEY_COMMAND_ALIASES);
        if (rawAliases != null) {
            if (rawAliases instanceof List<?> list) {
                aliases = new ArrayList<>();
                boolean unusableAlias = false;
                for (Object entry : list) {
                    Optional<String> parsed = textValue(entry).map(String::trim)
                            .filter(ClaimCommandSettings::isUsableName).map(text -> text.toLowerCase(Locale.ROOT));
                    if (parsed.isEmpty()) {
                        unusableAlias = true;
                    } else if (!parsed.get().equals(name) && !aliases.contains(parsed.get())) {
                        aliases.add(parsed.get());
                    }
                }
                if (unusableAlias) {
                    warnings.add(unusable.apply(KEY_COMMAND_ALIASES));
                }
            } else {
                warnings.add(unusable.apply(KEY_COMMAND_ALIASES));
            }
        }
        return new CommandWords(name, aliases);
    }

    private static ClaimCommandSettings parseClaimCommand(Object raw, List<ConfigWarning> warnings) {
        CommandWords words = parseCommandWords(raw, KEY_CLAIM_COMMAND, ClaimCommandSettings.DEFAULT_NAME,
                ClaimCommandSettings.DEFAULT_ALIASES, warnings, ConfigWarning::unusableClaimCommandValue);
        return new ClaimCommandSettings(words.name(), words.aliases(), parsePermissionDefault(
                raw, ClaimCommandSettings.DEFAULT_PERMISSION, ConfigWarning::unusableClaimCommandValue, warnings));
    }

    private static WikiCommandSettings parseWikiCommand(Object raw, List<ConfigWarning> warnings) {
        CommandWords words = parseCommandWords(raw, KEY_WIKI_COMMAND, WikiCommandSettings.DEFAULT_NAME,
                WikiCommandSettings.DEFAULT_ALIASES, warnings, ConfigWarning::unusableWikiCommandValue);
        return new WikiCommandSettings(words.name(), words.aliases(), parsePermissionDefault(
                raw, WikiCommandSettings.DEFAULT_PERMISSION, ConfigWarning::unusableWikiCommandValue, warnings));
    }

    /**
     * A command block's {@code permission_default}. A missing key uses the fallback silently; a value that is not
     * {@code true}, {@code false}, {@code op}, or {@code not op} warns high and uses the fallback.
     */
    private static DefaultAccess parsePermissionDefault(
            Object raw,
            DefaultAccess fallback,
            Function<String, ConfigWarning> unusable,
            List<ConfigWarning> warnings
    ) {
        if (!(raw instanceof Map<?, ?> block) || block.get(KEY_PERMISSION_DEFAULT) == null) {
            return fallback;
        }
        Optional<DefaultAccess> parsed = DefaultAccess.parse(block.get(KEY_PERMISSION_DEFAULT));
        if (parsed.isEmpty()) {
            warnings.add(unusable.apply(KEY_PERMISSION_DEFAULT));
        }
        return parsed.orElse(fallback);
    }

    private static AdminCommandSettings parseAdminCommand(Object raw, List<ConfigWarning> warnings) {
        CommandWords words = parseCommandWords(raw, KEY_ADMIN_COMMAND, AdminCommandSettings.DEFAULT_NAME,
                AdminCommandSettings.DEFAULT_ALIASES, warnings, ConfigWarning::unusableAdminCommandValue);
        return new AdminCommandSettings(words.name(), words.aliases());
    }

    private record CommandWords(String name, List<String> aliases) {
    }

    private static Optional<StorageType> parseStorageType(String raw) {
        String trimmed = raw.trim();
        if ("sqlite".equalsIgnoreCase(trimmed)) {
            return Optional.of(StorageType.SQLITE);
        }
        if ("mysql".equalsIgnoreCase(trimmed)) {
            return Optional.of(StorageType.MYSQL);
        }
        return Optional.empty();
    }

    /**
     * Text only. An unquoted number is refused, since YAML may change it (a leading zero reads as octal).
     */
    private static String storageText(
            Map<?, ?> storage,
            String key,
            String fallback,
            boolean blankAllowed,
            List<ConfigWarning> warnings
    ) {
        Object raw = storage.get(key);
        if (raw == null) {
            return fallback;
        }
        Optional<String> text = textValue(raw).filter(value -> blankAllowed || !value.isBlank());
        if (text.isEmpty()) {
            warnings.add(ConfigWarning.unusableStorageValue(key));
            return fallback;
        }
        return text.get();
    }

    private static int storageInt(
            Map<?, ?> storage,
            String key,
            int fallback,
            int min,
            int max,
            List<ConfigWarning> warnings
    ) {
        Object raw = storage.get(key);
        if (raw == null) {
            return fallback;
        }
        OptionalInt parsed = strictInt(raw);
        if (parsed.isEmpty() || parsed.getAsInt() < min || parsed.getAsInt() > max) {
            warnings.add(ConfigWarning.unusableStorageValue(key));
            return fallback;
        }
        return parsed.getAsInt();
    }

    private static Optional<WarningLevel> parseWarningLevel(String raw) {
        try {
            return Optional.of(WarningLevel.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Map<String, String> parseLootOverrides(Map<String, Object> root) {
        Object messages = root.get(KEY_MESSAGES);
        if (!(messages instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            overrides.put(key, entry.getValue() == null ? "" : messageText(entry.getValue()));
        }
        return overrides;
    }

    private static final java.util.regex.Pattern PLACEHOLDER_COLON =
            java.util.regex.Pattern.compile("\\{([A-Za-z0-9_]+):([^}]*)}");

    private static void scanDecimalsInLanguage(
            String languageId, Map<String, String> strings, List<ConfigWarning> warnings) {
        for (Map.Entry<String, String> entry : strings.entrySet()) {
            if (hasUnusableDecimals(entry.getValue())) {
                warnings.add(ConfigWarning.unusableDecimalsLanguage(languageId, entry.getKey()));
            }
        }
    }

    private static void scanDecimalsInLoot(
            String lootId, Map<String, String> overrides, List<ConfigWarning> warnings) {
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (hasUnusableDecimals(entry.getValue())) {
                warnings.add(ConfigWarning.unusableDecimalsLoot(lootId, entry.getKey()));
            }
        }
    }

    private static boolean hasUnusableDecimals(String text) {
        if (text == null || !text.contains(":")) {
            return false;
        }
        java.util.regex.Matcher m = PLACEHOLDER_COLON.matcher(text);
        while (m.find()) {
            String nStr = m.group(2);
            try {
                int n = Integer.parseInt(nStr);
                if (n < 0 || n > 6) {
                    return true;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> parseFlatStringMap(String file, String content) {
        Map<String, Object> root = parseRoot(file, content);
        Map<String, String> out = new LinkedHashMap<>();
        flattenStrings("", root, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void flattenStrings(String prefix, Map<String, Object> node, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenStrings(key, (Map<String, Object>) nested, out);
            } else if (value != null) {
                out.put(key, messageText(value));
            }
        }
    }

    /**
     * A message written as a list, such as {@code hologram_lines}, is kept as its lines joined by a line break.
     * {@link ConfigSnapshot#messageLines} splits it again.
     */
    private static String messageText(Object value) {
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            for (Object line : list) {
                lines.add(line == null ? "" : String.valueOf(line));
            }
            return String.join(ConfigSnapshot.LINE_BREAK, lines);
        }
        return String.valueOf(value);
    }

    private static final class ParseResult {
        private final Map<String, Object> root;
        private final boolean yamlRejected;
        private final OptionalInt quoteLine;

        private ParseResult(Map<String, Object> root, boolean yamlRejected, OptionalInt quoteLine) {
            this.root = root;
            this.yamlRejected = yamlRejected;
            this.quoteLine = quoteLine;
        }

        static ParseResult ok(Map<String, Object> root) {
            return new ParseResult(root, false, OptionalInt.empty());
        }

        static ParseResult yamlRejected(OptionalInt quoteLine) {
            return new ParseResult(Collections.emptyMap(), true, quoteLine);
        }

        OptionalInt quoteLine() {
            return quoteLine;
        }

        Map<String, Object> root() {
            return root;
        }

        boolean isRejected() {
            return yamlRejected;
        }
    }

    private static final class Globals {
        final String language;
        final WarningLevel warningLevel;
        final boolean debug;
        final StorageSettings storage;
        final ClaimCommandSettings claimCommand;
        final AdminCommandSettings adminCommand;
        final WikiCommandSettings wikiCommand;
        final Map<PlacementRule, Integer> placementAttempts;
        final List<String> bannedLootBlocks;
        final int hologramViewRange;
        final HologramProviderChoice hologramProvider;

        Globals(
                String language,
                WarningLevel warningLevel,
                boolean debug,
                StorageSettings storage,
                ClaimCommandSettings claimCommand,
                AdminCommandSettings adminCommand,
                WikiCommandSettings wikiCommand,
                Map<PlacementRule, Integer> placementAttempts,
                List<String> bannedLootBlocks,
                int hologramViewRange,
                HologramProviderChoice hologramProvider
        ) {
            this.language = language;
            this.warningLevel = warningLevel;
            this.debug = debug;
            this.storage = storage;
            this.claimCommand = claimCommand;
            this.adminCommand = adminCommand;
            this.wikiCommand = wikiCommand;
            this.placementAttempts = placementAttempts;
            this.bannedLootBlocks = bannedLootBlocks;
            this.hologramViewRange = hologramViewRange;
            this.hologramProvider = hologramProvider;
        }

        static Globals defaults() {
            return new Globals(
                    FALLBACK_LANGUAGE,
                    WarningLevel.LOW,
                    false,
                    StorageSettings.defaults(),
                    ClaimCommandSettings.defaults(),
                    AdminCommandSettings.defaults(),
                    WikiCommandSettings.defaults(),
                    SummonConfig.defaultPlacementAttempts(),
                    DEFAULT_BANNED_LOOT_BLOCKS,
                    HologramSettings.DEFAULT_VIEW_RANGE,
                    HologramProviderChoice.AUTO
            );
        }
    }

    private static final class DefaultMessageResolver implements MessageResolver {
        private final String activeLanguage;
        private final Map<String, Map<String, String>> languages;
        private final Map<String, Map<String, String>> lootOverrides;
        private final List<ConfigWarning> warnings;
        private final Map<String, Boolean> missingKeyWarned = new LinkedHashMap<>();
        private final Map<String, Boolean> missingEnglishWarned = new LinkedHashMap<>();

        DefaultMessageResolver(
                String activeLanguage,
                Map<String, Map<String, String>> languages,
                Map<String, Map<String, String>> lootOverrides,
                List<ConfigWarning> warnings
        ) {
            this.activeLanguage = activeLanguage;
            this.languages = languages;
            this.lootOverrides = lootOverrides;
            this.warnings = warnings;
        }

        @Override
        public String resolve(String lootId, String key) {
            Objects.requireNonNull(lootId, "lootId");
            Objects.requireNonNull(key, "key");

            Map<String, String> overrides = lootOverrides.getOrDefault(lootId, Collections.emptyMap());
            if (overrides.containsKey(key)) {
                return overrides.get(key);
            }

            Map<String, String> active = languages.getOrDefault(activeLanguage, Collections.emptyMap());
            if (active.containsKey(key)) {
                return active.get(key);
            }

            if (!FALLBACK_LANGUAGE.equals(activeLanguage)) {
                warnMissingKeyOnce(key);
            }

            Map<String, String> english = languages.getOrDefault(FALLBACK_LANGUAGE, Collections.emptyMap());
            if (english.containsKey(key)) {
                return english.get(key);
            }

            warnMissingEnglishOnce(key);
            return key;
        }

        @Override
        public Set<String> keys(String languageId) {
            Map<String, String> map = languages.get(languageId);
            if (map == null) {
                return Set.of();
            }
            return Set.copyOf(map.keySet());
        }

        private void warnMissingKeyOnce(String key) {
            String token = activeLanguage + "\0" + key;
            if (missingKeyWarned.putIfAbsent(token, Boolean.TRUE) == null) {
                warnings.add(ConfigWarning.missingKey(activeLanguage, key));
            }
        }

        private void warnMissingEnglishOnce(String key) {
            if (missingEnglishWarned.putIfAbsent(key, Boolean.TRUE) == null) {
                warnings.add(ConfigWarning.missingEnglishKey(key));
            }
        }
    }
}
