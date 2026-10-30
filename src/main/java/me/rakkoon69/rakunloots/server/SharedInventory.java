package me.rakkoon69.rakunloots.server;

import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * One inventory that several players view at once, made by a server adapter. A stack one player takes is gone for
 * every other viewer, and gone from {@link #contents()}.
 */
public interface SharedInventory {

    /**
     * The title players see, in legacy color codes.
     */
    String title();

    /**
     * The number of slots: a multiple of 9, from 9 to 54.
     */
    int size();

    /**
     * A copy of each stack by slot, for the slots that hold one now. Call it on the thread that owns the loot box.
     */
    Map<Integer, ItemStack> contents();
}
