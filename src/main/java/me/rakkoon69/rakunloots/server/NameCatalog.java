package me.rakkoon69.rakunloots.server;

import java.util.Optional;
import java.util.Set;

/**
 * Maps a written block or item id to the current id. Callers pass a real or fake catalog.
 * Empty means the catalog does not know the id.
 */
public interface NameCatalog {

    Optional<String> currentId(String writtenId);

    /**
     * Current block ids in a vanilla block tag such as {@code minecraft:flowers}, without the leading {@code #}.
     * Empty means the catalog does not know the tag.
     */
    default Set<String> tagMembers(String namespacedTag) {
        return Set.of();
    }

    /**
     * Whether the server knows this namespaced sound key. A catalog that does not say is strict: an unknown sound is
     * rejected. The real catalog checks the server's sound registry.
     */
    default boolean soundExists(String key) {
        return false;
    }

    static NameCatalog empty() {
        return writtenId -> Optional.empty();
    }
}
