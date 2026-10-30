package me.rakkoon69.rakunloots.claim;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.Loot;
import me.rakkoon69.rakunloots.config.MenuButton;
import me.rakkoon69.rakunloots.config.MenuPage;
import me.rakkoon69.rakunloots.config.MessageRenderer;
import me.rakkoon69.rakunloots.text.MessageText;
import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The claim menu: a player's unclaimed summon items by loot, with a click that takes one loot's items and a button
 * that takes everything. A click is ignored while that player's claim from this menu runs, even if they page or
 * reopen the menu. A completion refreshes the menu it came from and closes it when nothing is left; a completion for
 * a menu that was since closed or replaced leaves the newer view alone.
 */
public final class ClaimMenu {

    private static final String MENU_ID = "claim";
    private static final String CLAIM_ALL = "claim_all";
    private static final String KEY_TITLE = "menu_claim_title";
    private static final String KEY_ENTRY_NAME = "menu_claim_entry_name";
    private static final String KEY_ENTRY_LORE = "menu_claim_entry_lore";
    private static final String KEY_MISSING_NAME = "menu_claim_missing_name";
    private static final String KEY_MISSING_LORE = "menu_claim_missing_lore";
    private static final String KEY_BUTTON_NAME = "menu_button_claim_all_name";
    private static final String KEY_BUTTON_LORE = "menu_button_claim_all_lore";
    private static final String KEY_PREFIX = "prefix";
    private static final int STACK_LIMIT = 64;

    private final ServerAdapter adapter;
    private final Supplier<ConfigSnapshot> config;
    private final UnclaimedSummonItems claims;
    private final SummonItemFactory items;
    private final MenuService menus;
    private final MenuRenderer renderer;
    private final BiConsumer<String, Throwable> failures;
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();

