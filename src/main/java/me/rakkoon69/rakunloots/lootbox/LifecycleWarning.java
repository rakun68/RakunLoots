package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A problem the loot box lifecycle found. The lifecycle does not write to the log; it hands these to its warning sink.
 * Severities are the config severities, so the warning level filters them the same way.
 */
public final class LifecycleWarning implements Warning {

    public enum Kind implements WarningKind {
        /**
         * A fixed slot was taken by an earlier stack or lies outside the largest inventory. The stack went into a
         * random free slot. Always low.
         */
        SLOT_MOVED(Severity.LOW),
        /**
         * A stack found no free slot and was left out of the box. Always high.
         */
        NO_ROOM(Severity.HIGH),
        /**
         * The server refused the loot's presentation block or its head data, so nothing was summoned. Always high.
         */
        BLOCK_REFUSED(Severity.HIGH),
        /**
         * The store could not load at start. No stored box is registered, and their rows stay. Always severe.
         */
        LOAD_FAILED(Severity.SEVERE),
        /**
         * A flush failed as a whole. The boxes are marked again on the next save. Always severe.
         */
        SAVE_FAILED(Severity.SEVERE),
        /**
         * A loot box's block was no longer the block it was placed as, such as after another plugin replaced it. The
         * box ended as a break, without the block item, and the block found was left in place. Carries the box id,
         * loot id, world, position, and the block found. Always high.
         */
        CHANGED_BLOCK(Severity.HIGH),
        /**
         * One box observer threw while the lifecycle reported a box or a summon to it. The lifecycle and the other
         * observers went on as if it had not. Carries the box id (not for a summon event), loot id, world, position,
         * and the failure. Always high.
         */
        OBSERVER_FAILED(Severity.HIGH),
        /**
         * A step the lifecycle chains on the server adapter completed exceptionally, and would have gone unseen. The
         * lifecycle went on as before. Carries the box id, loot id, world, position, the step, and the failure.
         * Always high.
         */
        ASYNC_FAILED(Severity.HIGH),
        /**
         * A reload left live loot boxes whose loot is no longer in the config. Each runs on until it ends, as ADR 0014
         * says. One warning per reload carries how many boxes and which loot ids. Always high.
         */
        LOOT_MISSING(Severity.HIGH, "LIFECYCLE_LOOT_MISSING"),
        /**
         * A summon failed after its block write began, such as the roll throwing, or its search failed. The summon was
         * abandoned and its spot released. Carries the loot id and the failure. Always high.
         */
        SUMMON_FAILED(Severity.HIGH),
        /**
         * A step of a world timer summon or of its broadcast completed exceptionally, and would have gone unseen. The
         * timer went on. Carries the step, which names the world, and the failure. Always high.
         */
        TIMER_FAILED(Severity.HIGH);

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
    private final Optional<String> lootId;
    private final OptionalInt slot;
    private final Optional<Throwable> cause;
    private final Optional<UUID> boxId;
    private final Optional<UUID> worldKey;
    private final Optional<BlockPosition> position;
    private final Optional<String> blockFound;
    private final Optional<String> step;
    private final int boxCount;
    private final List<String> lootIds;

    private LifecycleWarning(
            Severity severity,
            Kind kind,
            Optional<String> lootId,
            OptionalInt slot,
            Optional<Throwable> cause
    ) {
        this(severity, kind, lootId, slot, cause, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, List.of());
    }

    private LifecycleWarning(
            Severity severity,
            Kind kind,
            Optional<String> lootId,
            OptionalInt slot,
            Optional<Throwable> cause,
            Optional<UUID> boxId,
            Optional<UUID> worldKey,
            Optional<BlockPosition> position,
            Optional<String> blockFound,
            Optional<String> step,
            int boxCount,
            List<String> lootIds
    ) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.lootId = Objects.requireNonNull(lootId, "lootId");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.boxId = Objects.requireNonNull(boxId, "boxId");
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        this.position = Objects.requireNonNull(position, "position");
        this.blockFound = Objects.requireNonNull(blockFound, "blockFound");
        this.step = Objects.requireNonNull(step, "step");
        this.boxCount = boxCount;
        this.lootIds = List.copyOf(lootIds);
    }

    static LifecycleWarning slotMoved(String lootId, int slot) {
        return new LifecycleWarning(Severity.LOW, Kind.SLOT_MOVED, Optional.of(lootId),
                OptionalInt.of(slot), Optional.empty());
    }

    static LifecycleWarning noRoom(String lootId) {
        return new LifecycleWarning(Severity.HIGH, Kind.NO_ROOM, Optional.of(lootId),
                OptionalInt.empty(), Optional.empty());
    }

    static LifecycleWarning blockRefused(String lootId, Throwable cause) {
        return new LifecycleWarning(Severity.HIGH, Kind.BLOCK_REFUSED, Optional.of(lootId),
                OptionalInt.empty(), Optional.of(cause));
    }

