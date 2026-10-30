package me.rakkoon69.rakunloots.wiki;

import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ContentEntry;
import me.rakkoon69.rakunloots.config.Loot;
import me.rakkoon69.rakunloots.config.MenuButton;
import me.rakkoon69.rakunloots.config.MenuPage;
import me.rakkoon69.rakunloots.config.OpenMode;
import me.rakkoon69.rakunloots.config.Roll;
import me.rakkoon69.rakunloots.config.WikiSettings;
import me.rakkoon69.rakunloots.items.ItemReference;
import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.config.MessageRenderer;
import me.rakkoon69.rakunloots.text.MessageText;
import me.rakkoon69.rakunloots.text.TimeText;
import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import me.rakkoon69.rakunloots.roll.LootChance;
import me.rakkoon69.rakunloots.roll.TimerChance;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The loots wiki: every listed loot in the player's world, rarest timer chance first, then loots the timer cannot
 * summon. An entry opens a read-only loot page whose contents show the exact roll chances, and whose info icon shows
 * the loot's own rules. Back returns to the same list page. Both pages render from the config snapshot read at open;
 * a loot page opened before the async chances are ready refreshes in place when they arrive.
 */
public final class WikiMenu {

    private static final String MENU_ID = "wiki";
    private static final String BACK = "back";
    private static final String INFO = "info";
    private static final String KEY_LIST_TITLE = "menu_wiki_list_title";
    private static final String KEY_ENTRY_NAME = "menu_wiki_list_entry_name";
    private static final String KEY_ENTRY_LORE = "menu_wiki_list_entry_lore";
    private static final String KEY_TIMER_CHANCE = "menu_wiki_list_timer_chance";
    private static final String KEY_NOT_ON_TIMER = "menu_wiki_list_not_on_timer";
    private static final String KEY_LIST_CLICK = "menu_wiki_list_click";
    private static final String KEY_LOOT_TITLE = "menu_wiki_loot_title";
    private static final String KEY_CONTENT_CHANCE = "menu_wiki_content_chance";
    private static final String KEY_CONTENT_ALWAYS = "menu_wiki_content_always";
    private static final String KEY_CONTENT_CALCULATING = "menu_wiki_content_calculating";
    private static final String KEY_CONTENT_AMOUNT = "menu_wiki_content_amount";
    private static final String KEY_MISSING_NAME = "menu_wiki_content_missing_name";
    private static final String KEY_MISSING_LORE = "menu_wiki_content_missing_lore";
    private static final String KEY_INFO_NAME = "menu_wiki_info_name";
    private static final String KEY_INFO_OPEN_MODE = "menu_wiki_info_open_mode";
    private static final String KEY_OPEN_MODE_DROP = "menu_wiki_open_mode_drop";
    private static final String KEY_OPEN_MODE_INVENTORY = "menu_wiki_open_mode_inventory";
    private static final String KEY_INFO_LOCK = "menu_wiki_info_lock";
    private static final String KEY_INFO_NO_LOCK = "menu_wiki_info_no_lock";
    private static final String KEY_INFO_LIFE = "menu_wiki_info_life";
    private static final String KEY_INFO_UNENDING = "menu_wiki_info_unending";
    private static final String KEY_INFO_ITEMS = "menu_wiki_info_items";
    private static final String KEY_INFO_WORLDS = "menu_wiki_info_worlds";
    private static final String KEY_INFO_WORLD = "menu_wiki_info_world";
    private static final String KEY_INFO_NO_WORLDS = "menu_wiki_info_no_worlds";
    private static final String KEY_BACK_NAME = "menu_button_back_name";
    private static final String KEY_BACK_LORE = "menu_button_back_lore";
    private static final String KEY_PREFIX = "prefix";

