package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.items.ItemReference;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable admin-authored loot: open mode, lock, life, loot end rules, roll, and presentation.
 * Takes no server types. Message overrides stay on the config snapshot.
 */
public final class Loot {

    private final String id;
    private final OpenMode openMode;
    private final int lockSeconds;
    private final OptionalInt lifeSeconds;
    private final LootEndRules lootEnd;
    private final String displayName;
    private final List<String> description;
    private final String block;
    private final Optional<String> headData;
    private final Roll roll;
    private final int summonDelayTicks;
    private final boolean messageOwnerOnRemove;
    private final Optional<ItemReference> summonItem;

    Loot(
            String id,
            OpenMode openMode,
            int lockSeconds,
            OptionalInt lifeSeconds,
            LootEndRules lootEnd,
            String displayName,
            List<String> description,
            String block,
            Optional<String> headData,
            Roll roll,
            int summonDelayTicks,
            boolean messageOwnerOnRemove,
            Optional<ItemReference> summonItem
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.openMode = Objects.requireNonNull(openMode, "openMode");
        if (lockSeconds < 0) {
            throw new IllegalArgumentException("lockSeconds must not be negative");
        }
        this.lockSeconds = lockSeconds;
        this.lifeSeconds = Objects.requireNonNull(lifeSeconds, "lifeSeconds");
        lifeSeconds.ifPresent(seconds -> {
            if (seconds < 0) {
                throw new IllegalArgumentException("lifeSeconds must not be negative");
            }
        });
        this.lootEnd = Objects.requireNonNull(lootEnd, "lootEnd");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Collections.unmodifiableList(List.copyOf(Objects.requireNonNull(description, "description")));
        this.block = Objects.requireNonNull(block, "block");
        this.headData = Objects.requireNonNull(headData, "headData");
        this.roll = Objects.requireNonNull(roll, "roll");
        if (summonDelayTicks < 0) {
            throw new IllegalArgumentException("summonDelayTicks must not be negative");
        }
        this.summonDelayTicks = summonDelayTicks;
        this.messageOwnerOnRemove = messageOwnerOnRemove;
        this.summonItem = Objects.requireNonNull(summonItem, "summonItem");
    }

    public String id() {
        return id;
    }

    public OpenMode openMode() {
        return openMode;
    }

    /**
     * Lock duration in seconds. 0 means no lock. Never negative.
     */
    public int lockSeconds() {
        return lockSeconds;
    }

    /**
     * Life in seconds. Empty means unending life. Never a negative value.
     */
    public OptionalInt lifeSeconds() {
        return lifeSeconds;
    }

    public LootEndRules lootEnd() {
        return lootEnd;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> description() {
        return description;
    }

    public String block() {
        return block;
    }

    public Optional<String> headData() {
        return headData;
    }

    public Roll roll() {
        return roll;
    }

    /**
     * Ticks from the summon's spot being found to its block being written, so a summon animation can play. 0 means
     * no wait. Never negative.
     */
    public int summonDelayTicks() {
        return summonDelayTicks;
    }

    /**
     * Whether the box's owner is told, with the {@code message_owner_on_remove} message, when the box ends. Off when
     * the loot file leaves {@code message_owner_on_remove_enable} out.
     */
    public boolean messageOwnerOnRemove() {
        return messageOwnerOnRemove;
    }

    /**
     * The item that summons this loot around the player who uses it, as an item reference with vanilla additions, or
     * empty when the loot has none.
     */
    public Optional<ItemReference> summonItem() {
        return summonItem;
    }
}