    public static LifecycleWarning summonFailed(String lootId, Throwable cause) {
        return new LifecycleWarning(Severity.HIGH, Kind.SUMMON_FAILED, Optional.of(lootId),
                OptionalInt.empty(), Optional.of(cause));
    }

    public static LifecycleWarning timerFailed(String step, Throwable cause) {
        return new LifecycleWarning(Severity.HIGH, Kind.TIMER_FAILED, Optional.empty(),
                OptionalInt.empty(), Optional.of(cause), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(step), 0, List.of());
    }

    static LifecycleWarning loadFailed(Throwable cause) {
        return new LifecycleWarning(Severity.SEVERE, Kind.LOAD_FAILED, Optional.empty(),
                OptionalInt.empty(), Optional.of(cause));
    }

    static LifecycleWarning saveFailed(Throwable cause) {
        return new LifecycleWarning(Severity.SEVERE, Kind.SAVE_FAILED, Optional.empty(),
                OptionalInt.empty(), Optional.of(cause));
    }

    static LifecycleWarning changedBlock(LootBox box, String blockFound) {
        return new LifecycleWarning(Severity.HIGH, Kind.CHANGED_BLOCK, Optional.of(box.lootId()),
                OptionalInt.empty(), Optional.empty(), Optional.of(box.boxId()), Optional.of(box.worldKey()),
                Optional.of(box.position()), Optional.of(blockFound), Optional.empty(), 0, List.of());
    }

    static LifecycleWarning observerFailed(LootBox box, Throwable cause) {
        return new LifecycleWarning(Severity.HIGH, Kind.OBSERVER_FAILED, Optional.of(box.lootId()),
                OptionalInt.empty(), Optional.of(cause), Optional.of(box.boxId()), Optional.of(box.worldKey()),
                Optional.of(box.position()), Optional.empty(), Optional.empty(), 0, List.of());
    }

    static LifecycleWarning observerFailed(String lootId, UUID worldKey, BlockPosition position, Throwable cause) {
        return new LifecycleWarning(Severity.HIGH, Kind.OBSERVER_FAILED, Optional.of(lootId),
                OptionalInt.empty(), Optional.of(cause), Optional.empty(), Optional.of(worldKey),
                Optional.of(position), Optional.empty(), Optional.empty(), 0, List.of());
    }

    static LifecycleWarning asyncFailed(LootBox box, String step, Throwable cause) {
        return new LifecycleWarning(Severity.HIGH, Kind.ASYNC_FAILED, Optional.of(box.lootId()),
                OptionalInt.empty(), Optional.of(cause), Optional.of(box.boxId()), Optional.of(box.worldKey()),
                Optional.of(box.position()), Optional.empty(), Optional.of(step), 0, List.of());
    }

    static LifecycleWarning lootMissing(int boxCount, List<String> lootIds) {
        return new LifecycleWarning(Severity.HIGH, Kind.LOOT_MISSING, Optional.empty(),
                OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), boxCount, lootIds);
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    public Optional<String> lootId() {
        return lootId;
    }

    /**
     * The fixed slot a moved stack asked for.
     */
    public OptionalInt slot() {
        return slot;
    }

    /**
     * The failure behind this warning, when there was one.
     */
    @Override
    public Optional<Throwable> cause() {
        return cause;
    }

    /**
     * The loot box this warning is about.
     */
    public Optional<UUID> boxId() {
        return boxId;
    }

    /**
     * The world of the block this warning is about.
     */
    public Optional<UUID> worldKey() {
        return worldKey;
    }

    /**
     * The block this warning is about.
     */
    public Optional<BlockPosition> position() {
        return position;
    }

    /**
     * The namespaced id of the block found where a loot box's placed block should be.
     */
    public Optional<String> blockFound() {
        return blockFound;
    }

    /**
     * The step of the lifecycle that failed, such as {@code dropStacks}.
     */
    public Optional<String> step() {
        return step;
    }

    /**
     * How many live boxes a {@link Kind#LOOT_MISSING} warning is about.
     */
    public int boxCount() {
        return boxCount;
    }

    /**
     * The ids, sorted, of the loots a {@link Kind#LOOT_MISSING} warning is about.
     */
    public List<String> lootIds() {
        return lootIds;
    }

    @Override
    public Optional<String> subject() {
        return lootId.or(() -> boxId.map(Object::toString));
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("loot", lootId),
                Warning.field("slot", slot.isPresent() ? Optional.of(slot.getAsInt()) : Optional.empty()),
                Warning.field("box", boxId),
                Warning.field("world", worldKey),
                Warning.field("position", position.map(p -> p.x() + ", " + p.y() + ", " + p.z())),
                Warning.field("blockFound", blockFound),
                Warning.field("step", step),
                boxCount > 0 ? "boxes=" + boxCount : "",
                lootIds.isEmpty() ? "" : "loots=" + String.join(", ", lootIds),
                Warning.field("cause", cause)
        );
    }
}
