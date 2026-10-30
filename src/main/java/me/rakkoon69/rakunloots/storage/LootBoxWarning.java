package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.Objects;
import java.util.Optional;

/**
 * A problem the store found while loading or flushing loot boxes. The store does not write to the log; the caller
 * reports these. Severities are the config severities, so the warning level filters them the same way.
 */
public final class LootBoxWarning implements Warning {

    public enum Kind implements WarningKind {
        /**
         * The box's loot is not in the current config. The box loaded flagged and its row stays. Always high.
         */
        LOOT_MISSING(Severity.HIGH, "STORAGE_LOOT_MISSING"),
        /**
         * The box's world is not loaded. The box loaded flagged and its row stays. Always high.
         */
        WORLD_NOT_LOADED(Severity.HIGH),
        /**
         * One stored stack could not be read. It was left out of the box, and its bytes stay until that box is
         * saved again. Always high.
         */
        UNREADABLE_STACK(Severity.HIGH),
        /**
         * A stored box could not be read, such as an id that is not a UUID. It did not load, and its row stays.
         * Always high.
         */
        UNREADABLE_BOX(Severity.HIGH),
        /**
         * A changed box or a queued delete could not be written. It stays pending and the next flush retries it.
         * Always severe.
         */
        FLUSH_FAILED(Severity.SEVERE);

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

    private final Severity severity;
    private final Kind kind;
    private final String boxId;
    private final Optional<String> lootId;
    private final Optional<Throwable> cause;

    private LootBoxWarning(
            Severity severity,
            Kind kind,
            String boxId,
            Optional<String> lootId,
            Optional<Throwable> cause
    ) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.boxId = Objects.requireNonNull(boxId, "boxId");
        this.lootId = Objects.requireNonNull(lootId, "lootId");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    static LootBoxWarning lootMissing(String boxId, String lootId) {
        return new LootBoxWarning(Severity.HIGH, Kind.LOOT_MISSING,
                boxId, Optional.of(lootId), Optional.empty());
    }

    static LootBoxWarning worldNotLoaded(String boxId, String lootId) {
        return new LootBoxWarning(Severity.HIGH, Kind.WORLD_NOT_LOADED,
                boxId, Optional.of(lootId), Optional.empty());
    }

    static LootBoxWarning unreadableStack(String boxId, Throwable cause) {
        return new LootBoxWarning(Severity.HIGH, Kind.UNREADABLE_STACK,
                boxId, Optional.empty(), Optional.of(cause));
    }

    static LootBoxWarning unreadableBox(String boxId, Throwable cause) {
        return new LootBoxWarning(Severity.HIGH, Kind.UNREADABLE_BOX,
                boxId, Optional.empty(), Optional.of(cause));
    }

    static LootBoxWarning flushFailed(String boxId, Throwable cause) {
        return new LootBoxWarning(Severity.SEVERE, Kind.FLUSH_FAILED,
                boxId, Optional.empty(), Optional.of(cause));
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    /**
     * The box id as stored. Text, since an unreadable box may not hold a UUID.
     */
    public String boxId() {
        return boxId;
    }

    public Optional<String> lootId() {
        return lootId;
    }

    /**
     * The failure behind this warning, when there was one.
     */
    @Override
    public Optional<Throwable> cause() {
        return cause;
    }

    @Override
    public Optional<String> subject() {
        return lootId.or(() -> Optional.of(boxId));
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("box", Optional.of(boxId)),
                Warning.field("loot", lootId),
                Warning.field("cause", cause)
        );
    }
}