    private final ServerAdapter adapter;
    private final Supplier<ConfigSnapshot> config;
    private final ItemSourceRegistry items;
    private final MenuService menus;
    private final MenuRenderer renderer;
    private final LootChanceCache chances;
    private final BiConsumer<String, Throwable> failures;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public WikiMenu(ServerAdapter adapter, Supplier<ConfigSnapshot> config, ItemSourceRegistry items,
                    MenuService menus, MenuRenderer renderer, LootChanceCache chances,
                    BiConsumer<String, Throwable> failures) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.items = Objects.requireNonNull(items, "items");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.chances = Objects.requireNonNull(chances, "chances");
        this.failures = Objects.requireNonNull(failures, "failures");
        chances.addListener(this::chancesReady);
    }

    /**
     * Opens the wiki for the player, after reading the loaded worlds. The player's location and the config are read
     * once; a failure to read either is reported and no menu opens.
     */
    public void open(UUID playerId, SenderLocation location) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(location, "location");
        ConfigSnapshot snapshot;
        MenuService.MenuState state;
        try {
            state = menus.state(playerId);
            snapshot = config.get();
        } catch (Throwable failure) {
            failures.accept("could not read the config for the loots wiki of " + playerId, failure);
            return;
        }

        CompletableFuture<List<LoadedWorld>> worlds;
        try {
            worlds = adapter.loadedWorlds();
        } catch (RuntimeException failure) {
            failures.accept("could not read the loaded worlds for the loots wiki of " + playerId, failure);
            return;
        }
        worlds.whenComplete((loaded, failure) -> {
            if (failure != null) {
                failures.accept("could not read the loaded worlds for the loots wiki of " + playerId,
                        unwrap(failure));
                return;
            }
            try {
                Session session = new Session(playerId, snapshot, rows(snapshot, loaded, location.worldKey()));
                menus.openTracked(playerId, renderList(session, session.state), state)
                        .whenComplete((epoch, openFailure) -> {
                            if (openFailure != null) {
                                failures.accept("could not open the loots wiki for " + playerId,
                                        unwrap(openFailure));
                                return;
                            }
                            if (epoch.isEmpty()) {
                                return;
                            }
                            long installed = epoch.get();
                            session.registeredAt(installed);
                            sessions.compute(playerId, (id, current) ->
                                    current == null || current.currentEpoch <= installed ? session : current);
                            menus.onRetired(playerId, installed, () -> sessions.remove(playerId, session));
                        });
            } catch (Throwable buildFailure) {
                failures.accept("could not build the loots wiki for " + playerId, buildFailure);
            }
        });
    }

    private static List<Row> rows(ConfigSnapshot snapshot, List<LoadedWorld> worlds, UUID worldKey) {
        Optional<String> worldName = worlds.stream()
                .filter(world -> world.key().equals(worldKey))
                .map(LoadedWorld::name)
                .findFirst();
        Map<String, Integer> weights = worldName.flatMap(snapshot::world)
                .map(settings -> settings.loots())
                .orElseGet(Map::of);
        Map<String, Double> timerChances = TimerChance.calculate(weights, snapshot.loots().keySet());

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Loot> entry : snapshot.loots().entrySet()) {
            WikiSettings wiki = snapshot.wiki(entry.getKey()).orElse(WikiSettings.shown());
            if (!wiki.show()) {
                continue;
            }
            Double chance = timerChances.get(entry.getKey());
            rows.add(new Row(entry.getKey(), entry.getValue(),
                    chance == null ? OptionalDouble.empty() : OptionalDouble.of(chance)));
        }
        rows.sort(Comparator
                .comparing((Row row) -> row.chance().isEmpty())
                .thenComparingDouble(row -> row.chance().orElse(0.0))
                .thenComparing(Row::id));
        return List.copyOf(rows);
    }

    private MenuView renderList(Session session, State state) {
        ConfigSnapshot snapshot = state.snapshot;
        MenuPage layout = snapshot.wikiListMenu();
        List<MenuIcon> entries = new ArrayList<>();
        for (Row row : state.rows) {
            ItemStack item = rowIcon(snapshot, row);
            decorate(item, text(snapshot, KEY_ENTRY_NAME, lootValues(row.loot())), entryLore(snapshot, row));
            entries.add(new MenuIcon(item, Optional.of(() -> session.showLoot(row.id())), Optional.empty()));
        }
        String title = text(snapshot, KEY_LIST_TITLE, Map.of());
        return renderer.render(MENU_ID, title, layout, entries, state.listPage, session::goToListPage,
                key -> snapshot.message("", key));
    }

    private MenuView renderLoot(Session session, State state, LootPage page, Loot loot) {
        ConfigSnapshot snapshot = state.snapshot;
        MenuPage layout = snapshot.wikiLootMenu();
        Map<String, LootChance.EntryChance> exact = chances.chances(snapshot, loot.id()).orElse(null);
        String title = text(snapshot, KEY_LOOT_TITLE, lootValues(loot));
        MenuView view = renderer.render(MENU_ID, title, layout, contentIcons(snapshot, loot, exact),
                page.page(), destination -> session.goToLootPage(loot.id(), destination),
                key -> snapshot.message("", key));

        Map<Integer, MenuIcon> icons = new LinkedHashMap<>(view.icons());
        MenuButton back = layout.buttons().get(BACK);
        if (back != null) {
            MenuIcon shown = icons.get(back.slot());
            if (shown != null) {
                ItemStack item = shown.item();
                if (!MenuRenderer.isMissingIcon(item)) {
                    decorate(item, text(snapshot, KEY_BACK_NAME, lootValues(loot)), backLore(snapshot, loot));
                }
                icons.put(back.slot(), new MenuIcon(item, Optional.of(session::showList), shown.sound()));
            }
        }
        MenuButton info = layout.buttons().get(INFO);
        if (info != null) {
            MenuIcon shown = icons.get(info.slot());
            if (shown != null && !MenuRenderer.isMissingIcon(shown.item())) {
                ItemStack item = shown.item();
                decorate(item, text(snapshot, KEY_INFO_NAME, lootValues(loot)), infoLore(snapshot, loot));
                icons.put(info.slot(), new MenuIcon(item, Optional.empty(), Optional.empty()));
            }
        }
        return new MenuView(view.title(), view.rows(), icons);
    }

    private List<MenuIcon> contentIcons(ConfigSnapshot snapshot, Loot loot,
                                        Map<String, LootChance.EntryChance> exact) {
        List<ContentEntry> shown = new ArrayList<>();
        for (ContentEntry entry : loot.roll().entries()) {
            if (entry.weight() != 0) {
                shown.add(entry);
            }
        }
        shown.sort(chanceOrder(exact));

        List<MenuIcon> icons = new ArrayList<>();
        for (ContentEntry entry : shown) {
            LootChance.EntryChance chance = exact == null ? null : exact.get(entry.id());
            icons.add(contentIcon(snapshot, loot, entry, chance));
        }
        return icons;
    }

    private static Comparator<ContentEntry> chanceOrder(Map<String, LootChance.EntryChance> chances) {
        return Comparator
                .comparingInt((ContentEntry entry) -> alwaysLast(entry, chances))
                .thenComparingDouble(entry -> {
                    LootChance.EntryChance chance = chances == null ? null : chances.get(entry.id());
                    return chance == null ? 0.0 : chance.probability();
                });
    }

    private static int alwaysLast(ContentEntry entry, Map<String, LootChance.EntryChance> chances) {
        LootChance.EntryChance chance = chances == null ? null : chances.get(entry.id());
        boolean always = chance == null ? entry.weight() < 0 : chance.kind() == LootChance.Kind.ALWAYS;
        return always ? 1 : 0;
    }

    private MenuIcon contentIcon(ConfigSnapshot snapshot, Loot loot, ContentEntry entry,
                                 LootChance.EntryChance chance) {
        ItemResolveResult resolved;
        try {
            resolved = items.resolve(entry.item());
        } catch (RuntimeException failure) {
            resolved = null;
        }
        if (resolved == null || resolved.isMiss() || resolved.item().isEmpty()) {
            return missingContent(snapshot, entry);
        }

        ItemStack item = resolved.item().orElseThrow().clone();
        item.setAmount(Math.min(entry.amount(), Math.max(1, item.getMaxStackSize())));
        List<String> lore = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getLore() != null) {
            lore.addAll(meta.getLore());
        }
        boolean always = chance == null
                ? entry.weight() < 0
                : chance.kind() == LootChance.Kind.ALWAYS;
        if (always) {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_CONTENT_ALWAYS, Map.of()));
        } else if (chance != null) {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_CONTENT_CHANCE, Map.of(),
                    Map.of("chance", chance.probability())));
        } else {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_CONTENT_CALCULATING, Map.of()));
        }
        addRendered(lore, MessageRenderer.render(snapshot, "", KEY_CONTENT_AMOUNT,
                Map.of("amount", Integer.toString(entry.amount()))));
        setLore(item, lore);
        return new MenuIcon(item, Optional.empty(), Optional.empty());
    }

    private static MenuIcon missingContent(ConfigSnapshot snapshot, ContentEntry entry) {
        Map<String, String> values = Map.of("item", referenceLabel(entry.item()));
        ItemStack item = new ItemStack(Material.BARRIER);
        decorate(item, text(snapshot, KEY_MISSING_NAME, values), lines(snapshot, KEY_MISSING_LORE, values));
        return new MenuIcon(item, Optional.empty(), Optional.empty());
    }

    private static String referenceLabel(ItemReference reference) {
        String source = reference.source() == null || reference.source().isBlank()
                ? "vanilla"
                : reference.source();
        return source + ":" + reference.id();
    }

    private static List<String> infoLore(ConfigSnapshot snapshot, Loot loot) {
        List<String> lore = new ArrayList<>();
        for (String line : loot.description()) {
            addRendered(lore, MessageText.color(line));
        }

        String modeKey = loot.openMode() == OpenMode.INVENTORY ? KEY_OPEN_MODE_INVENTORY : KEY_OPEN_MODE_DROP;
        String openMode = snapshot.message("", modeKey);
        if (!openMode.isBlank()) {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_OPEN_MODE,
                    Map.of("open_mode", openMode)));
        }
        if (loot.lockSeconds() > 0) {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_LOCK,
                    Map.of("lock", TimeText.time(loot.lockSeconds()))));
        } else {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_NO_LOCK, Map.of()));
        }
        if (loot.lifeSeconds().isPresent()) {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_LIFE,
                    Map.of("life", TimeText.time(loot.lifeSeconds().getAsInt()))));
        } else {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_UNENDING, Map.of()));
        }
        int[] range = itemRange(loot.roll());
        addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_ITEMS,
                Map.of("min", Integer.toString(range[0]), "max", Integer.toString(range[1]))));

        addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_WORLDS, Map.of()));
        List<String> worldNames = new ArrayList<>(snapshot.worlds().keySet());
        worldNames.sort(String::compareTo);
        int added = 0;
        for (String worldName : worldNames) {
            Map<String, Integer> weights = snapshot.world(worldName).orElseThrow().loots();
            Double chance = TimerChance.calculate(weights, snapshot.loots().keySet()).get(loot.id());
            if (chance == null) {
                continue;
            }
            added++;
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_WORLD,
                    Map.of("world", worldName), Map.of("chance", chance)));
        }
        if (added == 0) {
            addRendered(lore, MessageRenderer.render(snapshot, "", KEY_INFO_NO_WORLDS, Map.of()));
        }
        return lore;
    }

    private static int[] itemRange(Roll roll) {
        int drawable = 0;
        for (ContentEntry entry : roll.entries()) {
            if (entry.weight() > 0) {
                drawable++;
            }
        }
        int min = Math.max(0, roll.min());
        int max = Math.max(0, roll.max());
        if (min >= max) {
            min = max;
        }
        return new int[]{Math.min(min, drawable), Math.min(max, drawable)};
    }

    private ItemStack rowIcon(ConfigSnapshot snapshot, Row row) {
        WikiSettings wiki = snapshot.wiki(row.id()).orElse(WikiSettings.shown());
        if (wiki.icon().isPresent()) {
            try {
                ItemResolveResult resolved = items.resolve(wiki.icon().get());
                if (resolved.isHit()) {
                    return resolved.item().orElseThrow();
                }
            } catch (RuntimeException failure) {
                // A source that cannot resolve here still leaves the list usable; the row shows a barrier.
            }
            return new ItemStack(Material.BARRIER);
        }
        try {
            return adapter.blockItem(row.loot().block(), row.loot().headData())
                    .orElseGet(() -> new ItemStack(Material.BARRIER));
        } catch (RuntimeException failure) {
            return new ItemStack(Material.BARRIER);
        }
    }

    private static List<String> entryLore(ConfigSnapshot snapshot, Row row) {
        List<String> lore = new ArrayList<>();
        String prefix = snapshot.message("", KEY_PREFIX);
        for (String line : snapshot.messageLines("", KEY_ENTRY_LORE)) {
            if (line.isBlank()) {
                continue;
            }
            if (line.contains("{description}")) {
                for (String description : row.loot().description()) {
                    if (!description.isBlank()) {
                        lore.add(MessageText.color(replace(line.replace("{description}", description),
                                row.loot(), prefix)));
                    }
                }
                continue;
            }
            lore.add(MessageText.color(replace(line, row.loot(), prefix)));
        }

        Map<String, String> values = lootValues(row.loot());
        if (row.chance().isPresent()) {
            addIfNotBlank(lore, MessageRenderer.render(snapshot, "", KEY_TIMER_CHANCE, values,
                    Map.of("chance", row.chance().getAsDouble())));
        } else {
            addIfNotBlank(lore, MessageRenderer.render(snapshot, "", KEY_NOT_ON_TIMER, values));
        }
        addIfNotBlank(lore, MessageRenderer.render(snapshot, "", KEY_LIST_CLICK, values));
        return lore;
    }

    private static List<String> backLore(ConfigSnapshot snapshot, Loot loot) {
        List<String> lore = new ArrayList<>();
        String prefix = snapshot.message("", KEY_PREFIX);
        for (String line : snapshot.messageLines("", KEY_BACK_LORE)) {
            if (!line.isBlank()) {
                lore.add(MessageText.color(replace(line, loot, prefix)));
            }
        }
        return lore;
    }

    private static String replace(String line, Loot loot, String prefix) {
        return line.replace("{prefix}", prefix).replace("{loot}", loot.displayName());
    }

    private static String text(ConfigSnapshot snapshot, String key, Map<String, String> values) {
        return MessageRenderer.render(snapshot, "", key, values);
    }

    private static List<String> lines(ConfigSnapshot snapshot, String key, Map<String, String> values) {
        List<String> lines = new ArrayList<>();
        String text = MessageRenderer.render(snapshot, "", key, values);
        for (String line : text.split("\n", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void addRendered(List<String> lines, String text) {
        for (String line : text.split("\n", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
    }

    private static Map<String, String> lootValues(Loot loot) {
        return Map.of("loot", loot.displayName());
    }

    private static void addIfNotBlank(List<String> lines, String line) {
        if (!line.isBlank()) {
            lines.add(line);
        }
    }

    private static void decorate(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static void setLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private record Row(String id, Loot loot, OptionalDouble chance) {
    }

    private record LootPage(String lootId, int page, boolean ready) {
    }

    private record State(ConfigSnapshot snapshot, List<Row> rows, int listPage, Optional<LootPage> lootPage,
                         long epoch) {

        private State withListPage(int next) {
            return new State(snapshot, rows, next, lootPage, epoch);
        }

        private State withLootPage(Optional<LootPage> next) {
            return new State(snapshot, rows, listPage, next, epoch);
        }

        private State withEpoch(long next) {
            return new State(snapshot, rows, listPage, lootPage, next);
        }
    }

    private void chancesReady(ConfigSnapshot snapshot) {
        for (Session session : sessions.values()) {
            session.chancesReady(snapshot);
        }
    }

    private final class Session {

        private final UUID playerId;
        private final Object lock = new Object();
        private State state;
        private long revision;
        private long openSequence;
        private long openDone;
        private volatile long currentEpoch = -1L;

        private Session(UUID playerId, ConfigSnapshot snapshot, List<Row> rows) {
            this.playerId = playerId;
            this.state = new State(snapshot, rows, 0, Optional.empty(), -1L);
        }

        private void registeredAt(long epoch) {
            synchronized (lock) {
                state = state.withEpoch(epoch);
                currentEpoch = epoch;
                revision++;
            }
        }

        private void showLoot(String lootId) {
            synchronized (lock) {
                Loot loot = state.snapshot.loot(lootId).orElse(null);
                if (loot == null || state.epoch < 0L) {
                    return;
                }
                LootPage page = new LootPage(lootId, 0,
                        chances.chances(state.snapshot, lootId).isPresent());
                State target = state.withLootPage(Optional.of(page));
                long expected = state.epoch;
                long rendered = ++revision;
                MenuView view = renderLoot(this, target, page, loot);
                if (revision != rendered) {
                    return;
                }
                replace(expected, view, target);
            }
        }

        private void showList() {
            synchronized (lock) {
                if (state.epoch < 0L) {
                    return;
                }
                State target = state.withLootPage(Optional.empty());
                long expected = state.epoch;
                long rendered = ++revision;
                MenuView view = renderList(this, target);
                if (revision != rendered) {
                    return;
                }
                replace(expected, view, target);
            }
        }

        private void goToListPage(int destination) {
            synchronized (lock) {
                int page = renderer.clampPage(state.snapshot.wikiListMenu(), state.rows.size(), destination);
                state = state.withListPage(page);
                if (state.epoch < 0L) {
                    return;
                }
                long rendered = ++revision;
                MenuView view = renderList(this, state);
                if (revision != rendered) {
                    return;
                }
                menus.refreshIf(playerId, state.epoch, view);
            }
        }

        private void goToLootPage(String lootId, int destination) {
            synchronized (lock) {
                LootPage current = state.lootPage.orElse(null);
                if (current == null || !current.lootId().equals(lootId)) {
                    return;
                }
                Loot loot = state.snapshot.loot(lootId).orElse(null);
                if (loot == null) {
                    return;
                }
                int count = contentCount(loot);
                int page = renderer.clampPage(state.snapshot.wikiLootMenu(), count, destination);
                LootPage next = new LootPage(lootId, page,
                        current.ready() || chances.chances(state.snapshot, lootId).isPresent());
                state = state.withLootPage(Optional.of(next));
                if (state.epoch < 0L) {
                    return;
                }
                long rendered = ++revision;
                MenuView view = renderLoot(this, state, next, loot);
                if (revision != rendered) {
                    return;
                }
                menus.refreshIf(playerId, state.epoch, view);
            }
        }

        private void replace(long expected, MenuView view, State target) {
            long token = ++openSequence;
            menus.replaceIf(playerId, expected, view).whenComplete((epoch, failure) -> {
                if (failure != null) {
                    finishOpen(token);
                    failures.accept("could not replace the loots wiki for " + playerId, unwrap(failure));
                    return;
                }
                if (epoch.isEmpty()) {
                    finishOpen(token);
                    return;
                }
                replaced(token, expected, epoch.get(), target);
            });
        }

        private void finishOpen(long token) {
            synchronized (lock) {
                if (token > openDone) {
                    openDone = token;
                    if (token == openSequence) {
                        refreshReadyLocked();
                    }
                }
            }
        }

        private void replaced(long token, long expected, long next, State target) {
            synchronized (lock) {
                if (token > openDone) {
                    openDone = token;
                }
                if (state.epoch != expected) {
                    return;
                }
                state = target.withListPage(state.listPage).withEpoch(next);
                currentEpoch = next;
                revision++;
                refreshReadyLocked();
            }
        }

        private void chancesReady(ConfigSnapshot snapshot) {
            synchronized (lock) {
                if (openSequence != openDone) {
                    return;
                }
                if (state.epoch < 0L || state.snapshot != snapshot || state.lootPage.isEmpty()) {
                    return;
                }
                LootPage page = state.lootPage.get();
                if (page.ready()) {
                    return;
                }
                Loot loot = snapshot.loot(page.lootId()).orElse(null);
                if (loot == null) {
                    return;
                }
                LootPage ready = new LootPage(page.lootId(), page.page(), true);
                state = state.withLootPage(Optional.of(ready));
                long rendered = ++revision;
                MenuView view = renderLoot(this, state, ready, loot);
                if (revision != rendered) {
                    return;
                }
                menus.refreshIf(playerId, state.epoch, view);
            }
        }

        private void refreshReadyLocked() {
            LootPage page = state.lootPage.orElse(null);
            if (page == null || page.ready()) {
                return;
            }
            Loot loot = state.snapshot.loot(page.lootId()).orElse(null);
            if (loot == null || chances.chances(state.snapshot, page.lootId()).isEmpty()) {
                return;
            }
            LootPage ready = new LootPage(page.lootId(), page.page(), true);
            state = state.withLootPage(Optional.of(ready));
            long rendered = ++revision;
            MenuView view = renderLoot(this, state, ready, loot);
            if (revision != rendered) {
                return;
            }
            menus.refreshIf(playerId, state.epoch, view);
        }
    }

    private static int contentCount(Loot loot) {
        int count = 0;
        for (ContentEntry entry : loot.roll().entries()) {
            if (entry.weight() != 0) {
                count++;
            }
        }
        return count;
    }
}
