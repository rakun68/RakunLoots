package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.config.loot.Loot;
import me.rakkoon69.rakunloots.config.loot.LootAnimations;
import me.rakkoon69.rakunloots.config.loot.WikiSettings;
import me.rakkoon69.rakunloots.config.menu.MenuPage;
import me.rakkoon69.rakunloots.config.summon.SummonRules;
import me.rakkoon69.rakunloots.warning.WarningLevel;
import me.rakkoon69.rakunloots.warning.WarningSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Result of an in-memory config load.
 */
public final class ConfigSnapshot {

    static final String LINE_BREAK = "\n";
    private static final String KEY_HOLOGRAM_LINES = "hologram_lines";

    private final String activeLanguage;
    private final WarningLevel warningLevel;
    private final WarningSettings warningSettings;
    private final boolean debug;
    private final MessageResolver messages;
    private final Map<String, Optional<String>> lootLanguages;
    private final Map<String, Map<String, String>> lootOverrides;
    private final Map<String, String> resolvedNames;
    private final Map<String, WorldSettings> worlds;
    private final Map<String, Loot> loots;
    private final Map<String, WikiSettings> wikis;
    private final Map<String, SummonRules> summonRules;
    private final Map<String, HologramSettings> holograms;
    private final Map<String, VisualsSettings> visuals;
    private final Map<String, AnimationDefinition> animations;
    private final Map<String, LootAnimations> lootAnimations;
    private final int hologramViewRange;
    private final HologramProviderChoice hologramProvider;
    private final Map<String, Set<String>> blockGroups;
    private final StorageSettings storage;
    private final ClaimCommandSettings claimCommand;
    private final AdminCommandSettings adminCommand;
    private final WikiCommandSettings wikiCommand;
    private final MenuPage claimMenu;
    private final MenuPage wikiListMenu;
    private final MenuPage wikiLootMenu;
    private final List<ConfigWarning> warnings;

