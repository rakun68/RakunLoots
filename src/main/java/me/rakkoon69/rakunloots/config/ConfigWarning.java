package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A load-time problem that passed the warning level (and silence switch when applicable).
 * Tests assert ids and severities, not log wording.
 */
public final class ConfigWarning implements Warning {

    public enum Kind implements WarningKind {
        MISSING_GLOBALS(Severity.HIGH),
        UNKNOWN_WARNING_LEVEL(Severity.HIGH),
        UNUSABLE_DEBUG(Severity.HIGH),
        MISSING_LANGUAGE(Severity.HIGH),
        MISSING_KEY(Severity.HIGH),
        MISSING_ENGLISH_KEY(Severity.SEVERE),
        LOOT_LANGUAGE_CLASH(Severity.LOW),
        LOOT_LANGUAGE_MISSING(Severity.LOW),
        OLD_NAME(Severity.LOW),
        UNKNOWN_NAME(Severity.HIGH),
        MISSING_WORLDS(Severity.HIGH),
        UNUSABLE_OPEN_MODE(Severity.HIGH),
        UNUSABLE_LOCK(Severity.HIGH),
        NEGATIVE_LIFE(Severity.LOW),
        UNUSABLE_LIFE(Severity.HIGH),
        UNUSABLE_SUMMON_DELAY(Severity.HIGH),
        UNUSABLE_MESSAGE_OWNER_ON_REMOVE(Severity.HIGH),
        UNUSABLE_SUMMON_ITEM(Severity.HIGH),
        UNUSABLE_LOOT_END(Severity.HIGH),
        UNUSABLE_DISPLAY_NAME(Severity.HIGH),
        UNUSABLE_DESCRIPTION(Severity.HIGH),
        UNUSABLE_WIKI(Severity.HIGH),
        MISSING_BLOCK(Severity.HIGH),
        UNUSABLE_HEAD_DATA(Severity.HIGH),
        UNUSABLE_ROLL(Severity.HIGH),
        NO_CONTENT_ENTRIES(Severity.HIGH),
        SKIPPED_CONTENT_ENTRY(Severity.HIGH),
        REJECTED_SLOT(Severity.HIGH),
        CORRECTED_MIN_MAX(Severity.HIGH),
        UNKNOWN_KEY(Severity.HIGH),
        YAML_PARSE_REJECTED(Severity.HIGH),
        UNKNOWN_STORAGE_TYPE(Severity.HIGH),
        UNUSABLE_STORAGE_VALUE(Severity.HIGH),
        UNUSABLE_CLAIM_COMMAND_VALUE(Severity.HIGH),
        UNUSABLE_ADMIN_COMMAND_VALUE(Severity.HIGH),
        UNUSABLE_WIKI_COMMAND_VALUE(Severity.HIGH),
        OLD_KEY(Severity.HIGH),
        UNKNOWN_TAG(Severity.HIGH),
        UNKNOWN_GROUP(Severity.HIGH),
        GROUP_CYCLE(Severity.SEVERE),
        EMPTY_GROUP(Severity.LOW),
        ILLEGAL_GROUP_NAME(Severity.HIGH),
        UNUSABLE_SUMMON_TYPE(Severity.HIGH),
        UNUSABLE_SUMMON_OPTION(Severity.HIGH),
        UNUSABLE_SUMMON_SEARCH(Severity.HIGH),
        UNUSABLE_PLACEMENT_ATTEMPTS(Severity.HIGH),
        SMART_WITH_SKIP(Severity.HIGH),
        UNUSABLE_BANNED_LOOT_BLOCKS(Severity.HIGH),
        BANNED_LOOT_BLOCK(Severity.HIGH),
        UNUSABLE_HOLOGRAM(Severity.HIGH),
        UNKNOWN_HOLOGRAM_PROVIDER(Severity.HIGH),
        UNUSABLE_GLOW(Severity.HIGH),
        UNUSABLE_LASER(Severity.HIGH),
        UNUSABLE_WORLD_VALUE(Severity.HIGH),
        UNUSABLE_ANIMATION(Severity.HIGH),
        UNUSABLE_ANIMATIONS(Severity.HIGH),
        UNKNOWN_ANIMATION(Severity.HIGH, "CONFIG_UNKNOWN_ANIMATION"),
        SKIPPED_OPTIONAL_ENTRIES(Severity.LOW),
        MORE_TYPOS(Severity.HIGH),
        UNKNOWN_WARNING_KIND(Severity.LOW),
        UNUSABLE_WARNING_SEVERITY(Severity.HIGH),
        UNUSABLE_DECIMALS(Severity.HIGH),
        UNUSABLE_MENU_ROWS(Severity.HIGH),
        REJECTED_MENU_SLOT(Severity.HIGH),
        MENU_BUTTON_ON_CONTENT(Severity.HIGH),
        DUPLICATE_MENU_BUTTON_SLOT(Severity.HIGH),
        UNKNOWN_MENU_BUTTON(Severity.HIGH),
        UNKNOWN_MENU_FILE(Severity.HIGH),
        UNKNOWN_MENU_KEY(Severity.HIGH),
        UNUSABLE_MENU_VOLUME(Severity.HIGH),
        UNUSABLE_MENU_PITCH(Severity.HIGH),
        UNKNOWN_MENU_SOUND(Severity.HIGH),
        UNUSABLE_MENU_BUTTONS(Severity.HIGH),
        MENU_FALLBACK(Severity.SEVERE);

