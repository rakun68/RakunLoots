package me.rakkoon69.rakunloots.items;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hit or miss from resolving an item reference. A miss never carries an item.
 */
public final class ItemResolveResult {

    private final boolean hit;
    private final String source;
    private final String id;
    private final Optional<ItemStack> item;
    private final Optional<MissReason> missReason;
    private final List<ItemWarning> warnings;

    private ItemResolveResult(
            boolean hit,
            String source,
            String id,
            Optional<ItemStack> item,
            Optional<MissReason> missReason,
            List<ItemWarning> warnings
    ) {
        this.hit = hit;
        this.source = source;
        this.id = id;
        this.item = Objects.requireNonNull(item, "item");
        this.missReason = Objects.requireNonNull(missReason, "missReason");
        this.warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public static ItemResolveResult hit(String source, String id, ItemStack item) {
        return hit(source, id, item, List.of());
    }

    public static ItemResolveResult hit(String source, String id, ItemStack item, List<ItemWarning> warnings) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        return new ItemResolveResult(
                true,
                source,
                id,
                Optional.of(item),
                Optional.empty(),
                warnings == null ? List.of() : warnings
        );
    }

    public static ItemResolveResult miss(MissReason reason, ItemWarning warning) {
        return miss(null, null, reason, warning);
    }

    public static ItemResolveResult miss(String source, String id, MissReason reason, ItemWarning warning) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(warning, "warning");
        return new ItemResolveResult(
                false,
                source,
                id,
                Optional.empty(),
                Optional.of(reason),
                List.of(warning)
        );
    }

    public boolean isHit() {
        return hit;
    }

    public boolean isMiss() {
        return !hit;
    }

    public String source() {
        return source;
    }

    public String id() {
        return id;
    }

    public Optional<ItemStack> item() {
        return item;
    }

    public Optional<MissReason> missReason() {
        return missReason;
    }

    public List<ItemWarning> warnings() {
        return warnings;
    }
}