    ConfigSnapshot(
            String activeLanguage,
            WarningLevel warningLevel,
            WarningSettings warningSettings,
            boolean debug,
            MessageResolver messages,
            Map<String, Optional<String>> lootLanguages,
            Map<String, Map<String, String>> lootOverrides,
            Map<String, String> resolvedNames,
            Map<String, WorldSettings> worlds,
            Map<String, Loot> loots,
            Map<String, WikiSettings> wikis,
            Map<String, SummonRules> summonRules,
            Map<String, HologramSettings> holograms,
            Map<String, VisualsSettings> visuals,
            Map<String, AnimationDefinition> animations,
            Map<String, LootAnimations> lootAnimations,
            int hologramViewRange,
            HologramProviderChoice hologramProvider,
            Map<String, Set<String>> blockGroups,
            StorageSettings storage,
            ClaimCommandSettings claimCommand,
            AdminCommandSettings adminCommand,
            WikiCommandSettings wikiCommand,
            MenuPage claimMenu,
            MenuPage wikiListMenu,
            MenuPage wikiLootMenu,
            List<ConfigWarning> warnings
    ) {
        this.activeLanguage = Objects.requireNonNull(activeLanguage, "activeLanguage");
        this.warningLevel = Objects.requireNonNull(warningLevel, "warningLevel");
        this.warningSettings = Objects.requireNonNull(warningSettings, "warningSettings");
        this.debug = debug;
        this.messages = Objects.requireNonNull(messages, "messages");
        this.lootLanguages = Objects.requireNonNull(lootLanguages, "lootLanguages");
        this.lootOverrides = Objects.requireNonNull(lootOverrides, "lootOverrides");
        this.resolvedNames = Objects.requireNonNull(resolvedNames, "resolvedNames");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.loots = Objects.requireNonNull(loots, "loots");
        this.wikis = Objects.requireNonNull(wikis, "wikis");
        this.summonRules = Objects.requireNonNull(summonRules, "summonRules");
        this.holograms = Objects.requireNonNull(holograms, "holograms");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.lootAnimations = Objects.requireNonNull(lootAnimations, "lootAnimations");
        this.hologramViewRange = hologramViewRange;
        this.hologramProvider = Objects.requireNonNull(hologramProvider, "hologramProvider");
        this.blockGroups = Objects.requireNonNull(blockGroups, "blockGroups");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.claimCommand = Objects.requireNonNull(claimCommand, "claimCommand");
        this.adminCommand = Objects.requireNonNull(adminCommand, "adminCommand");
        this.wikiCommand = Objects.requireNonNull(wikiCommand, "wikiCommand");
        this.claimMenu = Objects.requireNonNull(claimMenu, "claimMenu");
        this.wikiListMenu = Objects.requireNonNull(wikiListMenu, "wikiListMenu");
        this.wikiLootMenu = Objects.requireNonNull(wikiLootMenu, "wikiLootMenu");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    public String activeLanguage() {
        return activeLanguage;
    }

    public WarningLevel warningLevel() {
        return warningLevel;
    }

    /**
     * What {@code warnings.yml} says about each warning kind.
     */
    public WarningSettings warningSettings() {
        return warningSettings;
    }

    /**
     * Whether debug mode is on: the plugin logs summon, world timer, loot end, and admin command outcomes to the
     * console at info, prefixed {@code [debug]}.
     */
    public boolean debug() {
        return debug;
    }

    /**
     * Resolved message text for a loot and key. Stored text only; no color or placeholder expansion.
     */
    public String message(String lootId, String key) {
        return messages.resolve(lootId, key);
    }

    /**
     * A message as lines. A message written as a list gives its entries; one written as a string gives one line.
     * Resolved as {@link #message} is.
     */
    public List<String> messageLines(String lootId, String key) {
        return List.of(message(lootId, key).split(LINE_BREAK, -1));
    }

    /**
     * Default-message keys present for a language id that was loaded.
     */
    public Set<String> languageKeys(String languageId) {
        return messages.keys(languageId);
    }

    /**
     * Language stamp from the loot file, if present.
     */
    public Optional<String> lootLanguage(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return lootLanguages.getOrDefault(lootId, Optional.empty());
    }

    /**
     * Message overrides written under {@code messages:} for that loot. Present keys include blank strings.
     */
    public Map<String, String> messageOverrides(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return lootOverrides.getOrDefault(lootId, Map.of());
    }

    /**
     * Current id for a block or item name found during load, after catalog resolution.
     * Unknown ids stay as written. Ids not present in the loaded files are absent.
     */
    public Optional<String> resolvedName(String writtenId) {
        Objects.requireNonNull(writtenId, "writtenId");
        return Optional.ofNullable(resolvedNames.get(writtenId));
    }

    public Map<String, String> resolvedNames() {
        return resolvedNames;
    }

    public Optional<WorldSettings> world(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return Optional.ofNullable(worlds.get(worldName));
    }

    public Map<String, WorldSettings> worlds() {
        return worlds;
    }

    /**
     * Immutable loots keyed by loot id, in source order. Skipped loots are absent.
     */
    public Map<String, Loot> loots() {
        return loots;
    }

    public Optional<Loot> loot(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return Optional.ofNullable(loots.get(lootId));
    }

    /**
     * Wiki settings for a loaded loot id. Held beside the loot, never on it. Absent when the loot was skipped.
     */
    public Optional<WikiSettings> wiki(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return Optional.ofNullable(wikis.get(lootId));
    }

    /**
     * Summon rules for a loaded loot id. Held beside the loot, never on it. Absent when the loot was skipped.
     */
    public Optional<SummonRules> summonRules(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return Optional.ofNullable(summonRules.get(lootId));
    }

    public Map<String, SummonRules> summonRules() {
        return summonRules;
    }

    /**
     * Hologram settings for a loaded loot id. Held beside the loot, never on it. Absent when the loot was skipped.
     */
    public Optional<HologramSettings> hologram(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return Optional.ofNullable(holograms.get(lootId));
    }

    /**
     * Glow and laser settings for a loaded loot id. Held beside the loot, never on it. Absent when the loot was
     * skipped.
     */
    public Optional<VisualsSettings> visuals(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return Optional.ofNullable(visuals.get(lootId));
    }

    /**
     * Every entry of {@code animations.yml} by id, in the order written. Entries that could not be used are absent.
     */
    public Map<String, AnimationDefinition> animations() {
        return animations;
    }

    public Optional<AnimationDefinition> animation(String animationId) {
        Objects.requireNonNull(animationId, "animationId");
        return Optional.ofNullable(animations.get(animationId));
    }

    /**
     * The animation ids a loaded loot names for each moment. Held beside the loot, never on it. Absent when the loot
     * was skipped.
     */
    public Optional<LootAnimations> lootAnimations(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        return Optional.ofNullable(lootAnimations.get(lootId));
    }

    /**
     * A loot's hologram lines with placeholders unfilled: the loot's own {@code hologram.lines}, or else the
     * {@code hologram_lines} message for that loot.
     */
    public List<String> hologramLines(String lootId) {
        Objects.requireNonNull(lootId, "lootId");
        HologramSettings settings = holograms.get(lootId);
        if (settings != null && settings.lines().isPresent()) {
            return settings.lines().get();
        }
        return messageLines(lootId, KEY_HOLOGRAM_LINES);
    }

    /**
     * {@code hologram.view_range} in the globals file, in blocks. A loot's own {@code view_range} is already on its
     * {@link HologramSettings}.
     */
    public int hologramViewRange() {
        return hologramViewRange;
    }

    /**
     * {@code hologram_provider} in the globals file.
     */
    public HologramProviderChoice hologramProvider() {
        return hologramProvider;
    }

    /**
     * A block group as a flat, immutable set of current block ids. Refused groups are absent.
     */
    public Optional<Set<String>> group(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(blockGroups.get(name));
    }

    /**
     * Every loaded block group by name: the shipped groups, with the owner's groups of the same name in their place.
     */
    public Map<String, Set<String>> groups() {
        return blockGroups;
    }

    /**
     * The {@code storage} block of the globals file, with defaults for anything missing or unusable.
     */
    public StorageSettings storage() {
        return storage;
    }

    /**
     * The {@code claim_command} block of the globals file: the name and aliases of the claim command.
     */
    public ClaimCommandSettings claimCommand() {
        return claimCommand;
    }

    /**
     * The {@code admin_command} block of the globals file: the name and aliases of the admin command.
     */
    public AdminCommandSettings adminCommand() {
        return adminCommand;
    }

    /**
     * The {@code wiki_command} block of the globals file: the name and aliases of the loots wiki command.
     */
    public WikiCommandSettings wikiCommand() {
        return wikiCommand;
    }

    /**
     * The claim menu page from {@code menus/claim.yml}, or the shipped page when the file is missing or unusable.
     */
    public MenuPage claimMenu() {
        return claimMenu;
    }

    /**
     * The wiki's list page from {@code menus/wiki.yml}, or the shipped page when it is missing or the wiki fell back.
     */
    public MenuPage wikiListMenu() {
        return wikiListMenu;
    }

    /**
     * The wiki's loot page from {@code menus/wiki.yml}, or the shipped page when it is missing or the wiki fell back.
     */
    public MenuPage wikiLootMenu() {
        return wikiLootMenu;
    }

    /**
     * The warnings of the load as {@code warnings.yml} judges them: each at the severity the file gives its kind, an
     * off kind dropped, and only what the warning level admits.
     */
    public List<ConfigWarning> warnings() {
        List<ConfigWarning> judged = new ArrayList<>();
        for (ConfigWarning warning : warnings) {
            warningSettings.judge(warning.kind(), warning.severity()).map(warning::withSeverity)
                    .filter(w -> warningLevel.admits(w.severity())).ifPresent(judged::add);
        }
        return List.copyOf(judged);
    }

    /**
     * Every warning of the load as it was raised, before {@code warnings.yml} and the warning level. The warning log
     * judges these itself, so the warnings command can list what the level hides.
     */
    public List<ConfigWarning> rawWarnings() {
        return List.copyOf(warnings);
    }
}