        private final Severity defaultSeverity;
        private final String stableName;

        Kind(Severity defaultSeverity) {
            this(defaultSeverity, null);
        }

        Kind(Severity defaultSeverity, String stableName) {
            this.defaultSeverity = defaultSeverity;
            this.stableName = stableName;
        }

        @Override
        public String stableName() {
            return stableName != null ? stableName : name();
        }

        @Override
        public Severity defaultSeverity() {
            return defaultSeverity;
        }
    }

    /** The typos a {@link Kind#MORE_TYPOS} warning names before it counts the rest. */
    public static final int MORE_TYPOS_NAMED = 10;

    private final Severity severity;
    private final Kind kind;
    private final Optional<String> languageId;
    private final Optional<String> key;
    private final Optional<String> lootId;
    private final Optional<String> lootLanguage;
    private final Optional<String> activeLanguage;
    private final Optional<String> writtenId;
    private final Optional<String> currentId;
    private final Optional<String> entryId;
    private final Optional<String> groupName;
    private final List<String> cycle;
    private final Optional<String> worldName;
    private final List<String> entries;
    private final List<String> closeMatches;
    private final OptionalInt quoteLine;
    private final Optional<String> hint;
    private final OptionalInt count;

    public ConfigWarning(Severity severity, Kind kind, Optional<String> languageId, Optional<String> key) {
        this(severity, kind, languageId, key, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ConfigWarning(
            Severity severity,
            Kind kind,
            Optional<String> languageId,
            Optional<String> key,
            Optional<String> lootId,
            Optional<String> lootLanguage,
            Optional<String> activeLanguage
    ) {
        this(severity, kind, languageId, key, lootId, lootLanguage, activeLanguage,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ConfigWarning(
            Severity severity,
            Kind kind,
            Optional<String> languageId,
            Optional<String> key,
            Optional<String> lootId,
            Optional<String> lootLanguage,
            Optional<String> activeLanguage,
            Optional<String> writtenId,
            Optional<String> currentId
    ) {
        this(severity, kind, languageId, key, lootId, lootLanguage, activeLanguage,
                writtenId, currentId, Optional.empty());
    }

    public ConfigWarning(
            Severity severity,
            Kind kind,
            Optional<String> languageId,
            Optional<String> key,
            Optional<String> lootId,
            Optional<String> lootLanguage,
            Optional<String> activeLanguage,
            Optional<String> writtenId,
            Optional<String> currentId,
            Optional<String> entryId
    ) {
        this(severity, kind, languageId, key, lootId, lootLanguage, activeLanguage,
                writtenId, currentId, entryId, Optional.empty(), List.of(), Optional.empty(), List.of(), List.of());
    }

    private ConfigWarning(
            Severity severity,
            Kind kind,
            Optional<String> languageId,
            Optional<String> key,
            Optional<String> lootId,
            Optional<String> lootLanguage,
            Optional<String> activeLanguage,
            Optional<String> writtenId,
            Optional<String> currentId,
            Optional<String> entryId,
            Optional<String> groupName,
            List<String> cycle,
            Optional<String> worldName,
            List<String> entries,
            List<String> closeMatches
    ) {
        this(severity, kind, languageId, key, lootId, lootLanguage, activeLanguage, writtenId, currentId, entryId,
                groupName, cycle, worldName, entries, closeMatches, OptionalInt.empty(), Optional.empty(), OptionalInt.empty());
    }

    private ConfigWarning(
            Severity severity,
            Kind kind,
            Optional<String> languageId,
            Optional<String> key,
            Optional<String> lootId,
            Optional<String> lootLanguage,
            Optional<String> activeLanguage,
            Optional<String> writtenId,
            Optional<String> currentId,
            Optional<String> entryId,
            Optional<String> groupName,
            List<String> cycle,
            Optional<String> worldName,
            List<String> entries,
            List<String> closeMatches,
            OptionalInt quoteLine,
            Optional<String> hint,
            OptionalInt count
    ) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.languageId = Objects.requireNonNull(languageId, "languageId");
        this.key = Objects.requireNonNull(key, "key");
        this.lootId = Objects.requireNonNull(lootId, "lootId");
        this.lootLanguage = Objects.requireNonNull(lootLanguage, "lootLanguage");
        this.activeLanguage = Objects.requireNonNull(activeLanguage, "activeLanguage");
        this.writtenId = Objects.requireNonNull(writtenId, "writtenId");
        this.currentId = Objects.requireNonNull(currentId, "currentId");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.groupName = Objects.requireNonNull(groupName, "groupName");
        this.cycle = List.copyOf(cycle);
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.entries = List.copyOf(entries);
        this.closeMatches = List.copyOf(closeMatches);
        this.quoteLine = Objects.requireNonNull(quoteLine, "quoteLine");
        this.hint = Objects.requireNonNull(hint, "hint");
        this.count = Objects.requireNonNull(count, "count");
    }

    public static ConfigWarning missingLanguage(String languageId) {
        return new ConfigWarning(Severity.HIGH, Kind.MISSING_LANGUAGE, Optional.of(languageId), Optional.empty());
    }

    public static ConfigWarning missingKey(String languageId, String key) {
        return new ConfigWarning(Severity.HIGH, Kind.MISSING_KEY, Optional.of(languageId), Optional.of(key));
    }

    public static ConfigWarning missingEnglishKey(String key) {
        return new ConfigWarning(Severity.SEVERE, Kind.MISSING_ENGLISH_KEY, Optional.of("en"), Optional.of(key));
    }

    public static ConfigWarning missingGlobals() {
        return new ConfigWarning(Severity.HIGH, Kind.MISSING_GLOBALS, Optional.empty(), Optional.empty());
    }

    public static ConfigWarning unknownWarningLevel() {
        return new ConfigWarning(Severity.HIGH, Kind.UNKNOWN_WARNING_LEVEL, Optional.empty(), Optional.empty());
    }

    /**
     * {@code debug} is not true or false. Debug mode stays off.
     */
    public static ConfigWarning unusableDebug() {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_DEBUG, Optional.empty(), Optional.of("debug"));
    }

    public static ConfigWarning lootLanguageClash(
            Severity severity,
            String lootId,
            String lootLanguage,
            String activeLanguage
    ) {
        return new ConfigWarning(
                severity,
                Kind.LOOT_LANGUAGE_CLASH,
                Optional.empty(),
                Optional.empty(),
                Optional.of(lootId),
                Optional.of(lootLanguage),
                Optional.of(activeLanguage)
        );
    }

    public static ConfigWarning lootLanguageMissing(String lootId, String activeLanguage) {
        return new ConfigWarning(
                Severity.LOW,
                Kind.LOOT_LANGUAGE_MISSING,
                Optional.empty(),
                Optional.empty(),
                Optional.of(lootId),
                Optional.empty(),
                Optional.of(activeLanguage)
        );
    }

    public static ConfigWarning oldName(String writtenId, String currentId) {
        return new ConfigWarning(
                Severity.LOW,
                Kind.OLD_NAME,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(writtenId),
                Optional.of(currentId)
        );
    }

    /**
     * A block id that no supported version knows, so a typo, with up to 3 close ids.
     */
    public static ConfigWarning unknownName(String writtenId, List<String> closeMatches) {
        return new ConfigWarning(Severity.HIGH, Kind.UNKNOWN_NAME, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(writtenId), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty(), List.of(), closeMatches);
    }

    public static ConfigWarning unknownName(String writtenId) {
        return new ConfigWarning(
                Severity.HIGH,
                Kind.UNKNOWN_NAME,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(writtenId),
                Optional.empty()
        );
    }

    public static ConfigWarning missingWorlds() {
        return new ConfigWarning(Severity.HIGH, Kind.MISSING_WORLDS, Optional.empty(), Optional.empty());
    }

    public static ConfigWarning lootLow(Kind kind, String lootId) {
        return new ConfigWarning(Severity.LOW, kind, Optional.empty(), Optional.empty(), Optional.of(lootId),
                Optional.empty(), Optional.empty());
    }

    public static ConfigWarning lootHigh(Kind kind, String lootId) {
        return lootHigh(kind, lootId, Optional.empty(), Optional.empty());
    }

    public static ConfigWarning lootHigh(Kind kind, String lootId, String entryId) {
        return lootHigh(kind, lootId, Optional.of(entryId), Optional.empty());
    }

    public static ConfigWarning lootHigh(Kind kind, String lootId, Optional<String> entryId, Optional<String> key) {
        return new ConfigWarning(
                Severity.HIGH,
                kind,
                Optional.empty(),
                key,
                Optional.of(lootId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                entryId
        );
    }

    public static ConfigWarning unknownKey(String lootId, String key) {
        return lootHigh(Kind.UNKNOWN_KEY, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * A loot's {@code wiki} value that cannot be used. The key names it; {@code wiki} means the block.
     */
    public static ConfigWarning unusableWikiValue(String lootId, String key) {
        return lootHigh(Kind.UNUSABLE_WIKI, lootId, Optional.empty(), Optional.of(key));
    }

    public static ConfigWarning yamlParseRejected(String lootId) {
        return lootHigh(Kind.YAML_PARSE_REJECTED, lootId);
    }

    /**
     * A loot file that is not valid YAML, with the line of the error when that line holds {@code {}.
     */
    static ConfigWarning yamlParseRejected(String lootId, OptionalInt quoteLine) {
        return yamlParseRejected(lootId).withQuoteLine(quoteLine);
    }

    public static ConfigWarning unknownStorageType(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNKNOWN_STORAGE_TYPE, Optional.empty(), Optional.of(key));
    }

    /**
     * A value in the {@code storage} block that cannot be used. The key names it; {@code storage} means the block.
     */
    public static ConfigWarning unusableStorageValue(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_STORAGE_VALUE, Optional.empty(), Optional.of(key));
    }

    /**
     * A value in the {@code claim_command} block that cannot be used. The key names it; {@code claim_command} means
     * the block.
     */
    public static ConfigWarning unusableClaimCommandValue(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_CLAIM_COMMAND_VALUE, Optional.empty(),
                Optional.of(key));
    }

    /**
     * A value in the {@code admin_command} block that cannot be used. The key names it; {@code admin_command} means
     * the block.
     */
    public static ConfigWarning unusableAdminCommandValue(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_ADMIN_COMMAND_VALUE, Optional.empty(),
                Optional.of(key));
    }

    /**
     * A value in the {@code wiki_command} block that cannot be used. The key names it; {@code wiki_command} means
     * the block.
     */
    public static ConfigWarning unusableWikiCommandValue(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_WIKI_COMMAND_VALUE, Optional.empty(),
                Optional.of(key));
    }

