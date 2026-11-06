package me.rakkoon69.rakunloots.command;

import me.rakkoon69.rakunloots.command.argument.ChoiceArgument;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.rakkoon69.rakunloots.command.SampleCommands.BASE;
import static me.rakkoon69.rakunloots.command.SampleCommands.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code help} over the sample tree and the shipped English messages: the list of what a sender can run, and every part
 * of one command's help.
 */
class HelpCommandTest {

    private static final String DIVIDER = "&8&m-----------------------------------------------------";

    private final SampleCommands sample = new SampleCommands();
    private final FakeCommandSender admin = sample.admin();

    @Test
    void helpListsEachCommandTheSenderCanRunOnOneLineWithItsUsageAndSummaryBetweenDividers() {
        assertEquals(List.of(
                DIVIDER,
                " &b&lRakunLoots &8» &fCommands &8(&76&8)",
                " &8• &b/rkl give&3 <loot> [player] [amount] &8- &7Gives summon items.",
                " &8• &b/rkl remove world&3 <world> &8- &7Removes the loot boxes of one world.",
                " &8• &b/rkl remove near&3 <radius> &8- &7Removes the loot boxes around you.",
                " &8• &b/rkl timer&3 <world> now [--force] &8- &7Runs a world's timer now.",
                " &8• &b/rkl whois&3 <player> <online> &8- &7Names a player.",
                " &8• &b/rkl help&3 [command] &8- &7Lists the commands you can run, or explains one.",
                " &7Run &b/rkl help [command] &7to see how one works.",
                DIVIDER
        ), sample.dispatch(admin, "rkl help"));
    }

    @Test
    void helpLeavesOutWhatTheSenderCannotRun() {
        FakeCommandSender limited = new FakeCommandSender("Helper", BASE + ".give", BASE + ".remove",
                BASE + ".remove.near", BASE + ".help");

        assertEquals(List.of(
                DIVIDER,
                " &b&lRakunLoots &8» &fCommands &8(&73&8)",
                " &8• &b/rkl give&3 <loot> [player] [amount] &8- &7Gives summon items.",
                " &8• &b/rkl remove near&3 <radius> &8- &7Removes the loot boxes around you.",
                " &8• &b/rkl help&3 [command] &8- &7Lists the commands you can run, or explains one.",
                " &7Run &b/rkl help [command] &7to see how one works.",
                DIVIDER
        ), sample.dispatch(limited, "rkl help"));
    }

    @Test
    void helpNamesTheOtherRootsItWasGivenThatTheSenderCanRunUnderTheirOwnNames() {
        CommandNode wiki = CommandNode.builder("loots").permission("rakunloots.wiki").helpKeys("command_wiki")
                .handler(inv -> { }).build();
        CommandNode claim = CommandNode.builder("claimsummon").permission("rakunloots.claim")
                .helpKeys("command_claim").handler(inv -> { })
                .child(CommandNode.builder("all").permission("rakunloots.claim").handler(inv -> { }).build())
                .build();
        CommandNode open = CommandNode.builder("open").permissionBase(BASE)
                .child(HelpCommand.node(List.of(wiki, claim)))
                .build();
        sample.registry.register(open);
        FakeCommandSender helper = new FakeCommandSender("Helper", BASE + ".help", "rakunloots.wiki");

        assertEquals(List.of(
                DIVIDER,
                " &b&lRakunLoots &8» &fCommands &8(&72&8)",
                " &8• &b/open help&3 [command] &8- &7Lists the commands you can run, or explains one.",
                " &fOther commands:",
                " &8• &b/loots&3 &8- &7Opens the loots wiki: every loot in your world and its chance.",
                " &7Run &b/open help [command] &7to see how one works.",
                DIVIDER
        ), sample.dispatch(helper, "open help"));

        FakeCommandSender both = new FakeCommandSender("Both", BASE + ".help", "rakunloots.wiki", "rakunloots.claim");
        assertEquals(" &8• &b/claimsummon&3 [all] &8- &7Opens the summon items waiting for you. Add all to take"
                + " them all.", sample.dispatch(both, "open help").get(5));
    }

    @Test
    void helpOfACommandShowsUsageDescriptionEachArgumentAndItsTypeAnExampleAndThePermission() {
        assertEquals(List.of(
                DIVIDER,
                " &b&lRakunLoots &8» &b/rkl give&3 <loot> [player] [amount]",
                " &7Gives a player the summon item of a loot.",
                " &8• &floot &8(&7a loot id&8) &7The loot whose summon item to give.",
                " &8• &fplayer &8(&7a player name&8) &7Who gets it. You, when left out.",
                " &8• &famount &8(&7a whole number from 1 to 64&8) &7How many. 1 when left out.",
                " &7Example: &b/rkl give airdrops Steve 2",
                " &7Permission: &erakunloots.command.give",
                DIVIDER
        ), sample.dispatch(admin, "rkl help give"));
    }

