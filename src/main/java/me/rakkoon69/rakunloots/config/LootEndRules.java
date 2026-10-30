package me.rakkoon69.rakunloots.config;

/**
 * What a loot allows when a loot box ends.
 */
public final class LootEndRules {

    private final boolean allowExplosion;
    private final boolean dropBlock;
    private final boolean timeoutSpill;
    private final boolean emptyRemoval;

    LootEndRules(
            boolean allowExplosion,
            boolean dropBlock,
            boolean timeoutSpill,
            boolean emptyRemoval
    ) {
        this.allowExplosion = allowExplosion;
        this.dropBlock = dropBlock;
        this.timeoutSpill = timeoutSpill;
        this.emptyRemoval = emptyRemoval;
    }

    public static LootEndRules allOff() {
        return new LootEndRules(false, false, false, false);
    }

    public boolean allowExplosion() {
        return allowExplosion;
    }

    public boolean dropBlock() {
        return dropBlock;
    }

    public boolean timeoutSpill() {
        return timeoutSpill;
    }

    public boolean emptyRemoval() {
        return emptyRemoval;
    }
}
