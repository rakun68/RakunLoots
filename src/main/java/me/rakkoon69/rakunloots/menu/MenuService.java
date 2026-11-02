package me.rakkoon69.rakunloots.menu;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.menu.MenuSound;
import me.rakkoon69.rakunloots.server.MenuHandle;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The one owner of the menus a player has open. It tracks the handle each menu was opened as, so a close event for an
 * old menu cannot retire its replacement, and retires an open that lands after its menu was closed or replaced.
 */
public final class MenuService {

    private record Entry(MenuHandle handle, MenuView view, MenuView visible, long visibleVersion) {

        private Entry withView(MenuView view) {
            return new Entry(handle, view, visible, visibleVersion);
        }

        private Entry withVisible(MenuView visible, long visibleVersion) {
            return new Entry(handle, view, visible, visibleVersion);
        }
    }

    private record Dispatch(MenuHandle handle, CompletableFuture<Void> tail) {
    }

    /**
     * A player's menu state when an open starts: the service generation and the player's menu epoch. Passing it back
     * to {@link #openTracked(UUID, MenuView, MenuState)} refuses an open that lost a race with a reload, close, or
     * replacement.
     */
    public record MenuState(long generation, long epoch) {
    }

    private static final class OpenAttempt {
        private final MenuHandle old;
        private final long epoch;
        private final long generation;
        private final boolean transferRetirements;
        private boolean oldClosed;
        private MenuHandle candidate;
        private boolean candidateClosed;

        private OpenAttempt(MenuHandle old, long epoch, long generation, boolean transferRetirements) {
            this.old = old;
            this.epoch = epoch;
            this.generation = generation;
            this.transferRetirements = transferRetirements;
        }
    }

    private final ServerAdapter adapter;
    private final MenuRenderer renderer;
    private final Object lock = new Object();
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, OpenAttempt> openings = new HashMap<>();
    private final Map<UUID, Dispatch> dispatches = new HashMap<>();
    private final Map<UUID, Long> epochs = new HashMap<>();
    private final Map<UUID, Map<Long, List<Runnable>>> retirements = new HashMap<>();
    private long generation;
    private long viewVersion;

    public MenuService(ServerAdapter adapter) {
        this(adapter, null);
    }