    @Test
    void helpOfANestedCommandTakesItsWordsAndUsesTheLabelTheSenderTyped() {
        assertEquals(List.of(
                DIVIDER,
                " &b&lRakunLoots &8» &b/rkloots remove world&3 <world>",
                " &7Ends every loot box in the world as an admin removal.",
                " &8• &fworld &8(&7a world name&8) &7The world to clear.",
                " &7Example: &b/rkl remove world world_nether",
                " &7Permission: &erakunloots.command.remove.world",
                DIVIDER
        ), sample.dispatch(admin, "rkloots help remove world"));
    }

    @Test
    void helpOfACommandWithChildrenListsTheChildrenTheSenderCanRun() {
        assertEquals(List.of(
                DIVIDER,
                " &b&lRakunLoots &8» &b/rkl remove&3 <world|near>",
                " &7Ends loot boxes as admin removals.",
                " &7Example: &b/rkl remove near 20",
                " &7Permission: &erakunloots.command.remove",
                " &fSubcommands:",
                " &8• &b/rkl remove world&3 <world> &8- &7Removes the loot boxes of one world.",
                " &8• &b/rkl remove near&3 <radius> &8- &7Removes the loot boxes around you.",
                DIVIDER
        ), sample.dispatch(admin, "rkl help remove"));
    }

    @Test
    void helpOfHelpShowsTheShippedMessages() {
        assertEquals(List.of(
                DIVIDER,
                " &b&lRakunLoots &8» &b/rkl help&3 [command]",
                " &7Without a command it lists every command you can run. With a command it shows how to use that"
                        + " command.",
                " &8• &fcommand &8(&7a command name&8) &7The command to explain, such as remove world. Leave it"
                        + " out to list every command.",
                " &7Example: &b/rkl help remove world",
                " &7Permission: &erakunloots.command.help",
                DIVIDER
        ), sample.dispatch(admin, "rkl help help"));
    }

    @Test
    void helpOfACommandThatEveryoneCanRunNamesEveryoneAsItsPermission() {
        CommandNode open = CommandNode.builder("open").permissionBase(BASE)
                .child(CommandNode.builder("ping").everyone().handler(inv -> { }).build())
                .child(HelpCommand.node())
                .build();
        sample.registry.register(open);
        FakeCommandSender helper = new FakeCommandSender("Helper", BASE + ".help");

        List<String> shown = sample.dispatch(helper, "open help ping");

        assertEquals(" &b&lRakunLoots &8» &b/open ping&3", shown.get(1));
        assertEquals(" &7Permission: &eeveryone", shown.get(shown.size() - 2));
    }

    @Test
    void helpOfACommandTheSenderCannotRunAnswersAsUnknownWithCloseMatchesFromWhatTheyCanRun() {
        FakeCommandSender limited = new FakeCommandSender("Helper", BASE + ".give", BASE + ".help");

        assertEquals(List.of(
                PREFIX + " &f/rkl help &c&ntimer&r&f",
                "&8> &cthere is no command &ftimer&c that you can run&7, expected &ea command name&7."
        ), sample.dispatch(limited, "rkl help timer"));
        assertEquals(List.of(
                PREFIX + " &f/rkl help &c&nhelpp&r&f",
                "&8> &cthere is no command &fhelpp&c that you can run&7, expected &ea command name&7."
                        + " Did you mean &ehelp&7?"
        ), sample.dispatch(limited, "rkl help helpp"));
    }

    @Test
    void helpCompletesTheWordsOfACommandThatTheSenderCanSee() {
        assertEquals(List.of("give", "help", "remove", "timer", "whois"), sample.registry.complete(admin, "rkl help "));
        assertEquals(List.of("remove"), sample.registry.complete(admin, "rkl help re"));
        assertEquals(List.of("near", "world"), sample.registry.complete(admin, "rkl help remove "));
        assertEquals(List.of("world"), sample.registry.complete(admin, "rkl help remove w"));
        assertEquals(List.of(), sample.registry.complete(admin, "rkl help nonesuch "));

        FakeCommandSender limited = new FakeCommandSender("Helper", BASE + ".give", BASE + ".help");
        assertEquals(List.of("give", "help"), sample.registry.complete(limited, "rkl help "));
    }

    @Test
    void aChoiceWithSeveralWordsKeepsItsNameInUsageWhileOneWithASingleWordShowsThatWord() {
        assertEquals("<mode>", CommandArgument.required("mode", new ChoiceArgument("a", "b")).usage());
        assertEquals("[mode]", CommandArgument.optional("mode", new ChoiceArgument("a", "b")).usage());
        assertEquals("now", CommandArgument.required("when", new ChoiceArgument("now")).usage());
        assertEquals("[--force]", CommandArgument.optional("force", new ChoiceArgument("--force")).usage());
    }
}
