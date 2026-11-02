package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandArgument;
import me.rakkoon69.rakunloots.command.CommandInvocation;
import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.CommandSources;
import me.rakkoon69.rakunloots.command.argument.LootIdArgument;
import me.rakkoon69.rakunloots.command.argument.WorldOrPlayerArgument;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.summon.SummonReport;
import me.rakkoon69.rakunloots.summon.SummonService;
import me.rakkoon69.rakunloots.summon.SummonTarget;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.PlayerPosition;
import me.rakkoon69.rakunloots.server.SenderLocation;
import me.rakkoon69.rakunloots.server.ServerAdapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code summon <loot> [world|player]} node, through the summon service's admin summon. With a world it runs
 * that world's summon search. With a player it summons in the loot's player ring around that player, in the world the
 * player is in. Either way the box has no owner. Without a target a player sender searches their own world, and the
 * console must name one. The reply comes when the summon ends, which is after the loot's summon delay.
 */
final class SummonCommand {

    private SummonCommand() {
    }

    static CommandNode node(SummonService service, ServerAdapter adapter, CommandSources sources) {
        return CommandNode.builder("summon")
                .argument(CommandArgument.required("loot", LootIdArgument.INSTANCE))
                .argument(CommandArgument.optional("target", WorldOrPlayerArgument.INSTANCE))
                .handler(invocation -> summon(invocation, service, adapter, sources))
                .build();
    }

    private static void summon(CommandInvocation invocation, SummonService service, ServerAdapter adapter,
                               CommandSources sources) {
        String lootId = invocation.get("loot", String.class);
        Optional<String> typed = invocation.find("target", String.class);
        CompletableFuture<Optional<SummonTarget>> target;
        if (typed.isEmpty()) {
            Optional<SenderLocation> here = invocation.sender().location();
            if (here.isEmpty()) {
                invocation.reply("cmd_summon_needs_target");
                return;
            }
            target = CompletableFuture.completedFuture(Optional.of(SummonTarget.world(here.get().worldKey())));
        } else if (isWorld(sources, typed.get())) {
            Optional<UUID> key = sources.worldKey(typed.get());
            if (key.isEmpty()) {
                invocation.reply("cmd_summon_world_not_loaded", Map.of("world", typed.get()));
                return;
            }
            target = CompletableFuture.completedFuture(Optional.of(SummonTarget.world(key.get())));
        } else {
            Optional<UUID> playerId = sources.playerId(typed.get());
            if (playerId.isEmpty()) {
                invocation.reply("cmd_summon_player_not_online", Map.of("player", typed.get()));
                return;
            }
            target = worldOf(adapter, playerId.get()).thenApply(world ->
                    world.map(key -> SummonTarget.player(key, playerId.get())));
        }
        target.thenCompose(found -> {
            if (found.isEmpty()) {
                invocation.reply("cmd_summon_player_not_online", Map.of("player", typed.orElse("")));
                return CompletableFuture.<SummonReport>completedFuture(null);
            }
            return service.adminSummon(lootId, found.get(), Optional.empty());
        }).whenComplete((report, failure) -> {
            if (failure != null) {
                invocation.reply("cmd_summon_failed", Map.of("loot", lootId));
            } else if (report != null) {
                reply(invocation, lootId, report);
            }
        });
    }

    private static boolean isWorld(CommandSources sources, String name) {
        return sources.worldNames().stream().anyMatch(world -> world.equalsIgnoreCase(name));
    }

    private static void reply(CommandInvocation invocation, String lootId, SummonReport report) {
        Optional<LootBox> box = report.box();
        if (box.isPresent()) {
            invocation.reply("cmd_summon_done", Map.of("loot", lootId,
                    "x", Integer.toString(box.get().position().x()),
                    "y", Integer.toString(box.get().position().y()),
                    "z", Integer.toString(box.get().position().z())));
            return;
        }
        invocation.reply("cmd_summon_abandoned", Map.of("loot", lootId, "reason", report.message()));
    }

    /**
     * The world a player is in, or empty when no loaded world lists them.
     */
    private static CompletableFuture<Optional<UUID>> worldOf(ServerAdapter adapter, UUID playerId) {
        return adapter.loadedWorlds().thenCompose(worlds -> search(adapter, worlds, 0, playerId));
    }

    private static CompletableFuture<Optional<UUID>> search(ServerAdapter adapter, List<LoadedWorld> worlds, int at,
                                                            UUID playerId) {
        if (at >= worlds.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        LoadedWorld world = worlds.get(at);
        return adapter.playerPositions(world.key()).thenCompose(players -> {
            for (PlayerPosition player : players) {
                if (player.playerId().equals(playerId)) {
                    return CompletableFuture.completedFuture(Optional.of(world.key()));
                }
            }
            return search(adapter, worlds, at + 1, playerId);
        });
    }
}
