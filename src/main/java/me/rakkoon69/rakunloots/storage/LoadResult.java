package me.rakkoon69.rakunloots.storage;

import java.util.List;
import java.util.Objects;

/**
 * Every box that loaded, plus the warnings load found.
 */
public final class LoadResult {

    private final List<LoadedBox> boxes;
    private final List<LootBoxWarning> warnings;

    LoadResult(List<LoadedBox> boxes, List<LootBoxWarning> warnings) {
        this.boxes = List.copyOf(Objects.requireNonNull(boxes, "boxes"));
        this.warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public List<LoadedBox> boxes() {
        return boxes;
    }

    public List<LootBoxWarning> warnings() {
        return warnings;
    }
}
