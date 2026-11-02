package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandArgument;
import me.rakkoon69.rakunloots.command.CommandInvocation;
import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.argument.ChoiceArgument;
import me.rakkoon69.rakunloots.command.argument.WorldArgument;
import me.rakkoon69.rakunloots.lootbox.LootBox;
import me.rakkoon69.rakunloots.summon.SummonReport;
import me.rakkoon69.rakunloots.summon.TimerOutcome;
import me.rakkoon69.rakunloots.summon.TimerRefusal;
import me.rakkoon69.rakunloots.summon.WorldTimers;

import java.util.Map;
import java.util.Optional;

/**
 * The {@code timer <world> now [--force]} node. It starts that world's timer full again and runs one timer summon
 * with the timer's usual checks. A refusal is told with its reason. {@code --force} skips the minimum player count and
 * the spawn limit, and nothing else.
 */
final class TimerCommand {

    private static final String NOW = "now";
    private static final String FORCE = "--force";

    private TimerCommand() {
    }

    static CommandNode node(WorldTimers timers) {
        return CommandNode.builder("timer")
                .argument(CommandArgument.required("world", WorldArgument.INSTANCE))
                .argument(CommandArgument.required(NOW, new ChoiceArgument(NOW)))
                .argument(CommandArgument.optional("force", new ChoiceArgument(FORCE)))
                .handler(invocation -> now(invocation, timers))
                .build();
    }

    private static void now(CommandInvocation invocation, WorldTimers timers) {
        String world = invocation.get("world", String.class);
        boolean force = invocation.has("force");
        timers.reset(world);
        timers.summonNow(world, force).whenComplete((outcome, failure) -> {
            if (failure != null) {
                invocation.reply("cmd_timer_failed", Map.of("world", world));
            } else {
                reply(invocation, world, outcome);
            }
        });
    }

    private static void reply(CommandInvocation invocation, String world, TimerOutcome outcome) {
        Optional<TimerRefusal> refusal = outcome.refusal();
        if (refusal.isPresent()) {
            invocation.reply(refusalKey(refusal.get()), Map.of("world", world));
            return;
        }
        SummonReport report = outcome.report().orElseThrow();
        Optional<LootBox> box = report.box();
        if (box.isPresent()) {
            invocation.reply("cmd_timer_done", Map.of("world", world, "loot", box.get().lootId(),
                    "x", Integer.toString(box.get().position().x()),
                    "y", Integer.toString(box.get().position().y()),
                    "z", Integer.toString(box.get().position().z())));
            return;
        }
        invocation.reply("cmd_timer_abandoned", Map.of("world", world, "reason", report.message()));
    }

    private static String refusalKey(TimerRefusal refusal) {
        return switch (refusal) {
            case WORLD_UNKNOWN -> "cmd_timer_world_unknown";
            case WORLD_NOT_LOADED -> "cmd_timer_world_not_loaded";
            case BELOW_MINIMUM_PLAYERS -> "cmd_timer_below_minimum_players";
            case AT_SPAWN_LIMIT -> "cmd_timer_at_spawn_limit";
            case NO_LOOT -> "cmd_timer_no_loot";
        };
    }
}
