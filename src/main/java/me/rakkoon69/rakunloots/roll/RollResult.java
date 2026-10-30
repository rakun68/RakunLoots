package me.rakkoon69.rakunloots.roll;

import java.util.List;
import java.util.Objects;

/**
 * Rolled contents plus the warnings the roll found.
 */
public final class RollResult {

    private final RolledContents contents;
    private final List<RollWarning> warnings;

    RollResult(RolledContents contents, List<RollWarning> warnings) {
        this.contents = Objects.requireNonNull(contents, "contents");
        this.warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public RolledContents contents() {
        return contents;
    }

    public List<RollWarning> warnings() {
        return warnings;
    }
}
