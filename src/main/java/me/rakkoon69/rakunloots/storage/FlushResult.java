package me.rakkoon69.rakunloots.storage;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What one flush wrote and deleted, what stays pending after a failure, and the warnings it found.
 */
public final class FlushResult {

    private final Set<UUID> written;
    private final Set<UUID> deleted;
    private final Set<UUID> failed;
    private final List<LootBoxWarning> warnings;

    FlushResult(Set<UUID> written, Set<UUID> deleted, Set<UUID> failed, List<LootBoxWarning> warnings) {
        this.written = Set.copyOf(Objects.requireNonNull(written, "written"));
        this.deleted = Set.copyOf(Objects.requireNonNull(deleted, "deleted"));
        this.failed = Set.copyOf(Objects.requireNonNull(failed, "failed"));
        this.warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    /**
     * Boxes this flush wrote.
     */
    public Set<UUID> written() {
        return written;
    }

    /**
     * Boxes whose queued delete this flush wrote. They no longer load.
     */
    public Set<UUID> deleted() {
        return deleted;
    }

    /**
     * Boxes whose change or queued delete this flush could not write. They stay pending, and the next flush retries
     * them.
     */
    public Set<UUID> failed() {
        return failed;
    }

    public List<LootBoxWarning> warnings() {
        return warnings;
    }
}
