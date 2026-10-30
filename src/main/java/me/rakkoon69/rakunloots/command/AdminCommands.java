package me.rakkoon69.rakunloots.command;

import me.rakkoon69.rakunloots.animation.AnimationService;
import me.rakkoon69.rakunloots.config.AdminCommandSettings;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.warning.WarningRecord;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.lootbox.SummonService;
import me.rakkoon69.rakunloots.lootbox.WorldTimers;
import me.rakkoon69.rakunloots.visuals.BoxVisualsService;
import me.rakkoon69.rakunloots.server.ServerAdapter;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The admin root: {@code help}, {@code reload}, {@code list}, {@code give}, {@code summon}, {@code remove},
 * {@code timer}, and {@code warnings}, under the name and aliases of the {@code admin_command} block of {@code config.yml}. Each command
 * has its own permission, {@code rakunloots.command.<path>}.
 */
public final class AdminCommands {

    /**
     * The prefix of every admin command's permission.
     */
    public static final String PERMISSION_BASE = "rakunloots.command";

    private AdminCommands() {
    }

    /**
     * What the admin commands act on.
     *
     * @param configLoader reads the config files and returns the new snapshot, for {@code reload}
     * @param search        takes the reloaded config into the summon search
     * @param menus         resets the menu generation and closes every menu, after a reload succeeds or fails late
     * @param giver        gives summon items, for {@code give}
     */
    public record Parts(
            Supplier<ConfigSnapshot> configLoader,
            ServerAdapter adapter,
            CommandSources sources,
            LootBoxLifecycle lifecycle,
            SummonService summons,
            WorldTimers timers,
            HologramService holograms,
            AnimationService animations,
            BoxVisualsService visuals,
            Consumer<ConfigSnapshot> search,
            Consumer<ConfigSnapshot> menus,
            GiveCommand.Giver giver,
            WarningRecord warnings
    ) {
    }

    public static CommandNode root(AdminCommandSettings settings, Parts parts) {
        return CommandNode.builder(settings.name())
                .aliases(settings.aliases().toArray(String[]::new))
                .permissionBase(PERMISSION_BASE)
                .child(HelpCommand.node())
                .child(ReloadCommand.node(parts.configLoader(), parts.summons(), parts.lifecycle(), parts.timers(),
                        parts.holograms(), parts.animations(), parts.visuals(), parts.search(), parts.menus()))
                .child(ListCommand.node(parts.sources()))
                .child(GiveCommand.node(parts.giver(), parts.sources()))
                .child(SummonCommand.node(parts.summons(), parts.adapter(), parts.sources()))
                .child(RemoveCommand.node(parts.lifecycle(), parts.sources()))
                .child(TimerCommand.node(parts.timers()))
                .child(WarningsCommand.node(parts.warnings()))
                .build();
    }
}
