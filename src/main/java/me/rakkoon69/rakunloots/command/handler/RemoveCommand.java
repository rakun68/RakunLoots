package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandArgument;
import me.rakkoon69.rakunloots.command.CommandInvocation;
import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.CommandSources;
import me.rakkoon69.rakunloots.command.argument.LootIdArgument;
import me.rakkoon69.rakunloots.command.argument.RadiusArgument;
import me.rakkoon69.rakunloots.command.argument.WorldArgument;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.SenderLocation;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The {@code remove} node: {@code remove all}, {@code remove world <world>}, {@code remove loot <loot>}, and
 * {@code remove near <radius>}. Each ends the chosen loot boxes as an admin removal through the lifecycle, and tells
 * the sender how many ended. {@code near} measures from the sender's position and needs a player sender.
 */
final class RemoveCommand {

    /**
     * The largest radius {@code remove near} takes, in blocks.
     */
    public static final int MAX_RADIUS = 1000;

    private RemoveCommand() {
    }

    static CommandNode node(LootBoxLifecycle lifecycle, CommandSources sources) {
        return CommandNode.builder("remove")
                .child(CommandNode.builder("all")
                        .handler(invocation -> done(invocation, lifecycle.remove(box -> true)))
                        .build())
                .child(CommandNode.builder("world")
                        .argument(CommandArgument.required("world", WorldArgument.INSTANCE))
                        .handler(invocation -> world(invocation, lifecycle, sources))
                        .build())
                .child(CommandNode.builder("loot")
                        .argument(CommandArgument.required("loot", LootIdArgument.INSTANCE))
                        .handler(invocation -> {
                            String lootId = invocation.get("loot", String.class);
                            done(invocation, lifecycle.remove(box -> box.lootId().equals(lootId)));
                        })
                        .build())
                .child(CommandNode.builder("near")
                        .argument(CommandArgument.required("radius", new RadiusArgument(MAX_RADIUS)))
                        .handler(invocation -> near(invocation, lifecycle))
                        .build())
                .build();
    }

    private static void world(CommandInvocation invocation, LootBoxLifecycle lifecycle, CommandSources sources) {
        String name = invocation.get("world", String.class);
        Optional<UUID> key = sources.worldKey(name);
        if (key.isEmpty()) {
            invocation.reply("cmd_remove_world_not_loaded", Map.of("world", name));
            return;
        }
        done(invocation, lifecycle.remove(box -> box.worldKey().equals(key.get())));
    }

    private static void near(CommandInvocation invocation, LootBoxLifecycle lifecycle) {
        Optional<SenderLocation> from = invocation.sender().location();
        if (from.isEmpty()) {
            invocation.reply("cmd_remove_near_needs_player");
            return;
        }
        done(invocation, lifecycle.remove(within(from.get(), invocation.get("radius", Double.class))));
    }

    /**
     * Boxes of the sender's world whose block centre is no farther than the radius from the sender.
     */
    private static Predicate<LootBox> within(SenderLocation here, double radius) {
        return box -> {
            if (!box.worldKey().equals(here.worldKey())) {
                return false;
            }
            BlockPosition at = box.position();
            double dx = at.x() + 0.5 - here.x();
            double dy = at.y() + 0.5 - here.y();
            double dz = at.z() + 0.5 - here.z();
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        };
    }

    private static void done(CommandInvocation invocation, int ended) {
        invocation.reply("cmd_remove_done", Map.of("amount", Integer.toString(ended)));
    }
}
