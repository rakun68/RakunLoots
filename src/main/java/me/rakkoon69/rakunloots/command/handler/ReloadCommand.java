package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.animation.AnimationService;
import me.rakkoon69.rakunloots.command.CommandInvocation;
import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.summon.SummonService;
import me.rakkoon69.rakunloots.summon.WorldTimers;
import me.rakkoon69.rakunloots.visuals.BoxVisualsService;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The {@code reload} node. It abandons every pending summon, which gives summon items back, then reads the config
 * again and hands the new snapshot to the summon service, the lifecycle, the world timers, the hologram service, the
 * animation service, the box visuals service, and the summon search, in that order. A config that cannot be read
 * changes nothing but the abandoned summons.
 */
final class ReloadCommand {

    private ReloadCommand() {
    }

    /**
     * @param configLoader reads the config files and returns the new snapshot
     */
    static CommandNode node(
            Supplier<ConfigSnapshot> configLoader,
            SummonService summons,
            LootBoxLifecycle lifecycle,
            WorldTimers timers,
            HologramService holograms,
            AnimationService animations,
            BoxVisualsService visuals,
            Consumer<ConfigSnapshot> search,
            Consumer<ConfigSnapshot> menus
    ) {
        return CommandNode.builder("reload")
                .handler(invocation -> reload(invocation, configLoader, summons, lifecycle, timers, holograms,
                        animations, visuals, search, menus))
                .build();
    }

    private static void reload(
            CommandInvocation invocation,
            Supplier<ConfigSnapshot> configLoader,
            SummonService summons,
            LootBoxLifecycle lifecycle,
            WorldTimers timers,
            HologramService holograms,
            AnimationService animations,
            BoxVisualsService visuals,
            Consumer<ConfigSnapshot> search,
            Consumer<ConfigSnapshot> menus
    ) {
        ConfigSnapshot snapshot;
        try {
            summons.stop();
            snapshot = configLoader.get();
        } catch (RuntimeException failure) {
            invocation.reply("cmd_reload_failed", Map.of("reason", String.valueOf(failure.getMessage())));
            return;
        }
        try {
            summons.reload(snapshot);
            lifecycle.reload(snapshot);
            timers.reload(snapshot);
            holograms.reload(snapshot);
            animations.reload(snapshot);
            visuals.reload(snapshot);
            search.accept(snapshot);
        } catch (RuntimeException failure) {
            closeMenus(menus, snapshot);
            invocation.reply("cmd_reload_failed", Map.of("reason", String.valueOf(failure.getMessage())));
            return;
        }
        closeMenus(menus, snapshot);
        invocation.reply("cmd_reload_done", Map.of(
                "loots", Integer.toString(snapshot.loots().size()),
                "warnings", Integer.toString(snapshot.warnings().size())));
    }

    private static void closeMenus(Consumer<ConfigSnapshot> menus, ConfigSnapshot snapshot) {
        try {
            menus.accept(snapshot);
        } catch (RuntimeException ignored) {
            // The reload's own failure is the one the admin needs to see.
        }
    }
}