    public ClaimMenu(ServerAdapter adapter, Supplier<ConfigSnapshot> config, UnclaimedSummonItems claims,
                     SummonItemFactory items, MenuService menus, MenuRenderer renderer,
                     BiConsumer<String, Throwable> failures) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.config = Objects.requireNonNull(config, "config");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.items = Objects.requireNonNull(items, "items");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /**
     * Opens the player's claim menu when they are owed something, after reading storage on the async scheduler. A
     * player owed nothing gets {@code whenNothing} on their thread instead, and no menu.
     */
    public void open(UUID playerId, Runnable whenNothing) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(whenNothing, "whenNothing");
        ConfigSnapshot snapshot;
        MenuService.MenuState state;
        try {
            state = menus.state(playerId);
            snapshot = config.get();
        } catch (Throwable failure) {
            failures.accept("could not read the config for the claim menu of " + playerId, failure);
            return;
        }
        claims.owed(playerId, snapshot).whenComplete((owed, failure) -> {
            if (failure != null) {
                // The read reported its failure to the sink.
                return;
            }
            if (owed.isEmpty()) {
                adapter.forPlayer(playerId).run(whenNothing);
                return;
            }
            Session session;
            try {
                session = new Session(playerId, snapshot, owed);
            } catch (Throwable failureToBuild) {
                failures.accept("could not build the claim menu for " + playerId, failureToBuild);
                return;
            }
            menus.openTracked(playerId, render(session), state).whenComplete((epoch, openFailure) -> {
                if (openFailure != null) {
                    failures.accept("could not open the claim menu for " + playerId, unwrap(openFailure));
                    return;
                }
                epoch.ifPresent(session::openedAs);
            });
        });
    }

    private void claim(Session session, String lootId) {
        UUID playerId = session.playerId;
        if (!claiming.add(playerId)) {
            return;
        }
        CompletableFuture<ClaimResult> result;
        try {
            result = lootId == null ? claims.claim(playerId) : claims.claim(playerId, lootId);
        } catch (RuntimeException failure) {
            claiming.remove(playerId);
            failures.accept("could not claim the summon items of " + playerId, failure);
            return;
        }
        result.whenComplete((done, failure) -> {
            // The claim reported its failure to the sink; the menu still re-reads what is really left.
            claiming.remove(playerId);
            refresh(session);
        });
    }

    private void refresh(Session session) {
        long sequence = session.beginRefresh();
        ConfigSnapshot snapshot;
        try {
            snapshot = config.get();
        } catch (RuntimeException failure) {
            failures.accept("could not read the config for the claim menu of " + session.playerId, failure);
            return;
        }
        try {
            claims.owed(session.playerId, snapshot).whenComplete((owed, failure) -> {
                if (failure != null) {
                    // The read reported its failure to the sink; leave the menu as it is, with the guard released.
                    return;
                }
                try {
                    if (owed.isEmpty()) {
                        session.closeIfCurrent(sequence);
                        return;
                    }
                    session.applyRefresh(sequence, owed, snapshot);
                } catch (Throwable failureToBuild) {
                    failures.accept("could not refresh the claim menu for " + session.playerId, failureToBuild);
                }
            });
        } catch (RuntimeException failure) {
            failures.accept("could not read the unclaimed summon items of " + session.playerId, failure);
        }
    }

    private MenuView render(Session session) {
        synchronized (session.lock) {
            return render(session, session.state);
        }
    }

    private MenuView render(Session session, State state) {
        ConfigSnapshot snapshot = state.snapshot;
        MenuPage layout = snapshot.claimMenu();
        List<MenuIcon> entries = new ArrayList<>();
        long total = 0L;
        for (UnclaimedSummonItem row : state.owed) {
            total += row.amount();
            Loot loot = row.lootMissing() ? null : snapshot.loot(row.lootId()).orElse(null);
            if (loot == null) {
                entries.add(new MenuIcon(missing(snapshot, row), Optional.empty(), Optional.empty()));
                continue;
            }
            Map<String, String> values = values(loot.displayName(), row.amount());
            ItemStack item;
            try {
                item = items.create(loot);
                item.setAmount(Math.min(row.amount(), STACK_LIMIT));
                decorate(item, text(snapshot, KEY_ENTRY_NAME, values), lines(snapshot, KEY_ENTRY_LORE, values));
            } catch (RuntimeException failure) {
                failures.accept("could not build the summon item of " + row.lootId() + " owed by "
                        + session.playerId, failure);
                entries.add(new MenuIcon(missing(snapshot, row), Optional.empty(), Optional.empty()));
                continue;
            }
            entries.add(new MenuIcon(item, Optional.of(() -> claim(session, row.lootId())), Optional.empty()));
        }

        String title = text(snapshot, KEY_TITLE, Map.of("amount", Long.toString(total)));
        MenuView view = renderer.render(MENU_ID, title, layout, entries, state.page, session::goToPage,
                key -> snapshot.message("", key));
        MenuButton claimAll = layout.buttons().get(CLAIM_ALL);
        MenuIcon icon = claimAll == null ? null : view.icons().get(claimAll.slot());
        if (icon == null) {
            return view;
        }
        ItemStack item = icon.item();
        decorate(item, text(snapshot, KEY_BUTTON_NAME, Map.of()), lines(snapshot, KEY_BUTTON_LORE, Map.of()));
        Map<Integer, MenuIcon> icons = new LinkedHashMap<>(view.icons());
        icons.put(claimAll.slot(), new MenuIcon(item, Optional.of(() -> claim(session, null)), icon.sound()));
        return new MenuView(view.title(), view.rows(), icons);
    }

    private ItemStack missing(ConfigSnapshot snapshot, UnclaimedSummonItem row) {
        Map<String, String> values = values(row.lootId(), row.amount());
        ItemStack barrier = new ItemStack(Material.BARRIER);
        decorate(barrier, text(snapshot, KEY_MISSING_NAME, values), lines(snapshot, KEY_MISSING_LORE, values));
        return barrier;
    }

    private static Map<String, String> values(String loot, int amount) {
        return Map.of("loot", loot, "amount", Integer.toString(amount));
    }

    private static String text(ConfigSnapshot snapshot, String key, Map<String, String> values) {
        return MessageRenderer.render(snapshot, "", key, values);
    }

    private static List<String> lines(ConfigSnapshot snapshot, String key, Map<String, String> values) {
        String prefix = snapshot.message("", KEY_PREFIX);
        List<String> lines = new ArrayList<>();
        for (String line : snapshot.messageLines("", key)) {
            if (line.isBlank()) {
                continue;
            }
            String rendered = line.replace("{prefix}", prefix);
            for (Map.Entry<String, String> value : values.entrySet()) {
                rendered = rendered.replace("{" + value.getKey() + "}", value.getValue());
            }
            lines.add(MessageText.color(rendered));
        }
        return lines;
    }

    private static long total(List<UnclaimedSummonItem> rows) {
        long total = 0L;
        for (UnclaimedSummonItem row : rows) {
            total += row.amount();
        }
        return total;
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

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private record State(ConfigSnapshot snapshot, List<UnclaimedSummonItem> owed, int page, long epoch) {

        private State withPage(int page) {
            return new State(snapshot, owed, page, epoch);
        }

        private State withEpoch(long epoch) {
            return new State(snapshot, owed, page, epoch);
        }
    }

    private final class Session {

        private final UUID playerId;
        private final Object lock = new Object();
        private State state;
        private long revision;
        private long refreshSequence;
        private long appliedRefresh;
        private long replacementSequence;
        private long replacementRevision;
        private boolean replacing;
        private boolean closeWhenReplaced;
        private String visibleTitle;

        private Session(UUID playerId, ConfigSnapshot snapshot, List<UnclaimedSummonItem> owed) {
            this.playerId = playerId;
            this.state = new State(snapshot, List.copyOf(owed), 0, -1L);
        }

        private void openedAs(long openedEpoch) {
            synchronized (lock) {
                state = state.withEpoch(openedEpoch);
                visibleTitle = title(state);
                revision++;
            }
        }

        private long beginRefresh() {
            synchronized (lock) {
                return ++refreshSequence;
            }
        }

        private void applyRefresh(long sequence, List<UnclaimedSummonItem> rows, ConfigSnapshot current) {
            synchronized (lock) {
                if (sequence < appliedRefresh || state.epoch < 0L) {
                    return;
                }
                appliedRefresh = sequence;
                int page = renderer.clampPage(current.claimMenu(), rows.size(), state.page);
                state = new State(current, List.copyOf(rows), page, state.epoch);
                if (!rows.isEmpty()) {
                    closeWhenReplaced = false;
                }
                if (replacing) {
                    revision++;
                    return;
                }
                long rendered = ++revision;
                MenuView view = render(this, state);
                if (revision != rendered || sequence < appliedRefresh) {
                    return;
                }
                presentLocked(view);
            }
        }

        private void closeIfCurrent(long sequence) {
            synchronized (lock) {
                if (sequence < appliedRefresh || state.epoch < 0L) {
                    return;
                }
                appliedRefresh = sequence;
                if (replacing) {
                    closeWhenReplaced = true;
                    return;
                }
                menus.closeIf(playerId, state.epoch);
            }
        }

        private void goToPage(int destination) {
            synchronized (lock) {
                int page = renderer.clampPage(state.snapshot.claimMenu(), state.owed.size(), destination);
                state = state.withPage(page);
                if (state.epoch < 0L) {
                    return;
                }
                if (replacing) {
                    revision++;
                    return;
                }
                long rendered = ++revision;
                MenuView view = render(this, state);
                if (revision != rendered) {
                    return;
                }
                presentLocked(view);
            }
        }

        private void presentLocked(MenuView view) {
            if (replacing || state.epoch < 0L) {
                return;
            }
            String requestedTitle = view.title();
            if (Objects.equals(visibleTitle, requestedTitle)) {
                menus.refreshIf(playerId, state.epoch, view);
                return;
            }
            replacing = true;
            long token = ++replacementSequence;
            replacementRevision = revision;
            long expected = state.epoch;
            menus.replaceIf(playerId, expected, view).whenComplete((nextEpoch, failure) -> finishReplacement(
                    token, expected, requestedTitle, nextEpoch, failure));
        }

        private void finishReplacement(long token, long expected, String requestedTitle,
                                       Optional<Long> nextEpoch, Throwable failure) {
            synchronized (lock) {
                if (token != replacementSequence || !replacing || state.epoch != expected) {
                    return;
                }
                replacing = false;
                if (failure != null) {
                    failures.accept("could not replace the claim menu for " + playerId, unwrap(failure));
                    if (closeWhenReplaced) {
                        closeWhenReplaced = false;
                        menus.closeIf(playerId, expected);
                    } else {
                        menus.refreshIf(playerId, expected, render(this, state));
                    }
                    return;
                }
                if (nextEpoch.isEmpty()) {
                    boolean close = closeWhenReplaced;
                    closeWhenReplaced = false;
                    if (close) {
                        menus.closeIf(playerId, expected);
                    } else {
                        menus.refreshIf(playerId, expected, render(this, state));
                    }
                    return;
                }
                boolean changedWhileReplacing = revision != replacementRevision;
                state = state.withEpoch(nextEpoch.orElseThrow());
                visibleTitle = requestedTitle;
                if (closeWhenReplaced) {
                    closeWhenReplaced = false;
                    menus.closeIf(playerId, state.epoch);
                    return;
                }
                if (!changedWhileReplacing) {
                    return;
                }
                revision++;
                long rendered = revision;
                MenuView latest = render(this, state);
                if (revision != rendered) {
                    return;
                }
                presentLocked(latest);
            }
        }

        private static String title(State state) {
            return text(state.snapshot, KEY_TITLE, Map.of("amount", Long.toString(total(state.owed))));
        }
    }
}
