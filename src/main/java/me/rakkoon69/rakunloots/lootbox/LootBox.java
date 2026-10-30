package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.roll.RolledContents;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.storage.LootBoxRecord;
import me.rakkoon69.rakunloots.storage.SummonKind;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * One live loot box: its id, loot id, owner, block, placed block, remaining timers, and rolled contents with a
 * concrete slot on every stack. Immutable, so a save or a query reads one box as it was at one moment; the lifecycle
 * swaps in a new one on each change. Open mode, lock length, life, loot end rules, and presentation come from the
 * current loot.
 */
public final class LootBox {

    private final UUID boxId;
    private final String lootId;
    private final Optional<UUID> owner;
    private final UUID worldKey;
    private final BlockPosition position;
    private final String placedBlockId;
    private final OptionalInt remainingLifeSeconds;
    private final int remainingLockSeconds;
    private final RolledContents contents;
    private final SummonKind source;

    LootBox(
            UUID boxId,
            String lootId,
            Optional<UUID> owner,
            UUID worldKey,
            BlockPosition position,
            String placedBlockId,
            OptionalInt remainingLifeSeconds,
            int remainingLockSeconds,
            RolledContents contents,
            SummonKind source
    ) {
        this.boxId = Objects.requireNonNull(boxId, "boxId");
        this.lootId = Objects.requireNonNull(lootId, "lootId");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        this.position = Objects.requireNonNull(position, "position");
        this.placedBlockId = Objects.requireNonNull(placedBlockId, "placedBlockId");
        this.remainingLifeSeconds = Objects.requireNonNull(remainingLifeSeconds, "remainingLifeSeconds");
        this.remainingLockSeconds = remainingLockSeconds;
        this.contents = Objects.requireNonNull(contents, "contents");
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * The live box a stored box becomes. Its stacks keep the slots placed at summon.
     */
    static LootBox of(LootBoxRecord record) {
        return new LootBox(record.boxId(), record.lootId(), record.owner(), record.worldId(),
                new BlockPosition(record.x(), record.y(), record.z()), record.placedBlockId(),
                record.remainingLifeSeconds(), record.remainingLockSeconds(), record.contents(), record.source());
    }

    /**
     * This box with other contents left in it, such as after players took from its shared inventory.
     */
    LootBox withContents(RolledContents left) {
        return new LootBox(boxId, lootId, owner, worldKey, position, placedBlockId, remainingLifeSeconds,
                remainingLockSeconds, left, source);
    }

    /**
     * This box one second later: remaining life and remaining lock down by 1, down to 0. Unending life stays
     * unending.
     */
    LootBox oneSecondLater() {
        OptionalInt life = remainingLifeSeconds.isPresent()
                ? OptionalInt.of(Math.max(0, remainingLifeSeconds.getAsInt() - 1))
                : remainingLifeSeconds;
        return new LootBox(boxId, lootId, owner, worldKey, position, placedBlockId, life,
                Math.max(0, remainingLockSeconds - 1), contents, source);
    }

    /**
     * This box with its remaining life at most the given life. Unending life caps nothing; a box with unending life
     * takes a finite one.
     */
    LootBox lifeCappedAt(OptionalInt life) {
        if (life.isEmpty() || (remainingLifeSeconds.isPresent()
                && remainingLifeSeconds.getAsInt() <= life.getAsInt())) {
            return this;
        }
        return new LootBox(boxId, lootId, owner, worldKey, position, placedBlockId, life, remainingLockSeconds,
                contents, source);
    }

    /**
     * What a restart keeps of this box.
     */
    LootBoxRecord toRecord() {
        return new LootBoxRecord(boxId, lootId, source, owner, worldKey, position.x(), position.y(), position.z(),
                placedBlockId, remainingLifeSeconds, remainingLockSeconds, contents);
    }

    public UUID boxId() {
        return boxId;
    }

    public String lootId() {
        return lootId;
    }

    /**
     * The player the lock admits. Empty means no owner: only the bypass permission opens a locked box.
     */
    public Optional<UUID> owner() {
        return owner;
    }

    public UUID worldKey() {
        return worldKey;
    }

    public BlockPosition position() {
        return position;
    }

    /**
     * The namespaced block id the box was placed as. A changed block is checked against this, not against the
     * current loot.
     */
    public String placedBlockId() {
        return placedBlockId;
    }

    /**
     * Life left in seconds. Empty means unending life.
     */
    public OptionalInt remainingLifeSeconds() {
        return remainingLifeSeconds;
    }

    /**
     * Lock left in seconds. 0 means unlocked.
     */
    public int remainingLockSeconds() {
        return remainingLockSeconds;
    }

    /**
     * The rolled contents left in the box. Every stack has a slot.
     */
    public RolledContents contents() {
        return contents;
    }

    /**
     * How the box was summoned. {@link SummonKind#UNKNOWN} for a box stored before this was kept.
     */
    public SummonKind source() {
        return source;
    }
}