    /**
     * A key from before the current file layout that still loads. The file is left unchanged.
     */
    public static ConfigWarning oldKey(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.OLD_KEY, Optional.empty(), Optional.of(key));
    }

    /**
     * The blocks file is not valid YAML. The shipped groups load in its place.
     */
    public static ConfigWarning blocksParseRejected() {
        return new ConfigWarning(Severity.HIGH, Kind.YAML_PARSE_REJECTED, Optional.empty(), Optional.of("blocks"));
    }

    static ConfigWarning blocksParseRejected(OptionalInt quoteLine) {
        return blocksParseRejected().withQuoteLine(quoteLine);
    }

    /**
     * A {@code #namespace:tag} entry that no supported version knows, so a typo, with up to 3 close tags. Names the
     * group or the loot holding the list.
     */
    public static ConfigWarning unknownTag(
            Optional<String> groupName, Optional<String> lootId, String written, List<String> closeMatches) {
        return blockList(Severity.HIGH, Kind.UNKNOWN_TAG, groupName, lootId, Optional.of(written), List.of(),
                closeMatches);
    }

    /**
     * A {@code #namespace:tag} entry that no supported version knows, in a block list the globals file holds.
     */
    public static ConfigWarning unknownTag(String key, String written, List<String> closeMatches) {
        return new ConfigWarning(Severity.HIGH, Kind.UNKNOWN_TAG, Optional.empty(), Optional.of(key), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(written), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty(), List.of(), closeMatches);
    }

    /**
     * An {@code @group} entry naming a group that does not exist or was refused.
     */
    public static ConfigWarning unknownGroup(Optional<String> groupName, Optional<String> lootId, String written) {
        return blockList(Severity.HIGH, Kind.UNKNOWN_GROUP, groupName, lootId, Optional.of(written), List.of());
    }

    /**
     * An {@code @group} entry naming a group that does not exist or was refused, in a block list the globals file
     * holds. The key names the list.
     */
    public static ConfigWarning unknownGroup(String key, String written) {
        return globalBlockList(Severity.HIGH, Kind.UNKNOWN_GROUP, key, written);
    }

    /**
     * {@code banned_loot_blocks} is not a list. The shipped group is used in its place.
     */
    public static ConfigWarning unusableBannedLootBlocks(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_BANNED_LOOT_BLOCKS, Optional.empty(), Optional.of(key));
    }

    /**
     * A loot refused because its presentation block is a banned loot block. Names the written and the current id.
     */
    public static ConfigWarning bannedLootBlock(String lootId, String writtenId, String currentId) {
        return new ConfigWarning(Severity.HIGH, Kind.BANNED_LOOT_BLOCK, Optional.empty(), Optional.empty(),
                Optional.of(lootId), Optional.empty(), Optional.empty(), Optional.of(writtenId), Optional.of(currentId));
    }

    /**
     * A value in the globals' {@code hologram} section that cannot be used. The key names it; {@code hologram} means
     * the section.
     */
    public static ConfigWarning unusableHologramValue(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_HOLOGRAM, Optional.empty(), Optional.of(key));
    }

    /**
     * A value in a loot's {@code hologram:} section that cannot be used. The key names it; {@code hologram} means the
     * section.
     */
    public static ConfigWarning unusableHologramValue(String lootId, String key) {
        return lootHigh(Kind.UNUSABLE_HOLOGRAM, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * A value in a loot's {@code glow:} section that cannot be used. The key names it; {@code glow} means the section.
     */
    public static ConfigWarning unusableGlowValue(String lootId, String key) {
        return lootHigh(Kind.UNUSABLE_GLOW, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * A value in a loot's {@code laser:} section that cannot be used. The key names it; {@code laser} means the
     * section.
     */
    public static ConfigWarning unusableLaserValue(String lootId, String key) {
        return lootHigh(Kind.UNUSABLE_LASER, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * {@code hologram_provider} names no known provider. {@code auto} is used in its place.
     */
    public static ConfigWarning unknownHologramProvider(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNKNOWN_HOLOGRAM_PROVIDER, Optional.empty(), Optional.of(key));
    }

    /**
     * An entry of {@code animations.yml} that is not a section with a {@code type}, or whose {@code variables} is not a
     * section. The key is the entry's id. The entry is left out.
     */
    public static ConfigWarning unusableAnimation(String animationId) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_ANIMATION, Optional.empty(), Optional.of(animationId));
    }

    /**
     * {@code animations.yml} is not valid YAML. It holds no animation.
     */
    public static ConfigWarning animationsParseRejected() {
        return new ConfigWarning(Severity.HIGH, Kind.YAML_PARSE_REJECTED, Optional.empty(), Optional.of("animations"));
    }

    static ConfigWarning animationsParseRejected(OptionalInt quoteLine) {
        return animationsParseRejected().withQuoteLine(quoteLine);
    }

    /**
     * A loot's {@code animations:} section, or one moment's list in it, that cannot be used. The key is its path, such
     * as {@code animations.summon}.
     */
    public static ConfigWarning unusableAnimations(String lootId, String key) {
        return lootHigh(Kind.UNUSABLE_ANIMATIONS, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * A loot names an animation id that {@code animations.yml} does not define. The key is the moment's path, such as
     * {@code animations.summon}, and the written id is the missing id.
     */
    public static ConfigWarning unknownAnimation(String lootId, String key, String animationId) {
        return new ConfigWarning(Severity.HIGH, Kind.UNKNOWN_ANIMATION, Optional.empty(), Optional.of(key),
                Optional.of(lootId), Optional.empty(), Optional.empty(), Optional.of(animationId), Optional.empty());
    }

    /**
     * A group refused because it contains itself. The cycle lists every group in the loop, sorted by name.
     */
    public static ConfigWarning groupCycle(String groupName, List<String> cycle) {
        return blockList(Severity.SEVERE, Kind.GROUP_CYCLE, Optional.of(groupName), Optional.empty(),
                Optional.empty(), cycle);
    }

    public static ConfigWarning illegalGroupName(String groupName) {
        return blockList(Severity.HIGH, Kind.ILLEGAL_GROUP_NAME, Optional.of(groupName), Optional.empty(),
                Optional.empty(), List.of());
    }

    public static ConfigWarning emptyGroup(String groupName) {
        return blockList(Severity.LOW, Kind.EMPTY_GROUP, Optional.of(groupName), Optional.empty(),
                Optional.empty(), List.of());
    }

    /**
     * Optional block list entries the server does not know, as written with the leading {@code ?}. One per load.
     */
    public static ConfigWarning skippedOptionalEntries(List<String> entries) {
        return new ConfigWarning(Severity.LOW, Kind.SKIPPED_OPTIONAL_ENTRIES, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(), Optional.empty(), entries, List.of());
    }

    /**
     * The typos of a load past the ones warned on their own, in one line. It names the first
     * {@value #MORE_TYPOS_NAMED} and counts the rest. One per load, and only when there are any.
     */
    public static ConfigWarning moreTypos(List<String> typos) {
        int rest = typos.size() - MORE_TYPOS_NAMED;
        return new ConfigWarning(Severity.HIGH, Kind.MORE_TYPOS, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(), Optional.empty(),
                typos.subList(0, Math.min(typos.size(), MORE_TYPOS_NAMED)), List.of(),
                OptionalInt.empty(), Optional.empty(), rest > 0 ? OptionalInt.of(rest) : OptionalInt.empty());
    }

    /**
     * A name in {@code warnings.yml} that no warning kind has. The key is the name. The line is left out.
     */
    public static ConfigWarning unknownWarningKind(String name) {
        return new ConfigWarning(Severity.LOW, Kind.UNKNOWN_WARNING_KIND, Optional.empty(), Optional.of(name));
    }

    /**
     * A severity in {@code warnings.yml} that is not low, high, severe, or off. The key is the kind. The kind keeps its
     * default.
     */
    public static ConfigWarning unusableWarningSeverity(String name) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_WARNING_SEVERITY, Optional.empty(), Optional.of(name));
    }

    /**
     * {@code warnings.yml} is not valid YAML. Every kind keeps its default.
     */
    public static ConfigWarning warningsParseRejected() {
        return new ConfigWarning(Severity.HIGH, Kind.YAML_PARSE_REJECTED, Optional.empty(), Optional.of("warnings"));
    }

    static ConfigWarning warningsParseRejected(OptionalInt quoteLine) {
        return warningsParseRejected().withQuoteLine(quoteLine);
    }

    public static ConfigWarning unusableSummonType(String lootId) {
        return lootHigh(Kind.UNUSABLE_SUMMON_TYPE, lootId);
    }

    public static ConfigWarning unusableSummonOption(String lootId, String key) {
        return lootHigh(Kind.UNUSABLE_SUMMON_OPTION, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * An old key in a loot's {@code summon_settings}. The key names it.
     */
    public static ConfigWarning lootOldKey(String lootId, String key) {
        return lootHigh(Kind.OLD_KEY, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * A {@code placement_attempts} value in the globals file that cannot be used. The key names the rule.
     */
    public static ConfigWarning unusablePlacementAttempts(String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_PLACEMENT_ATTEMPTS, Optional.empty(), Optional.of(key));
    }

    /**
     * An old key in a world's settings. The key names it.
     */
    public static ConfigWarning worldOldKey(String worldName, String key) {
        return world(Kind.OLD_KEY, worldName, Optional.of(key));
    }

    /**
     * A {@code summon_search} value that cannot be used. The key is its path inside the section.
     */
    public static ConfigWarning unusableSummonSearch(String worldName, String key) {
        return world(Kind.UNUSABLE_SUMMON_SEARCH, worldName, Optional.of(key));
    }

    /**
     * A world timer value in {@code worlds.yml} that cannot be used. The key names it. The default is used.
     */
    public static ConfigWarning unusableWorldValue(String worldName, String key) {
        return world(Kind.UNUSABLE_WORLD_VALUE, worldName, Optional.of(key));
    }

    /**
     * Smart mode with {@code unloaded_chunks: skip}. The search reads it as {@code load}.
     */
    public static ConfigWarning smartWithSkip(String worldName) {
        return world(Kind.SMART_WITH_SKIP, worldName, Optional.of("unloaded_chunks"));
    }

    /**
     * A language string or loot message override placeholder with decimals outside 0 to 6, or not a number.
     */
    public static ConfigWarning unusableDecimalsLanguage(String languageId, String key) {
        return new ConfigWarning(Severity.HIGH, Kind.UNUSABLE_DECIMALS, Optional.of(languageId), Optional.of(key));
    }

    /**
     * A loot message override placeholder with decimals outside 0 to 6, or not a number.
     */
    public static ConfigWarning unusableDecimalsLoot(String lootId, String key) {
        return lootHigh(Kind.UNUSABLE_DECIMALS, lootId, Optional.empty(), Optional.of(key));
    }

    /**
     * A menu file's {@code rows} that cannot be used. The shipped value is used in its place.
     */
    public static ConfigWarning unusableMenuRows(String menuId, String key) {
        return menu(Severity.HIGH, Kind.UNUSABLE_MENU_ROWS, menuId, key);
    }

    /**
     * A menu slot that cannot be used, or is outside the page. It is dropped.
     */
    public static ConfigWarning rejectedMenuSlot(String menuId, String key) {
        return menu(Severity.HIGH, Kind.REJECTED_MENU_SLOT, menuId, key);
    }

    /**
     * A button on a content slot. The button stays, and the slot leaves the content.
     */
    public static ConfigWarning menuButtonOnContent(String menuId, String button) {
        return menu(Severity.HIGH, Kind.MENU_BUTTON_ON_CONTENT, menuId, button);
    }

    /**
     * Two buttons on one slot. The later button is dropped.
     */
    public static ConfigWarning duplicateMenuButtonSlot(String menuId, String button) {
        return menu(Severity.HIGH, Kind.DUPLICATE_MENU_BUTTON_SLOT, menuId, button);
    }

    /**
     * A button name the page does not have. The button is dropped.
     */
    public static ConfigWarning unknownMenuButton(String menuId, String button) {
        return menu(Severity.HIGH, Kind.UNKNOWN_MENU_BUTTON, menuId, button);
    }

    /**
     * A {@code menus/} file whose id no menu has. The file is ignored.
     */
    public static ConfigWarning unknownMenuFile(String menuId) {
        return menu(Severity.HIGH, Kind.UNKNOWN_MENU_FILE, menuId, null);
    }

    /**
     * A key in a menu file that no menu, page, button, or sound has. The key is ignored.
     */
    public static ConfigWarning unknownMenuKey(String menuId, String key) {
        return menu(Severity.HIGH, Kind.UNKNOWN_MENU_KEY, menuId, key);
    }

    /**
     * A button sound value that cannot be used. It is {@code 1.0} in its place.
     */
    public static ConfigWarning unusableMenuSoundValue(Kind kind, String menuId, String button) {
        return menu(Severity.HIGH, kind, menuId, button);
    }

    /**
     * A sound key that is missing, unusable, or unknown to the server. The button plays nothing.
     */
    public static ConfigWarning unknownMenuSound(String menuId, String button) {
        return menu(Severity.HIGH, Kind.UNKNOWN_MENU_SOUND, menuId, button);
    }

    /**
     * A menu's {@code buttons} block, or one button in it, that is not a section. No button loads from it.
     */
    public static ConfigWarning unusableMenuButtons(String menuId) {
        return menu(Severity.HIGH, Kind.UNUSABLE_MENU_BUTTONS, menuId, "buttons");
    }

    /**
     * A menu file that cannot be read, whose root is not a map, or whose page ends with no content slot. The whole
     * menu uses the shipped layout.
     */
    public static ConfigWarning menuFallback(String menuId) {
        return menu(Severity.SEVERE, Kind.MENU_FALLBACK, menuId, menuId);
    }

    private static ConfigWarning menu(Severity severity, Kind kind, String menuId, String key) {
        return new ConfigWarning(
                severity,
                kind,
                Optional.empty(),
                Optional.ofNullable(key),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(menuId),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                List.of()
        );
    }

    private static ConfigWarning world(Kind kind, String worldName, Optional<String> key) {
        return new ConfigWarning(Severity.HIGH, kind, Optional.empty(), key, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                Optional.of(worldName), List.of(), List.of());
    }

    private static ConfigWarning globalBlockList(Severity severity, Kind kind, String key, String written) {
        return new ConfigWarning(severity, kind, Optional.empty(), Optional.of(key), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(written), Optional.empty());
    }

    private static ConfigWarning blockList(
            Severity severity,
            Kind kind,
            Optional<String> groupName,
            Optional<String> lootId,
            Optional<String> writtenId,
            List<String> cycle
    ) {
        return blockList(severity, kind, groupName, lootId, writtenId, cycle, List.of());
    }

    private static ConfigWarning blockList(
            Severity severity,
            Kind kind,
            Optional<String> groupName,
            Optional<String> lootId,
            Optional<String> writtenId,
            List<String> cycle,
            List<String> closeMatches
    ) {
        return new ConfigWarning(severity, kind, Optional.empty(), Optional.empty(), lootId, Optional.empty(),
                Optional.empty(), writtenId, Optional.empty(), Optional.empty(), groupName, cycle, Optional.empty(), List.of(), closeMatches);
    }

    public Severity severity() {
        return severity;
    }

    public Kind kind() {
        return kind;
    }

    public Optional<String> languageId() {
        return languageId;
    }

    public Optional<String> key() {
        return key;
    }

    public Optional<String> lootId() {
        return lootId;
    }

    public Optional<String> lootLanguage() {
        return lootLanguage;
    }

    public Optional<String> activeLanguage() {
        return activeLanguage;
    }

    public Optional<String> writtenId() {
        return writtenId;
    }

    public Optional<String> currentId() {
        return currentId;
    }

    public Optional<String> entryId() {
        return entryId;
    }

    public Optional<String> groupName() {
        return groupName;
    }

    public List<String> cycle() {
        return cycle;
    }

    public Optional<String> worldName() {
        return worldName;
    }

    public List<String> entries() {
        return entries;
    }

    /**
     * Up to 3 names close to the written one, closest first. Empty when none is close or the kind has none.
     */
    public List<String> closeMatches() {
        return closeMatches;
    }

    /**
     * The line, counted from 1, of a YAML error that holds {@code {}, so the value there probably needs quotes. Empty
     * for every other warning.
     */
    /**
     * How many entries a warning holds past the ones it lists, for a {@link Kind#MORE_TYPOS} warning. Empty when it
     * lists them all, and for every other warning.
     */
    public OptionalInt count() {
        return count;
    }

    public OptionalInt quoteLine() {
        return quoteLine;
    }

    /**
     * What to do about the warning, in the active language, when there is something to say. Today only a YAML error on a
     * line that holds {@code {} has one: quote the value.
     */
    public Optional<String> hint() {
        return hint;
    }

    @Override
    public Optional<String> subject() {
        if (!entries.isEmpty()) {
            return Optional.of(String.join(", ", entries) + more());
        }
        return writtenId.or(() -> entryId).or(() -> groupName).or(() -> lootId)
                .or(() -> key).or(() -> worldName).or(() -> languageId);
    }

    private String more() {
        return count.isPresent() ? " (" + count.getAsInt() + " more)" : "";
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("language", languageId),
                Warning.field("key", key),
                Warning.field("loot", lootId),
                Warning.field("lootLanguage", lootLanguage),
                Warning.field("activeLanguage", activeLanguage),
                Warning.field("written", writtenId),
                Warning.field("current", currentId),
                Warning.field("entry", entryId),
                Warning.field("group", groupName),
                cycle.isEmpty() ? "" : "cycle=" + cycle,
                entries.isEmpty() ? "" : "entries=" + entries + more(),
                closeMatches.isEmpty() ? "" : "closeMatches=" + closeMatches,
                Warning.field("world", worldName),
                Warning.field("hint", hint)
        );
    }

    @Override
    public Optional<Throwable> cause() {
        return Optional.empty();
    }

    /**
     * This warning with another severity, as {@code warnings.yml} sets it.
     */
    public ConfigWarning withSeverity(Severity other) {
        return other == severity ? this : new ConfigWarning(other, kind, languageId, key, lootId, lootLanguage,
                activeLanguage, writtenId, currentId, entryId, groupName, cycle, worldName, entries, closeMatches,
                quoteLine, hint, count);
    }

    /**
     * This warning with the hint written.
     */
    ConfigWarning withHint(String text) {
        return new ConfigWarning(severity, kind, languageId, key, lootId, lootLanguage, activeLanguage, writtenId,
                currentId, entryId, groupName, cycle, worldName, entries, closeMatches, quoteLine,
                Optional.of(text), count);
    }

    private ConfigWarning withQuoteLine(OptionalInt line) {
        return new ConfigWarning(severity, kind, languageId, key, lootId, lootLanguage, activeLanguage, writtenId,
                currentId, entryId, groupName, cycle, worldName, entries, closeMatches, line, hint, count);
    }
}
