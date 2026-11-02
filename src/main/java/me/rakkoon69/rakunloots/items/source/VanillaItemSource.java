package me.rakkoon69.rakunloots.items.source;

import me.rakkoon69.rakunloots.items.ItemResolveResult;
import me.rakkoon69.rakunloots.items.ItemSource;
import me.rakkoon69.rakunloots.items.ItemWarning;
import me.rakkoon69.rakunloots.items.MissReason;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves a vanilla material id to a stack of amount 1.
 */
final class VanillaItemSource implements ItemSource {

    static final String NAME = "vanilla";

    @Override
    public ItemResolveResult resolve(String id) {
        if (id == null || id.isBlank()) {
            return ItemResolveResult.miss(NAME, id, MissReason.BLANK_ID, ItemWarning.blankId());
        }
        Material material = Material.matchMaterial(id);
        if (material == null || material.isAir()) {
            return ItemResolveResult.miss(NAME, id, MissReason.UNKNOWN_ID, ItemWarning.unknownId(id));
        }
        return ItemResolveResult.hit(NAME, id, new ItemStack(material, 1));
    }
}
