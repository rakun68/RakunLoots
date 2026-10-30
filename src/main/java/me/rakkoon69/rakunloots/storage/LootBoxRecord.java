package me.rakkoon69.rakunloots.storage;

import me.rakkoon69.rakunloots.roll.RolledContents;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What a restart keeps of one loot box: its rolled contents, owner, location, placed block, loot id, and remaining
 * timers, and how it was summoned.
 * It does not snapshot the loot. Open mode, lock length, life, loot end rules, and presentation come from the
 * current config snapshot.
 */
public final class LootBoxRecord {

    private final UUID boxId;
    private final String lootId;
    private final Optional<UUID> owner;
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final String placedBlockId;
    private final OptionalInt remainingLifeSeconds;
    private final int remainingLockSeconds;
    private final RolledContents contents;
    private final SummonKind source;

    public LootBoxRecord(
            UUID boxId,
            String lootId,
            SummonKind source,
            Optional<UUID> owner,
            UUID worldId,
            int x,
            int y,
            int z,
            String placedBlockId,
            OptionalInt remainingLifeSeconds,
            int remainingLockSeconds,
            RolledContents contents
    ) {
        this.boxId = Objects.requireNonNull(boxId, "boxId");
        this.lootId = Objects.requireNonNull(lootId, "lootId");
        this.source = Objects.requireNonNull(source, "source");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.x = x;
        this.y = y;
        this.z = z;
        this.placedBlockId = Objects.requireNonNull(placedBlockId, "placedBlockId");
        if (placedBlockId.isBlank()) {
            throw new IllegalArgumentException("placedBlockId must not be blank");
        }
        this.remainingLifeSeconds = Objects.requireNonNull(remainingLifeSeconds, "remainingLifeSeconds");
        remainingLifeSeconds.ifPresent(seconds -> {
            if (seconds < 0) {
                throw new IllegalArgumentException("remainingLifeSeconds must not be negative");
            }
        });
        if (remainingLockSeconds < 0) {
            throw new IllegalArgumentException("remainingLockSeconds must not be negative");
        }
        this.remainingLockSeconds = remainingLockSeconds;
        this.contents = Objects.requireNonNull(contents, "contents");
    }

    /**
     * Set at summon. Identifies this box in storage.
     */
    public UUID boxId() {
        return boxId;
    }

    public String lootId() {
        return lootId;
    }

    /**
     * How the box was summoned. {@link SummonKind#UNKNOWN} for a box stored before this was kept.
     */
    public SummonKind source() {
        return source;
    }

    /**
     * The player the lock admits. Empty means no owner: only the bypass permission opens a locked box.
     */
    public Optional<UUID> owner() {
        return owner;
    }

    public UUID worldId() {
        return worldId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /**
     * The namespaced block id the box was placed as, such as {@code minecraft:barrel}. A changed block is checked
     * against this, not against the current loot, so a loot edit does not end live boxes.
     */
    public String placedBlockId() {
        return placedBlockId;
    }

    /**
     * Life left in seconds. Empty means unending life. Counts down only while the server runs.
     */
    public OptionalInt remainingLifeSeconds() {
        return remainingLifeSeconds;
    }

    /**
     * Lock left in seconds. 0 means no lock. Counts down only while the server runs.
     */
    public int remainingLockSeconds() {
        return remainingLockSeconds;
    }

    public RolledContents contents() {
        return contents;
    }

    /**
     * This box with another remaining life. Empty means unending life.
     */
    LootBoxRecord withRemainingLife(OptionalInt remainingLifeSeconds) {
        return new LootBoxRecord(boxId, lootId, source, owner, worldId, x, y, z, placedBlockId, remainingLifeSeconds,
                remainingLockSeconds, contents);
    }
}
