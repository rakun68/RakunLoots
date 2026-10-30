package me.rakkoon69.rakunloots.command;

import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A sample root over the shipped messages: {@code rkl give <loot> [player] [amount]}, {@code rkl remove} with
 * {@code world <world>} and {@code near <radius>}, {@code rkl timer <world> now [--force]}, {@code rkl whois
 * <player> <online>}, and the shipped {@code help}. Each handler records the line it was given. The messages of the
 * sample nodes sit in test text over the shipped messages, which cover everything else.
 */
final class SampleCommands {

    static final String BASE = "rakunloots.command";
    static final String PREFIX = "&f[&bRakunLoots&f]&7";

    private static final Map<String, String> SAMPLE_TEXT = Map.ofEntries(
            Map.entry("command_give_summary", "Gives summon items."),
            Map.entry("command_give_description", "Gives a player the summon item of a loot."),
            Map.entry("command_give_example", "/rkl give airdrops Steve 2"),
            Map.entry("command_give_arg_loot", "The loot whose summon item to give."),
            Map.entry("command_give_arg_player", "Who gets it. You, when left out."),
            Map.entry("command_give_arg_amount", "How many. 1 when left out."),
            Map.entry("command_remove_summary", "Removes loot boxes."),
            Map.entry("command_remove_description", "Ends loot boxes as admin removals."),
            Map.entry("command_remove_example", "/rkl remove near 20"),
            Map.entry("command_remove_world_summary", "Removes the loot boxes of one world."),
            Map.entry("command_remove_world_description", "Ends every loot box in the world as an admin removal."),
            Map.entry("command_remove_world_example", "/rkl remove world world_nether"),
            Map.entry("command_remove_world_arg_world", "The world to clear."),
            Map.entry("command_remove_near_summary", "Removes the loot boxes around you."),
            Map.entry("command_remove_near_description", "Ends every loot box within the radius."),
            Map.entry("command_remove_near_example", "/rkl remove near 20"),
            Map.entry("command_remove_near_arg_radius", "How far from you, in blocks."),
            Map.entry("command_timer_summary", "Runs a world's timer now."),
            Map.entry("command_timer_description", "Resets the world timer and runs one timer summon."),
            Map.entry("command_timer_example", "/rkl timer world now --force"),
            Map.entry("command_timer_arg_world", "The world whose timer to run."),
            Map.entry("command_timer_arg_when", "Only now."),
            Map.entry("command_timer_arg_force", "Skips the player count and the spawn limit."),
            Map.entry("command_whois_summary", "Names a player."),
            Map.entry("command_whois_description", "Shows who a player is."),
            Map.entry("command_whois_example", "/rkl whois Steve Alex"),
            Map.entry("command_whois_arg_player", "Any player the server knows."),
            Map.entry("command_whois_arg_online", "A player who is online.")
    );

    final CommandRegistry registry;
    final List<String> ran = new ArrayList<>();
    final FakeCommandSources sources = new FakeCommandSources();

    SampleCommands() {
        var snapshot = ShippedHologramConfig.load();
        CommandMessages messages = key -> SAMPLE_TEXT.getOrDefault(key, snapshot.message("", key));
        registry = new CommandRegistry(sources, messages);
        registry.register(root());
    }

    CommandNode root() {
        return CommandNode.builder("rkl")
                .aliases("rkloots")
                .permissionBase(BASE)
                .child(CommandNode.builder("give")
                        .argument(CommandArgument.required("loot", LootIdArgument.INSTANCE))
                        .argument(CommandArgument.optional("player", PlayerArgument.INSTANCE))
                        .argument(CommandArgument.optional("amount", new IntegerArgument(1, 64)))
                        .handler(inv -> ran.add("give " + inv.get("loot", String.class)
                                + " " + inv.find("player", String.class).orElse("-")
                                + " " + inv.find("amount", Integer.class).orElse(0)))
                        .build())
                .child(CommandNode.builder("remove")
                        .child(CommandNode.builder("world")
                                .argument(CommandArgument.required("world", WorldArgument.INSTANCE))
                                .handler(inv -> ran.add("remove world " + inv.get("world", String.class)))
                                .build())
                        .child(CommandNode.builder("near")
                                .argument(CommandArgument.required("radius", new RadiusArgument(100)))
                                .handler(inv -> ran.add("remove near " + inv.get("radius", Double.class)))
                                .build())
                        .build())
                .child(CommandNode.builder("timer")
                        .aliases("t")
                        .argument(CommandArgument.required("world", WorldArgument.INSTANCE))
                        .argument(CommandArgument.required("when", new ChoiceArgument("now")))
                        .argument(CommandArgument.optional("force", new ChoiceArgument("--force")))
                        .handler(inv -> ran.add("timer " + inv.get("world", String.class)
                                + " " + inv.has("force")))
                        .build())
                .child(CommandNode.builder("whois")
                        .argument(CommandArgument.required("player", PlayerArgument.INSTANCE))
                        .argument(CommandArgument.required("online", OnlinePlayerArgument.INSTANCE))
                        .handler(inv -> ran.add("whois " + inv.get("player", String.class)
                                + " " + inv.get("online", String.class)))
                        .build())
                .child(HelpCommand.node())
                .build();
    }

    FakeCommandSender admin() {
        return new FakeCommandSender("Admin",
                BASE + ".give", BASE + ".remove", BASE + ".remove.world", BASE + ".remove.near",
                BASE + ".timer", BASE + ".whois", BASE + ".help");
    }

    List<String> dispatch(CommandSender sender, String line) {
        FakeCommandSender fake = (FakeCommandSender) sender;
        int before = fake.messages().size();
        registry.dispatch(sender, line);
        return fake.messages().subList(before, fake.messages().size());
    }
}
