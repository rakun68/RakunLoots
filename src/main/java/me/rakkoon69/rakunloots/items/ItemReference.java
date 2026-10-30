package me.rakkoon69.rakunloots.items;

import java.util.Objects;

/**
 * A source name plus an id that points at one item.
 * A missing or blank source name is the vanilla source.
 * A vanilla reference may also carry vanilla additions.
 */
public final class ItemReference {

    private final String source;
    private final String id;
    private final VanillaAdditions additions;

    private ItemReference(String source, String id, VanillaAdditions additions) {
        this.source = source;
        this.id = Objects.requireNonNull(id, "id");
        this.additions = Objects.requireNonNull(additions, "additions");
    }

    /**
     * @param source null or blank means vanilla
     */
    public static ItemReference of(String source, String id) {
        return of(source, id, VanillaAdditions.none());
    }

    public static ItemReference of(String source, String id, VanillaAdditions additions) {
        return new ItemReference(source, id == null ? "" : id, additions == null ? VanillaAdditions.none() : additions);
    }

    public String source() {
        return source;
    }

    public String id() {
        return id;
    }

    public VanillaAdditions additions() {
        return additions;
    }
}
