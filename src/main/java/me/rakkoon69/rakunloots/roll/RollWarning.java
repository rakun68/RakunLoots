package me.rakkoon69.rakunloots.roll;

import me.rakkoon69.rakunloots.items.ItemWarning;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.Objects;
import java.util.Optional;

/**
 * A roll-time problem for one content entry. The roll does not write to the log; the caller reports these.
 */
public final class RollWarning implements Warning {

    public enum Kind implements WarningKind {
        /**
         * The entry's item reference missed. The entry was left out and no replacement was drawn. Always high.
         */
        MISSED_ENTRY(Severity.HIGH),
        /**
         * The entry's item resolved, with a warning from its item source.
         */
        ITEM_WARNING(Severity.HIGH);

        private final Severity defaultSeverity;

        Kind(Severity defaultSeverity) {
            this.defaultSeverity = defaultSeverity;
        }

        @Override
        public Severity defaultSeverity() {
            return defaultSeverity;
        }
    }

    private final Severity severity;
    private final Kind kind;
    private final String lootId;
    private final String entryId;
    private final ItemWarning cause;

    private RollWarning(Severity severity, Kind kind, String lootId, String entryId, ItemWarning cause) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.lootId = Objects.requireNonNull(lootId, "lootId");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    static RollWarning missedEntry(String lootId, String entryId, ItemWarning cause) {
        return new RollWarning(Severity.HIGH, Kind.MISSED_ENTRY, lootId, entryId, cause);
    }

    static RollWarning itemWarning(String lootId, String entryId, ItemWarning cause) {
        return new RollWarning(cause.severity(), Kind.ITEM_WARNING, lootId, entryId, cause);
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    public String lootId() {
        return lootId;
    }

    public String entryId() {
        return entryId;
    }

    /**
     * The item source's warning behind this one.
     */
    public ItemWarning itemCause() {
        return cause;
    }

    @Override
    public Optional<String> subject() {
        return Optional.of(lootId);
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("loot", Optional.of(lootId)),
                Warning.field("entry", Optional.of(entryId)),
                "item=" + cause.kind(),
                Warning.field("detail", cause.detail())
        );
    }

    @Override
    public Optional<Throwable> cause() {
        return Optional.empty();
    }
}