    public MenuService(ServerAdapter adapter, MenuRenderer renderer) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.renderer = renderer;
    }

    public CompletableFuture<Void> open(UUID playerId, MenuView view) {
        return openTracked(playerId, view).thenApply(epoch -> null);
    }

    /**
     * Opens a menu and returns the generation it was installed as, or empty when the player did not see it. A later
     * refresh or close can name that generation, so a completion that arrives after the menu was closed, paged away,
     * or replaced does not touch the newer view.
     */
    public CompletableFuture<Optional<Long>> openTracked(UUID playerId, MenuView view) {
        return openTracked(playerId, view, state(playerId));
    }

    /**
     * Opens a menu only if the player's menu state still matches the state captured when the open began.
     */
    public CompletableFuture<Optional<Long>> openTracked(UUID playerId, MenuView view, MenuState expected) {
        return openTracked(playerId, view, expected, false);
    }

    private CompletableFuture<Optional<Long>> openTracked(UUID playerId, MenuView view, MenuState expected,
                                                          boolean transferRetirements) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(expected, "expected");
        Map<Integer, ItemStack> contents = contents(view);
        OpenAttempt attempt;
        MenuHandle made;
        synchronized (lock) {
            if (generation != expected.generation() || epochs.getOrDefault(playerId, 0L) != expected.epoch()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            Entry current = entries.get(playerId);
            attempt = new OpenAttempt(current == null ? null : current.handle(), expected.epoch(),
                    expected.generation(), transferRetirements);
            OpenAttempt previous = openings.get(playerId);
            if (previous != null && previous.oldClosed && previous.old == attempt.old) {
                attempt.oldClosed = true;
            }
            if (previous != null && previous.candidate != null && !previous.candidateClosed) {
                previous.candidateClosed = true;
                close(previous.candidate);
            }
            openings.put(playerId, attempt);
        }
        try {
            made = adapter.createMenu(playerId, view.title(), view.size(), contents);
        } catch (RuntimeException failure) {
            forget(playerId, attempt);
            return CompletableFuture.failedFuture(failure);
        }
        boolean superseded;
        synchronized (lock) {
            superseded = openings.get(playerId) != attempt;
            if (superseded) {
                attempt.candidateClosed = true;
                close(made);
            } else {
                attempt.candidate = made;
            }
        }
        MenuHandle expectedOld = attempt.transferRetirements ? attempt.old : null;
        return adapter.openMenu(playerId, made, expectedOld)
                .handle((opened, failure) -> new Opened(opened, failure))
                .thenCompose(opened -> {
                    if (opened.failure() != null) {
                        forget(playerId, attempt);
                        return CompletableFuture.failedFuture(unwrap(opened.failure()));
                    }
                    if (opened.handle().isEmpty()) {
                        forget(playerId, attempt);
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    return land(playerId, view, attempt, opened.handle().orElseThrow());
                });
    }

    private record Opened(Optional<MenuHandle> handle, Throwable failure) {
    }

    private CompletableFuture<Optional<Long>> land(UUID playerId, MenuView view, OpenAttempt attempt, MenuHandle next) {
        boolean installed;
        long installedEpoch = 0L;
        List<Runnable> retired = List.of();
        synchronized (lock) {
            Entry current = entries.get(playerId);
            boolean sameOld = current == null ? attempt.old == null : current.handle() == attempt.old;
            installed = openings.get(playerId) == attempt
                    && attempt.generation == generation
                    && attempt.epoch == epochs.getOrDefault(playerId, 0L)
                    && sameOld;
            if (installed) {
                openings.remove(playerId);
                entries.put(playerId, new Entry(next, view, view, ++viewVersion));
                dispatches.remove(playerId);
                installedEpoch = epochs.merge(playerId, 1L, Long::sum);
                if (attempt.transferRetirements) {
                    moveRetirements(playerId, attempt.epoch, installedEpoch);
                } else {
                    retired = takeRetirements(playerId, attempt.epoch);
                }
            } else if (attempt.oldClosed && openings.get(playerId) == null) {
                retired = forgetLocked(playerId, attempt);
            }
        }
        runAll(retired);
        boolean landed = installed;
        long epoch = installedEpoch;
        MenuHandle close = !installed && !attempt.candidateClosed ? next : installed ? attempt.old : null;
        return close(close).thenApply(done -> landed ? Optional.of(epoch) : Optional.empty());
    }

    private void forget(UUID playerId, OpenAttempt attempt) {
        List<Runnable> retired;
        synchronized (lock) {
            retired = forgetLocked(playerId, attempt);
        }
        runAll(retired);
    }

    private List<Runnable> forgetLocked(UUID playerId, OpenAttempt attempt) {
        OpenAttempt pending = openings.get(playerId);
        if (pending == attempt) {
            openings.remove(playerId);
            pending = null;
        }
        Entry current = entries.get(playerId);
        if (pending == null && attempt.oldClosed && current != null && current.handle() == attempt.old) {
            entries.remove(playerId);
            dispatches.remove(playerId);
            epochs.merge(playerId, 1L, Long::sum);
            return takeRetirements(playerId, attempt.epoch);
        }
        return List.of();
    }

    /**
     * Registers an action for the menu installed as {@code epoch}. When that epoch was already retired, the action
     * runs at once and this returns false, so a late registration cannot resurrect a session.
     */
    public boolean onRetired(UUID playerId, long epoch, Runnable action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        boolean active;
        synchronized (lock) {
            Entry current = entries.get(playerId);
            active = current != null && epochs.getOrDefault(playerId, 0L) == epoch;
            if (active) {
                retirements.computeIfAbsent(playerId, id -> new HashMap<>())
                        .computeIfAbsent(epoch, installed -> new ArrayList<>()).add(action);
            }
        }
        if (!active) {
            action.run();
        }
        return active;
    }

    private void moveRetirements(UUID playerId, long from, long to) {
        List<Runnable> moved = takeRetirements(playerId, from);
        if (moved.isEmpty()) {
            return;
        }
        retirements.computeIfAbsent(playerId, id -> new HashMap<>())
                .computeIfAbsent(to, installed -> new ArrayList<>()).addAll(moved);
    }

    private List<Runnable> takeRetirements(UUID playerId, long epoch) {
        Map<Long, List<Runnable>> byEpoch = retirements.get(playerId);
        if (byEpoch == null) {
            return List.of();
        }
        List<Runnable> taken = byEpoch.remove(epoch);
        if (byEpoch.isEmpty()) {
            retirements.remove(playerId);
        }
        return taken == null ? List.of() : taken;
    }

    private List<Runnable> takeAllRetirements(UUID playerId) {
        Map<Long, List<Runnable>> byEpoch = retirements.remove(playerId);
        if (byEpoch == null) {
            return List.of();
        }
        List<Runnable> all = new ArrayList<>();
        byEpoch.values().forEach(all::addAll);
        return all;
    }

    private static void runAll(List<Runnable> actions) {
        for (Runnable action : actions) {
            action.run();
        }
    }

    /**
     * The player's current menu state, read atomically with the generation.
     */
    public MenuState state(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (lock) {
            return new MenuState(generation, epochs.getOrDefault(playerId, 0L));
        }
    }

    public CompletableFuture<Void> refresh(UUID playerId, MenuView view) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(view, "view");
        Entry current;
        synchronized (lock) {
            current = entries.get(playerId);
            if (current == null) {
                return CompletableFuture.completedFuture(null);
            }
            entries.put(playerId, current.withView(view));
        }
        return dispatch(playerId, current.handle(), view);
    }

    /**
     * Refreshes the menu only while it is still the one opened as {@code epoch}. A menu closed, reopened, replaced, or
     * closed by a reload since then is left alone.
     */
    public CompletableFuture<Void> refreshIf(UUID playerId, long epoch, MenuView view) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(view, "view");
        Entry current;
        synchronized (lock) {
            current = entries.get(playerId);
            if (current == null || epochs.getOrDefault(playerId, 0L) != epoch) {
                return CompletableFuture.completedFuture(null);
            }
            entries.put(playerId, current.withView(view));
        }
        return dispatch(playerId, current.handle(), view);
    }

    /**
     * Replaces the menu with a new inventory only while it is still the one opened as {@code epoch}. A refresh
     * updates the existing inventory in place, so it cannot change the title or size; this reopens instead, which
     * also gives the caller the new epoch. A retirement registered for the old epoch moves to the installed epoch, so
     * navigation keeps one logical session. Empty when the menu was closed, reopened, or replaced since that epoch.
     */
    public CompletableFuture<Optional<Long>> replaceIf(UUID playerId, long epoch, MenuView view) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(view, "view");
        MenuState expected;
        synchronized (lock) {
            Entry current = entries.get(playerId);
            if (current == null || epochs.getOrDefault(playerId, 0L) != epoch) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            expected = new MenuState(generation, epoch);
        }
        return openTracked(playerId, view, expected, true);
    }

    /**
     * Closes the menu only while it is still the one opened as {@code epoch}.
     */
    public CompletableFuture<Void> closeIf(UUID playerId, long epoch) {
        Objects.requireNonNull(playerId, "playerId");
        Entry current;
        OpenAttempt opening;
        CompletableFuture<Void> pendingClose = CompletableFuture.completedFuture(null);
        List<Runnable> retired;
        synchronized (lock) {
            current = entries.get(playerId);
            if (current == null || epochs.getOrDefault(playerId, 0L) != epoch) {
                return CompletableFuture.completedFuture(null);
            }
            entries.remove(playerId);
            opening = openings.remove(playerId);
            if (opening != null && opening.candidate != null && !opening.candidateClosed) {
                opening.candidateClosed = true;
                pendingClose = close(opening.candidate);
            }
            dispatches.remove(playerId);
            epochs.merge(playerId, 1L, Long::sum);
            retired = takeRetirements(playerId, epoch);
        }
        runAll(retired);
        return CompletableFuture.allOf(close(current.handle()), pendingClose);
    }

    public CompletableFuture<Void> click(UUID playerId, int slot) {
        return click(playerId, slot, null, -1L);
    }

    /**
     * Runs a click on the player's thread on the next tick. The physically visible menu and its version are captured
     * now, so a refresh that replaces the view before the tick cannot redirect this click to a different icon action.
     * A click that arrives after its menu was replaced, closed, or visibly refreshed does nothing.
     */
    public CompletableFuture<Void> clickLater(UUID playerId, int slot) {
        Objects.requireNonNull(playerId, "playerId");
        MenuHandle expected;
        long expectedVersion;
        synchronized (lock) {
            Entry current = entries.get(playerId);
            if (current == null) {
                return CompletableFuture.completedFuture(null);
            }
            expected = current.handle();
            expectedVersion = current.visibleVersion();
        }
        return adapter.runOnPlayerNextTick(playerId, () -> click(playerId, slot, expected, expectedVersion));
    }

    private CompletableFuture<Void> click(UUID playerId, int slot, MenuHandle expected, long expectedVersion) {
        Objects.requireNonNull(playerId, "playerId");
        Entry current;
        synchronized (lock) {
            current = entries.get(playerId);
            if (current != null && expected != null
                    && (current.handle() != expected || current.visibleVersion() != expectedVersion)) {
                current = null;
            }
        }
        if (current == null || slot < 0 || slot >= current.visible().size()) {
            return CompletableFuture.completedFuture(null);
        }
        MenuIcon icon = current.visible().icons().get(slot);
        if (icon == null || icon.action().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            icon.action().orElseThrow().run();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        Optional<MenuSound> sound = icon.sound();
        if (sound.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        MenuSound played = sound.get();
        return adapter.playSound(playerId, played.key(), played.volume(), played.pitch()).thenApply(heard -> null);
    }

    public boolean isPhysicalOpen(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return adapter.isPhysicalOpenInProgress(playerId);
    }

    /**
     * Records the internal close of an old menu caused by the adapter's physical replacement open.
     */
    public void replacementClosed(UUID playerId, MenuHandle menu) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(menu, "menu");
        synchronized (lock) {
            Entry current = entries.get(playerId);
            if (current == null || current.handle() != menu) {
                return;
            }
            OpenAttempt opening = openings.get(playerId);
            if (opening != null && opening.old == menu) {
                opening.oldClosed = true;
            }
        }
    }

    /**
     * Handles an unqualified/external close of a menu. This is terminal: it cancels any pending
     * replacement for this player, retires the epoch, and cleans up the session.
     */
    public void closed(UUID playerId, MenuHandle menu) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(menu, "menu");
        List<Runnable> retired = List.of();
        synchronized (lock) {
            Entry current = entries.get(playerId);
            if (current == null || current.handle() != menu) {
                return;
            }
            long epoch = epochs.getOrDefault(playerId, 0L);
            entries.remove(playerId);
            OpenAttempt opening = openings.remove(playerId);
            if (opening != null && opening.candidate != null && !opening.candidateClosed) {
                opening.candidateClosed = true;
                close(opening.candidate);
            }
            dispatches.remove(playerId);
            epochs.merge(playerId, 1L, Long::sum);
            retired = takeRetirements(playerId, epoch);
        }
        runAll(retired);
    }

    /**
     * Closes one player's menu and cancels any open still waiting to be shown. Other players keep theirs.
     */
    public CompletableFuture<Void> close(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Entry current;
        OpenAttempt opening;
        MenuHandle pending;
        CompletableFuture<Void> pendingClose = CompletableFuture.completedFuture(null);
        List<Runnable> retired;
        synchronized (lock) {
            current = entries.remove(playerId);
            opening = openings.remove(playerId);
            pending = opening == null || opening.candidateClosed ? null : opening.candidate;
            if (opening != null && pending != null) {
                opening.candidateClosed = true;
                pendingClose = close(pending);
            }
            dispatches.remove(playerId);
            epochs.merge(playerId, 1L, Long::sum);
            retired = takeAllRetirements(playerId);
        }
        runAll(retired);
        MenuHandle menu = current != null ? current.handle() : opening == null ? null : opening.old;
        CompletableFuture<Void> closeCurrent = close(menu);
        return CompletableFuture.allOf(closeCurrent, pendingClose);
    }

    public CompletableFuture<Void> closeAll() {
        List<Entry> closing;
        List<CompletableFuture<Void>> pendingCloses = new ArrayList<>();
        List<Runnable> retired = new ArrayList<>();
        synchronized (lock) {
            closing = List.copyOf(entries.values());
            for (OpenAttempt attempt : openings.values()) {
                if (attempt.candidate != null && !attempt.candidateClosed) {
                    attempt.candidateClosed = true;
                    pendingCloses.add(close(attempt.candidate));
                }
            }
            for (UUID playerId : entries.keySet()) {
                retired.addAll(takeAllRetirements(playerId));
            }
            entries.clear();
            openings.clear();
            dispatches.clear();
            generation++;
            epochs.replaceAll((playerId, epoch) -> epoch + 1);
        }
        runAll(retired);
        CompletableFuture<?>[] closes = java.util.stream.Stream.concat(
                        closing.stream().map(entry -> close(entry.handle())),
                        pendingCloses.stream())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(closes);
    }

    public void reload(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (renderer != null) {
            renderer.resetWarnings();
        }
        closeAll();
    }

    /**
     * Serializes physical menu updates for one player's current handle. Paper may run an update from a caller thread
     * after this method returns, so an older update can land after a newer one. Chaining each update's completion
     * means the newer view is applied after the older view, and a queued update also rechecks the handle at the moment
     * it actually runs.
     */
    private CompletableFuture<Void> dispatch(UUID playerId, MenuHandle handle, MenuView view) {
        Dispatch next;
        CompletableFuture<Void> tail;
        synchronized (lock) {
            Dispatch prior = dispatches.get(playerId);
            CompletableFuture<Void> previous = prior != null && prior.handle() == handle
                    ? prior.tail()
                    : CompletableFuture.completedFuture(null);
            tail = previous.handle((done, failure) -> null)
                    .thenCompose(done -> applyDispatch(playerId, handle, view));
            next = new Dispatch(handle, tail);
            dispatches.put(playerId, next);
        }
        tail.whenComplete((done, failure) -> {
            synchronized (lock) {
                if (dispatches.get(playerId) == next) {
                    dispatches.remove(playerId);
                }
            }
        });
        return tail;
    }

    private CompletableFuture<Void> applyDispatch(UUID playerId, MenuHandle handle, MenuView view) {
        synchronized (lock) {
            Entry current = entries.get(playerId);
            if (current == null || current.handle() != handle) {
                return CompletableFuture.completedFuture(null);
            }
        }
        CompletableFuture<Void> applied;
        try {
            applied = adapter.updateMenu(handle, contents(view));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return applied.thenRun(() -> markVisible(playerId, handle, view));
    }

    private void markVisible(UUID playerId, MenuHandle handle, MenuView view) {
        synchronized (lock) {
            Entry current = entries.get(playerId);
            if (current == null || current.handle() != handle || current.visible() == view) {
                return;
            }
            entries.put(playerId, current.withVisible(view, ++viewVersion));
        }
    }

    private CompletableFuture<Void> close(MenuHandle menu) {
        if (menu == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return adapter.closeMenu(menu);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static Map<Integer, ItemStack> contents(MenuView view) {
        Map<Integer, ItemStack> contents = new HashMap<>();
        view.icons().forEach((slot, icon) -> contents.put(slot, icon.item()));
        return contents;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }
}
