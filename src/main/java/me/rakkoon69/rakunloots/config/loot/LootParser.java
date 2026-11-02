package me.rakkoon69.rakunloots.config.loot;

import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.items.VanillaAdditions;
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
import me.rakkoon69.rakunloots.config.ConfigWarning;

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

/**
 * Parses one loot file into its loot, and the loot's wiki settings. Item references written like a roll entry's
 * item, such as a menu icon, parse here too.
 */
public final class LootParser {

    private static final int DEFAULT_LIFE_SECONDS = 360;

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

    private LootParser() {
    }

    public static Optional<Loot> parseLoot(
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
    public static WikiSettings parseWiki(
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

    public static Optional<ItemReference> parseItemReference(
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
}
