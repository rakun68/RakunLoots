package me.rakkoon69.rakunloots.warning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the warning log saw since the last enable or reload, for the {@code warnings} command: per warning kind, its
 * severity, how many times it was raised, the first 3 distinct subjects, and whether any of them passed the warning
 * level. It holds at most one entry per kind, so its size is bounded by the number of kinds, and it is never stored.
 * Called from any thread.
 */
public final class WarningRecord {

    /**
     * How many subjects an entry keeps.
     */
    public static final int SUBJECTS_KEPT = 3;

    /**
     * One kind's entry. {@code subjects} are the first {@value #SUBJECTS_KEPT} distinct ones; {@code count} counts every
     * warning of the kind, so {@code count - subjects.size()} is how many more there were.
     */
    public record Entry(String kind, Severity severity, int count, List<String> subjects,
                        boolean passedLevel) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /**
     * Counts one warning.
     *
     * @param passedLevel whether the warning level let it reach the log
     */
    public synchronized void record(WarningKind kind, Severity severity, Optional<String> subject,
                                    boolean passedLevel) {
        Objects.requireNonNull(kind, "kind");
        record(kind.stableName(), severity, subject, passedLevel);
    }

    private synchronized void record(String kindName, Severity severity, Optional<String> subject,
                                    boolean passedLevel) {
        Objects.requireNonNull(kindName, "kindName");
        Objects.requireNonNull(severity, "severity");
        Entry old = entries.get(kindName);
        List<String> subjects = old == null ? new ArrayList<>() : new ArrayList<>(old.subjects());
        if (subject.isPresent() && subjects.size() < SUBJECTS_KEPT && !subjects.contains(subject.get())) {
            subjects.add(subject.get());
        }
        entries.put(kindName, new Entry(kindName, severity, (old == null ? 0 : old.count()) + 1, List.copyOf(subjects),
                passedLevel || (old != null && old.passedLevel())));
    }

    /**
     * Forgets everything, as enable and reload do.
     */
    public synchronized void clear() {
        entries.clear();
    }

    /**
     * The entries in the order their kinds were first raised.
     */
    public synchronized List<Entry> entries() {
        return List.copyOf(entries.values());
    }
}
