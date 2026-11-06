package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.animation.AnimationService;
import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.CommandSources;
import me.rakkoon69.rakunloots.command.HelpCommand;
import me.rakkoon69.rakunloots.config.AdminCommandSettings;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.warning.WarningRecord;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.summon.SummonService;
import me.rakkoon69.rakunloots.summon.WorldTimers;
import me.rakkoon69.rakunloots.summon.item.ClaimMenu;
import me.rakkoon69.rakunloots.summon.item.UnclaimedSummonItems;
import me.rakkoon69.rakunloots.visuals.BoxVisualsService;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.wiki.WikiMenu;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The RakunLoots commands, the one entry to this package. The admin root holds {@code help}, {@code reload},
 * {@code list}, {@code give}, {@code summon}, {@code remove}, {@code timer}, and {@code warnings}, under the name and
 * aliases of the {@code admin_command} block of {@code config.yml}. Each admin command has its own permission,
 * {@code rakunloots.command.<path>}. The claim and wiki roots stand alone, each under its own block and permission.
 */
public final class RakunLootsCommands {

    /**
     * The prefix of every admin command's permission.
     */
    public static final String PERMISSION_BASE = "rakunloots.command";

    /**
     * The permission of the claim command, whose default the config names.
     */
    public static final String CLAIM_PERMISSION = ClaimCommand.PERMISSION;

    /**
     * The permission of the wiki command, whose default the config names.
     */
    public static final String WIKI_PERMISSION = WikiCommand.PERMISSION;

    private RakunLootsCommands() {
    }

    /**
     * Where a give goes: to the player's inventory as far as it fits, and the rest as unclaimed summon items.
     */
    @FunctionalInterface
    public interface Giver {

        void give(UUID playerId, String lootId, int amount);
    }

    /**
     * What the commands act on.
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
            Giver giver,
            WarningRecord warnings,
            UnclaimedSummonItems claims,
            ClaimMenu claimMenu,
            WikiMenu wikiMenu
    ) {
    }

    /**
     * Hands the admin, claim, and wiki roots to the registrar, under the names the config gives them.
     */
    public static void register(Consumer<CommandNode> registrar, ConfigSnapshot config, Parts parts) {
        CommandNode claim = ClaimCommand.node(config.claimCommand(), parts.claims(), parts.claimMenu());
        CommandNode wiki = WikiCommand.node(config.wikiCommand(), parts.wikiMenu());
        registrar.accept(root(config.adminCommand(), parts, List.of(wiki, claim)));
        registrar.accept(claim);
        registrar.accept(wiki);
    }

    static CommandNode root(AdminCommandSettings settings, Parts parts) {
        return root(settings, parts, List.of());
    }

    /**
     * @param others the stand-alone roots that {@code help} names after the admin commands
     */
    static CommandNode root(AdminCommandSettings settings, Parts parts, List<CommandNode> others) {
        return CommandNode.builder(settings.name())
                .aliases(settings.aliases().toArray(String[]::new))
                .permissionBase(PERMISSION_BASE)
                .child(HelpCommand.node(others))
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
