package me.rakkoon69.rakunloots.items;

/**
 * A named way to resolve an item id. Third-party resolvers receive the id only.
 */
@FunctionalInterface
public interface ItemSource {

    ItemResolveResult resolve(String id);
}
