package me.rakkoon69.rakunloots.storage;

import java.util.Objects;

/**
 * One box as load found it, with its flags. A flagged box stays in storage; nothing deletes it silently.
 */
public final class LoadedBox {

    private final LootBoxRecord box;
    private final boolean lootMissing;
    private final boolean worldNotLoaded;

    LoadedBox(LootBoxRecord box, boolean lootMissing, boolean worldNotLoaded) {
        this.box = Objects.requireNonNull(box, "box");
        this.lootMissing = lootMissing;
        this.worldNotLoaded = worldNotLoaded;
    }

    /**
     * The stored box. Its remaining life is already capped by the current loot's life.
     */
    public LootBoxRecord box() {
        return box;
    }

    /**
     * The box's loot is not in the current config.
     */
    public boolean lootMissing() {
        return lootMissing;
    }

    /**
     * The box's world was not loaded when the box loaded.
     */
    public boolean worldNotLoaded() {
        return worldNotLoaded;
    }
}
